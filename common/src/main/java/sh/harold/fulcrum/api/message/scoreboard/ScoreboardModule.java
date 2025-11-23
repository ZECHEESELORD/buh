package sh.harold.fulcrum.api.message.scoreboard;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a slice of scoreboard content.
 * Implementations should return short, unique lines (max 40 chars) for the viewer.
 */
public interface ScoreboardModule {

    String getModuleId();

    List<String> renderLines(Player player);

    default boolean isEnabled(Player player) {
        return true;
    }
}
