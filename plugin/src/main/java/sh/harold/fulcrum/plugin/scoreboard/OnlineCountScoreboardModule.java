package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardModule;

import java.util.List;
import java.util.Objects;

public final class OnlineCountScoreboardModule implements ScoreboardModule {

    private static final String MODULE_ID = "online_count";
    private final Server server;

    public OnlineCountScoreboardModule(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public String getModuleId() {
        return MODULE_ID;
    }

    @Override
    public List<String> renderLines(Player player) {
        int online = server.getOnlinePlayers().size();
        int max = server.getMaxPlayers();
        return List.of("&fOnline: &a" + online + "&7/&a" + max);
    }
}
