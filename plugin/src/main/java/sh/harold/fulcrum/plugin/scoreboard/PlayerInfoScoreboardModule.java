package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.util.List;

public final class PlayerInfoScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "player_info";

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        String nameLine = "&fName: &b" + player.getName();
        String pingLine = "&fPing: &a" + player.getPing() + "ms";
        return List.of(nameLine, pingLine);
    }
}
