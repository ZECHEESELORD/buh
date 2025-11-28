package sh.harold.fulcrum.plugin.osu;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OsuLinkService {

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final OsuLinkConfig config;
    private final OsuOAuthClient oauthClient;
    private final Logger logger;
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    public OsuLinkService(JavaPlugin plugin, DocumentCollection players, OsuLinkConfig config) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.config = Objects.requireNonNull(config, "config");
        if (config.oauth().clientId().isBlank() || config.oauth().clientSecret().isBlank()) {
            throw new IllegalStateException("osu OAuth client id/secret must be configured.");
        }
        this.oauthClient = new OsuOAuthClient(config);
        this.logger = plugin.getLogger();
    }

    public String createLink(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        cleanExpired();
        String token = UUID.randomUUID().toString();
        pendingLinks.put(token, new PendingLink(playerId, username, Instant.now()));
        return oauthClient.authorizationUrl(token);
    }

    public LinkResult completeLink(String token, String authorizationCode) {
        cleanExpired();
        PendingLink pending = pendingLinks.get(token);
        if (pending == null || pending.isExpired(config.tokenTtl())) {
            pendingLinks.remove(token);
            return LinkResult.failure("This link request is missing or expired. Run /linkosuaccount again.");
        }
        try {
            OsuOAuthClient.OsuUserProfile profile = oauthClient.exchangeCode(authorizationCode);
            if (profile.username() == null || profile.username().isBlank()) {
                pendingLinks.remove(token);
                return LinkResult.failure("Failed to read your osu! username. Run /linkosuaccount again.");
            }

            LinkResult result = persistLink(pending, profile);
            pendingLinks.remove(token);

            notifyPlayer(pending.playerId(), profile.username());
            return result;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            pendingLinks.remove(token);
            logger.log(Level.WARNING, "osu OAuth exchange interrupted for " + pending.playerId(), interruptedException);
            return LinkResult.failure("Link cancelled. Run /linkosuaccount again.");
        } catch (IOException ioException) {
            pendingLinks.remove(token);
            logger.log(Level.WARNING, "osu OAuth exchange failed for " + pending.playerId(), ioException);
            return LinkResult.failure("osu! login failed. Run /linkosuaccount again.");
        } catch (Exception exception) {
            pendingLinks.remove(token);
            logger.log(Level.SEVERE, "Failed to link osu account for " + pending.playerId(), exception);
            return LinkResult.failure("Failed to persist link. Try again in a moment.");
        }
    }

    private void notifyPlayer(UUID playerId, String osuUsername) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                return;
            }
            player.sendMessage("Linked osu! account: " + osuUsername);
        });
    }

    private void cleanExpired() {
        Instant cutoff = Instant.now().minus(config.tokenTtl());
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(cutoff));
    }

    private LinkResult persistLink(PendingLink pending, OsuOAuthClient.OsuUserProfile profile) {
        Document document = players.load(pending.playerId().toString()).toCompletableFuture().join();
        CompletionStage<Void> usernameStage = document.set("osu.username", profile.username().trim());
        CompletionStage<Void> idStage = document.set("osu.userId", profile.userId());
        CompletionStage<Void> rankStage = profile.globalRank() != null
            ? document.set("osu.rank", profile.globalRank())
            : CompletableFuture.completedFuture(null);
        CompletionStage<Void> countryStage = profile.countryCode() != null && !profile.countryCode().isBlank()
            ? document.set("osu.country", profile.countryCode())
            : CompletableFuture.completedFuture(null);
        CompletionStage<Void> linkedAtStage = document.set("osu.linkedAt", Instant.now().toString());

        CompletableFuture.allOf(
            usernameStage.toCompletableFuture(),
            idStage.toCompletableFuture(),
            rankStage.toCompletableFuture(),
            countryStage.toCompletableFuture(),
            linkedAtStage.toCompletableFuture()
        ).join();

        return LinkResult.success(pending.username(), profile.username());
    }

    public record LinkResult(boolean success, String message, String playerUsername, String osuUsername) {
        public static LinkResult success(String playerUsername, String osuUsername) {
            return new LinkResult(true, "Linked osu! account.", playerUsername, osuUsername);
        }

        public static LinkResult failure(String message) {
            return new LinkResult(false, message, null, null);
        }
    }

    record PendingLink(UUID playerId, String username, Instant createdAt) {
        boolean isExpired(java.time.Duration ttl) {
            return createdAt.isBefore(Instant.now().minus(ttl));
        }
    }
}
