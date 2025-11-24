package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.util.List;

public final class FeatureVoteResultScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "feature_vote";

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        return List.of(
            "&7Vote Winner:",
            "&8 ● &6Economy &8(44%)"
        );
    }
}
