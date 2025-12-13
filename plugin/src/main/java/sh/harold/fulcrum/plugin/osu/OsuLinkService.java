package sh.harold.fulcrum.plugin.osu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OsuLinkService {

    enum Provider {
        OSU,
        DISCORD
    }

    private static final String OSU_LAST_REFRESH_PATH = "linking.osu.lastRefresh";
    private static final Duration OSU_PROFILE_REFRESH_TTL = Duration.ofHours(1);

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final LinkAccountConfig config;
    private final OsuOAuthClient osuClient;
    private final OsuPublicApiClient osuPublicApiClient;
    private final DiscordOAuthClient discordClient;
    private final Consumer<UUID> linkCompleteCallback;
    private final Logger logger;
    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> osuRefreshTasks = new ConcurrentHashMap<>();

    public OsuLinkService(JavaPlugin plugin, DocumentCollection players, LinkAccountConfig config, Consumer<UUID> linkCompleteCallback) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.config = Objects.requireNonNull(config, "config");
        this.linkCompleteCallback = Objects.requireNonNull(linkCompleteCallback, "linkCompleteCallback");
        if (config.osu().clientId().isBlank() || config.osu().clientSecret().isBlank()) {
            throw new IllegalStateException("osu OAuth client id/secret must be configured.");
        }
        if (config.discord().clientId().isBlank() || config.discord().clientSecret().isBlank()) {
            throw new IllegalStateException("discord OAuth client id/secret must be configured.");
        }
        this.osuClient = new OsuOAuthClient(config, config.osu());
        this.osuPublicApiClient = new OsuPublicApiClient(config.osu());
        this.discordClient = new DiscordOAuthClient(config, config.discord());
        this.logger = plugin.getLogger();
    }

    public String createOsuLink(UUID playerId, String username) {
        return createLink(Provider.OSU, playerId, username);
    }

    public String createDiscordLink(UUID playerId, String username) {
        return createLink(Provider.DISCORD, playerId, username);
    }

    public LinkResult completeOsuLink(String token, String authorizationCode) {
        return completeLink(token, authorizationCode, Provider.OSU);
    }

    public LinkResult completeDiscordLink(String token, String authorizationCode) {
        return completeLink(token, authorizationCode, Provider.DISCORD);
    }

    public boolean hasOsuLink(UUID playerId) {
        return hasLink(playerId, Provider.OSU);
    }

    public boolean hasDiscordLink(UUID playerId) {
        return hasLink(playerId, Provider.DISCORD);
    }

    public CompletionStage<Void> refreshOsuProfile(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return osuRefreshTasks.computeIfAbsent(playerId, id -> refreshOsuProfileInternal(id)
            .toCompletableFuture()
            .whenComplete((ignored, throwable) -> osuRefreshTasks.remove(id)));
    }

    private CompletionStage<Void> refreshOsuProfileInternal(UUID playerId) {
        return players.load(playerId.toString())
            .thenCompose(document -> refreshOsuProfile(playerId, document))
            .exceptionally(throwable -> {
                logger.log(Level.FINE, "Failed to refresh osu profile for " + playerId, throwable);
                return null;
            });
    }

    private CompletionStage<Void> refreshOsuProfile(UUID playerId, Document document) {
        if (document == null || !document.exists()) {
            return CompletableFuture.completedFuture(null);
        }

        Long osuUserId = readLong(document, "linking.osu.userId")
            .or(() -> readLong(document, "osu.userId"))
            .orElse(null);
        String osuUsername = document.get("linking.osu.username", String.class)
            .filter(value -> !value.isBlank())
            .orElseGet(() -> document.get("osu.username", String.class)
                .filter(value -> !value.isBlank())
                .orElse(null));
        if ((osuUserId == null || osuUserId <= 0) && (osuUsername == null || osuUsername.isBlank())) {
            return CompletableFuture.completedFuture(null);
        }

        if (!isOsuRefreshDue(document)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> stamp = document.set(OSU_LAST_REFRESH_PATH, Instant.now().toString()).toCompletableFuture();
        CompletionStage<OsuPublicApiClient.OsuUserProfile> profileStage = osuUserId != null && osuUserId > 0
            ? osuPublicApiClient.fetchUserById(osuUserId)
            : osuPublicApiClient.fetchUserByUsername(osuUsername);
        return stamp.thenCombine(profileStage.toCompletableFuture(), (ignored, profile) -> profile)
            .thenCompose(profile -> applyOsuProfileUpdate(playerId, document, profile))
            .exceptionally(throwable -> {
                logger.log(Level.FINE, "Failed to refresh osu profile for " + playerId, throwable);
                return null;
            });
    }

    private boolean isOsuRefreshDue(Document document) {
        Instant lastRefresh = parseInstant(document.get(OSU_LAST_REFRESH_PATH, String.class).orElse(null));
        if (lastRefresh == null) {
            return true;
        }
        return lastRefresh.plus(OSU_PROFILE_REFRESH_TTL).isBefore(Instant.now());
    }

    private CompletionStage<Void> applyOsuProfileUpdate(UUID playerId, Document document, OsuPublicApiClient.OsuUserProfile profile) {
        if (profile == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (profile.userId() <= 0) {
            logger.fine(() -> "osu profile refresh returned invalid user id for " + playerId);
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> setValues = new HashMap<>();
        List<String> removePaths = new ArrayList<>();

        if (profile.username() != null && !profile.username().isBlank()) {
            setValues.put("linking.osu.username", profile.username().trim());
        }
        setValues.put("linking.osu.userId", profile.userId());

        if (profile.countryCode() != null && !profile.countryCode().isBlank()) {
            setValues.put("linking.osu.country", profile.countryCode().trim());
        } else {
            removePaths.add("linking.osu.country");
        }

        Integer rank = profile.globalRank();
        if (rank != null && rank > 0) {
            setValues.put("linking.osu.rank", rank);
        } else {
            removePaths.add("linking.osu.rank");
        }

        return document.patch(setValues, removePaths);
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String createLink(Provider provider, UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        cleanExpired();
        String token = UUID.randomUUID().toString();
        pendingLinks.put(token, new PendingLink(playerId, username, Instant.now(), provider));
        return provider == Provider.OSU
            ? osuClient.authorizationUrl(token)
            : discordClient.authorizationUrl(token);
    }

    private LinkResult completeLink(String token, String authorizationCode, Provider provider) {
        cleanExpired();
        PendingLink pending = pendingLinks.get(token);
        if (pending == null || pending.isExpired(config.tokenTtl()) || pending.provider() != provider) {
            pendingLinks.remove(token);
            return LinkResult.failure("This link request is missing or expired. Run the link command again.");
        }
        try {
            if (provider == Provider.OSU) {
                OsuOAuthClient.OsuUserProfile profile = osuClient.exchangeCode(authorizationCode);
                if (profile.username() == null || profile.username().isBlank()) {
                    pendingLinks.remove(token);
                    return LinkResult.failure("Failed to read your osu! username. Run /linkosuaccount again.");
                }
                LinkResult result = persistOsuLink(pending, profile);
                pendingLinks.remove(token);
                if (!result.success()) {
                    return result;
                }
                notifyPlayer(pending.playerId(), linkedMessage("osu!", profile.username()));
                notifyLinkComplete(pending.playerId());
                return result;
            }
            DiscordOAuthClient.DiscordUser profile = discordClient.exchangeCode(authorizationCode);
            if (profile.username() == null || profile.username().isBlank()) {
                pendingLinks.remove(token);
                return LinkResult.failure("Failed to read your Discord username. Run /linkdiscordaccount again.");
            }
            LinkResult result = persistDiscordLink(pending, profile);
            pendingLinks.remove(token);
            if (!result.success()) {
                return result;
            }
            notifyPlayer(pending.playerId(), linkedMessage("Discord", profile.username()));
            notifyLinkComplete(pending.playerId());
            return result;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            pendingLinks.remove(token);
            logger.log(Level.WARNING, provider + " OAuth exchange interrupted for " + pending.playerId(), interruptedException);
            return LinkResult.failure("Link cancelled. Run the link command again.");
        } catch (IOException ioException) {
            pendingLinks.remove(token);
            logger.log(Level.WARNING, provider + " OAuth exchange failed for " + pending.playerId(), ioException);
            return LinkResult.failure("Login failed. Run the link command again.");
        } catch (Exception exception) {
            pendingLinks.remove(token);
            logger.log(Level.SEVERE, "Failed to link account (" + provider + ") for " + pending.playerId(), exception);
            return LinkResult.failure("Failed to persist link. Try again in a moment.");
        }
    }

    private boolean hasLink(UUID playerId, Provider provider) {
        try {
            Document document = players.load(playerId.toString()).toCompletableFuture().join();
            if (provider == Provider.OSU) {
                return document.get("linking.osu.userId", Number.class).map(num -> num.longValue() > 0).orElse(false)
                    || document.get("linking.osu.username", String.class).filter(val -> !val.isBlank()).isPresent();
            }
            return document.get("linking.discord.userId", Number.class).map(num -> num.longValue() > 0).orElse(false)
                || document.get("linking.discord.username", String.class).filter(val -> !val.isBlank()).isPresent()
                || document.get("linking.discordId", Number.class).map(num -> num.longValue() > 0).orElse(false);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to check link status for " + playerId + " (" + provider + ")", exception);
            return false;
        }
    }

    private void notifyPlayer(UUID playerId, Component message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                return;
            }
            player.sendMessage(message);
        });
    }

    private void notifyLinkComplete(UUID playerId) {
        try {
            linkCompleteCallback.accept(playerId);
        } catch (Exception exception) {
            logger.log(Level.WARNING, "Failed to notify link completion for " + playerId, exception);
        }
    }

    private void cleanExpired() {
        Instant cutoff = Instant.now().minus(config.tokenTtl());
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private LinkResult persistOsuLink(PendingLink pending, OsuOAuthClient.OsuUserProfile profile) {
        String accountId = String.valueOf(profile.userId());
        synchronized (accountLock(accountLockKey(Provider.OSU, accountId))) {
            Optional<UUID> existingLink = findExistingOsuLink(profile, pending.playerId());
            if (existingLink.isPresent()) {
                return LinkResult.failure("That osu! account is already linked to another Minecraft player.");
            }

            Document document = players.load(pending.playerId().toString()).toCompletableFuture().join();
            if (isDifferentOsuAccount(document, profile)) {
                return LinkResult.failure("This Minecraft account is already linked to a different osu! account.");
            }
            CompletionStage<Void> usernameStage = document.set("linking.osu.username", profile.username().trim());
            CompletionStage<Void> idStage = document.set("linking.osu.userId", profile.userId());
            CompletionStage<Void> rankStage = profile.globalRank() != null
                ? document.set("linking.osu.rank", profile.globalRank())
                : CompletableFuture.completedFuture(null);
            CompletionStage<Void> countryStage = profile.countryCode() != null && !profile.countryCode().isBlank()
                ? document.set("linking.osu.country", profile.countryCode())
                : CompletableFuture.completedFuture(null);
            CompletionStage<Void> clearLegacy = CompletableFuture.allOf(
                document.remove("osu.username").toCompletableFuture(),
                document.remove("osu.userId").toCompletableFuture(),
                document.remove("osu.rank").toCompletableFuture(),
                document.remove("osu.country").toCompletableFuture(),
                document.remove("osu.linkedAt").toCompletableFuture()
            );

            CompletableFuture.allOf(
                usernameStage.toCompletableFuture(),
                idStage.toCompletableFuture(),
                rankStage.toCompletableFuture(),
                countryStage.toCompletableFuture(),
                clearLegacy.toCompletableFuture()
            ).join();

            return LinkResult.success(pending.username(), profile.username());
        }
    }

    private Component linkedMessage(String provider, String username) {
        return Component.text()
            .append(Component.newline())
            .append(Component.text("LINKED!", NamedTextColor.GREEN, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text("You linked your " + provider + " account (" + username + ").", NamedTextColor.GRAY))
            .append(Component.newline())
            .build();
    }

    Component linkPrompt(String subject, String body, String url) {
        return Component.text()
            .append(Component.text(subject, NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text(body + " ", NamedTextColor.GRAY))
            .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD).clickEvent(ClickEvent.openUrl(url)))
            .build();
    }

    Component errorMessage(String subject, String body) {
        return Component.text()
            .append(Component.text(subject, NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text(body, NamedTextColor.GRAY))
            .build();
    }

    private LinkResult persistDiscordLink(PendingLink pending, DiscordOAuthClient.DiscordUser profile) {
        String accountId = profile.id();
        synchronized (accountLock(accountLockKey(Provider.DISCORD, accountId))) {
            Optional<UUID> existingLink = findExistingDiscordLink(profile, pending.playerId());
            if (existingLink.isPresent()) {
                return LinkResult.failure("That Discord account is already linked to another Minecraft player.");
            }

            Document document = players.load(pending.playerId().toString()).toCompletableFuture().join();
            if (isDifferentDiscordAccount(document, profile)) {
                return LinkResult.failure("This Minecraft account is already linked to a different Discord account.");
            }
            Long discordId = parseLong(profile.id());
            CompletionStage<Void> idStage = discordId != null
                ? document.set("linking.discord.userId", discordId)
                : document.set("linking.discord.userId", profile.id());
            CompletionStage<Void> usernameStage = document.set("linking.discord.username", profile.username());
            CompletionStage<Void> globalNameStage = profile.globalName() != null && !profile.globalName().isBlank()
                ? document.set("linking.discord.globalName", profile.globalName())
                : CompletableFuture.completedFuture(null);
            CompletionStage<Void> discriminatorStage = profile.discriminator() != null && !profile.discriminator().isBlank()
                ? document.set("linking.discord.discriminator", profile.discriminator())
                : CompletableFuture.completedFuture(null);
            CompletionStage<Void> rootIdStage = discordId != null
                ? document.set("linking.discordId", discordId)
                : document.set("linking.discordId", profile.id());

            CompletableFuture.allOf(
                idStage.toCompletableFuture(),
                usernameStage.toCompletableFuture(),
                globalNameStage.toCompletableFuture(),
                discriminatorStage.toCompletableFuture(),
                rootIdStage.toCompletableFuture()
            ).join();

            return LinkResult.success(pending.username(), profile.username());
        }
    }

    private boolean isDifferentOsuAccount(Document document, OsuOAuthClient.OsuUserProfile profile) {
        Long existingId = readLong(document, "linking.osu.userId")
            .or(() -> readLong(document, "osu.userId"))
            .orElse(null);
        if (existingId != null && existingId != profile.userId()) {
            return true;
        }
        String existingUsername = document.get("linking.osu.username", String.class)
            .filter(value -> !value.isBlank())
            .orElseGet(() -> document.get("osu.username", String.class)
                .filter(value -> !value.isBlank())
                .orElse(null));
        return existingId == null
            && existingUsername != null
            && !existingUsername.equalsIgnoreCase(profile.username());
    }

    private boolean isDifferentDiscordAccount(Document document, DiscordOAuthClient.DiscordUser profile) {
        Optional<String> existingId = readId(document, "linking.discord.userId", "linking.discordId");
        if (existingId.isPresent() && !existingId.get().equals(profile.id())) {
            return true;
        }
        String existingUsername = document.get("linking.discord.username", String.class)
            .filter(value -> !value.isBlank())
            .orElse(null);
        return existingId.isEmpty()
            && existingUsername != null
            && !existingUsername.equalsIgnoreCase(profile.username());
    }

    private Optional<UUID> findExistingDiscordLink(DiscordOAuthClient.DiscordUser profile, UUID playerId) {
        return findExistingLink(playerId, document -> matchesDiscordAccount(document, profile));
    }

    private Optional<UUID> findExistingOsuLink(OsuOAuthClient.OsuUserProfile profile, UUID playerId) {
        return findExistingLink(playerId, document -> matchesOsuAccount(document, profile));
    }

    private Optional<UUID> findExistingLink(UUID playerId, java.util.function.Predicate<Document> predicate) {
        return players.all()
            .toCompletableFuture()
            .join()
            .stream()
            .filter(Document::exists)
            .filter(document -> !document.key().id().equals(playerId.toString()))
            .filter(predicate)
            .map(this::parsePlayerId)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private boolean matchesDiscordAccount(Document document, DiscordOAuthClient.DiscordUser profile) {
        Optional<String> existingId = readId(document, "linking.discord.userId", "linking.discordId");
        if (existingId.isPresent() && existingId.get().equals(profile.id())) {
            return true;
        }
        if (existingId.isPresent()) {
            return false;
        }
        String existingUsername = document.get("linking.discord.username", String.class)
            .filter(value -> !value.isBlank())
            .orElse(null);
        return existingUsername != null
            && profile.username() != null
            && existingUsername.equalsIgnoreCase(profile.username());
    }

    private boolean matchesOsuAccount(Document document, OsuOAuthClient.OsuUserProfile profile) {
        Optional<Long> existingId = readLong(document, "linking.osu.userId")
            .or(() -> readLong(document, "osu.userId"));
        if (existingId.isPresent() && existingId.get() == profile.userId()) {
            return true;
        }
        if (existingId.isPresent()) {
            return false;
        }
        String existingUsername = document.get("linking.osu.username", String.class)
            .filter(value -> !value.isBlank())
            .orElseGet(() -> document.get("osu.username", String.class)
                .filter(value -> !value.isBlank())
                .orElse(null));
        return existingUsername != null
            && profile.username() != null
            && existingUsername.equalsIgnoreCase(profile.username());
    }

    private Optional<Long> readLong(Document document, String path) {
        return document.get(path, Number.class)
            .map(Number::longValue)
            .or(() -> document.get(path, String.class).map(this::parseLong).filter(Objects::nonNull));
    }

    private Optional<String> readId(Document document, String... paths) {
        for (String path : paths) {
            Optional<String> numberValue = document.get(path, Number.class).map(Number::toString).filter(value -> !value.isBlank());
            if (numberValue.isPresent()) {
                return numberValue;
            }
            Optional<String> stringValue = document.get(path, String.class).filter(value -> !value.isBlank());
            if (stringValue.isPresent()) {
                return stringValue;
            }
        }
        return Optional.empty();
    }

    private Object accountLock(String key) {
        return accountLocks.computeIfAbsent(key, ignored -> new Object());
    }

    private String accountLockKey(Provider provider, String accountId) {
        String normalized = accountId == null ? "" : accountId.trim();
        return provider.name() + ":" + normalized;
    }

    private Optional<UUID> parsePlayerId(Document document) {
        try {
            return Optional.of(UUID.fromString(document.key().id()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record LinkResult(boolean success, String message, String playerUsername, String accountUsername) {
        public static LinkResult success(String playerUsername, String accountUsername) {
            return new LinkResult(true, "Linked account.", playerUsername, accountUsername);
        }

        public static LinkResult failure(String message) {
            return new LinkResult(false, message, null, null);
        }
    }

    record PendingLink(UUID playerId, String username, Instant createdAt, Provider provider) {
        boolean isExpired(java.time.Duration ttl) {
            return createdAt.isBefore(Instant.now().minus(ttl));
        }
    }
}
