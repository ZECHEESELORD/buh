package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.util.List;

public final class VoteCommandScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "vote_command";

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        return List.of("&b&l/vote &f(NOW FIXED!)");
    }
}
