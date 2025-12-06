package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

final class ActionCosmeticListener implements Listener {

    private static final String SIT_ACTION_KEY = "sit";
    private static final String CRAWL_ACTION_KEY = "crawl";
    private static final String RIDE_ACTION_KEY = "ride";

    private final UnlockableService unlockableService;
    private final CosmeticRegistry cosmeticRegistry;
    private final Logger logger;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private final Map<UUID, Long> crouchTaps = new HashMap<>();

    ActionCosmeticListener(UnlockableService unlockableService, CosmeticRegistry cosmeticRegistry, Logger logger) {
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.cosmeticRegistry = Objects.requireNonNull(cosmeticRegistry, "cosmeticRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Optional<PlayerUnlockableState> state = unlockableService.cachedState(player.getUniqueId());
        if (state.isEmpty()) {
            return;
        }
        Optional<UnlockableId> actionId = state.get().equippedCosmetic(CosmeticSection.ACTIONS);
        if (actionId.isEmpty()) {
            return;
        }
        Optional<Cosmetic> cosmetic = cosmeticRegistry.cosmetic(actionId.get());
        if (cosmetic.isEmpty() || !(cosmetic.get() instanceof ActionCosmetic actionCosmetic)) {
            return;
        }
        if (SIT_ACTION_KEY.equalsIgnoreCase(actionCosmetic.actionKey())) {
            handleSit(event, player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerRide(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        Optional<PlayerUnlockableState> state = unlockableService.cachedState(player.getUniqueId());
        if (state.isEmpty()) {
            return;
        }
        Optional<UnlockableId> actionId = state.get().equippedCosmetic(CosmeticSection.ACTIONS);
        if (actionId.isEmpty()) {
            return;
        }
        Optional<Cosmetic> cosmetic = cosmeticRegistry.cosmetic(actionId.get());
        if (cosmetic.isEmpty() || !(cosmetic.get() instanceof ActionCosmetic actionCosmetic)) {
            return;
        }
        if (!RIDE_ACTION_KEY.equalsIgnoreCase(actionCosmetic.actionKey())) {
            return;
        }
        handleRide(event, player, target);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) {
            return;
        }
        ArmorStand seat = seats.get(player.getUniqueId());
        if (seat != null && event.getVehicle().getUniqueId().equals(seat.getUniqueId())) {
            removeSeat(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) {
            return;
        }
        Optional<PlayerUnlockableState> state = unlockableService.cachedState(player.getUniqueId());
        if (state.isEmpty()) {
            return;
        }
        Optional<UnlockableId> actionId = state.get().equippedCosmetic(CosmeticSection.ACTIONS);
        if (actionId.isEmpty()) {
            return;
        }
        Optional<Cosmetic> cosmetic = cosmeticRegistry.cosmetic(actionId.get());
        if (cosmetic.isEmpty() || !(cosmetic.get() instanceof ActionCosmetic actionCosmetic)) {
            return;
        }
        if (!CRAWL_ACTION_KEY.equalsIgnoreCase(actionCosmetic.actionKey())) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = crouchTaps.getOrDefault(player.getUniqueId(), 0L);
        crouchTaps.put(player.getUniqueId(), now);
        if (now - last > 350) {
            return;
        }
        toggleCrawl(player);
    }

    void clearSeats() {
        seats.values().forEach(ArmorStand::remove);
        seats.clear();
        crouchTaps.clear();
    }

    private void handleSit(PlayerInteractEvent event, Player player) {
        if (player.getVehicle() != null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Block above = clicked.getRelative(BlockFace.UP);
        if (!above.isPassable()) {
            return;
        }
        Location seatLocation = clicked.getLocation().add(0.5, 1.0, 0.5);
        try {
            removeSeat(player.getUniqueId());
            ArmorStand seat = spawnSeat(player, seatLocation);
            boolean mounted = seat.addPassenger(player);
            if (!mounted) {
                seat.remove();
                return;
            }
            seats.put(player.getUniqueId(), seat);
            event.setCancelled(true);
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to place sit seat for " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private ArmorStand spawnSeat(Player player, Location seatLocation) {
        return player.getWorld().spawn(seatLocation, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSilent(true);
            armorStand.setCollidable(false);
            armorStand.setPersistent(false);
            armorStand.setRemoveWhenFarAway(true);
        });
    }

    private void removeSeat(UUID playerId) {
        ArmorStand seat = seats.remove(playerId);
        if (seat != null && !seat.isDead()) {
            seat.remove();
        }
    }

    private void toggleCrawl(Player player) {
        try {
            player.setSwimming(!player.isSwimming());
        } catch (Throwable throwable) {
            logger.fine(() -> "Failed to toggle crawl for " + player.getUniqueId() + ": " + throwable.getMessage());
        }
    }

    private void handleRide(PlayerInteractEntityEvent event, Player player, Player target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        removeSeat(player.getUniqueId());
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Entity top = topPassenger(target);
        if (containsPlayer(top, player.getUniqueId())) {
            return;
        }
        boolean mounted = top.addPassenger(player);
        if (mounted) {
            event.setCancelled(true);
        }
    }

    private Entity topPassenger(Entity entity) {
        Entity current = entity;
        while (!current.getPassengers().isEmpty()) {
            current = current.getPassengers().get(0);
        }
        return current;
    }

    private boolean containsPlayer(Entity entity, UUID playerId) {
        if (entity.getUniqueId().equals(playerId)) {
            return true;
        }
        for (Entity passenger : entity.getPassengers()) {
            if (containsPlayer(passenger, playerId)) {
                return true;
            }
        }
        return false;
    }
}
