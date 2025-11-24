package sh.harold.fulcrum.plugin.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VanishService implements Listener {

    private static final String VANISH_PATH = "staff.vanish";
    private static final Duration VANISH_STATE_TIMEOUT = Duration.ofSeconds(2);
    private static final Component VANISH_TITLE = LegacyComponentSerializer.legacyAmpersand().deserialize("&8Vanish: &a&lENABLED");

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final DocumentCollection players;
    private final Logger logger;
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, BossBar> vanishBars = new ConcurrentHashMap<>();

    public VanishService(JavaPlugin plugin, StaffGuard staffGuard, DataApi dataApi) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.logger = plugin.getLogger();
    }

    public boolean isVanished(Player player) {
        return vanishedPlayers.contains(player.getUniqueId());
    }

    public VanishState toggle(Player player) {
        boolean next = !isVanished(player);
        return setVanished(player, next);
    }

    public VanishState setVanished(Player player, boolean vanish) {
        UUID playerId = player.getUniqueId();
        boolean changed = vanish
            ? vanishedPlayers.add(playerId)
            : vanishedPlayers.remove(playerId);

        refreshVisibility(player);
        if (vanish) {
            showVanishBar(player);
        } else {
            hideVanishBar(player);
        }
        persistVanishState(playerId, vanish);
        return new VanishState(vanish, changed);
    }

    public void revealAll() {
        for (UUID playerId : Set.copyOf(vanishedPlayers)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            showToEveryone(player);
            hideVanishBar(player);
        }
        vanishedPlayers.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID playerId = event.getUniqueId();
        players.load(playerId.toString())
            .toCompletableFuture()
            .orTimeout(VANISH_STATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .thenAccept(document -> hydrateVanishState(playerId, document))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to load vanish state for " + playerId, throwable);
                return null;
            })
            .join();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (isVanished(joining)) {
            event.joinMessage(null);
            refreshVisibility(joining);
            joining.sendMessage(Component.text("You remain vanished; use /vanish off to reappear.", NamedTextColor.YELLOW));
            showVanishBar(joining);
        }
        hideVanishedFrom(joining);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (isVanished(event.getPlayer())) {
            event.quitMessage(null);
            hideVanishBar(event.getPlayer());
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

    private void showToEveryone(Player target) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }
            viewer.showPlayer(plugin, target);
        }
    }

    private void persistVanishState(UUID playerId, boolean vanished) {
        players.load(playerId.toString())
            .thenCompose(document -> document.set(VANISH_PATH, vanished))
            .toCompletableFuture()
            .orTimeout(VANISH_STATE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to persist vanish state for " + playerId, throwable);
                return null;
            });
    }

    private void hydrateVanishState(UUID playerId, Document document) {
        boolean stored = document.get(VANISH_PATH, Boolean.class).orElse(false);
        if (stored) {
            vanishedPlayers.add(playerId);
            return;
        }
        vanishedPlayers.remove(playerId);
    }

    private void showVanishBar(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        BossBar bar = vanishBars.computeIfAbsent(player.getUniqueId(),
            ignored -> BossBar.bossBar(VANISH_TITLE, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS));
        player.showBossBar(bar);
    }

    private void hideVanishBar(Player player) {
        if (player == null) {
            return;
        }
        BossBar bar = vanishBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public record VanishState(boolean vanished, boolean changed) {
    }
}
