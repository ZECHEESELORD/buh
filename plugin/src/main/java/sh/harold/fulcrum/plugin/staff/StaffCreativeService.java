package sh.harold.fulcrum.plugin.staff;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffCreativeService implements Listener {

    public static final String CREATIVE_TAG = "fulcrum:staff_creative";

    private static final long CREATIVE_BREAK_DELAY_TICKS = 6L;
    private static final long CREATIVE_HOLD_THRESHOLD_TICKS = 1L;

    private static final Component STAFF_PREFIX = Component.text("[STAFF] ", NamedTextColor.AQUA);
    private static final NamedTextColor STAFF_ACCENT = NamedTextColor.AQUA;

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<PlacementKey, PlacementSnapshot> placementSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, BreakState> breakStates = new ConcurrentHashMap<>();

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
        clearPlacementSnapshots(id);
        breakStates.remove(id);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        event.setCancelled(true);
        if (event.getBlock().getType().isAir()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long currentTick = player.getWorld().getFullTime();
        BreakState state = breakStates.get(playerId);
        boolean holding = state != null && currentTick - state.lastDamageTick() <= CREATIVE_HOLD_THRESHOLD_TICKS;
        if (holding && currentTick - state.lastBreakTick() < CREATIVE_BREAK_DELAY_TICKS) {
            breakStates.put(playerId, state.withLastDamageTick(currentTick));
            return;
        }
        if (player.breakBlock(event.getBlock())) {
            breakStates.put(playerId, new BreakState(currentTick, currentTick));
        } else if (state != null) {
            breakStates.put(playerId, state.withLastDamageTick(currentTick));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrePlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        int heldSlot = hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : -1;
        placementSnapshots.put(new PlacementKey(player.getUniqueId(), hand), new PlacementSnapshot(heldSlot, item.clone()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        PlacementSnapshot snapshot = placementSnapshots.remove(new PlacementKey(player.getUniqueId(), hand));
        ItemStack placed = snapshot == null ? event.getItemInHand() : snapshot.stack();
        if (placed == null || placed.getType().isAir()) {
            return;
        }
        int heldSlot = snapshot == null
            ? (hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : -1)
            : snapshot.heldSlot();
        ItemStack restore = placed.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> restoreItem(player, hand, heldSlot, restore));
    }

    private void restoreItem(Player player, EquipmentSlot hand, int heldSlot, ItemStack snapshot) {
        if (!player.isOnline()) {
            return;
        }
        if (!player.getScoreboardTags().contains(CREATIVE_TAG)) {
            return;
        }
        if (hand == EquipmentSlot.HAND) {
            if (heldSlot >= 0) {
                player.getInventory().setItem(heldSlot, snapshot);
            } else {
                player.getInventory().setItemInMainHand(snapshot);
            }
            return;
        }
        if (hand == EquipmentSlot.OFF_HAND) {
            player.getInventory().setItemInOffHand(snapshot);
        }
    }

    private void clearPlacementSnapshots(UUID playerId) {
        placementSnapshots.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private record Session(GameMode previousMode, boolean allowedFlight, boolean wasFlying, boolean invulnerable, BossBar bar) {
    }

    private record PlacementKey(UUID playerId, EquipmentSlot hand) {
    }

    private record PlacementSnapshot(int heldSlot, ItemStack stack) {
    }

    private record BreakState(long lastDamageTick, long lastBreakTick) {
        private BreakState withLastDamageTick(long tick) {
            return new BreakState(tick, lastBreakTick);
        }
    }

    private BossBar bossBar() {
        Component label = STAFF_PREFIX
            .append(Component.text("Creative Mode", STAFF_ACCENT))
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        return BossBar.bossBar(label, 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }
}
