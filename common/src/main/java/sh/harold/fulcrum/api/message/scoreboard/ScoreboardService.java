package sh.harold.fulcrum.api.message.scoreboard;

import java.time.Duration;
import java.util.UUID;

/**
 * Minimal scoreboard service contract that avoids NMS coupling.
 */
public interface ScoreboardService {

    void registerScoreboard(String scoreboardId, ScoreboardDefinition definition);

    void unregisterScoreboard(String scoreboardId);

    boolean isScoreboardRegistered(String scoreboardId);

    void showScoreboard(UUID playerId, String scoreboardId);

    void hideScoreboard(UUID playerId);

    void refreshPlayerScoreboard(UUID playerId);

    String getCurrentScoreboardId(UUID playerId);

    boolean hasScoreboardDisplayed(UUID playerId);

    void flashModule(UUID playerId, int moduleIndex, ScoreboardModule module, Duration duration);

    void setPlayerTitle(UUID playerId, String title);

    String getPlayerTitle(UUID playerId);

    void clearPlayerTitle(UUID playerId);

    void setModuleOverride(UUID playerId, String moduleId, boolean enabled);

    boolean isModuleOverrideEnabled(UUID playerId, String moduleId);

    void clearPlayerData(UUID playerId);
}
