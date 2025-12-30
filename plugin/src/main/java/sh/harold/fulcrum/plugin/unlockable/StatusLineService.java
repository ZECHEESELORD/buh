package sh.harold.fulcrum.plugin.unlockable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatusLineService implements Listener {

    private static final long REFRESH_INTERVAL_TICKS = 40L;
    private static final float LABEL_SCALE = 0.85f;
    private static final float STATUS_LABEL_OFFSET_Y = 0.5f;
    private static final float LABEL_VIEW_RANGE = 48.0f;
    private static final String LABEL_TYPE = "status-line";

    private final Plugin plugin;
    private final UnlockableService unlockableService;
    private final CosmeticRegistry cosmeticRegistry;
    private final Logger logger;
    private final NamespacedKey labelOwnerKey;
    private final NamespacedKey labelTypeKey;
    private final Map<UUID, UUID> labelsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastStatus = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLoads = ConcurrentHashMap.newKeySet();
    private BukkitTask refreshTask;

    public StatusLineService(
        Plugin plugin,
        UnlockableService unlockableService,
        CosmeticRegistry cosmeticRegistry,
        Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.cosmeticRegistry = Objects.requireNonNull(cosmeticRegistry, "cosmeticRegistry");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.labelOwnerKey = new NamespacedKey(plugin, "status-line-owner");
        this.labelTypeKey = new NamespacedKey(plugin, "status-line-type");
    }

    public void start() {
        cleanupLoadedLabels();
        refreshOnline();
        refreshTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::refreshOnline, 20L, REFRESH_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        cleanupLoadedLabels();
        labelsByOwner.clear();
        lastStatus.clear();
        pendingLoads.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        clear(event.getPlayer());
    }

    private void refreshOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Optional<PlayerUnlockableState> cached = unlockableService.cachedState(playerId);
        if (cached.isPresent()) {
            applyState(player, cached.get());
            return;
        }
        if (!pendingLoads.add(playerId)) {
            return;
        }
        unlockableService.loadState(playerId).whenComplete((state, throwable) -> {
            pendingLoads.remove(playerId);
            if (throwable != null) {
                logger.log(Level.WARNING, "Failed to resolve status line for " + playerId, throwable);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player target = plugin.getServer().getPlayer(playerId);
                if (target == null || !target.isOnline()) {
                    return;
                }
                applyState(target, state);
            });
        });
    }

    private void applyState(Player player, PlayerUnlockableState state) {
        if (player == null || state == null) {
            return;
        }
        Optional<StatusCosmetic> equipped = equippedStatus(state);
        if (equipped.isEmpty()) {
            clear(player);
            return;
        }
        StatusCosmetic cosmetic = equipped.get();
        String status = cosmetic.status();
        UUID playerId = player.getUniqueId();
        TextDisplay display = findLabel(player);
        if (display != null && Objects.equals(lastStatus.get(playerId), status)) {
            return;
        }
        if (display == null) {
            display = spawnLabel(player);
            if (display == null) {
                return;
            }
        }
        Component rendered = LuckPermsTextFormat.deserializePrefix(status)
            .decoration(TextDecoration.ITALIC, false);
        display.text(rendered);
        lastStatus.put(playerId, status);
    }

    private Optional<StatusCosmetic> equippedStatus(PlayerUnlockableState state) {
        if (state == null) {
            return Optional.empty();
        }
        UnlockableId equipped = state.equippedCosmetics(CosmeticSection.STATUS).stream()
            .min(Comparator.naturalOrder())
            .orElse(null);
        if (equipped == null) {
            return Optional.empty();
        }
        return cosmeticRegistry.cosmetic(equipped)
            .filter(StatusCosmetic.class::isInstance)
            .map(StatusCosmetic.class::cast);
    }

    private void clear(Player player) {
        if (player == null) {
            return;
        }
        removeLabel(player);
        lastStatus.remove(player.getUniqueId());
    }

    private void cleanupLoadedLabels() {
        for (var world : plugin.getServer().getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                cleanupLabelEntity(display);
            }
        }
    }

    private boolean cleanupLabelEntity(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return false;
        }
        PersistentDataContainer container = display.getPersistentDataContainer();
        String ownerRaw = container.get(labelOwnerKey, PersistentDataType.STRING);
        String labelType = container.get(labelTypeKey, PersistentDataType.STRING);
        if (ownerRaw == null || ownerRaw.isBlank() || !LABEL_TYPE.equals(labelType)) {
            return false;
        }
        try {
            UUID ownerId = UUID.fromString(ownerRaw);
            labelsByOwner.remove(ownerId, display.getUniqueId());
            lastStatus.remove(ownerId);
        } catch (IllegalArgumentException ignored) {
        }
        display.remove();
        return true;
    }

    private TextDisplay findLabel(Player owner) {
        UUID ownerId = owner.getUniqueId();
        UUID existingId = labelsByOwner.get(ownerId);
        if (existingId != null) {
            Entity resolved = Bukkit.getEntity(existingId);
            if (resolved instanceof TextDisplay display && display.isValid()) {
                applyLabelDefaults(display);
                return display;
            }
            labelsByOwner.remove(ownerId);
        }

        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedLabel(display, ownerId)) {
                applyLabelDefaults(display);
                labelsByOwner.put(ownerId, display.getUniqueId());
                return display;
            }
        }

        return null;
    }

    private TextDisplay spawnLabel(Player owner) {
        UUID ownerId = owner.getUniqueId();
        TextDisplay created = owner.getWorld().spawn(owner.getLocation(), TextDisplay.class, display -> {
            applyLabelDefaults(display);
            PersistentDataContainer container = display.getPersistentDataContainer();
            container.set(labelOwnerKey, PersistentDataType.STRING, ownerId.toString());
            container.set(labelTypeKey, PersistentDataType.STRING, LABEL_TYPE);
        });

        owner.addPassenger(created);
        labelsByOwner.put(ownerId, created.getUniqueId());
        return created;
    }

    private void removeLabel(Player owner) {
        if (owner == null) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        UUID labelId = labelsByOwner.remove(ownerId);
        if (labelId != null) {
            Entity resolved = Bukkit.getEntity(labelId);
            if (resolved != null) {
                resolved.remove();
            }
        }
        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedLabel(display, ownerId)) {
                display.remove();
            }
        }
    }

    private boolean isOwnedLabel(TextDisplay display, UUID ownerId) {
        PersistentDataContainer container = display.getPersistentDataContainer();
        String storedOwner = container.get(labelOwnerKey, PersistentDataType.STRING);
        String storedType = container.get(labelTypeKey, PersistentDataType.STRING);
        return ownerId.toString().equals(storedOwner) && LABEL_TYPE.equals(storedType);
    }

    private void applyLabelDefaults(TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setGravity(false);
        display.setPersistent(false);
        display.setTextOpacity((byte) 0xFF);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setViewRange(LABEL_VIEW_RANGE);
        applyDefaultTransform(display);
    }

    private void applyDefaultTransform(TextDisplay display) {
        org.bukkit.util.Transformation current = display.getTransformation();
        if (current == null) {
            return;
        }
        org.joml.Vector3f translation = new org.joml.Vector3f(0.0f, STATUS_LABEL_OFFSET_Y, 0.0f);
        org.joml.Vector3f scale = new org.joml.Vector3f(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);
        display.setTransformation(new org.bukkit.util.Transformation(
            translation,
            current.getLeftRotation(),
            scale,
            current.getRightRotation()
        ));
    }
}
