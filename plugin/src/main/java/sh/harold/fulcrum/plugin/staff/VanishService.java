package sh.harold.fulcrum.plugin.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishService implements Listener {

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();

    public VanishService(JavaPlugin plugin, StaffGuard staffGuard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    public VanishState toggle(Player player) {
        boolean next = !isVanished(player);
        return setVanished(player, next);
    }

    public VanishState setVanished(Player player, boolean vanish) {
        boolean changed = vanish
            ? vanishedPlayers.add(player.getUniqueId())
            : vanishedPlayers.remove(player.getUniqueId());

        refreshVisibility(player);
        return new VanishState(vanish, changed);
    }

    public void revealAll() {
        for (UUID playerId : Set.copyOf(vanishedPlayers)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                vanishedPlayers.remove(playerId);
                continue;
            }
            setVanished(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (isVanished(joining)) {
            event.joinMessage(null);
            refreshVisibility(joining);
            joining.sendMessage(Component.text("You remain vanished; use /vanish off to reappear.", NamedTextColor.YELLOW));
        }
        hideVanishedFrom(joining);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer())) {
            event.quitMessage(null);
        }
    }

    private void hideVanishedFrom(Player viewer) {
        if (staffGuard.isStaff(viewer)) {
            return;
        }
        for (UUID vanishedId : Set.copyOf(vanishedPlayers)) {
            Player vanished = plugin.getServer().getPlayer(vanishedId);
            if (vanished != null && vanished.isOnline()) {
                viewer.hidePlayer(plugin, vanished);
            }
        }
    }

    private void refreshVisibility(Player target) {
        boolean targetVanished = isVanished(target);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            if (targetVanished && !staffGuard.isStaff(viewer)) {
                viewer.hidePlayer(plugin, target);
            } else {
                viewer.showPlayer(plugin, target);
            }
        }
    }

    public record VanishState(boolean vanished, boolean changed) {
    }
}
