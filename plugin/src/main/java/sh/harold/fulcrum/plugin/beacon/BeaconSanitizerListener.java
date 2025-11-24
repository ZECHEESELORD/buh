package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Objects;

final class BeaconSanitizerListener implements Listener {

    private final BeaconSanitizerService sanitizerService;

    BeaconSanitizerListener(BeaconSanitizerService sanitizerService) {
        this.sanitizerService = Objects.requireNonNull(sanitizerService, "sanitizerService");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        sanitizerService.handlePlayerLoad(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent event) {
        sanitizerService.handlePlayerLoad(event.getPlayer());
    }
}
