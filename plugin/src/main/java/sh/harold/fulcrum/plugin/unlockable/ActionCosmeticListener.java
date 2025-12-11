package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;

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
    private static final String RIDE_NO_COLLIDE_TEAM = "rideNoCollide";

    private final UnlockableService unlockableService;
    private final CosmeticRegistry cosmeticRegistry;
    private final Logger logger;
    private final JavaPlugin plugin;
    private final CrawlManager crawlManager;
    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private final Map<UUID, Long> crouchTaps = new HashMap<>();
    private final Map<UUID, Long> rideGrace = new HashMap<>();
    private final Map<UUID, UUID> riderHosts = new HashMap<>();
    private final Map<UUID, Integer> collisionMembership = new HashMap<>();
    private final Map<UUID, String> collisionNames = new HashMap<>();

    ActionCosmeticListener(UnlockableService unlockableService, CosmeticRegistry cosmeticRegistry, JavaPlugin plugin, Logger logger, CrawlManager crawlManager) {
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.cosmeticRegistry = Objects.requireNonNull(cosmeticRegistry, "cosmeticRegistry");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.crawlManager = Objects.requireNonNull(crawlManager, "crawlManager");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !event.getPlayer().isSneaking()) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable()) {
                return;
            }
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (hasAction(player.getUniqueId(), SIT_ACTION_KEY)) {
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
        if (hasAction(player.getUniqueId(), RIDE_ACTION_KEY)) {
            handleRide(event, player, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) {
            return;
        }
        Long graceUntil = rideGrace.get(player.getUniqueId());
        if (graceUntil != null && System.currentTimeMillis() < graceUntil) {
            event.setCancelled(true);
            return;
        }
        ArmorStand seat = seats.get(player.getUniqueId());
        if (seat != null && event.getVehicle().getUniqueId().equals(seat.getUniqueId())) {
            removeSeat(player.getUniqueId());
        }
        if (riderHosts.containsKey(player.getUniqueId())) {
            removeRide(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Long graceUntil = rideGrace.get(player.getUniqueId());
        if (graceUntil != null && System.currentTimeMillis() < graceUntil) {
            event.setCancelled(true);
            return;
        }
        if (riderHosts.containsKey(player.getUniqueId())) {
            removeRide(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
        removeAllRidesForHost(event.getPlayer().getUniqueId());
        removeRide(event.getPlayer().getUniqueId());
        crawlManager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
        removeAllRidesForHost(event.getPlayer().getUniqueId());
        removeRide(event.getPlayer().getUniqueId());
        crawlManager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        removeSeat(event.getEntity().getUniqueId());
        removeAllRidesForHost(event.getEntity().getUniqueId());
        removeRide(event.getEntity().getUniqueId());
        crawlManager.stop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        removeAllRidesForHost(event.getPlayer().getUniqueId());
        removeRide(event.getPlayer().getUniqueId());
        crawlManager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeSeat(event.getPlayer().getUniqueId());
        removeAllRidesForHost(event.getPlayer().getUniqueId());
        removeRide(event.getPlayer().getUniqueId());
        crawlManager.stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !Objects.equals(event.getFrom().getWorld(), event.getTo().getWorld())) {
            removeSeat(event.getPlayer().getUniqueId());
            removeAllRidesForHost(event.getPlayer().getUniqueId());
            removeRide(event.getPlayer().getUniqueId());
            crawlManager.stop(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        // Allow natural jumping while crawling; no more custom hop/velocity tweaks.
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!event.isSneaking()) {
            return;
        }
        if (!hasAction(player.getUniqueId(), CRAWL_ACTION_KEY)) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = crouchTaps.getOrDefault(player.getUniqueId(), 0L);
        crouchTaps.put(player.getUniqueId(), now);
        if (now - last > 350) {
            return;
        }
        logger.info(() -> "Crawl toggle requested by " + player.getUniqueId());
        toggleCrawl(player);
    }

    void clearSeats() {
        seats.values().forEach(ArmorStand::remove);
        seats.clear();
        crouchTaps.clear();
        rideGrace.clear();
        UUID[] riderIds = riderHosts.keySet().toArray(UUID[]::new);
        for (UUID riderId : riderIds) {
            removeRide(riderId);
        }
        collisionMembership.clear();
        collisionNames.clear();
        crawlManager.stopAll();
    }

    private void handleSit(PlayerInteractEvent event, Player player) {
        if (player.getVehicle() != null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (event.getBlockFace() == BlockFace.DOWN) {
            return;
        }
        if (!playerIsGrounded(player) && player.getLocation().getPitch() > 0.0f) {
            return;
        }
        Block target = resolveSitTarget(clicked, event.getPlayer().isSneaking());
        if (target == null) {
            return;
        }
        Block above = target.getRelative(BlockFace.UP);
        if (!above.isPassable()) {
            return;
        }
        Location seatLocation = target.getLocation().add(0.5, seatYOffset(target), 0.5);
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

    private Block resolveSitTarget(Block clicked, boolean sneaking) {
        if (clicked.isSolid()) {
            return clicked;
        }
        Block below = clicked.getRelative(BlockFace.DOWN);
        if (below.isSolid()) {
            return below;
        }
        return null;
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
        CrawlManager.CrawlToggleResult result = crawlManager.toggle(player);
        if (result == CrawlManager.CrawlToggleResult.FAILED) {
            logger.fine(() -> "Failed to toggle crawl for " + player.getUniqueId());
        }
    }

    private void handleRide(PlayerInteractEntityEvent event, Player player, Player target) {
        if (target.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        removeSeat(player.getUniqueId());
        removeRide(player.getUniqueId());
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        Entity top = topPassenger(target);
        if (containsPlayer(top, player.getUniqueId())) {
            return;
        }
        boolean mounted = top.addPassenger(player);
        if (!mounted) {
            return;
        }
        if (top instanceof Player host) {
            registerCollisionlessRide(player, host);
        }
        event.setCancelled(true);
        rideGrace.put(player.getUniqueId(), System.currentTimeMillis() + 500L);
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

    private void removeAllRidesForHost(UUID hostId) {
        UUID[] riderIds = riderHosts.entrySet().stream()
            .filter(entry -> hostId.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .toArray(UUID[]::new);
        for (UUID riderId : riderIds) {
            removeRide(riderId);
        }
    }

    private void removeRide(UUID riderId) {
        rideGrace.remove(riderId);
        UUID hostId = riderHosts.remove(riderId);
        removeCollisionMember(riderId);
        if (hostId != null) {
            removeCollisionMember(hostId);
        }
    }

    private void registerCollisionlessRide(Player rider, Player host) {
        riderHosts.put(rider.getUniqueId(), host.getUniqueId());
        addCollisionMember(rider);
        addCollisionMember(host);
    }

    private void addCollisionMember(Player player) {
        Team team = collisionTeam(true);
        if (team == null) {
            return;
        }
        collisionNames.putIfAbsent(player.getUniqueId(), player.getName());
        collisionMembership.merge(player.getUniqueId(), 1, Integer::sum);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void removeCollisionMember(UUID playerId) {
        Integer count = collisionMembership.get(playerId);
        if (count == null) {
            return;
        }
        int updated = count - 1;
        if (updated <= 0) {
            collisionMembership.remove(playerId);
            String name = collisionNames.remove(playerId);
            Team team = collisionTeam(false);
            if (team != null && name != null) {
                team.removeEntry(name);
            }
        } else {
            collisionMembership.put(playerId, updated);
        }
    }

    private Team collisionTeam(boolean createIfMissing) {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            return null;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        Team team = scoreboard.getTeam(RIDE_NO_COLLIDE_TEAM);
        if (team == null && createIfMissing) {
            team = scoreboard.registerNewTeam(RIDE_NO_COLLIDE_TEAM);
        }
        if (team != null) {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private boolean hasAction(UUID playerId, String actionKey) {
        Optional<PlayerUnlockableState> state = unlockableService.cachedState(playerId);
        if (state.isEmpty()) {
            return false;
        }
        return state.get().equippedCosmetics(CosmeticSection.ACTIONS).stream()
            .map(cosmeticRegistry::cosmetic)
            .flatMap(Optional::stream)
            .filter(ActionCosmetic.class::isInstance)
            .map(ActionCosmetic.class::cast)
            .anyMatch(action -> actionKey.equalsIgnoreCase(action.actionKey()));
    }

    private boolean playerIsGrounded(Player player) {
        Location below = player.getLocation().clone().subtract(0, 0.1, 0);
        return !below.getBlock().isPassable();
    }

    private double seatYOffset(Block target) {
        if (target.getBlockData() instanceof org.bukkit.block.data.type.Stairs) {
            return 0.5;
        }
        if (target.getBlockData() instanceof org.bukkit.block.data.type.Slab slab && slab.getType() == org.bukkit.block.data.type.Slab.Type.BOTTOM) {
            return 0.5;
        }
        return 1.0;
    }
}
