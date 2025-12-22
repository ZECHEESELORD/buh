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
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerLevelingService {

    private static final String XP_PATH = "progression.xp";
    private static final long DEFAULT_XP = 0L;

    private final DocumentCollection players;
    private final LevelingCurve curve;

    public PlayerLevelingService(DataApi dataApi) {
        this(dataApi, LevelingCurve.addictive());
    }

    public PlayerLevelingService(DataApi dataApi, LevelingCurve curve) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.curve = Objects.requireNonNull(curve, "curve");
    }

    public LevelingCurve curve() {
        return curve;
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

    public CompletionStage<LevelProgress> loadProgress(UUID playerId) {
        return loadXp(playerId)
            .thenApply(curve::progressFor);
    }

    public CompletionStage<LevelProgress> addXp(UUID playerId, long delta) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(document -> updateXp(document, delta))
            .thenApply(curve::progressFor)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to update XP for " + playerId, throwable);
            });
    }

    public CompletionStage<LevelProgress> setXp(UUID playerId, long xp) {
        Objects.requireNonNull(playerId, "playerId");
        long clamped = clampXp(xp);
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(XP_PATH, clamped).thenApply(ignored -> clamped))
            .thenApply(curve::progressFor)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to set XP for " + playerId, throwable);
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
        return Math.max(0L, xp);
    }

    private static long readLong(Map<String, Object> data, String path, long defaultValue) {
        Object value = readPath(data, path);
        if (value instanceof Number number) {
            return number.longValue();
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
}
