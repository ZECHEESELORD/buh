package sh.harold.fulcrum.plugin.accountlink;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccountLinkService {

    private static final String DISCORD_ID_PATH = "discordId";
    private static final String OSU_USER_ID_PATH = "osu.userId";
    private static final String OSU_USERNAME_PATH = "osu.username";
    private static final String OSU_RANK_PATH = "osu.rank";
    private static final String OSU_COUNTRY_PATH = "osu.country";
    private static final String DISCORD_LINK_UUID_PATH = "uuid";
    private static final String DISCORD_LINK_USERNAME_PATH = "username";
    private static final String VERIFIED_GROUP = "verified";

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final DocumentCollection tickets;
    private final DocumentCollection discordLinks;
    private final Logger logger;

    public AccountLinkService(JavaPlugin plugin, DocumentCollection players, DocumentCollection tickets, DocumentCollection discordLinks) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.tickets = Objects.requireNonNull(tickets, "tickets");
        this.discordLinks = Objects.requireNonNull(discordLinks, "discordLinks");
        this.logger = plugin.getLogger();
    }

    public CompletionStage<LinkResult> createLink(LinkRequest request) {
        Objects.requireNonNull(request, "request");
        UUID playerId = request.uuid();
        String uuidKey = playerId.toString();
        String discordKey = Long.toString(request.discordId());

        CompletableFuture<Document> playerFuture = players.load(uuidKey).toCompletableFuture();
        CompletableFuture<Document> ticketFuture = tickets.load(uuidKey).toCompletableFuture();
        CompletableFuture<Document> discordFuture = discordLinks.load(discordKey).toCompletableFuture();

        return CompletableFuture.allOf(playerFuture, ticketFuture, discordFuture)
            .thenCompose(ignored -> {
                Document playerDocument = playerFuture.join();
                Document ticketDocument = ticketFuture.join();
                Document discordDocument = discordFuture.join();

                Optional<Long> existingDiscord = readDiscordId(playerDocument);
                if (existingDiscord.isPresent() && existingDiscord.get() != request.discordId()) {
                    return CompletableFuture.completedFuture(new LinkResult.Rejected("That Minecraft account is already linked to another Discord user."));
                }

                Optional<LinkTicket> existingTicket = readTicket(ticketDocument);
                if (existingTicket.isPresent() && existingTicket.get().discordId() != request.discordId()) {
                    return CompletableFuture.completedFuture(new LinkResult.Rejected("That Minecraft account already has a pending link for another Discord user."));
                }

                UUID previousUuid = readDiscordLink(discordDocument);
                CompletionStage<Void> cleanupStage = previousUuid != null && !previousUuid.equals(playerId)
                    ? cleanupPreviousLink(previousUuid)
                    : CompletableFuture.completedFuture(null);

                LinkTicket ticket = new LinkTicket(playerId, request.discordId(), request.username(), request.osu(), Instant.now());

                return cleanupStage
                    .thenCompose(ignoredCleanup -> persistTicket(ticket))
                    .thenCompose(ignoredTicket -> persistDiscordMapping(ticket))
                    .handle((ignoredPersist, throwable) -> {
                        if (throwable != null) {
                            logger.log(Level.SEVERE, "Failed to create link ticket for " + playerId, throwable);
                            return new LinkResult.Rejected("Could not store link; try again soon.");
                        }
                        scheduleWhitelistAndPermissions(ticket.username());
                        return new LinkResult.Success(ticket);
                    });
            });
    }

    public CompletionStage<PreLoginDecision> preLoginDecision(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String uuidKey = playerId.toString();
        CompletableFuture<Document> playerFuture = players.load(uuidKey).toCompletableFuture();
        CompletableFuture<Document> ticketFuture = tickets.load(uuidKey).toCompletableFuture();

        return playerFuture.thenCombine(ticketFuture, (playerDocument, ticketDocument) -> {
            if (hasLinkedAccount(playerDocument)) {
                return PreLoginDecision.allow();
            }
            Optional<LinkTicket> ticket = readTicket(ticketDocument);
            if (ticket.isPresent()) {
                return PreLoginDecision.allowWithTicket();
            }
            if (playerDocument.exists()) {
                String message = """
                    Hi! We migrated our linking system! You'll have to relink. Sorry!
                    
                    §71. Visit the §bDiscord§7.
                    §72. Follow the instructions in §b#link-account§7!
                    §73. §aRejoin!
                    """;
                return PreLoginDecision.deny(message);
            }
            String message = """
                Hi! You'll need to link your account before you join!
                
                §71. Visit the §bDiscord§7.
                §72. Follow the instructions in §b#link-account§7!
                §73. §aRejoin!
                """;
            return PreLoginDecision.deny(message);
        }).exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Failed to evaluate pre-login link status for " + playerId, throwable);
            return PreLoginDecision.deny("Account link check failed; try again in a moment.");
        });
    }

    public CompletionStage<Void> consumeTicket(UUID playerId, String currentUsername) {
        Objects.requireNonNull(playerId, "playerId");
        String uuidKey = playerId.toString();
        return tickets.load(uuidKey)
            .thenCompose(ticketDocument -> {
                Optional<LinkTicket> ticket = readTicket(ticketDocument);
                if (ticket.isEmpty()) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                return players.load(uuidKey)
                    .thenCompose(playerDocument -> copyLinkToPlayer(playerDocument, ticket.get()))
                    .thenCompose(ignored -> tickets.delete(uuidKey).thenApply(ignoredDelete -> null));
            })
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to consume link ticket for " + playerId, throwable);
                return (Void) null;
            });
    }

    private CompletionStage<Void> cleanupPreviousLink(UUID previousUuid) {
        String key = previousUuid.toString();
        CompletableFuture<Document> previousPlayerFuture = players.load(key).toCompletableFuture();
        CompletableFuture<Document> previousTicketFuture = tickets.load(key).toCompletableFuture();

        return previousPlayerFuture.thenCombine(previousTicketFuture, (playerDocument, ticketDocument) -> {
            Optional<String> username = playerDocument.get("meta.username", String.class);
            if (username.isEmpty()) {
                username = readTicket(ticketDocument).map(LinkTicket::username);
            }
            CompletionStage<Void> playerCleanup = playerDocument.exists()
                ? removeLinkFromPlayer(playerDocument)
                : CompletableFuture.completedFuture(null);

            CompletionStage<Void> ticketCleanup = ticketDocument.exists()
                ? tickets.delete(key).thenApply(ignored -> null)
                : CompletableFuture.completedFuture(null);

            username.ifPresent(this::scheduleWhitelistRemoval);

            return CompletableFuture.allOf(playerCleanup.toCompletableFuture(), ticketCleanup.toCompletableFuture());
        }).thenCompose(stage -> stage)
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to clean up previous link for " + previousUuid, throwable);
                return null;
            });
    }

    private CompletionStage<Void> persistTicket(LinkTicket ticket) {
        Map<String, Object> payload = Map.of(
            "uuid", ticket.uuid().toString(),
            "discordId", ticket.discordId(),
            "username", ticket.username(),
            "createdAt", ticket.createdAt().toString(),
            "osu", Map.of(
                "userId", ticket.osu().userId(),
                "username", ticket.osu().username(),
                "rank", ticket.osu().rank(),
                "country", ticket.osu().country()
            ),
            "consumed", false
        );
        return tickets.create(ticket.uuid().toString(), payload).thenApply(ignored -> null);
    }

    private CompletionStage<Void> persistDiscordMapping(LinkTicket ticket) {
        Map<String, Object> payload = Map.of(
            DISCORD_LINK_UUID_PATH, ticket.uuid().toString(),
            DISCORD_LINK_USERNAME_PATH, ticket.username(),
            "updatedAt", Instant.now().toString()
        );
        return discordLinks.create(Long.toString(ticket.discordId()), payload).thenApply(ignored -> null);
    }

    private CompletionStage<Void> copyLinkToPlayer(Document playerDocument, LinkTicket ticket) {
        CompletionStage<Void> discordStage = playerDocument.set(DISCORD_ID_PATH, ticket.discordId());
        CompletionStage<Void> userIdStage = playerDocument.set(OSU_USER_ID_PATH, ticket.osu().userId());
        CompletionStage<Void> usernameStage = playerDocument.set(OSU_USERNAME_PATH, ticket.osu().username());
        CompletionStage<Void> rankStage = playerDocument.set(OSU_RANK_PATH, ticket.osu().rank());
        CompletionStage<Void> countryStage = playerDocument.set(OSU_COUNTRY_PATH, ticket.osu().country());

        return CompletableFuture.allOf(
            discordStage.toCompletableFuture(),
            userIdStage.toCompletableFuture(),
            usernameStage.toCompletableFuture(),
            rankStage.toCompletableFuture(),
            countryStage.toCompletableFuture()
        );
    }

    private CompletionStage<Void> removeLinkFromPlayer(Document document) {
        CompletionStage<Void> discordStage = document.remove(DISCORD_ID_PATH);
        CompletionStage<Void> osuStage = document.remove("osu");
        return CompletableFuture.allOf(discordStage.toCompletableFuture(), osuStage.toCompletableFuture());
    }

    private Optional<Long> readDiscordId(Document document) {
        return document.get(DISCORD_ID_PATH, Number.class).map(Number::longValue);
    }

    private Optional<LinkTicket> readTicket(Document document) {
        if (document == null || !document.exists()) {
            return Optional.empty();
        }
        Optional<Number> discordId = document.get(DISCORD_ID_PATH, Number.class);
        Optional<String> username = document.get("username", String.class);
        Optional<Number> osuUserId = document.get(OSU_USER_ID_PATH, Number.class);
        Optional<String> osuUsername = document.get(OSU_USERNAME_PATH, String.class);
        Optional<Number> osuRank = document.get(OSU_RANK_PATH, Number.class);
        Optional<String> osuCountry = document.get(OSU_COUNTRY_PATH, String.class);
        Optional<String> createdAt = document.get("createdAt", String.class);

        if (discordId.isEmpty() || username.isEmpty() || osuUserId.isEmpty() || osuUsername.isEmpty() || osuRank.isEmpty() || osuCountry.isEmpty() || createdAt.isEmpty()) {
            return Optional.empty();
        }

        Instant created;
        try {
            created = Instant.parse(createdAt.get());
        } catch (DateTimeParseException exception) {
            logger.log(Level.WARNING, "Invalid ticket timestamp for " + document.key().id(), exception);
            return Optional.empty();
        }

        OsuProfile osu = new OsuProfile(
            osuUserId.get().longValue(),
            osuUsername.get(),
            osuRank.get().intValue(),
            osuCountry.get()
        );
        UUID uuid = parseUuid(document.key().id());
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.of(new LinkTicket(uuid, discordId.get().longValue(), username.get(), osu, created));
    }

    private UUID readDiscordLink(Document document) {
        if (document == null || !document.exists()) {
            return null;
        }
        return document.get(DISCORD_LINK_UUID_PATH, String.class).map(this::parseUuid).orElse(null);
    }

    private boolean hasLinkedAccount(Document document) {
        if (document == null || !document.exists()) {
            return false;
        }
        Optional<Long> discord = readDiscordId(document);
        Optional<Number> osuUserId = document.get(OSU_USER_ID_PATH, Number.class);
        return discord.isPresent() && osuUserId.isPresent();
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void scheduleWhitelistAndPermissions(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        runSync(() -> {
            if (!plugin.isEnabled()) {
                return;
            }
            boolean whitelisted = plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), "whitelist add " + username);
            boolean lpApplied = plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), "lp user " + username + " parent set " + VERIFIED_GROUP);
            if (!whitelisted || !lpApplied) {
                logger.warning("Link side effects incomplete for " + username + "; whitelist added=" + whitelisted + ", lp applied=" + lpApplied);
            }
        });
    }

    private void scheduleWhitelistRemoval(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        runSync(() -> {
            if (!plugin.isEnabled()) {
                return;
            }
            boolean removed = plugin.getServer().dispatchCommand(Bukkit.getConsoleSender(), "whitelist remove " + username);
            if (!removed) {
                logger.warning("Could not remove whitelist entry for " + username);
            }
        });
    }

    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public record OsuProfile(long userId, String username, int rank, String country) {
    }

    public record LinkRequest(UUID uuid, long discordId, String username, OsuProfile osu) {
    }

    public sealed interface LinkResult permits LinkResult.Success, LinkResult.Rejected {
        record Success(LinkTicket ticket) implements LinkResult {
        }
        record Rejected(String reason) implements LinkResult {
        }
    }

    public enum PreLoginStatus {
        ALLOW,
        ALLOW_WITH_TICKET,
        DENY
    }

    public record PreLoginDecision(PreLoginStatus status, String message) {
        public static PreLoginDecision allow() {
            return new PreLoginDecision(PreLoginStatus.ALLOW, null);
        }

        public static PreLoginDecision allowWithTicket() {
            return new PreLoginDecision(PreLoginStatus.ALLOW_WITH_TICKET, null);
        }

        public static PreLoginDecision deny(String message) {
            return new PreLoginDecision(PreLoginStatus.DENY, message);
        }
    }

    public record LinkTicket(UUID uuid, long discordId, String username, OsuProfile osu, Instant createdAt) {
    }
}
