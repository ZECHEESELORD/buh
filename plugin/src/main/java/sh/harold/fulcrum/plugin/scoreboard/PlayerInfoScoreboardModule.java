package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;
import sh.harold.fulcrum.plugin.playerdata.LevelProgress;
import sh.harold.fulcrum.plugin.playerdata.LevelTier;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerInfoScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "player_info";
    private static final Duration STALE_AFTER = Duration.ofSeconds(10);

    private final PlayerLevelingService levelingService;
    private final Logger logger;
    private final Map<UUID, CachedLevel> levels = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<LevelProgress>> inflight = new ConcurrentHashMap<>();

    public PlayerInfoScoreboardModule(PlayerLevelingService levelingService, Logger logger) {
        this.levelingService = Objects.requireNonNull(levelingService, "levelingService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        UUID playerId = player.getUniqueId();
        CachedLevel cached = levels.get(playerId);
        if (shouldRefresh(cached) && player.isOnline()) {
            requestLevel(playerId);
        }
        int level = cached == null ? 0 : cached.level();
        String line = "&fLevel: " + levelLegacyColor(level) + level;
        return List.of(line);
    }

    void clear(UUID playerId) {
        levels.remove(playerId);
        inflight.remove(playerId);
    }

    private boolean shouldRefresh(CachedLevel cached) {
        if (cached == null) {
            return true;
        }
        return cached.isStale(STALE_AFTER);
    }

    private void requestLevel(UUID playerId) {
        inflight.computeIfAbsent(playerId, ignored -> levelingService.loadProgress(playerId)
            .toCompletableFuture()
            .whenComplete((progress, throwable) -> {
                if (progress != null) {
                    levels.put(playerId, new CachedLevel(progress.level(), Instant.now()));
                } else if (throwable != null) {
                    logger.log(Level.FINE, "Failed to fetch level for " + playerId, throwable);
                }
                inflight.remove(playerId);
            }));
    }

    private String levelLegacyColor(int level) {
        LevelTier tier = resolveTier(level);
        return switch (tier) {
            case DEFAULT -> "&7";
            case WHITE -> "&f";
            case YELLOW -> "&e";
            case GREEN -> "&a";
            case DARK_GREEN -> "&2";
            case AQUA -> "&b";
            case CYAN -> "&3";
            case BLUE -> "&9";
            case PINK -> "&d";
            case PURPLE -> "&5";
            case GOLD -> "&6";
            case RED -> "&c";
            case DARK_RED -> "&4";
        };
    }

    private LevelTier resolveTier(int level) {
        int safeLevel = Math.max(0, level);
        LevelTier resolved = LevelTier.DEFAULT;
        for (LevelTier tier : LevelTier.values()) {
            if (safeLevel >= tier.minLevel()) {
                resolved = tier;
            } else {
                break;
            }
        }
        return resolved;
    }

    private record CachedLevel(int level, Instant fetchedAt) {

        boolean isStale(Duration ttl) {
            return fetchedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}
