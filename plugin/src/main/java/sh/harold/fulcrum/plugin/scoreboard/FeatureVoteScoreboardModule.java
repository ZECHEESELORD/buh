package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.util.List;

public final class FeatureVoteScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "feature_vote";

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        return List.of(
            "&fVote for features!",
            "&8 ● &cBounties System",
            "&8 ● &dCustom Items Engine",
            "&8 ● &aSettlements",
            "&8 ● &6Economy"
        );
    }
}
