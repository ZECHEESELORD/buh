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
    private static final String DAMAGE_MARKERS_PATH = "settings.damage.markers.enabled";
    private static final String CUSTOM_ITEM_NAMES_PATH = "settings.items.custom_names.enabled";
    private static final boolean DEFAULT_SCOREBOARD = true;
    private static final boolean DEFAULT_PVP = false;
    private static final boolean DEFAULT_DAMAGE_MARKERS = true;
    private static final boolean DEFAULT_CUSTOM_ITEM_NAMES = false;
    private static final UsernameView DEFAULT_USERNAME_VIEW = UsernameView.MINECRAFT;

    private final DocumentCollection players;
    private final Map<UUID, Boolean> pvpCache;
    private final Map<UUID, UsernameView> usernameViewCache;
    private final Map<UUID, Boolean> damageMarkerCache;
    private final Map<UUID, Boolean> customItemNamesCache;
    private final Map<UUID, PlayerSettings> settingsCache;

    public PlayerSettingsService(DataApi dataApi) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.pvpCache = new ConcurrentHashMap<>();
        this.usernameViewCache = new ConcurrentHashMap<>();
        this.damageMarkerCache = new ConcurrentHashMap<>();
        this.customItemNamesCache = new ConcurrentHashMap<>();
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

    public CompletionStage<Boolean> areDamageMarkersEnabled(UUID playerId) {
        return loadSettings(playerId)
            .thenApply(PlayerSettings::damageMarkersEnabled)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load damage marker setting for " + playerId, throwable);
            });
    }

    public CompletionStage<Boolean> areCustomItemNamesEnabled(UUID playerId) {
        return loadSettings(playerId)
            .thenApply(PlayerSettings::customItemNamesEnabled)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load item name display setting for " + playerId, throwable);
            });
    }

    public CompletionStage<PlayerSettings> loadSettings(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerSettings cached = settingsCache.get(playerId);
        if (cached != null) {
            cachePvp(playerId, cached.pvpEnabled());
            cacheUsernameView(playerId, cached.usernameView());
            cacheDamageMarkers(playerId, cached.damageMarkersEnabled());
            cacheCustomItemNames(playerId, cached.customItemNamesEnabled());
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

    public CompletionStage<Boolean> setDamageMarkersEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        return persistSetting(playerId, DAMAGE_MARKERS_PATH, enabled, false, "damage markers");
    }

    public CompletionStage<Boolean> setCustomItemNamesEnabled(UUID playerId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        return persistSetting(playerId, CUSTOM_ITEM_NAMES_PATH, enabled, false, "custom item names")
            .thenApply(updated -> {
                cacheCustomItemNames(playerId, updated);
                updateCachedSettings(playerId, settings -> new PlayerSettings(
                    settings.scoreboardEnabled(),
                    settings.pvpEnabled(),
                    settings.usernameView(),
                    settings.damageMarkersEnabled(),
                    updated
                ));
                return updated;
            });
    }

    public CompletionStage<UsernameView> setUsernameView(UUID playerId, UsernameView view) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(view, "view");
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(USERNAME_VIEW_PATH, view.name()).thenApply(ignored -> view))
            .thenApply(updated -> {
                cacheUsernameView(playerId, updated);
                updateCachedSettings(playerId, settings -> new PlayerSettings(
                    settings.scoreboardEnabled(),
                    settings.pvpEnabled(),
                    updated,
                    settings.damageMarkersEnabled(),
                    settings.customItemNamesEnabled()
                ));
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

    public CompletionStage<Boolean> toggleDamageMarkers(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return toggleSetting(playerId, DAMAGE_MARKERS_PATH, DEFAULT_DAMAGE_MARKERS, false, "damage markers");
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
                updateCachedSettings(playerId, settings -> new PlayerSettings(
                    settings.scoreboardEnabled(),
                    settings.pvpEnabled(),
                    updated,
                    settings.damageMarkersEnabled(),
                    settings.customItemNamesEnabled()
                ));
                return updated;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to toggle username view for " + playerId, throwable);
            });
    }

    public CompletionStage<Boolean> toggleCustomItemNames(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return toggleSetting(playerId, CUSTOM_ITEM_NAMES_PATH, DEFAULT_CUSTOM_ITEM_NAMES, false, "custom item names")
            .thenApply(updated -> {
                cacheCustomItemNames(playerId, updated);
                updateCachedSettings(playerId, settings -> new PlayerSettings(
                    settings.scoreboardEnabled(),
                    settings.pvpEnabled(),
                    settings.usernameView(),
                    settings.damageMarkersEnabled(),
                    updated
                ));
                return updated;
            });
    }

    public boolean cachedPvpEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return pvpCache.getOrDefault(playerId, DEFAULT_PVP);
    }

    public boolean cachedDamageMarkersEnabled(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return damageMarkerCache.getOrDefault(playerId, DEFAULT_DAMAGE_MARKERS);
    }

    public boolean cachedCustomItemNames(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return customItemNamesCache.getOrDefault(playerId, DEFAULT_CUSTOM_ITEM_NAMES);
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
        damageMarkerCache.remove(playerId);
        customItemNamesCache.remove(playerId);
        settingsCache.remove(playerId);
    }

    private CompletionStage<PlayerSettings> resolveSettings(Document document, UUID playerId) {
        Optional<Boolean> scoreboardStored = document.get(SCOREBOARD_PATH, Boolean.class);
        Optional<Boolean> pvpStored = document.get(PVP_PATH, Boolean.class);
        Optional<String> usernameViewStored = document.get(USERNAME_VIEW_PATH, String.class);
        Optional<Boolean> damageMarkersStored = document.get(DAMAGE_MARKERS_PATH, Boolean.class);
        Optional<Boolean> customItemNamesStored = document.get(CUSTOM_ITEM_NAMES_PATH, Boolean.class);

        boolean scoreboardEnabled = scoreboardStored.orElse(DEFAULT_SCOREBOARD);
        boolean pvpEnabled = pvpStored.orElse(DEFAULT_PVP);
        boolean damageMarkersEnabled = damageMarkersStored.orElse(DEFAULT_DAMAGE_MARKERS);
        boolean customItemNamesEnabled = customItemNamesStored.orElse(DEFAULT_CUSTOM_ITEM_NAMES);
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
        if (damageMarkersStored.isEmpty()) {
            defaults.put(DAMAGE_MARKERS_PATH, damageMarkersEnabled);
        }
        if (customItemNamesStored.isEmpty()) {
            defaults.put(CUSTOM_ITEM_NAMES_PATH, customItemNamesEnabled);
        }

        CompletionStage<Void> persist = defaults.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : document.patch(defaults, java.util.List.of());

        return persist.thenApply(ignored -> {
            cachePvp(playerId, pvpEnabled);
            cacheUsernameView(playerId, usernameView);
            cacheDamageMarkers(playerId, damageMarkersEnabled);
            cacheCustomItemNames(playerId, customItemNamesEnabled);
            PlayerSettings settings = new PlayerSettings(scoreboardEnabled, pvpEnabled, usernameView, damageMarkersEnabled, customItemNamesEnabled);
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
                if (path.equals(DAMAGE_MARKERS_PATH)) {
                    cacheDamageMarkers(playerId, updated);
                }
                if (path.equals(CUSTOM_ITEM_NAMES_PATH)) {
                    cacheCustomItemNames(playerId, updated);
                }
                updateCachedSettings(playerId, settings -> updatedSettings(settings, path, updated));
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
                if (path.equals(DAMAGE_MARKERS_PATH)) {
                    cacheDamageMarkers(playerId, updated);
                }
                if (path.equals(CUSTOM_ITEM_NAMES_PATH)) {
                    cacheCustomItemNames(playerId, updated);
                }
                updateCachedSettings(playerId, settings -> updatedSettings(settings, path, updated));
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

    private void cacheDamageMarkers(UUID playerId, boolean enabled) {
        damageMarkerCache.put(playerId, enabled);
    }

    private void cacheCustomItemNames(UUID playerId, boolean enabled) {
        customItemNamesCache.put(playerId, enabled);
    }

    private PlayerSettings updatedSettings(PlayerSettings settings, String path, boolean updatedValue) {
        if (path.equals(SCOREBOARD_PATH)) {
            return new PlayerSettings(updatedValue, settings.pvpEnabled(), settings.usernameView(), settings.damageMarkersEnabled(), settings.customItemNamesEnabled());
        }
        if (path.equals(PVP_PATH)) {
            return new PlayerSettings(settings.scoreboardEnabled(), updatedValue, settings.usernameView(), settings.damageMarkersEnabled(), settings.customItemNamesEnabled());
        }
        if (path.equals(DAMAGE_MARKERS_PATH)) {
            return new PlayerSettings(settings.scoreboardEnabled(), settings.pvpEnabled(), settings.usernameView(), updatedValue, settings.customItemNamesEnabled());
        }
        if (path.equals(CUSTOM_ITEM_NAMES_PATH)) {
            return new PlayerSettings(settings.scoreboardEnabled(), settings.pvpEnabled(), settings.usernameView(), settings.damageMarkersEnabled(), updatedValue);
        }
        return settings;
    }

    private void updateCachedSettings(UUID playerId, UnaryOperator<PlayerSettings> mutator) {
        settingsCache.compute(playerId, (id, existing) -> {
            PlayerSettings base = existing == null
                ? new PlayerSettings(DEFAULT_SCOREBOARD, DEFAULT_PVP, DEFAULT_USERNAME_VIEW, DEFAULT_DAMAGE_MARKERS, DEFAULT_CUSTOM_ITEM_NAMES)
                : existing;
            PlayerSettings updated = mutator.apply(base);
            cachePvp(id, updated.pvpEnabled());
            cacheUsernameView(id, updated.usernameView());
            cacheDamageMarkers(id, updated.damageMarkersEnabled());
            cacheCustomItemNames(id, updated.customItemNamesEnabled());
            return updated;
        });
    }
}
