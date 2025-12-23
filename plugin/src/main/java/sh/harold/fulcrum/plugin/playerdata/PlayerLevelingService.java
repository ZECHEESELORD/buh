package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

public final class PlayerLevelingService {

    private static final String XP_PATH = "progression.xp";
    private static final String PRESTIGE_PATH = "progression.prestige";
    private static final long DEFAULT_XP = 0L;
    private static final int DEFAULT_PRESTIGE = 0;
    private static final int MAX_LEVEL = 200;

    private final DocumentCollection players;
    private final LevelingCurve curve;
    private final long maxTotalXp;
    private final List<LevelUpdateListener> listeners = new CopyOnWriteArrayList<>();

    public PlayerLevelingService(DataApi dataApi) {
        this(dataApi, LevelingCurve.addictive());
    }

    public PlayerLevelingService(DataApi dataApi, LevelingCurve curve) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.curve = Objects.requireNonNull(curve, "curve");
        this.maxTotalXp = computeMaxTotalXp(curve, MAX_LEVEL);
    }

    public LevelingCurve curve() {
        return curve;
    }

    public void addListener(LevelUpdateListener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
    }

    public void removeListener(LevelUpdateListener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    public int maxLevel() {
        return MAX_LEVEL;
    }

    public LevelProgress progressFor(long xp) {
        return curve.progressFor(clampXp(xp), MAX_LEVEL);
    }

    public long totalXpForLevel(int level) {
        int target = Math.max(0, Math.min(level, MAX_LEVEL));
        long total = 0L;
        for (int step = 0; step < target; step++) {
            long cost = curve.xpForNextLevel(step);
            try {
                total = Math.addExact(total, cost);
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    public CompletionStage<Long> loadXp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenApply(document -> document.get(XP_PATH, Number.class)
                .map(Number::longValue)
                .orElse(DEFAULT_XP))
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load XP for " + playerId, throwable);
            });
    }

    public CompletionStage<Integer> loadPrestige(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenApply(document -> document.get(PRESTIGE_PATH, Number.class)
                .map(Number::intValue)
                .orElse(DEFAULT_PRESTIGE))
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load prestige for " + playerId, throwable);
            });
    }

    public CompletionStage<LevelProgress> loadProgress(UUID playerId) {
        return loadXp(playerId)
            .thenApply(this::progressFor);
    }

    public CompletionStage<LevelProgress> addXp(UUID playerId, long delta) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(document -> updateXp(document, delta))
            .thenApply(this::progressFor)
            .thenApply(progress -> {
                notifyListeners(playerId, progress);
                return progress;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to update XP for " + playerId, throwable);
            });
    }

    public CompletionStage<LevelProgress> setXp(UUID playerId, long xp) {
        Objects.requireNonNull(playerId, "playerId");
        long clamped = clampXp(xp);
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(XP_PATH, clamped).thenApply(ignored -> clamped))
            .thenApply(this::progressFor)
            .thenApply(progress -> {
                notifyListeners(playerId, progress);
                return progress;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to set XP for " + playerId, throwable);
            });
    }

    public CompletionStage<PrestigeResult> prestige(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(this::updatePrestige)
            .thenApply(result -> {
                notifyListeners(playerId, result.progress());
                return result;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to prestige for " + playerId, throwable);
            });
    }

    private CompletionStage<Long> updateXp(Document document, long delta) {
        AtomicLong updated = new AtomicLong(DEFAULT_XP);
        return document.update(data -> {
            long current = readLong(data, XP_PATH, DEFAULT_XP);
            long next = clampXp(safeAdd(current, delta));
            writePath(data, XP_PATH, next);
            updated.set(next);
            return data;
        }).thenApply(ignored -> updated.get());
    }

    private long safeAdd(long current, long delta) {
        try {
            return Math.addExact(current, delta);
        } catch (ArithmeticException exception) {
            return delta >= 0 ? Long.MAX_VALUE : 0L;
        }
    }

    private long clampXp(long xp) {
        if (xp <= 0L) {
            return 0L;
        }
        return Math.min(xp, maxTotalXp);
    }

    private void notifyListeners(UUID playerId, LevelProgress progress) {
        if (playerId == null || progress == null || listeners.isEmpty()) {
            return;
        }
        for (LevelUpdateListener listener : listeners) {
            try {
                listener.onLevelUpdate(playerId, progress);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private CompletionStage<PrestigeResult> updatePrestige(Document document) {
        AtomicLong updatedXp = new AtomicLong(DEFAULT_XP);
        java.util.concurrent.atomic.AtomicInteger updatedPrestige = new java.util.concurrent.atomic.AtomicInteger(DEFAULT_PRESTIGE);
        java.util.concurrent.atomic.AtomicReference<LevelProgress> progressRef = new java.util.concurrent.atomic.AtomicReference<>(progressFor(DEFAULT_XP));
        java.util.concurrent.atomic.AtomicBoolean prestiged = new java.util.concurrent.atomic.AtomicBoolean(false);
        return document.update(data -> {
            long storedXp = readLong(data, XP_PATH, DEFAULT_XP);
            long currentXp = clampXp(storedXp);
            int currentPrestige = readInt(data, PRESTIGE_PATH, DEFAULT_PRESTIGE);
            if (currentXp != storedXp) {
                writePath(data, XP_PATH, currentXp);
            }
            LevelProgress progress = progressFor(currentXp);
            if (progress.level() < MAX_LEVEL) {
                updatedXp.set(currentXp);
                updatedPrestige.set(currentPrestige);
                progressRef.set(progress);
                return data;
            }
            int nextPrestige = Math.max(0, currentPrestige) + 1;
            writePath(data, XP_PATH, DEFAULT_XP);
            writePath(data, PRESTIGE_PATH, nextPrestige);
            updatedXp.set(DEFAULT_XP);
            updatedPrestige.set(nextPrestige);
            progressRef.set(progressFor(DEFAULT_XP));
            prestiged.set(true);
            return data;
        }).thenApply(ignored -> new PrestigeResult(prestiged.get(), updatedPrestige.get(), progressRef.get()));
    }

    private static long computeMaxTotalXp(LevelingCurve curve, int maxLevel) {
        long total = 0L;
        int cap = Math.max(0, maxLevel);
        for (int level = 0; level < cap; level++) {
            long cost = curve.xpForNextLevel(level);
            try {
                total = Math.addExact(total, cost);
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }
        return total;
    }

    private static long readLong(Map<String, Object> data, String path, long defaultValue) {
        Object value = readPath(data, path);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return defaultValue;
    }

    private static int readInt(Map<String, Object> data, String path, int defaultValue) {
        Object value = readPath(data, path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private static Object readPath(Map<String, Object> data, String path) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(path, "path");
        Object current = data;
        String[] segments = path.split("\\.");
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static void writePath(Map<String, Object> data, String path, Object value) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(path, "path");
        String[] segments = path.split("\\.");
        Map<String, Object> current = data;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.get(segments[i]);
            if (next instanceof Map<?, ?> map) {
                current = castMap(map);
            } else {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(segments[i], created);
                current = created;
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    public record PrestigeResult(boolean prestiged, int prestigeCount, LevelProgress progress) {
    }

    @FunctionalInterface
    public interface LevelUpdateListener {
        void onLevelUpdate(UUID playerId, LevelProgress progress);
    }
}
