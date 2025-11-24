package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class PlayerSettingsService {

    private static final String SCOREBOARD_PATH = "settings.scoreboard.enabled";

    private final DocumentCollection players;

    public PlayerSettingsService(DataApi dataApi) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
    }

    public CompletionStage<Boolean> isScoreboardEnabled(UUID playerId) {
        return loadScoreboardSetting(playerId).exceptionally(throwable -> {
            throw new CompletionException("Failed to load scoreboard setting for " + playerId, throwable);
        });
    }

    public CompletionStage<Boolean> setScoreboardEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(SCOREBOARD_PATH, enabled).thenApply(ignored -> enabled))
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store scoreboard setting for " + playerId, throwable);
            });
    }

    public CompletionStage<Boolean> toggleScoreboard(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(document -> {
                boolean current = readScoreboard(document);
                boolean updated = !current;
                return document.set(SCOREBOARD_PATH, updated).thenApply(ignored -> updated);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to toggle scoreboard setting for " + playerId, throwable);
            });
    }

    private CompletionStage<Boolean> loadScoreboardSetting(UUID playerId) {
        return players.load(playerId.toString())
            .thenCompose(document -> {
                Boolean stored = document.get(SCOREBOARD_PATH, Boolean.class).orElse(null);
                if (stored != null) {
                    return CompletableFuture.completedFuture(stored);
                }
                return document.set(SCOREBOARD_PATH, true)
                    .thenApply(ignored -> true);
            });
    }

    private boolean readScoreboard(Document document) {
        Optional<Boolean> stored = document.get(SCOREBOARD_PATH, Boolean.class);
        return stored.orElse(true);
    }
}
