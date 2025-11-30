package sh.harold.fulcrum.plugin.playerdata;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class PlayerSettingsService {

    private static final String SCOREBOARD_PATH = "settings.scoreboard.enabled";
    private static final String PVP_PATH = "settings.pvp.enabled";
    private static final String USERNAME_VIEW_PATH = "settings.username.view";
    private static final boolean DEFAULT_SCOREBOARD = true;
    private static final boolean DEFAULT_PVP = false;
    private static final UsernameView DEFAULT_USERNAME_VIEW = UsernameView.MINECRAFT;

    private final DocumentCollection players;
    private final Map<UUID, Boolean> pvpCache;
    private final Map<UUID, UsernameView> usernameViewCache;
    private final Map<UUID, PlayerSettings> settingsCache;

    public PlayerSettingsService(DataApi dataApi) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.pvpCache = new ConcurrentHashMap<>();
        this.usernameViewCache = new ConcurrentHashMap<>();
        this.settingsCache = new ConcurrentHashMap<>();
    }

    public CompletionStage<Boolean> isScoreboardEnabled(UUID playerId) {
        return loadSettings(playerId)
            .thenApply(PlayerSettings::scoreboardEnabled)
            .exceptionally(throwable -> {
            throw new CompletionException("Failed to load scoreboard setting for " + playerId, throwable);
        });
    }

    public CompletionStage<Boolean> isPvpEnabled(UUID playerId) {
        return loadSettings(playerId)
            .thenApply(PlayerSettings::pvpEnabled)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load PvP setting for " + playerId, throwable);
            });
    }

    public CompletionStage<PlayerSettings> loadSettings(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerSettings cached = settingsCache.get(playerId);
        if (cached != null) {
            cachePvp(playerId, cached.pvpEnabled());
            cacheUsernameView(playerId, cached.usernameView());
            return CompletableFuture.completedFuture(cached);
        }
        return players.load(playerId.toString())
            .thenCompose(document -> resolveSettings(document, playerId))
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load settings for " + playerId, throwable);
            });
    }

    public CompletionStage<Boolean> setScoreboardEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        return persistSetting(playerId, SCOREBOARD_PATH, enabled, false, "scoreboard");
    }

    public CompletionStage<Boolean> setPvpEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        return persistSetting(playerId, PVP_PATH, enabled, true, "PvP");
    }

    public CompletionStage<UsernameView> setUsernameView(UUID playerId, UsernameView view) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(USERNAME_VIEW_PATH, view.name()).thenApply(ignored -> view))
            .thenApply(updated -> {
                cacheUsernameView(playerId, updated);
                updateCachedSettings(playerId, settings -> new PlayerSettings(settings.scoreboardEnabled(), settings.pvpEnabled(), updated));
                return updated;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store username view for " + playerId, throwable);
            });
    }

    public CompletionStage<Boolean> toggleScoreboard(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return toggleSetting(playerId, SCOREBOARD_PATH, DEFAULT_SCOREBOARD, false, "scoreboard");
    }

    public CompletionStage<Boolean> togglePvp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return toggleSetting(playerId, PVP_PATH, DEFAULT_PVP, true, "PvP");
    }

    public CompletionStage<UsernameView> toggleUsernameView(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.load(playerId.toString())
            .thenCompose(document -> {
                UsernameView current = document.get(USERNAME_VIEW_PATH, String.class)
                    .map(UsernameView::fromConfig)
                    .orElse(DEFAULT_USERNAME_VIEW);
                UsernameView updated = current.next();
                return document.set(USERNAME_VIEW_PATH, updated.name()).thenApply(ignored -> updated);
            })
            .thenApply(updated -> {
                cacheUsernameView(playerId, updated);
                updateCachedSettings(playerId, settings -> new PlayerSettings(settings.scoreboardEnabled(), settings.pvpEnabled(), updated));
                return updated;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to toggle username view for " + playerId, throwable);
            });
    }

    public boolean cachedPvpEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return pvpCache.getOrDefault(playerId, DEFAULT_PVP);
    }

    public boolean hasCachedPvp(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return pvpCache.containsKey(playerId);
    }

    public UsernameView cachedUsernameView(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return usernameViewCache.getOrDefault(playerId, DEFAULT_USERNAME_VIEW);
    }

    public void evictCachedSettings(UUID playerId) {
        pvpCache.remove(Objects.requireNonNull(playerId, "playerId"));
        usernameViewCache.remove(playerId);
        settingsCache.remove(playerId);
    }

    private CompletionStage<PlayerSettings> resolveSettings(Document document, UUID playerId) {
        Optional<Boolean> scoreboardStored = document.get(SCOREBOARD_PATH, Boolean.class);
        Optional<Boolean> pvpStored = document.get(PVP_PATH, Boolean.class);
        Optional<String> usernameViewStored = document.get(USERNAME_VIEW_PATH, String.class);

        boolean scoreboardEnabled = scoreboardStored.orElse(DEFAULT_SCOREBOARD);
        boolean pvpEnabled = pvpStored.orElse(DEFAULT_PVP);
        UsernameView usernameView = usernameViewStored
            .map(UsernameView::fromConfig)
            .orElse(DEFAULT_USERNAME_VIEW);

        Map<String, Object> defaults = new LinkedHashMap<>();
        if (scoreboardStored.isEmpty()) {
            defaults.put(SCOREBOARD_PATH, scoreboardEnabled);
        }
        if (pvpStored.isEmpty()) {
            defaults.put(PVP_PATH, pvpEnabled);
        }
        if (usernameViewStored.isEmpty()) {
            defaults.put(USERNAME_VIEW_PATH, usernameView.name());
        }

        CompletionStage<Void> persist = defaults.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : document.patch(defaults, java.util.List.of());

        return persist.thenApply(ignored -> {
            cachePvp(playerId, pvpEnabled);
            cacheUsernameView(playerId, usernameView);
            PlayerSettings settings = new PlayerSettings(scoreboardEnabled, pvpEnabled, usernameView);
            settingsCache.put(playerId, settings);
            return settings;
        });
    }

    private CompletionStage<Boolean> persistSetting(UUID playerId, String path, boolean value, boolean cachePvp, String label) {
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(path, value).thenApply(ignored -> value))
            .thenApply(updated -> {
                if (cachePvp) {
                    cachePvp(playerId, updated);
                }
                updateCachedSettings(playerId, settings -> path.equals(SCOREBOARD_PATH)
                    ? new PlayerSettings(updated, settings.pvpEnabled(), settings.usernameView())
                    : new PlayerSettings(settings.scoreboardEnabled(), updated, settings.usernameView()));
                return updated;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store " + label + " setting for " + playerId, throwable);
            });
    }

    private CompletionStage<Boolean> toggleSetting(UUID playerId, String path, boolean defaultValue, boolean cachePvp, String label) {
        return players.load(playerId.toString())
            .thenCompose(document -> {
                boolean current = document.get(path, Boolean.class).orElse(defaultValue);
                boolean updated = !current;
                return document.set(path, updated).thenApply(ignored -> updated);
            })
            .thenApply(updated -> {
                if (cachePvp) {
                    cachePvp(playerId, updated);
                }
                updateCachedSettings(playerId, settings -> path.equals(SCOREBOARD_PATH)
                    ? new PlayerSettings(updated, settings.pvpEnabled(), settings.usernameView())
                    : new PlayerSettings(settings.scoreboardEnabled(), updated, settings.usernameView()));
                return updated;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to toggle " + label + " setting for " + playerId, throwable);
            });
    }

    private void cachePvp(UUID playerId, boolean enabled) {
        pvpCache.put(playerId, enabled);
    }

    private void cacheUsernameView(UUID playerId, UsernameView view) {
        usernameViewCache.put(playerId, view);
    }

    private void updateCachedSettings(UUID playerId, UnaryOperator<PlayerSettings> mutator) {
        settingsCache.compute(playerId, (id, existing) -> {
            PlayerSettings base = existing == null
                ? new PlayerSettings(DEFAULT_SCOREBOARD, DEFAULT_PVP, DEFAULT_USERNAME_VIEW)
                : existing;
            PlayerSettings updated = mutator.apply(base);
            cachePvp(id, updated.pvpEnabled());
            cacheUsernameView(id, updated.usernameView());
            return updated;
        });
    }
}
