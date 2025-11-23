package sh.harold.fulcrum.api.message.scoreboard.player;

import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerScoreboardState {

    private final UUID playerId;
    private final Map<String, ModuleOverride> moduleOverrides = new ConcurrentHashMap<>();
    private final Map<Integer, FlashState> activeFlashes = new ConcurrentHashMap<>();
    private volatile String currentScoreboardId;
    private volatile String customTitle;

    public PlayerScoreboardState(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCurrentScoreboardId() {
        return currentScoreboardId;
    }

    public void setCurrentScoreboardId(String currentScoreboardId) {
        this.currentScoreboardId = currentScoreboardId;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle = customTitle;
    }

    public boolean hasCustomTitle() {
        return customTitle != null && !customTitle.isBlank();
    }

    public boolean hasScoreboard() {
        return currentScoreboardId != null;
    }

    public Optional<ModuleOverride> getOverride(String moduleId) {
        return Optional.ofNullable(moduleOverrides.get(moduleId));
    }

    public void setOverride(ModuleOverride override) {
        moduleOverrides.put(override.moduleId(), override);
    }

    public void removeOverride(String moduleId) {
        moduleOverrides.remove(moduleId);
    }

    public void clearOverrides() {
        moduleOverrides.clear();
    }

    public void startFlash(int index, ScoreboardModule module, Duration duration) {
        activeFlashes.put(index, new FlashState(module, System.currentTimeMillis() + duration.toMillis()));
    }

    public void stopFlash(int index) {
        activeFlashes.remove(index);
    }

    public Optional<ScoreboardModule> activeFlash(int index) {
        FlashState state = activeFlashes.get(index);
        if (state == null) {
            return Optional.empty();
        }
        if (state.isExpired()) {
            activeFlashes.remove(index);
            return Optional.empty();
        }
        return Optional.of(state.module);
    }

    public void clearFlashes() {
        activeFlashes.clear();
    }

    private record FlashState(ScoreboardModule module, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
