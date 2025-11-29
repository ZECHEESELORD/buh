package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;
import sh.harold.fulcrum.plugin.economy.BalanceSnapshot;
import sh.harold.fulcrum.plugin.economy.EconomyService;

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

public final class ShardBalanceScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "shard_balance";
    private static final Duration STALE_AFTER = Duration.ofSeconds(5);
    private static final Duration DELTA_TTL = Duration.ofSeconds(5);

    private final EconomyService economyService;
    private final Logger logger;
    private final Map<UUID, CachedBalance> balances = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<BalanceSnapshot>> inflight = new ConcurrentHashMap<>();
    private final Map<UUID, BalanceDelta> deltas = new ConcurrentHashMap<>();

    public ShardBalanceScoreboardModule(EconomyService economyService, Logger logger) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        UUID playerId = player.getUniqueId();
        CachedBalance cached = balances.get(playerId);
        if (shouldRefresh(cached) && player.isOnline()) {
            requestBalance(playerId);
        }
        long shards = cached == null ? 0L : cached.amount();
        BalanceDelta delta = deltaFor(playerId);
        String line = " &fShards: &3" + shards;
        if (delta != null) {
            line += " " + formatDelta(delta.amount());
        }
        return List.of(line);
    }

    void clear(UUID playerId) {
        balances.remove(playerId);
        inflight.remove(playerId);
        deltas.remove(playerId);
    }

    void pushBalance(UUID playerId, long balance) {
        balances.compute(playerId, (ignored, previous) -> {
            long previousAmount = previous == null ? balance : previous.amount();
            long delta = balance - previousAmount;
            if (delta != 0) {
                deltas.put(playerId, new BalanceDelta(delta, Instant.now()));
            } else {
                deltas.remove(playerId);
            }
            return new CachedBalance(balance, Instant.now());
        });
    }

    private boolean shouldRefresh(CachedBalance cached) {
        if (cached == null) {
            return true;
        }
        return cached.isStale(STALE_AFTER);
    }

    private void requestBalance(UUID playerId) {
        inflight.computeIfAbsent(playerId, ignored -> economyService.balance(playerId)
            .toCompletableFuture()
            .whenComplete((snapshot, throwable) -> {
                if (snapshot != null) {
                    balances.compute(playerId, (ignoredId, previous) -> {
                        long previousAmount = previous == null ? snapshot.balance() : previous.amount();
                        long delta = snapshot.balance() - previousAmount;
                        if (delta != 0) {
                            deltas.put(playerId, new BalanceDelta(delta, Instant.now()));
                        } else {
                            deltas.remove(playerId);
                        }
                        return new CachedBalance(snapshot.balance(), Instant.now());
                    });
                } else if (throwable != null) {
                    logger.log(Level.FINE, "Failed to fetch shard balance for " + playerId, throwable);
                }
                inflight.remove(playerId);
            }));
    }

    private BalanceDelta deltaFor(UUID playerId) {
        BalanceDelta delta = deltas.get(playerId);
        if (delta == null) {
            return null;
        }
        if (delta.isExpired(DELTA_TTL)) {
            deltas.remove(playerId);
            return null;
        }
        return delta;
    }

    private String formatDelta(long delta) {
        String color = delta >= 0 ? "&b" : "&c";
        String sign = delta >= 0 ? "+" : "-";
        long magnitude = Math.abs(delta);
        return color + "(" + sign + magnitude + ")";
    }

    private record CachedBalance(long amount, Instant fetchedAt) {

        boolean isStale(Duration ttl) {
            return fetchedAt.plus(ttl).isBefore(Instant.now());
        }
    }

    private record BalanceDelta(long amount, Instant recordedAt) {

        boolean isExpired(Duration ttl) {
            return recordedAt.plus(ttl).isBefore(Instant.now());
        }
    }
}
