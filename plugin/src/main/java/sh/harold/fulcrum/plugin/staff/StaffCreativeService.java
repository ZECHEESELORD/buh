package sh.harold.fulcrum.plugin.staff;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffCreativeService implements Listener {

    public static final String CREATIVE_TAG = "fulcrum:staff_creative";

    private static final Component STAFF_PREFIX = Component.text("[STAFF] ", NamedTextColor.AQUA);
    private static final NamedTextColor STAFF_ACCENT = NamedTextColor.AQUA;

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public StaffCreativeService(JavaPlugin plugin, StaffGuard staffGuard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean enable(Player player) {
        if (!staffGuard.isStaff(player)) {
            return false;
        }
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            return true;
        }
        Session session = new Session(
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isInvulnerable(),
            bossBar()
        );
        sessions.put(id, session);

        player.addScoreboardTag(CREATIVE_TAG);
        player.showBossBar(session.bar());
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        return true;
    }

    public boolean disable(Player player) {
        UUID id = player.getUniqueId();
        Session session = sessions.remove(id);
        player.removeScoreboardTag(CREATIVE_TAG);
        if (session == null) {
            return false;
        }
        player.hideBossBar(session.bar());
        player.setGameMode(session.previousMode());
        player.setAllowFlight(session.allowedFlight());
        player.setFlying(session.wasFlying() && session.allowedFlight());
        player.setInvulnerable(session.invulnerable());
        return true;
    }

    public Optional<Boolean> toggle(Player player, GameMode mode) {
        if (mode == GameMode.CREATIVE) {
            return Optional.of(enable(player));
        }
        if (mode == GameMode.SURVIVAL || mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE) {
            boolean changed = disable(player);
            player.setGameMode(mode);
            return Optional.of(changed);
        }
        return Optional.empty();
    }

    public void disableAll() {
        sessions.keySet().stream()
            .map(plugin.getServer()::getPlayer)
            .filter(Objects::nonNull)
            .forEach(this::disable);
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        disable(event.getPlayer());
    }

    private record Session(GameMode previousMode, boolean allowedFlight, boolean wasFlying, boolean invulnerable, BossBar bar) {
    }

    private BossBar bossBar() {
        Component label = STAFF_PREFIX
            .append(Component.text("Creative Mode", STAFF_ACCENT))
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        return BossBar.bossBar(label, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }
}
