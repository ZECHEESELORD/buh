package sh.harold.fulcrum.plugin.accountlink;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Duration;
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
    private static final String REQUEST_DISCORD_ID_PATH = "userId";
    private static final String REQUEST_SOURCE_PATH = "source";
    private static final String REQUEST_INVITED_BY_PATH = "invitedBy";
    private static final String REQUEST_STATUS_PATH = "status";
    private static final String REQUEST_SPONSOR_ID_PATH = "sponsorId";
    private static final String REQUEST_MESSAGE_ID_PATH = "requestMessageId";
    private static final String REQUEST_CREATED_AT_PATH = "createdAt";
    private static final String REQUEST_CONSUMED_PATH = "consumed";
    private static final String VERIFIED_GROUP = "verified";
    // Tickets need to outlive staff review; keep them around for a while.
    private static final Duration TICKET_TTL = Duration.ofDays(30);

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final DocumentCollection discordLinks;
    private final DocumentCollection linkRequests;
    private final Logger logger;

    public AccountLinkService(JavaPlugin plugin, DocumentCollection players, DocumentCollection discordLinks, DocumentCollection linkRequests) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.discordLinks = Objects.requireNonNull(discordLinks, "discordLinks");
        this.linkRequests = linkRequests;
        this.logger = plugin.getLogger();
    }

    public CompletionStage<LinkResult> createLink(LinkRequest request) {
        Objects.requireNonNull(request, "request");
        UUID playerId = request.uuid();
        String uuidKey = playerId.toString();
        String discordKey = Long.toString(request.discordId());

        CompletableFuture<Document> playerFuture = players.load(uuidKey).toCompletableFuture();
        CompletableFuture<Document> requestFuture = loadRequestDocument(uuidKey).toCompletableFuture();
        CompletableFuture<Document> discordFuture = discordLinks.load(discordKey).toCompletableFuture();

        return CompletableFuture.allOf(playerFuture, requestFuture, discordFuture)
            .thenCompose(ignored -> {
                Document playerDocument = playerFuture.join();
                Document requestDocument = requestFuture.join();
                Document discordDocument = discordFuture.join();

                Optional<Long> existingDiscord = readDiscordId(playerDocument);
                if (existingDiscord.isPresent() && existingDiscord.get() != request.discordId()) {
                    return CompletableFuture.completedFuture(new LinkResult.Rejected("That Minecraft account is already linked to another Discord user."));
                }

                Optional<LinkTicket> existingTicket = readTicket(requestDocument);
                if (existingTicket.isPresent() && existingTicket.get().discordId() != request.discordId()) {
                    return CompletableFuture.completedFuture(new LinkResult.Rejected("That Minecraft account already has a pending link for another Discord user."));
                }

                UUID previousUuid = readDiscordLink(discordDocument);
                CompletionStage<Void> cleanupStage = previousUuid != null && !previousUuid.equals(playerId)
                    ? cleanupPreviousLink(previousUuid)
                    : CompletableFuture.completedFuture(null);

                boolean existingPlayer = playerDocument.exists();
                LinkTicket ticket = new LinkTicket(playerId, request.discordId(), request.username(), request.osu(), Instant.now(), null, null, null);

                return cleanupStage
                    .thenCompose(ignoredCleanup -> persistLinkRequest(ticket))
                    .thenCompose(ignoredTicket -> persistDiscordMapping(ticket))
                    .handle((ignoredPersist, throwable) -> {
                        if (throwable != null) {
                            logger.log(Level.SEVERE, "Failed to create link ticket for " + playerId, throwable);
                            return new LinkResult.Rejected("Could not store link; try again soon.");
                        }
                        if (existingPlayer) {
                            scheduleWhitelistAndPermissions(ticket.username());
                        }
                        return new LinkResult.Success(ticket);
                    });
            });
    }

    public CompletionStage<PreLoginDecision> preLoginDecision(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        String uuidKey = playerId.toString();
        CompletableFuture<Document> playerFuture = players.load(uuidKey).toCompletableFuture();
        CompletableFuture<Document> requestFuture = loadRequestDocument(uuidKey).toCompletableFuture();

        return pendingReviewMessage(playerId)
            .thenCompose(pendingMessage -> {
                if (pendingMessage.isPresent()) {
                    return CompletableFuture.completedFuture(PreLoginDecision.deny(pendingMessage.get()));
                }
                return playerFuture.thenCombine(requestFuture, (playerDocument, requestDocument) -> {
                    Optional<LinkTicket> ticket = readTicket(requestDocument);
                    Optional<RequestStoreState> requestState = requestStateFor(playerId);

                    if (hasLinkedAccount(playerDocument)) {
                        return PreLoginDecision.allow();
                    }

                    if (ticket.isPresent() && isExpired(ticket.get())) {
                        if (requestDocument != null && requestDocument.exists()) {
                            linkRequests.delete(requestDocument.key().id());
                        }
                        ticket = Optional.empty();
                    }

                    if (ticket.isPresent()) {
                        if (requestState.isPresent() && requestState.get() == RequestStoreState.DENIED) {
                            return PreLoginDecision.deny("Your whitelist request was denied by staff.");
                        }
                        if (!playerDocument.exists() && requestState.isPresent() && requestState.get() != RequestStoreState.APPROVED) {
                            return PreLoginDecision.deny("""
                                We've received the whitelist request! Hold on tight... Staff are reviewing it!
                                Estimated Time: <4h
                                """);
                        }
                        if (requestState.isPresent() && requestState.get() == RequestStoreState.APPROVED) {
                            scheduleWhitelistAndPermissions(ticket.get().username());
                        }
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
                });
            }).exceptionally(throwable -> {
            logger.log(Level.SEVERE, "Failed to evaluate pre-login link status for " + playerId, throwable);
            return PreLoginDecision.deny("Account link check failed; try again in a moment.");
        });
    }

    public CompletionStage<Void> consumeTicket(UUID playerId, String currentUsername) {
        Objects.requireNonNull(playerId, "playerId");
        String uuidKey = playerId.toString();
        return loadRequestDocument(uuidKey)
            .thenCompose(ticketDocument -> {
                if (ticketDocument == null) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                Optional<LinkTicket> ticket = readTicket(ticketDocument);
                if (ticket.isEmpty()) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                return players.load(uuidKey)
                    .thenCompose(playerDocument -> copyLinkToPlayer(playerDocument, ticket.get()))
                    .thenCompose(ignored -> linkRequests.delete(ticketDocument.key().id())
                        .handle((deleted, throwable) -> {
                            if (throwable != null || !Boolean.TRUE.equals(deleted)) {
                                logger.log(Level.WARNING, "Failed to delete link request after consumption for " + playerId, throwable);
                            }
                            return null;
                        }));
            })
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to consume link ticket for " + playerId, throwable);
                return (Void) null;
            });
    }

    private CompletionStage<Void> cleanupPreviousLink(UUID previousUuid) {
        String key = previousUuid.toString();
        CompletableFuture<Document> previousPlayerFuture = players.load(key).toCompletableFuture();
        CompletableFuture<Document> previousTicketFuture = loadRequestDocument(key).toCompletableFuture();

        return previousPlayerFuture.thenCombine(previousTicketFuture, (playerDocument, ticketDocument) -> {
            Optional<String> username = playerDocument.get("meta.username", String.class);
            if (username.isEmpty()) {
                username = readTicket(ticketDocument).map(LinkTicket::username);
            }
            CompletionStage<Void> playerCleanup = playerDocument.exists()
                ? removeLinkFromPlayer(playerDocument)
                : CompletableFuture.completedFuture(null);

            CompletionStage<Void> ticketCleanup = ticketDocument.exists()
                ? linkRequests.delete(key).thenApply(ignored -> null)
                : CompletableFuture.completedFuture(null);

            username.ifPresent(this::scheduleWhitelistRemoval);

            return CompletableFuture.allOf(playerCleanup.toCompletableFuture(), ticketCleanup.toCompletableFuture());
        }).thenCompose(stage -> stage)
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to clean up previous link for " + previousUuid, throwable);
                return null;
            });
    }

    private CompletionStage<Void> persistLinkRequest(LinkTicket ticket) {
        String key = ticket.uuid().toString();
        return loadRequestDocument(key)
            .thenCompose(document -> {
                boolean exists = document != null && document.exists();
                String status = exists
                    ? document.get(REQUEST_STATUS_PATH, String.class).orElse(RequestStoreState.PENDING.name())
                    : RequestStoreState.PENDING.name();
                String createdAt = exists ? document.get(REQUEST_CREATED_AT_PATH, String.class).orElse(ticket.createdAt().toString()) : ticket.createdAt().toString();

                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put(REQUEST_DISCORD_ID_PATH, ticket.discordId());
                if (exists) {
                    document.get("username", String.class).ifPresent(name -> payload.put("username", name));
                }
                payload.put(REQUEST_CREATED_AT_PATH, createdAt);
                if (exists) {
                    document.get(REQUEST_SOURCE_PATH, String.class).ifPresent(source -> payload.put(REQUEST_SOURCE_PATH, source));
                    document.get(REQUEST_INVITED_BY_PATH, String.class).ifPresent(invited -> payload.put(REQUEST_INVITED_BY_PATH, invited));
                }
                payload.put("minecraft", Map.of(
                    "uuid", key,
                    "username", ticket.username()
                ));
                payload.put("osu", Map.of(
                    "userId", ticket.osu().userId(),
                    "username", ticket.osu().username(),
                    "rank", ticket.osu().rank(),
                    "country", ticket.osu().country()
                ));
                payload.put(REQUEST_STATUS_PATH, status);
                payload.put("canReapply", document.get("canReapply", Boolean.class).orElse(true));
                document.get("decisionReason", String.class).ifPresent(reason -> payload.put("decisionReason", reason));
                if (exists) {
                    document.get(REQUEST_SPONSOR_ID_PATH, Number.class).ifPresent(id -> payload.put(REQUEST_SPONSOR_ID_PATH, id.longValue()));
                    document.get(REQUEST_MESSAGE_ID_PATH, Number.class).ifPresent(id -> payload.put(REQUEST_MESSAGE_ID_PATH, id.longValue()));
                    document.get(REQUEST_CONSUMED_PATH, Boolean.class).ifPresent(consumed -> payload.put(REQUEST_CONSUMED_PATH, consumed));
                }

                return exists
                    ? document.overwrite(payload)
                    : linkRequests.create(key, payload).thenApply(ignored -> null);
            });
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
        CompletionStage<Void> linkingDiscord = playerDocument.set("linking.discordId", ticket.discordId());
        CompletionStage<Void> linkingOsuUser = playerDocument.set("linking.osu.userId", ticket.osu().userId());
        CompletionStage<Void> linkingOsuUsername = playerDocument.set("linking.osu.username", ticket.osu().username());
        CompletionStage<Void> linkingOsuRank = playerDocument.set("linking.osu.rank", ticket.osu().rank());
        CompletionStage<Void> linkingOsuCountry = playerDocument.set("linking.osu.country", ticket.osu().country());
        CompletionStage<Void> linkingSource = ticket.source() != null
            ? playerDocument.set("linking.source", ticket.source())
            : CompletableFuture.completedFuture(null);
        CompletionStage<Void> linkingInvitedBy = ticket.invitedBy() != null
            ? playerDocument.set("linking.invitedBy", ticket.invitedBy())
            : CompletableFuture.completedFuture(null);
        CompletionStage<Void> linkingSponsor = ticket.sponsorId() != null
            ? playerDocument.set("linking.sponsorId", ticket.sponsorId())
            : CompletableFuture.completedFuture(null);

        return CompletableFuture.allOf(
            discordStage.toCompletableFuture(),
            linkingDiscord.toCompletableFuture(),
            linkingOsuUser.toCompletableFuture(),
            linkingOsuUsername.toCompletableFuture(),
            linkingOsuRank.toCompletableFuture(),
            linkingOsuCountry.toCompletableFuture(),
            linkingSource.toCompletableFuture(),
            linkingInvitedBy.toCompletableFuture(),
            linkingSponsor.toCompletableFuture()
        );
    }

    private CompletionStage<Void> removeLinkFromPlayer(Document document) {
        CompletionStage<Void> discordStage = document.remove(DISCORD_ID_PATH);
        CompletionStage<Void> linkingStage = document.remove("linking");
        return CompletableFuture.allOf(discordStage.toCompletableFuture(), linkingStage.toCompletableFuture());
    }

    private Optional<Long> readDiscordId(Document document) {
        return document.get(DISCORD_ID_PATH, Number.class).map(Number::longValue);
    }

    private CompletionStage<Document> loadRequestDocument(String uuidKey) {
        if (linkRequests == null) {
            return CompletableFuture.completedFuture(null);
        }
        return linkRequests.load(uuidKey)
            .thenCompose(document -> {
                if (document != null && document.exists()) {
                    return CompletableFuture.completedFuture(document);
                }
                return linkRequests.all()
                    .thenApply(list -> list.stream()
                        .filter(Document::exists)
                        .filter(doc -> uuidKey.equals(doc.get("minecraft.uuid", String.class).orElse(null)))
                        .findFirst()
                        .orElse(document));
            });
    }

    private Optional<LinkTicket> readTicket(Document document) {
        if (document == null || !document.exists()) {
            return Optional.empty();
        }
        Optional<Number> discordId = document.get(REQUEST_DISCORD_ID_PATH, Number.class);
        Optional<String> username = document.get("minecraft.username", String.class);
        Optional<Number> osuUserId = document.get(OSU_USER_ID_PATH, Number.class);
        Optional<String> osuUsername = document.get(OSU_USERNAME_PATH, String.class);
        Optional<Number> osuRank = document.get(OSU_RANK_PATH, Number.class);
        Optional<String> osuCountry = document.get(OSU_COUNTRY_PATH, String.class);
        Optional<String> createdAt = document.get(REQUEST_CREATED_AT_PATH, String.class);
        Optional<String> source = document.get(REQUEST_SOURCE_PATH, String.class);
        Optional<String> invitedBy = document.get(REQUEST_INVITED_BY_PATH, String.class);
        Optional<Number> sponsorId = document.get(REQUEST_SPONSOR_ID_PATH, Number.class);

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
        return Optional.of(new LinkTicket(
            uuid,
            discordId.get().longValue(),
            username.get(),
            osu,
            created,
            source.orElse(null),
            invitedBy.orElse(null),
            sponsorId.map(Number::longValue).orElse(null)
        ));
    }

    private boolean isExpired(LinkTicket ticket) {
        Instant cutoff = Instant.now().minus(TICKET_TTL);
        return ticket.createdAt().isBefore(cutoff);
    }

    private Optional<RequestStoreState> requestStateFor(UUID playerId) {
        if (linkRequests == null) {
            return Optional.empty();
        }
        try {
            Document document = loadRequestDocument(playerId.toString()).toCompletableFuture().join();
            if (document == null || !document.exists()) {
                return Optional.empty();
            }
            Optional<LinkTicket> ticket = readTicket(document);
            if (ticket.isPresent() && isExpired(ticket.get())) {
                linkRequests.delete(document.key().id());
                return Optional.empty();
            }
            return document.get(REQUEST_STATUS_PATH, String.class)
                .map(String::toUpperCase)
                .map(RequestStoreState::valueOf);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to read request state for " + playerId, exception);
            return Optional.empty();
        }
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
        Optional<Number> osuUserId = document.get("linking.osu.userId", Number.class);
        return discord.isPresent() && osuUserId.isPresent();
    }

    private CompletionStage<Optional<String>> pendingReviewMessage(UUID playerId) {
        if (linkRequests == null) {
            return CompletableFuture.completedFuture(Optional.<String>empty());
        }
        return loadRequestDocument(playerId.toString())
            .thenCompose(document -> {
                if (document == null || !document.exists()) {
                    return CompletableFuture.completedFuture(Optional.<String>empty());
                }
                Optional<LinkTicket> ticket = readTicket(document);
                if (ticket.isEmpty() || isExpired(ticket.get())) {
                    if (ticket.isPresent() && isExpired(ticket.get()) && document != null && document.exists()) {
                        linkRequests.delete(document.key().id());
                    }
                    return CompletableFuture.completedFuture(Optional.<String>empty());
                }
                if (!"PENDING".equals(document.get(REQUEST_STATUS_PATH, String.class).orElse(null))) {
                    return CompletableFuture.completedFuture(Optional.<String>empty());
                }
                Optional<Number> sponsorId = document.get(REQUEST_SPONSOR_ID_PATH, Number.class);
                if (sponsorId.isEmpty()) {
                    return CompletableFuture.completedFuture(Optional.of("""
                        We've received the whitelist request! Hold on tight... Staff are reviewing it!
                        Estimated Time: <4h
                        """));
                }
                return resolveSponsorUsername(sponsorId.get().longValue())
                    .thenApply(username -> Optional.of("""
                        We've received the whitelist request!
                        We're just waiting on %s to confirm your identity!
                        
                        Tell them to check their Discord DMs!
                        """.formatted(username != null ? username : "your sponsor")));
            })
            .<Optional<String>>exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to check pending review status for " + playerId, throwable);
                return Optional.<String>empty();
            });
    }

    private CompletionStage<String> resolveSponsorUsername(long sponsorId) {
        return players.all()
            .thenApply(list -> list.stream()
                .filter(doc -> doc.get(DISCORD_ID_PATH, Number.class).map(Number::longValue).orElse(0L) == sponsorId)
                .map(doc -> doc.get("meta.username", String.class).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null));
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
        // no-op: whitelist and LuckPerms side effects removed
    }

    private void scheduleWhitelistRemoval(String username) {
        // no-op: whitelist removal removed
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

    public record LinkTicket(UUID uuid, long discordId, String username, OsuProfile osu, Instant createdAt, String source, String invitedBy, Long sponsorId) {
    }

    private enum RequestStoreState {
        PENDING,
        APPROVED,
        DENIED
    }
}
