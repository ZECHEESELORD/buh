package sh.harold.fulcrum.plugin.osu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

final class OsuRankRefreshListener implements Listener {

    private final OsuLinkService osuLinkService;

    OsuRankRefreshListener(OsuLinkService osuLinkService) {
        this.osuLinkService = Objects.requireNonNull(osuLinkService, "osuLinkService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        osuLinkService.refreshOsuProfile(event.getPlayer().getUniqueId());
    }
}

