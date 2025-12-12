package sh.harold.fulcrum.plugin.playermenu;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.Menu;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.ledger.LedgerEntry;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.OptionalInt;

final class BankMenuView {

    private static final int ROWS = 6;
    private static final int STAGED_SLOT = 13;
    private static final int[] SNAKE_PATH = {4, 5, 14, 23, 22, 21, 12, 3};

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final DocumentCollection players;
    private final LedgerRepository ledger;
    private final Logger logger;
    private final Map<UUID, BankSession> sessions = new ConcurrentHashMap<>();

    BankMenuView(
        JavaPlugin plugin,
        MenuService menuService,
        DocumentCollection players,
        LedgerRepository ledger,
        Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.players = Objects.requireNonNull(players, "players");
        this.ledger = ledger;
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void open(Player player, Consumer<Player> backAction) {
        UUID playerId = player.getUniqueId();
        BankSession session = sessions.computeIfAbsent(playerId, ignored -> new BankSession());
        Consumer<Player> resolvedBackAction = backAction != null ? backAction : session.backAction();
        if (resolvedBackAction != null) {
            session.backAction(resolvedBackAction);
        }
        Consumer<Player> menuBackAction = session.backAction();

        players.load(playerId.toString())
            .thenApply(document -> document.get("bank.shards", Number.class).map(Number::longValue).orElse(0L))
            .thenCombine(recentLedger(playerId, 5), (shards, entries) -> new BankState(shards, entries))
            .whenComplete((state, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load bank state for " + playerId, throwable);
                    player.sendMessage("§cBank is snoozing; try again soon.");
                    return;
                }

                MenuDisplayItem balance = MenuDisplayItem.builder(Material.EMERALD)
                    .name("&aShard Balance")
                    .secondary("Savings")
                    .description("&7Shards: &b" + state.shards())
                    .slot(29)
                    .build();

                MenuButton stagedButton = buildStagedButton(session, state.shards());
                String[] ledgerLore = formatLedger(state.entries()).toArray(String[]::new);
                MenuButton ledgerButton = MenuButton.builder(Material.BOOK)
                    .name("&dView Transactions")
                    .secondary("Ledger")
                    .description("Recent shard activity.")
                    .lore(ledgerLore)
                    .slot(33)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(viewer -> viewer.sendMessage(Component.text("Ledger synced for your account.", NamedTextColor.GRAY)))
                    .build();

                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .secondary("Player Menu")
                    .description("Return to the player menu.")
                    .slot(MenuButton.getBackSlot(ROWS))
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(viewer -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (menuBackAction != null) {
                            menuBackAction.accept(viewer);
                        } else {
                            viewer.closeInventory();
                        }
                    }))
                    .build();

                menuService.createMenuBuilder()
                    .title("Bank")
                    .rows(ROWS)
                    .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                    .addButton(MenuButton.createPositionedClose(ROWS))
                    .addButton(backButton)
                    .addButton(stagedButton)
                    .addButton(ledgerButton)
                    .addItem(balance, 29)
                    .buildAsync(player)
                    .whenComplete((menu, openError) -> {
                        if (openError != null) {
                            logger.log(Level.SEVERE, "Failed to open bank for " + playerId, openError);
                            player.sendMessage("§cBank is snoozing; try again soon.");
                            return;
                        }
                        menu.setProperty("bankMenu", true);
                        menu.setProperty("closeOnOutsideClick", false);
                        menu.setProperty("bottomClickHandler", (BiConsumer<Player, InventoryClickEvent>) (viewer, event) -> {
                            handleBankInventoryClick(viewer, event, session);
                        });
                        menu.onClose(() -> {
                            refundIfAny(playerId, player);
                            stopSnakeAnimation(menu);
                        });
                        startSnakeAnimation(menu, session.stagedItem() != null);
                    });
            });
    }

    private MenuButton buildStagedButton(BankSession session, long shards) {
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            return MenuButton.builder(Material.STONE_BUTTON)
                .name("&bShatter Diamonds")
                .secondary("Deposit")
                .lore(" ")
                .lore("&71. &7Click &bDiamond &7in inventory")
                .lore("&72. &7Confirm transaction &8(shatter)")
                .lore("&73. &dProfit???")
                .lore("")
                .lore("&b1 diamond &7= &39 shards.")
                .slot(STAGED_SLOT)
                .sound(Sound.UI_BUTTON_CLICK)
                .onClick(player -> player.sendMessage("§7Select diamonds from your inventory, then click again to deposit."))
                .build();
        }
        long diamonds = toDiamondCount(staged.getType(), staged.getAmount());
        long shardGain = diamonds * 9;
        Material buttonMaterial = staged.getType();
        int amount = Math.min(staged.getAmount(), buttonMaterial.getMaxStackSize());
        return MenuButton.builder(buttonMaterial)
            .amount(amount)
            .name("&aDeposit " + staged.getAmount() + "x " + buttonMaterial.name().toLowerCase(Locale.ROOT).replace('_', ' '))
            .secondary("Shatter")
            .lore("")
            .lore("&7Convert into &3" + shardGain + " shards&7.")
            .lore("&7Current shards: &b" + shards + "&7.")
            .lore("")
            .lore("&c&lWARNING:&r&c This action is permanent!")
            .lore("")
            .lore("&eRight Click to return items!")
            .slot(STAGED_SLOT)
            .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK)
            .onClick(ClickType.LEFT, player -> depositStaged(player))
            .onClick(ClickType.RIGHT, player -> {
                refund(getSession(player.getUniqueId()), player);
                player.closeInventory();
            })
            .build();
    }

    private void handleBankInventoryClick(Player player, InventoryClickEvent event, BankSession session) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(player.getInventory())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (!isDiamondLike(clicked.getType())) {
            player.sendMessage("§cSelect diamonds or diamond blocks to shatter.");
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (session.stagedItem() != null && session.stagedSlot() == slot) {
            player.sendMessage("§eThose diamonds are already staged. Confirm or return them.");
            return;
        }
        ItemStack staged = clicked.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (session.stagedItem() != null) {
                unmaskSessionSlot(session, player);
            }
            if (!maskSlot(player, slot, maskPane())) {
                player.sendMessage("§cCould not stage those diamonds; try again.");
                return;
            }
            session.stage(staged, slot);
            session.clearConfirmation();
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            reopen(player);
        });
    }

    private void reopen(Player player) {
        BankSession session = getSession(player.getUniqueId());
        open(player, session.backAction());
    }

    private void depositStaged(Player player) {
        BankSession session = getSession(player.getUniqueId());
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            player.sendMessage("§cNo diamonds staged. Select diamonds from your inventory first.");
            return;
        }
        if (!session.confirmPending()) {
            session.requestConfirmation();
            player.sendMessage("§eClick again to shatter these diamonds into shards. Right-click to cancel and return them.");
            return;
        }
        long diamonds = toDiamondCount(staged.getType(), staged.getAmount());
        long shardGain = diamonds * 9;
        UUID playerId = player.getUniqueId();
        if (!verifyAndRemoveStagedFromInventory(player, session)) {
            player.sendMessage("§cThose diamonds moved; restage them.");
            session.clear();
            reopen(player);
            return;
        }
        players.load(playerId.toString())
            .thenCompose(document -> {
                long current = document.get("bank.shards", Number.class).map(Number::longValue).orElse(0L);
                long updated = current + shardGain;
                return document.set("bank.shards", updated)
                    .thenCompose(ignored -> appendLedgerEntry(playerId, LedgerEntry.LedgerType.DEPOSIT, shardGain, updated, "bank:shards"))
                    .exceptionallyCompose(error -> document.set("bank.shards", current).thenApply(ignored -> {
                        throw new CompletionException(error);
                    }));
            })
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to deposit diamonds for " + playerId, throwable);
                    player.sendMessage("§cDeposit failed; your diamonds were returned.");
                    refund(session, player);
                    reopen(player);
                    return;
                }
                session.clear();
                session.clearConfirmation();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
                player.sendMessage("§aShattered into §b" + shardGain + " shards§a.");
                reopen(player);
            });
    }

    private void refundIfAny(UUID playerId, Player player) {
        BankSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            return;
        }
        if (!player.isOnline()) {
            refund(session, player);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                refund(session, player);
                return;
            }
            Menu openMenu = MenuInventoryHolder.getMenu(player.getOpenInventory().getTopInventory());
            boolean bankOpen = openMenu != null
                && openMenu.getProperty("bankMenu", Boolean.class).orElse(false);
            if (bankOpen) {
                return;
            }
            refund(session, player);
        });
    }

    private BankSession getSession(UUID playerId) {
        return sessions.computeIfAbsent(playerId, ignored -> new BankSession());
    }

    private void refund(BankSession session, Player player) {
        ItemStack staged = session.stagedItem();
        if (staged != null && !staged.getType().isAir()) {
            unmaskSessionSlot(session, player);
            return;
        }
        session.clear();
    }

    private void unmaskSessionSlot(BankSession session, Player player) {
        if (!player.isOnline()) {
            return;
        }
        int slot = session.stagedSlot();
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            session.clear();
            return;
        }
        if (slot >= 0 && slot < player.getInventory().getSize()) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                player.getInventory().setItem(slot, staged);
                maskSlot(player, slot, staged);
                session.clear();
                return;
            }
            if (existing.isSimilar(staged) && existing.getAmount() == staged.getAmount()) {
                maskSlot(player, slot, existing);
                session.clear();
                return;
            }
        }
        player.getInventory().addItem(staged);
        session.clear();
    }

    private boolean verifyAndRemoveStagedFromInventory(Player player, BankSession session) {
        if (!player.isOnline()) {
            return false;
        }
        int slot = session.stagedSlot();
        ItemStack staged = session.stagedItem();
        if (slot < 0 || slot >= player.getInventory().getSize() || staged == null || staged.getType().isAir()) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir() || current.getType() != staged.getType() || current.getAmount() != staged.getAmount()) {
            return false;
        }
        player.getInventory().setItem(slot, null);
        maskSlot(player, slot, maskPane());
        return true;
    }

    private boolean maskSlot(Player player, int slot, ItemStack item) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        try {
            int rawSlot = player.getOpenInventory().convertSlot(slot);
            boolean sent = false;
            var peItem = SpigotConversionUtil.fromBukkitItemStack(item);
            for (int windowId : currentWindowIds(player)) {
                var packet = new WrapperPlayServerSetSlot(windowId, 0, rawSlot, peItem);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
                sent = true;
            }
            return sent;
        } catch (Exception exception) {
            logger.log(Level.FINE, "Failed to mask slot via packet", exception);
            return false;
        }
    }

    private Iterable<Integer> currentWindowIds(Player player) {
        java.util.Set<Integer> ids = new HashSet<>();
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
            if (craftPlayerClass.isInstance(player)) {
                Method getHandle = craftPlayerClass.getMethod("getHandle");
                Object handle = getHandle.invoke(player);
                Field containerMenuField = handle.getClass().getField("containerMenu");
                Object menu = containerMenuField.get(handle);
                Field containerIdField = menu.getClass().getField("containerId");
                int containerId = containerIdField.getInt(menu);
                ids.add(containerId);
            }
        } catch (Exception ignored) {
        }
        ids.add(0);
        return ids;
    }

    private boolean isDiamondLike(Material material) {
        return material == Material.DIAMOND || material == Material.DIAMOND_BLOCK;
    }

    private long toDiamondCount(Material type, int amount) {
        if (type == Material.DIAMOND_BLOCK) {
            return amount * 9L;
        }
        return amount;
    }

    private void startSnakeAnimation(Menu menu, boolean staged) {
        if (menu == null || menu.getInventory() == null) {
            return;
        }
        var inventory = menu.getInventory();
        var viewerOpt = menu.getViewer();
        if (viewerOpt.isEmpty()) {
            return;
        }
        Player viewer = viewerOpt.get();
        if (!viewer.isOnline()) {
            return;
        }

        ItemStack head = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemStack body = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack tail = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        Map<Integer, ItemStack> originals = new java.util.HashMap<>();
        for (int slot : SNAKE_PATH) {
            originals.put(slot, inventory.getItem(slot));
        }

        final int[] index = {0};
        long period = staged ? 2L : 6L;
        var task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!menu.isOpen()) {
                restoreSnake(menu, originals);
                return;
            }
            for (int slot : SNAKE_PATH) {
                inventory.setItem(slot, originals.get(slot));
            }
            int headSlot = SNAKE_PATH[index[0]];
            int midSlot = SNAKE_PATH[(index[0] - 1 + SNAKE_PATH.length) % SNAKE_PATH.length];
            int tailSlot = SNAKE_PATH[(index[0] - 2 + SNAKE_PATH.length) % SNAKE_PATH.length];
            inventory.setItem(headSlot, head);
            inventory.setItem(midSlot, body);
            inventory.setItem(tailSlot, tail);
            index[0] = (index[0] + 1) % SNAKE_PATH.length;
        }, 0L, period);

        menu.getContext().setProperty("bankSnakeTask", task);
        menu.getContext().setProperty("bankSnakeOriginals", originals);
    }

    private void stopSnakeAnimation(Menu menu) {
        if (menu == null) {
            return;
        }
        menu.getContext().getProperty("bankSnakeTask", org.bukkit.scheduler.BukkitTask.class)
            .ifPresent(org.bukkit.scheduler.BukkitTask::cancel);
        Map<Integer, ItemStack> originals = menu.getContext()
            .getProperty("bankSnakeOriginals", Map.class)
            .orElse(null);
        if (originals != null) {
            restoreSnake(menu, originals);
        }
    }

    private void restoreSnake(Menu menu, Map<Integer, ItemStack> originals) {
        if (menu == null || menu.getInventory() == null || originals == null) {
            return;
        }
        originals.forEach(menu.getInventory()::setItem);
    }

    private CompletionStage<List<LedgerEntry>> recentLedger(UUID playerId, int limit) {
        if (ledger == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return ledger.recent(playerId, limit);
    }

    private List<String> formatLedger(List<LedgerEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of("&7No recent shard activity.");
        }
        List<String> lines = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            boolean credit = entry.type() == LedgerEntry.LedgerType.DEPOSIT || entry.type() == LedgerEntry.LedgerType.TRANSFER_IN;
            String deltaColor = credit ? "&a" : "&c";
            String sign = credit ? "+" : "-";
            long previousBalance = credit
                ? entry.resultingBalance() - entry.amount()
                : entry.resultingBalance() + entry.amount();
            String line = "&3" + previousBalance
                + " &7➜ &b" + entry.resultingBalance()
                + " " + deltaColor + "(" + sign + entry.amount() + " Shard" + (entry.amount() == 1 ? "" : "s") + ")"
                + " &7- " + formatRelativeTime(entry.createdAt());
            lines.add(line);
        }
        return lines;
    }

    private String formatRelativeTime(Instant instant) {
        if (instant == null) {
            return "unknown";
        }
        Instant now = Instant.now();
        long seconds = Math.max(0, java.time.Duration.between(instant, now).getSeconds());
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        return days + "d ago";
    }

    private CompletionStage<Void> appendLedgerEntry(UUID playerId, LedgerEntry.LedgerType type, long amount, long balance, String source) {
        if (ledger == null) {
            return CompletableFuture.completedFuture(null);
        }
        LedgerEntry entry = new LedgerEntry(playerId, type, amount, balance, source, Instant.now());
        return ledger.append(entry);
    }

    private static final class BankSession {
        private ItemStack stagedItem;
        private boolean confirmPending;
        private int stagedSlot = -1;
        private Consumer<Player> backAction;

        ItemStack stagedItem() {
            return stagedItem;
        }

        void stage(ItemStack item, int slot) {
            this.stagedItem = item;
            this.stagedSlot = slot;
        }

        boolean confirmPending() {
            return confirmPending;
        }

        void requestConfirmation() {
            this.confirmPending = true;
        }

        void clearConfirmation() {
            this.confirmPending = false;
        }

        void clear() {
            stagedItem = null;
            confirmPending = false;
            stagedSlot = -1;
        }

        int stagedSlot() {
            return stagedSlot;
        }

        Consumer<Player> backAction() {
            return backAction;
        }

        void backAction(Consumer<Player> backAction) {
            this.backAction = backAction;
        }
    }

    OptionalInt stagedRawSlot(Player player, Integer windowId) {
        BankSession session = sessions.get(player.getUniqueId());
        if (session == null || session.stagedItem() == null) {
            return OptionalInt.empty();
        }
        int slot = session.stagedSlot();
        if (slot < 0) {
            return OptionalInt.empty();
        }
        int raw = player.getOpenInventory().convertSlot(slot);
        return OptionalInt.of(raw);
    }

    ItemStack maskPane() {
        return new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
    }

    private record BankState(long shards, List<LedgerEntry> entries) {
    }
}
