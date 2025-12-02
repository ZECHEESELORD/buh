package sh.harold.fulcrum.plugin.staff;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.PlayerInventory;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Captures player inventories and opens read-only views for staff.
 */
public final class OpenInventoryService implements Listener {

    private static final int VIEW_SIZE = 54;
    private static final int ARMOR_AND_OFFHAND_SLOTS = 5;
    private static final int HOTBAR_SLOTS = 9;
    private static final int STORAGE_SLOTS = 27;
    private static final int ARMOR_ROW_START = 0;
    private static final int SPACER_ROW_START = 9;
    private static final int HOTBAR_ROW_START = 18;
    private static final int STORAGE_ROW_START = 27;
    private static final ItemStack SPACER = spacerItem();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT)
        .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final Logger logger;
    private final Map<UUID, LiveViewSession> liveViews = new ConcurrentHashMap<>();

    OpenInventoryService(JavaPlugin plugin, DataApi dataApi) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.logger = plugin.getLogger();
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player viewer, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            viewer.sendMessage(Component.text("Give a player name to peek at an inventory.", NamedTextColor.RED));
            return;
        }

        Player online = plugin.getServer().getPlayerExact(targetName);
        if (online != null) {
            runSync(() -> openLiveInventory(viewer, online));
            return;
        }

        resolveTarget(targetName)
            .thenCompose(resolved -> {
                if (resolved == null) {
                    return CompletableFuture.completedFuture(new ResolutionOutcome(null, Optional.empty(), null));
                }
                return loadSnapshot(resolved.id(), resolved.username())
                    .handle((snapshot, throwable) -> new ResolutionOutcome(resolved, snapshot, throwable));
            })
            .whenComplete((outcome, throwable) -> runSync(() -> handleResolutionOutcome(viewer, targetName, outcome, throwable)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        persistSnapshot(event.getPlayer());
        closeLiveViews(event.getPlayer().getUniqueId(), null);
        closeLiveViewsForTarget(event.getPlayer().getUniqueId(), Component.text("They left; view closed.", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof OpenInventoryHolder)) {
            return;
        }
        UUID viewerId = event.getWhoClicked().getUniqueId();
        LiveViewSession session = liveViews.get(viewerId);

        if (event.getClickedInventory() == event.getWhoClicked().getOpenInventory().getTopInventory()) {
            event.setCancelled(true);
            if (session != null && session.isLive()) {
                handleLiveTopClick(event, session);
            }
            return;
        }

        if (session != null && session.isLive()) {
            if (event.isShiftClick() && event.getClickedInventory() instanceof PlayerInventory) {
                event.setCancelled(true);
                handleShiftIntoTarget(event, session);
            }
            return;
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof OpenInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof OpenInventoryHolder) {
            closeLiveViews(event.getPlayer().getUniqueId(), null);
        }
    }

    private CompletionStage<ResolvedTarget> resolveTarget(String targetName) {
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(targetName);
        if (cached != null) {
            String username = cached.getName() != null ? cached.getName() : targetName;
            return CompletableFuture.completedFuture(new ResolvedTarget(cached.getUniqueId(), username));
        }
        String normalized = targetName.trim();
        return players.all()
            .thenApply(documents -> documents.stream()
                .map(PlayerDirectoryEntry::fromDocument)
                .flatMap(Optional::stream)
                .filter(entry -> entry.username().equalsIgnoreCase(normalized))
                .findFirst()
                .map(entry -> new ResolvedTarget(entry.id(), entry.username()))
                .orElseGet(() -> {
                    UUID parsed = parseUuid(normalized);
                    return parsed != null ? new ResolvedTarget(parsed, normalized) : null;
                }))
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to resolve inventory target " + normalized, throwable);
                return null;
            });
    }

    private void handleResolutionOutcome(Player viewer, String targetName, ResolutionOutcome outcome, Throwable resolveError) {
        if (resolveError != null) {
            logger.log(Level.SEVERE, "Failed to resolve inventory target " + targetName, resolveError);
            viewer.sendMessage(Component.text("Inventory lookup stumbled; try again.", NamedTextColor.RED));
            return;
        }
        if (outcome == null || outcome.resolved() == null) {
            viewer.sendMessage(Component.text("No record of that player; try a different name.", NamedTextColor.RED));
            return;
        }
        if (outcome.snapshotError() != null) {
            logger.log(Level.SEVERE, "Failed to load inventory snapshot for " + outcome.resolved().id(), outcome.snapshotError());
            viewer.sendMessage(Component.text("Inventory archive faltered; wait a moment and retry.", NamedTextColor.RED));
            return;
        }
        Optional<InventorySnapshot> snapshot = outcome.snapshot();
        if (snapshot.isEmpty()) {
            viewer.sendMessage(Component.text("No stored inventory for " + outcome.resolved().username() + ".", NamedTextColor.RED));
            return;
        }
        InventorySnapshot resolvedSnapshot = snapshot.get();
        InventoryViewContext viewContext = new InventoryViewContext(outcome.resolved().id(), resolvedSnapshot, false);
        openInventory(viewer, viewContext);
    }

    private void openInventory(Player viewer, InventoryViewContext context) {
        InventorySnapshot snapshot = context.snapshot();
        String username = snapshot.username();
        OpenInventoryHolder holder = new OpenInventoryHolder(context.targetId(), username, context.live(), snapshot.capturedAt());
        Component title = Component.text("Inventory: " + username);
        Inventory inventory = Bukkit.createInventory(holder, VIEW_SIZE, title);
        holder.bind(inventory);

        fillInventory(inventory, snapshot);
        viewer.openInventory(inventory);

        String freshness = DATE_FORMATTER.format(snapshot.capturedAt());
        Component message = Component.text()
            .append(Component.text("Viewing ", NamedTextColor.GRAY))
            .append(Component.text(username, NamedTextColor.AQUA))
            .append(Component.text(context.live() ? " (online)" : " (offline)", NamedTextColor.DARK_GRAY))
            .append(Component.text("; saved ", NamedTextColor.GRAY))
            .append(Component.text(freshness, NamedTextColor.YELLOW))
            .append(Component.text("; read only.", NamedTextColor.GRAY))
            .build();
        viewer.sendMessage(message);
    }

    private void openLiveInventory(Player viewer, Player target) {
        InventorySnapshot snapshot = captureSnapshot(target);
        InventoryViewContext viewContext = new InventoryViewContext(target.getUniqueId(), snapshot, true);
        Inventory inventory = createInventory(viewContext);
        fillInventory(inventory, snapshot);
        viewer.openInventory(inventory);
        registerLiveView(viewer, target, inventory);
        Component message = Component.text()
            .append(Component.text("Viewing ", NamedTextColor.GRAY))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text(" live; editable.", NamedTextColor.GREEN))
            .build();
        viewer.sendMessage(message);
    }

    private Inventory createInventory(InventoryViewContext context) {
        InventorySnapshot snapshot = context.snapshot();
        String username = snapshot.username();
        OpenInventoryHolder holder = new OpenInventoryHolder(context.targetId(), username, context.live(), snapshot.capturedAt());
        Component title = Component.text("Inventory: " + username);
        Inventory inventory = Bukkit.createInventory(holder, VIEW_SIZE, title);
        holder.bind(inventory);
        return inventory;
    }

    private void handleLiveTopClick(InventoryClickEvent event, LiveViewSession session) {
        int slot = event.getSlot();
        if (isSpacer(slot)) {
            return;
        }
        ItemStack cursor = cloneItem(event.getCursor());
        ItemStack targetItem = cloneItem(getTargetItem(session.target, slot));
        setTargetItem(session.target, slot, cursor);
        event.setCursor(targetItem);
        event.getWhoClicked().setItemOnCursor(targetItem);
        InventorySnapshot snapshot = captureSnapshot(session.target);
        fillInventory(session.view, snapshot);
    }

    private void handleShiftIntoTarget(InventoryClickEvent event, LiveViewSession session) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) {
            return;
        }
        Player viewer = session.viewer();
        PlayerInventory viewerInventory = viewer.getInventory();
        int slot = event.getSlot();
        ItemStack moving = current.clone();
        viewerInventory.setItem(slot, null);
        Map<Integer, ItemStack> leftover = session.target.getInventory().addItem(moving);
        if (!leftover.isEmpty()) {
            ItemStack remaining = leftover.values().stream().findFirst().orElse(null);
            viewerInventory.setItem(slot, remaining);
        }
        InventorySnapshot snapshot = captureSnapshot(session.target);
        fillInventory(session.view, snapshot);
    }

    private boolean isSpacer(int slot) {
        return slot >= ARMOR_AND_OFFHAND_SLOTS && slot < HOTBAR_ROW_START;
    }

    private ItemStack getTargetItem(Player target, int slot) {
        PlayerInventory inventory = target.getInventory();
        if (slot == ARMOR_ROW_START) {
            return inventory.getHelmet();
        }
        if (slot == ARMOR_ROW_START + 1) {
            return inventory.getChestplate();
        }
        if (slot == ARMOR_ROW_START + 2) {
            return inventory.getLeggings();
        }
        if (slot == ARMOR_ROW_START + 3) {
            return inventory.getBoots();
        }
        if (slot == ARMOR_ROW_START + 4) {
            return inventory.getItemInOffHand();
        }
        if (slot >= HOTBAR_ROW_START && slot < HOTBAR_ROW_START + HOTBAR_SLOTS) {
            int targetSlot = slot - HOTBAR_ROW_START;
            return inventory.getItem(targetSlot);
        }
        if (slot >= STORAGE_ROW_START && slot < STORAGE_ROW_START + STORAGE_SLOTS) {
            int targetSlot = 9 + (slot - STORAGE_ROW_START);
            return inventory.getItem(targetSlot);
        }
        return null;
    }

    private void setTargetItem(Player target, int slot, ItemStack item) {
        PlayerInventory inventory = target.getInventory();
        ItemStack toSet = item == null ? null : item.clone();
        if (slot == ARMOR_ROW_START) {
            inventory.setHelmet(toSet);
            return;
        }
        if (slot == ARMOR_ROW_START + 1) {
            inventory.setChestplate(toSet);
            return;
        }
        if (slot == ARMOR_ROW_START + 2) {
            inventory.setLeggings(toSet);
            return;
        }
        if (slot == ARMOR_ROW_START + 3) {
            inventory.setBoots(toSet);
            return;
        }
        if (slot == ARMOR_ROW_START + 4) {
            inventory.setItemInOffHand(toSet);
            return;
        }
        if (slot >= HOTBAR_ROW_START && slot < HOTBAR_ROW_START + HOTBAR_SLOTS) {
            int targetSlot = slot - HOTBAR_ROW_START;
            inventory.setItem(targetSlot, toSet);
            return;
        }
        if (slot >= STORAGE_ROW_START && slot < STORAGE_ROW_START + STORAGE_SLOTS) {
            int targetSlot = 9 + (slot - STORAGE_ROW_START);
            inventory.setItem(targetSlot, toSet);
        }
    }

    private void fillInventory(Inventory view, InventorySnapshot snapshot) {
        for (int slot = 0; slot < VIEW_SIZE; slot++) {
            view.setItem(slot, null);
        }
        for (int index = ARMOR_ROW_START; index < ARMOR_AND_OFFHAND_SLOTS; index++) {
            view.setItem(index, SPACER);
        }
        for (int index = ARMOR_AND_OFFHAND_SLOTS; index < HOTBAR_SLOTS; index++) {
            view.setItem(index, SPACER);
        }
        for (int index = SPACER_ROW_START; index < SPACER_ROW_START + HOTBAR_SLOTS; index++) {
            view.setItem(index, SPACER);
        }
        view.setItem(ARMOR_ROW_START, snapshot.helmet());
        view.setItem(ARMOR_ROW_START + 1, snapshot.chestplate());
        view.setItem(ARMOR_ROW_START + 2, snapshot.leggings());
        view.setItem(ARMOR_ROW_START + 3, snapshot.boots());
        view.setItem(ARMOR_ROW_START + 4, snapshot.offhand());

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            view.setItem(HOTBAR_ROW_START + i, snapshot.hotbar().get(i));
        }

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            int slot = STORAGE_ROW_START + i;
            view.setItem(slot, snapshot.storage().get(i));
        }
    }

    private InventorySnapshot captureSnapshot(Player player) {
        Instant capturedAt = Instant.now();
        PlayerInventoryAccess inventory = new PlayerInventoryAccess(player);
        return new InventorySnapshot(
            player.getName(),
            cloneItem(inventory.helmet()),
            cloneItem(inventory.chestplate()),
            cloneItem(inventory.leggings()),
            cloneItem(inventory.boots()),
            cloneItem(inventory.offhand()),
            cloneList(inventory.hotbar(), HOTBAR_SLOTS),
            cloneList(inventory.storage(), STORAGE_SLOTS),
            capturedAt
        );
    }

    private void persistSnapshot(Player player) {
        InventorySnapshot snapshot = captureSnapshot(player);
        Map<String, Object> serialized = serialize(snapshot);
        players.load(player.getUniqueId().toString())
            .thenCompose(document -> document.set("inventory.snapshot", serialized))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to persist inventory snapshot for " + player.getUniqueId(), throwable);
                return null;
            });
    }

    private Map<String, Object> serialize(InventorySnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", snapshot.username());
        data.put("capturedAt", snapshot.capturedAt().toString());
        data.put("armor", serializeArmor(snapshot));
        data.put("offhand", serializeItem(snapshot.offhand()));
        data.put("hotbar", serializeItems(snapshot.hotbar()));
        data.put("storage", serializeItems(snapshot.storage()));
        return data;
    }

    private List<Map<String, Object>> serializeArmor(InventorySnapshot snapshot) {
        List<Map<String, Object>> armor = new ArrayList<>();
        armor.add(serializeItem(snapshot.helmet()));
        armor.add(serializeItem(snapshot.chestplate()));
        armor.add(serializeItem(snapshot.leggings()));
        armor.add(serializeItem(snapshot.boots()));
        return armor;
    }

    private List<Map<String, Object>> serializeItems(List<ItemStack> items) {
        List<Map<String, Object>> serialized = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            serialized.add(serializeItem(item));
        }
        return serialized;
    }

    private Map<String, Object> serializeItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return new LinkedHashMap<>(item.clone().serialize());
    }

    private CompletionStage<Optional<InventorySnapshot>> loadSnapshot(UUID targetId, String username) {
        return players.load(targetId.toString())
            .thenApply(Document::snapshot)
            .thenApply(data -> parseSnapshot(data, username));
    }

    private void registerLiveView(Player viewer, Player target, Inventory inventory) {
        UUID viewerId = viewer.getUniqueId();
        LiveViewSession existing = liveViews.remove(viewerId);
        if (existing != null) {
            existing.cancel();
        }
        LiveViewSession session = new LiveViewSession(viewer, target, inventory);
        liveViews.put(viewerId, session);
        session.start();
    }

    private void closeLiveViews(UUID viewerId, Component message) {
        LiveViewSession session = liveViews.remove(viewerId);
        if (session == null) {
            return;
        }
        session.cancel();
        if (message != null) {
            runSync(() -> session.viewer().sendMessage(message));
        }
    }

    private void closeLiveViewsForTarget(UUID targetId, Component message) {
        List<UUID> toClose = new ArrayList<>();
        liveViews.forEach((viewerId, session) -> {
            if (session.target.getUniqueId().equals(targetId)) {
                toClose.add(viewerId);
            }
        });
        for (UUID viewerId : toClose) {
            LiveViewSession session = liveViews.remove(viewerId);
            if (session == null) {
                continue;
            }
            session.cancel();
            runSync(() -> {
                if (session.viewer().isOnline()) {
                    if (message != null) {
                        session.viewer().sendMessage(message);
                    }
                    session.viewer().closeInventory();
                }
            });
        }
    }

    private Optional<InventorySnapshot> parseSnapshot(Map<String, Object> document, String fallbackUsername) {
        if (document == null) {
            return Optional.empty();
        }
        Object rawSnapshot = document.get("inventory");
        if (!(rawSnapshot instanceof Map<?, ?> inventoryRoot)) {
            return Optional.empty();
        }
        Object raw = inventoryRoot.get("snapshot");
        if (!(raw instanceof Map<?, ?> snapshotMap)) {
            return Optional.empty();
        }

        String username = Optional.ofNullable(snapshotMap.get("username"))
            .map(Object::toString)
            .filter(name -> !name.isBlank())
            .orElse(fallbackUsername);
        Instant capturedAt = parseInstant(snapshotMap.get("capturedAt"));
        List<ItemStack> armor = parseArmor(snapshotMap.get("armor"));
        ItemStack offhand = parseItem(snapshotMap.get("offhand"));
        List<ItemStack> hotbar = parseItems(snapshotMap.get("hotbar"), HOTBAR_SLOTS);
        List<ItemStack> storage = parseItems(snapshotMap.get("storage"), STORAGE_SLOTS);
        if (armor.size() < 4 || hotbar.size() < HOTBAR_SLOTS || storage.size() < STORAGE_SLOTS) {
            return Optional.empty();
        }
        return Optional.of(new InventorySnapshot(
            username,
            armor.get(0),
            armor.get(1),
            armor.get(2),
            armor.get(3),
            offhand,
            hotbar,
            storage,
            capturedAt != null ? capturedAt : Instant.now()
        ));
    }

    private List<ItemStack> parseArmor(Object raw) {
        List<ItemStack> armor = parseItems(raw, 4);
        while (armor.size() < 4) {
            armor.add(null);
        }
        return armor;
    }

    private List<ItemStack> parseItems(Object raw, int expected) {
        if (!(raw instanceof List<?> list)) {
            return emptyItemList(expected);
        }
        List<ItemStack> items = new ArrayList<>(expected);
        for (Object element : list) {
            items.add(parseItem(element));
        }
        while (items.size() < expected) {
            items.add(null);
        }
        if (items.size() > expected) {
            return items.subList(0, expected);
        }
        return items;
    }

    private ItemStack parseItem(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        try {
            return ItemStack.deserialize(copy);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Failed to deserialize stored inventory item", exception);
            return null;
        }
    }

    private Instant parseInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void runSync(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private static ItemStack spacerItem() {
        ItemStack spacer = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = spacer.getItemMeta();
        meta.displayName(Component.text(" "));
        spacer.setItemMeta(meta);
        return spacer;
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null) {
            return null;
        }
        return item.clone();
    }

    private List<ItemStack> cloneList(List<ItemStack> items, int expected) {
        List<ItemStack> clones = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            ItemStack item = i < items.size() ? items.get(i) : null;
            clones.add(cloneItem(item));
        }
        return clones;
    }

    private List<ItemStack> emptyItemList(int size) {
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            items.add(null);
        }
        return items;
    }

    private record InventorySnapshot(
        String username,
        ItemStack helmet,
        ItemStack chestplate,
        ItemStack leggings,
        ItemStack boots,
        ItemStack offhand,
        List<ItemStack> hotbar,
        List<ItemStack> storage,
        Instant capturedAt
    ) {
        InventorySnapshot {
            username = Objects.requireNonNull(username, "username");
            Objects.requireNonNull(hotbar, "hotbar");
            Objects.requireNonNull(storage, "storage");
            hotbar = Collections.unmodifiableList(new ArrayList<>(hotbar));
            storage = Collections.unmodifiableList(new ArrayList<>(storage));
            capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        }
    }

    private record InventoryViewContext(UUID targetId, InventorySnapshot snapshot, boolean live) {
    }

    private record ResolutionOutcome(ResolvedTarget resolved, Optional<InventorySnapshot> snapshot, Throwable snapshotError) {
        ResolutionOutcome {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    private record ResolvedTarget(UUID id, String username) {
        ResolvedTarget {
            id = Objects.requireNonNull(id, "id");
            username = Objects.requireNonNull(username, "username");
        }
    }

    private final class LiveViewSession implements Runnable {
        private final Player viewer;
        private final Player target;
        private final Inventory view;
        private BukkitTask task;

        private LiveViewSession(Player viewer, Player target, Inventory view) {
            this.viewer = viewer;
            this.target = target;
            this.view = view;
        }

        private void start() {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 0L, 10L);
        }

        private boolean isLive() {
            return task != null;
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        @Override
        public void run() {
            if (!viewer.isOnline()) {
                closeLiveViews(viewer.getUniqueId(), null);
                return;
            }
            InventoryHolder currentHolder = viewer.getOpenInventory().getTopInventory().getHolder();
            if (currentHolder != view.getHolder()) {
                closeLiveViews(viewer.getUniqueId(), null);
                return;
            }
            if (!target.isOnline()) {
                closeLiveViews(viewer.getUniqueId(), Component.text("Player went offline; view closed.", NamedTextColor.YELLOW));
                viewer.closeInventory();
                return;
            }
            InventorySnapshot snapshot = captureSnapshot(target);
            fillInventory(view, snapshot);
        }

        private Player viewer() {
            return viewer;
        }
    }

    private static final class PlayerInventoryAccess {
        private final Player player;

        private PlayerInventoryAccess(Player player) {
            this.player = player;
        }

        private ItemStack helmet() {
            return player.getInventory().getHelmet();
        }

        private ItemStack chestplate() {
            return player.getInventory().getChestplate();
        }

        private ItemStack leggings() {
            return player.getInventory().getLeggings();
        }

        private ItemStack boots() {
            return player.getInventory().getBoots();
        }

        private ItemStack offhand() {
            return player.getInventory().getItemInOffHand();
        }

        private List<ItemStack> hotbar() {
            List<ItemStack> items = new ArrayList<>(HOTBAR_SLOTS);
            for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
                items.add(player.getInventory().getItem(slot));
            }
            return items;
        }

        private List<ItemStack> storage() {
            List<ItemStack> items = new ArrayList<>(STORAGE_SLOTS);
            for (int slot = 9; slot < 9 + STORAGE_SLOTS; slot++) {
                items.add(player.getInventory().getItem(slot));
            }
            return items;
        }
    }

    private static final class OpenInventoryHolder implements InventoryHolder {
        private final UUID targetId;
        private final String targetName;
        private final boolean live;
        private final Instant capturedAt;
        private Inventory inventory;

        private OpenInventoryHolder(UUID targetId, String targetName, boolean live, Instant capturedAt) {
            this.targetId = targetId;
            this.targetName = targetName;
            this.live = live;
            this.capturedAt = capturedAt;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
