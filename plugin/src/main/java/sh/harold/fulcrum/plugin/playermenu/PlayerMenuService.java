package sh.harold.fulcrum.plugin.playermenu;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.menu.Menu;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.impl.DefaultListMenu;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryHolder;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.ledger.LedgerEntry;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryEntry;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettings;
import sh.harold.fulcrum.plugin.playerdata.PlayerSettingsService;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;
import sh.harold.fulcrum.plugin.playerdata.UsernameView;
import sh.harold.fulcrum.plugin.scoreboard.ScoreboardFeature;
import sh.harold.fulcrum.plugin.stash.StashService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableRegistry;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.BiConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.OptionalInt;

public final class PlayerMenuService {

    private static final int MENU_ROWS = 6;
    private static final int MENU_HEADLINE_SLOT = 13;
    private static final String DISPLAY_NAME = "&aPlayer Menu &7(Right Click)";
    private static final List<String> LORE_LINES = List.of("&e&lCLICK &eto open!");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final Duration SETTINGS_COOLDOWN = Duration.ofMillis(500);
    private static final Duration PVP_TOGGLE_COOLDOWN = Duration.ofMinutes(30);
    private static final String PVP_COOLDOWN_GROUP = "player-settings:pvp-toggle";
    private static final Material BASE_MENU_ITEM = Material.PUFFERFISH;
    private static final int STAGED_SLOT = 13;
    private static final int[] SNAKE_PATH = {
        4, 5, 14, 23, 22, 21, 12, 3
    };
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final Logger logger;
    private final DocumentCollection players;
    private final LedgerRepository ledger;
    private final MenuService menuService;
    private final StashService stashService;
    private final PlayerSettingsService settingsService;
    private final ScoreboardService scoreboardService;
    private final UsernameDisplayService usernameDisplayService;
    private final UnlockableService unlockableService;
    private final UnlockableRegistry unlockableRegistry;
    private final PerkMenuView perkMenuView;
    private final NamespacedKey markerKey;
    private final NamespacedKey displayMaterialKey;
    private final ProtocolManager protocolManager;
    private final java.util.Map<UUID, BankSession> bankSessions = new ConcurrentHashMap<>();

    public PlayerMenuService(
        JavaPlugin plugin,
        DataApi dataApi,
        StashService stashService,
        MenuService menuService,
        PlayerSettingsService settingsService,
        UsernameDisplayService usernameDisplayService,
        ScoreboardService scoreboardService,
        UnlockableService unlockableService,
        UnlockableRegistry unlockableRegistry
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.ledger = dataApi.ledger().orElse(null);
        this.stashService = Objects.requireNonNull(stashService, "stashService");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.usernameDisplayService = Objects.requireNonNull(usernameDisplayService, "usernameDisplayService");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.unlockableRegistry = Objects.requireNonNull(unlockableRegistry, "unlockableRegistry");
        this.perkMenuView = new PerkMenuView(
            plugin,
            menuService,
            unlockableService,
            unlockableRegistry,
            this.players
        );
        this.markerKey = new NamespacedKey(plugin, "player_menu");
        this.displayMaterialKey = new NamespacedKey(plugin, "player_menu_display");
        this.protocolManager = plugin.getServer().getPluginManager().isPluginEnabled("ProtocolLib")
            ? ProtocolLibrary.getProtocolManager()
            : null;
        registerSpoofingAdapter();
    }

    public CompletionStage<Void> distribute(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        return players.load(playerId.toString())
            .thenCompose(document -> resolveConfig(document)
            .thenCompose(config -> placeMenuItem(player, config)))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to prepare player menu for " + playerId, throwable);
                return null;
            });
    }

    public CompletionStage<Void> openMenu(Player player) {
        Objects.requireNonNull(player, "player");
        MenuDisplayItem headline = MenuDisplayItem.builder(Material.PLAYER_HEAD)
            .name("&aPlayer Menu")
            .secondary(player.getName())
            .description("&7Features unlock soon; stay tuned.")
            .slot(MENU_HEADLINE_SLOT)
            .build();

        int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
        MenuButton settingsButton = MenuButton.builder(Material.REDSTONE_TORCH)
            .name("&cView/Modify Settings")
            .secondary("Game Settings")
            .description("Adjust gameplay preferences and other options.")
            .slot(closeSlot + 1)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openSettings(viewer))
            .build();
        MenuButton directoryButton = MenuButton.builder(Material.PLAYER_HEAD)
            .name("&bPlayer Directory")
            .secondary("Roster")
            .description("Browse everyone who has visited the server.")
            .slot(20)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openPlayerDirectory)
            .build();
        MenuButton bankButton = MenuButton.builder(Material.ENDER_CHEST)
            .name("&bBank")
            .secondary("Economy")
            .description("Shatter diamonds into shards and peek at transactions.")
            .slot(19)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openBankMenu)
            .build();
        MenuButton perksButton = MenuButton.builder(Material.ENCHANTED_BOOK)
            .name("&dPerks & Upgrades")
            .secondary("Gameplay")
            .description("Unlock and toggle perks you have earned.")
            .slot(21)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> perkMenuView.openHub(viewer, this::openMenu))
            .build();
        List<MenuDisplayItem> comingSoon = new ArrayList<>();
        for (int slot = 22; slot <= 25; slot++) {
            MenuDisplayItem placeholder = MenuDisplayItem.builder(Material.GRAY_STAINED_GLASS_PANE)
                .name("&7???")
                .secondary("Coming Soon")
                .description("Fresh perks will land here soon; stay curious.")
                .slot(slot)
                .build();
            comingSoon.add(placeholder);
        }

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            var builder = menuService.createMenuBuilder()
                .title("Player Menu")
                .rows(MENU_ROWS)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                .addButton(settingsButton)
                .addButton(directoryButton)
                .addButton(bankButton)
                .addButton(perksButton)
                .addItem(headline, MENU_HEADLINE_SLOT);

            comingSoon.forEach(item -> builder.addItem(item, item.getSlot()));

            builder.buildAsync(player)
                .whenComplete((menu, throwable) -> {
                    if (throwable != null) {
                        logger.log(Level.SEVERE, "Failed to open player menu for " + player.getUniqueId(), throwable);
                        openFuture.completeExceptionally(throwable);
                        return;
                    }
                    openFuture.complete(null);
                });
        } catch (Throwable throwable) {
            logger.log(Level.SEVERE, "Failed to open player menu for " + player.getUniqueId(), throwable);
            openFuture.completeExceptionally(throwable);
        }
        return openFuture;
    }

    private void openPlayerDirectory(Player player) {
        UUID viewerId = player.getUniqueId();
        renderDirectoryLoading(player);
        players.all()
            .thenApply(documents -> documents.stream()
                .map(this::toDirectoryEntry)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PlayerDirectoryEntry::username, String.CASE_INSENSITIVE_ORDER))
                .toList())
            .whenComplete((entries, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load player directory for " + viewerId, throwable);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (!displayDirectoryError(player)) {
                            player.sendMessage("§cDirectory is snoozing; try again soon.");
                        }
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!tryPopulateOpenDirectory(player, entries)) {
                        renderPlayerDirectory(player, entries);
                    }
                });
            });
    }

    private void renderPlayerDirectory(Player player, List<PlayerDirectoryEntry> entries) {
        var builder = menuService.createListMenu()
            .title("Player Directory")
            .rows(MENU_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .addButton(MenuButton.createPositionedClose(MENU_ROWS))
            .addButton(directoryBackButton())
            .addItems(entries, this::buildDirectoryItem)
            .emptyMessage(Component.text("No adventurers have visited yet."));

        builder.buildAsync(player)
            .thenAccept(menu -> menu.setProperty("directoryMenu", true))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open player directory for " + player.getUniqueId(), throwable);
                player.sendMessage("§cDirectory is snoozing; try again soon.");
                return null;
            });
    }

    private void renderDirectoryLoading(Player player) {
        MenuDisplayItem loading = MenuDisplayItem.builder(Material.CLOCK)
            .name("&eLoading Directory...")
            .secondary("Roster")
            .description("Fetching the latest roster; hang tight.")
            .build();

        menuService.createListMenu()
            .title("Player Directory")
            .rows(MENU_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .addButton(MenuButton.createPositionedClose(MENU_ROWS))
            .addButton(directoryBackButton())
            .addItems(List.of(loading))
            .emptyMessage(Component.text("Loading directory..."))
            .buildAsync(player)
            .thenAccept(menu -> menu.setProperty("directoryMenu", true))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open loading directory for " + player.getUniqueId(), throwable);
                player.sendMessage("§cDirectory is snoozing; try again soon.");
                return null;
            });
    }

    private boolean tryPopulateOpenDirectory(Player player, List<PlayerDirectoryEntry> entries) {
        return menuService.getOpenMenu(player)
            .filter(menu -> menu.getProperty("directoryMenu", Boolean.class).orElse(false))
            .filter(menu -> menu instanceof DefaultListMenu)
            .map(menu -> {
                DefaultListMenu listMenu = (DefaultListMenu) menu;
                listMenu.clearContentItems();
                listMenu.addContentItems(entries.stream().map(this::buildDirectoryItem).toList());
                listMenu.getContext().setProperty("directoryLoaded", true);
                listMenu.update();
                return true;
            })
            .orElse(false);
    }

    private boolean displayDirectoryError(Player player) {
        return menuService.getOpenMenu(player)
            .filter(menu -> menu.getProperty("directoryMenu", Boolean.class).orElse(false))
            .filter(menu -> menu instanceof DefaultListMenu)
            .map(menu -> {
                DefaultListMenu listMenu = (DefaultListMenu) menu;
                listMenu.clearContentItems();
                MenuDisplayItem error = MenuDisplayItem.builder(Material.BARRIER)
                    .name("&cDirectory Offline")
                    .secondary("Roster")
                    .description("Could not fetch the roster; try again soon.")
                    .build();
                listMenu.addContentItem(error);
                listMenu.update();
                return true;
            })
            .orElse(false);
    }

    private MenuButton directoryBackButton() {
        return MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to the player menu.")
            .slot(MenuButton.getBackSlot(MENU_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openMenu)
            .build();
    }

    private MenuDisplayItem buildDirectoryItem(PlayerDirectoryEntry entry) {
        boolean online = plugin.getServer().getPlayer(entry.id()) != null;
        String pvp = entry.pvpEnabled() ? "&aEnabled &c[☠]" : "&cDisabled &a[☮]";

        MenuDisplayItem item = MenuDisplayItem.builder(Material.PLAYER_HEAD)
            .name("&a" + entry.username())
            .secondary("UUID: " + entry.shortId())
            .lore("")
            .lore("&7Playtime: &e" + entry.playtimeLabel())
            .lore("&7First join: &b" + entry.firstJoinLabel())
            .lore("&7Last seen: &b" + entry.lastSeenLabel(online))
            .lore("")
            .lore("&7PvP: " + pvp)
            .lore("")
            .lore("&7osu! username: " + colorOsuValue(entry.osuUsernameLabel(), entry.hasOsuUsername()))
            .lore("&7osu! rank: " + colorOsuValue(entry.osuRankLabel(), entry.hasOsuRank()))
            .lore("&7osu! country: " + colorOsuValue(entry.osuCountryLabel(), entry.hasOsuCountry()))
            .lore("")
            .lore("&7Discord: " + colorDiscord(entry))
            .build();

        ItemStack display = item.getDisplayItem();
        ItemMeta meta = display.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(plugin.getServer().getOfflinePlayer(entry.id()));
            display.setItemMeta(skullMeta);
            item.setDisplayItem(display);
        }

        return item;
    }

    private String colorOsuValue(String value, boolean linked) {
        if (!linked) {
            return "&7" + value;
        }
        return "&b" + value;
    }

    private String colorDiscord(PlayerDirectoryEntry entry) {
        if (!entry.hasDiscord()) {
            return "&7Not linked";
        }
        StringBuilder builder = new StringBuilder();
        if (entry.discordGlobalName() != null && !entry.discordGlobalName().isBlank()) {
            builder.append("&b").append(entry.discordGlobalName().trim());
        }
        if (entry.discordUsername() != null && !entry.discordUsername().isBlank()) {
            if (builder.length() > 0) {
                builder.append(" ");
            }
            builder.append("&8(").append("&b").append(entry.discordUsername().trim()).append("&8)");
        }
        return builder.toString();
    }

    private PlayerDirectoryEntry toDirectoryEntry(Document document) {
        return PlayerDirectoryEntry.fromDocument(document).orElse(null);
    }


    private void openBankMenu(Player player) {
        UUID playerId = player.getUniqueId();
        BankSession session = bankSessions.computeIfAbsent(playerId, ignored -> new BankSession());

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
                    .onClick(viewer -> viewer.sendMessage("§7Ledger synced for your account."))
                    .build();

                int rows = 6;
                int closeSlot = MenuButton.getCloseSlot(rows);
                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .description("Return to the player menu.")
                    .slot(closeSlot - 1)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(this::openMenu)
                    .build();

                menuService.createMenuBuilder()
                    .title("Bank")
                    .rows(rows)
                    .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                    .addButton(MenuButton.createPositionedClose(rows))
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
                restoreStaged(session, player);
            }
            if (!maskSlot(player, slot, maskPane())) {
                player.sendMessage("§cCould not stage those diamonds; try again.");
                return;
            }
            session.stage(staged, slot);
            session.clearConfirmation();
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            openBankMenu(player);
        });
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
            openBankMenu(player);
            return;
        }
        players.load(playerId.toString())
            .thenCompose(document -> {
                long current = document.get("bank.shards", Number.class).map(Number::longValue).orElse(0L);
                long updated = current + shardGain;
                return document.set("bank.shards", updated)
                    .thenCompose(ignored -> appendLedgerEntry(playerId, LedgerEntry.LedgerType.DEPOSIT, shardGain, updated, "bank:shards"))
                    .exceptionallyCompose(error -> {
                        // Roll back balance if ledger fails
                        return document.set("bank.shards", current)
                            .thenApply(ignored -> {
                                throw new CompletionException(error);
                            });
                    });
            })
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to deposit diamonds for " + playerId, throwable);
                    player.sendMessage("§cDeposit failed; your diamonds were returned.");
                    refund(session, player);
                    openBankMenu(player);
                    return;
                }
                session.clear();
                session.clearConfirmation();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
                player.sendMessage("§aShattered into §b" + shardGain + " shards§a.");
                openBankMenu(player);
            });
    }

    private void refundIfAny(UUID playerId, Player player) {
        BankSession session = bankSessions.get(playerId);
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
        return bankSessions.computeIfAbsent(playerId, ignored -> new BankSession());
    }

    private void refund(BankSession session, Player player) {
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            session.clear();
            return;
        }
        restoreStaged(session, player);
        session.clear();
    }

    private void restoreStaged(BankSession session, Player player) {
        if (!player.isOnline()) {
            return;
        }
        int slot = session.stagedSlot();
        ItemStack staged = session.stagedItem();
        if (staged == null || staged.getType().isAir()) {
            return;
        }
        if (slot >= 0 && slot < player.getInventory().getSize()) {
            ItemStack existing = player.getInventory().getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                player.getInventory().setItem(slot, staged);
                maskSlot(player, slot, staged);
                return;
            }
            if (existing.isSimilar(staged) && existing.getAmount() == staged.getAmount()) {
                maskSlot(player, slot, existing);
                return;
            }
        }
        player.getInventory().addItem(staged);
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
        if (protocolManager == null) {
            return false;
        }
        try {
            int rawSlot = player.getOpenInventory().convertSlot(slot);
            boolean sent = false;
            for (int windowId : currentWindowIds(player)) {
                var packet = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);
                packet.getIntegers().write(0, windowId);
                packet.getIntegers().write(1, rawSlot);
                packet.getItemModifier().write(0, item);
                protocolManager.sendServerPacket(player, packet);
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
        // Current open container id via reflection (NMS)
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
            // fall through to default ids
        }
        ids.add(0); // player inventory window as fallback
        return ids;
    }

    private boolean isDiamondLike(Material material) {
        return material == Material.DIAMOND || material == Material.DIAMOND_BLOCK;
    }

    private long toDiamondCount(Material material, int amount) {
        return switch (material) {
            case DIAMOND -> amount;
            case DIAMOND_BLOCK -> amount * 9L;
            default -> 0L;
        };
    }

    private void startSnakeAnimation(sh.harold.fulcrum.api.menu.Menu menu, boolean staged) {
        if (menu == null || menu.getInventory() == null) {
            return;
        }
        stopSnakeAnimation(menu);
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
        long period = staged ? 2L : 4L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
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

    private void stopSnakeAnimation(sh.harold.fulcrum.api.menu.Menu menu) {
        if (menu == null) {
            return;
        }
        menu.getContext().getProperty("bankSnakeTask", BukkitTask.class)
            .ifPresent(BukkitTask::cancel);
        Map<Integer, ItemStack> originals = menu.getContext()
            .getProperty("bankSnakeOriginals", Map.class)
            .orElse(null);
        if (originals != null && menu.getInventory() != null) {
            restoreSnake(menu, originals);
        }
    }

    private void restoreSnake(sh.harold.fulcrum.api.menu.Menu menu, Map<Integer, ItemStack> originals) {
        if (menu == null || menu.getInventory() == null || originals == null) {
            return;
        }
        originals.forEach(menu.getInventory()::setItem);
    }

    // Perk menus are rendered by PerkMenuView.

    private MenuDisplayItem emptyPerkSlot(int slot) {
        return MenuDisplayItem.builder(Material.GRAY_STAINED_GLASS_PANE)
            .name("&8Empty Perk Slot")
            .secondary("")
            .description("&7Future perk will land here.")
            .slot(slot)
            .build();
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
        return ledger.append(entry).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to append bank ledger entry for " + playerId, throwable);
            throw new CompletionException(throwable);
        });
    }

    private record BankState(long shards, List<LedgerEntry> entries) {
    }

    public CompletionStage<Void> openSettings(Player player) {
        Objects.requireNonNull(player, "player");
        return openSettingsMenu(player);
    }

    private CompletionStage<Void> openSettingsMenu(Player player) {
        UUID playerId = player.getUniqueId();
        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        settingsService.loadSettings(playerId)
            .whenComplete((settings, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load settings for " + playerId, throwable);
                    player.sendMessage("§cSettings are snoozing; try again soon.");
                    openFuture.completeExceptionally(throwable);
                    return;
                }

                boolean scoreboardEnabled = settings.scoreboardEnabled();
                boolean pvpEnabled = settings.pvpEnabled();
                UsernameView usernameView = settings.usernameView();
                int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .description("Return to the player menu.")
                    .slot(closeSlot - 1)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(this::openMenu)
                    .build();

                MenuButton scoreboardToggle = MenuButton.builder(Material.COMPARATOR)
                    .name(scoreboardEnabled ? "&aScoreboard: Enabled" : "&cScoreboard: Disabled")
                    .secondary("Display")
                    .description("Toggle the sidebar scoreboard on or off.")
                    .slot(10)
                    .cooldown(SETTINGS_COOLDOWN)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(viewer -> toggleScoreboard(viewer, !scoreboardEnabled))
                    .build();

                MenuButton pvpToggle = MenuButton.builder(Material.IRON_SWORD)
                    .name(pvpEnabled ? "&aPvP: Enabled &c[☠]" : "&cPvP: Disabled &a[☮]")
                    .secondary("Combat")
                    .description(pvpEnabled ? "Other players can spar with you." : "Keep the peace; nobody can tag you.")
                    .slot(11)
                    .cooldown(PVP_TOGGLE_COOLDOWN)
                    .cooldownGroup(PVP_COOLDOWN_GROUP)
                    .requireConfirmation("&cConfirm PvP toggle; click again.")
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(viewer -> togglePvp(viewer, !pvpEnabled))
                    .build();

                MenuButton usernameViewButton = MenuButton.builder(Material.NAME_TAG)
                    .name("&bUsername View: " + usernameView.label())
                    .secondary("Name Display")
                    .description("Switch how names appear in chat, nametags, and the tab list.")
                    .lore("")
                    .slot(29)
                    .cooldown(SETTINGS_COOLDOWN)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .lore(usernameViewLore(usernameView).toArray(new String[0]))
                    .onClick(viewer -> toggleUsernameView(viewer, usernameView.next()))
                    .build();

                MenuButton relocateButton = MenuButton.builder(Material.ENDER_PEARL)
                    .name("&bRelocate Menu Item")
                    .secondary("Inventory Slot")
                    .description("Choose where the player menu item lives.")
                    .slot(28)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(this::openRelocateMenu)
                    .build();

                try {
                    menuService.createMenuBuilder()
                        .title("Settings")
                        .rows(MENU_ROWS)
                        .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                        .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                        .addButton(backButton)
                        .addButton(scoreboardToggle)
                        .addButton(pvpToggle)
                        .addButton(usernameViewButton)
                        .addButton(relocateButton)
                        .buildAsync(player)
                        .whenComplete((menu, openError) -> {
                            if (openError != null) {
                                logger.log(Level.SEVERE, "Failed to open settings menu for " + player.getUniqueId(), openError);
                                player.sendMessage("§cSettings are snoozing; try again soon.");
                                openFuture.completeExceptionally(openError);
                                return;
                            }
                            openFuture.complete(null);
                        });
                } catch (Throwable openError) {
                    logger.log(Level.SEVERE, "Failed to open settings menu for " + player.getUniqueId(), openError);
                    player.sendMessage("§cSettings are snoozing; try again soon.");
                    openFuture.completeExceptionally(openError);
                }
            });
        return openFuture;
    }

    private void toggleScoreboard(Player player, boolean enable) {
        UUID playerId = player.getUniqueId();
        settingsService.setScoreboardEnabled(playerId, enable)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (enable) {
                    scoreboardService.showScoreboard(playerId, ScoreboardFeature.SCOREBOARD_ID);
                } else {
                    scoreboardService.hideScoreboard(playerId);
                }
                openSettings(player);
            }))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to toggle scoreboard for " + playerId, throwable);
                player.sendMessage("§cCould not update your scoreboard setting.");
                return null;
            });
    }

    private void togglePvp(Player player, boolean enable) {
        UUID playerId = player.getUniqueId();
        settingsService.setPvpEnabled(playerId, enable)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().getOnlinePlayers().forEach(usernameDisplayService::refreshView);
                player.sendMessage(enable ? "§aPvP is now on; swing responsibly." : "§ePvP is snoozing; you're safe for now.");
                openSettings(player);
            }))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to toggle PvP for " + playerId, throwable);
                player.sendMessage("§cCould not update your PvP setting.");
                return null;
            });
    }

    private void toggleUsernameView(Player player, UsernameView view) {
        UUID playerId = player.getUniqueId();
        settingsService.setUsernameView(playerId, view)
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                usernameDisplayService.refreshView(player);
                openSettings(player);
            }))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to update username view for " + playerId, throwable);
                player.sendMessage("§cCould not update your username display.");
                return null;
            });
    }

    private List<String> usernameViewLore(UsernameView selected) {
        String minecraftLine = (selected == UsernameView.MINECRAFT ? "&e➜ " : "&7   ") + "Minecraft Usernames";
        String osuLine = (selected == UsernameView.OSU ? "&e➜ " : "&7   ") + "osu!Username";
        String discordLine = (selected == UsernameView.DISCORD ? "&e➜ " : "&7   ") + "Discord Display Name";
        return List.of(minecraftLine, osuLine, discordLine);
    }

    private void openRelocateMenu(Player player) {
        UUID playerId = player.getUniqueId();
        loadConfig(playerId)
            .whenComplete((config, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to load menu item config for relocation for " + playerId, throwable);
                    player.sendMessage("§cCould not load your menu item.");
                    return;
                }

                int closeSlot = MenuButton.getCloseSlot(MENU_ROWS);
                int backSlot = closeSlot - 1;
                boolean enabled = config.enabled();
                boolean hasMenuItem = findMenuItemSlot(player.getInventory()) >= 0;

                MenuButton backButton = MenuButton.builder(Material.ARROW)
                    .name("&7Back")
                    .description("Return to the settings menu.")
                    .slot(backSlot)
                    .sound(Sound.UI_BUTTON_CLICK)
                    .onClick(viewer -> openSettings(viewer))
                    .build();

                List<MenuItemPlacement> placements = buildMenuPlacements(player, config);

                var builder = menuService.createMenuBuilder()
                    .title("Relocate Menu Item")
                    .rows(MENU_ROWS)
                    .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                    .addButton(MenuButton.createPositionedClose(MENU_ROWS))
                    .addButton(backButton);

                if (hasMenuItem) {
                    MenuButton visibilityToggle = MenuButton.builder(Material.ORANGE_DYE)
                        .name(enabled ? "&aMenu Item Visible" : "&cMenu Item Hidden")
                        .secondary("Inventory Toggle")
                        .description(enabled ? "Hide the player menu item from your inventory." : "Show the player menu item in your inventory.")
                        .slot(closeSlot + 1)
                        .cooldown(SETTINGS_COOLDOWN)
                        .sound(Sound.UI_BUTTON_CLICK)
                        .onClick(viewer -> updateMenuItemVisibility(viewer, config, !enabled))
                        .build();
                    builder.addButton(visibilityToggle);
                }

                placements.forEach(placement -> builder.addButton(placement.button(), placement.menuSlot()));

                fillDividerRow(builder);

                builder.buildAsync(player)
                    .exceptionally(openError -> {
                        logger.log(Level.SEVERE, "Failed to open relocation menu for " + playerId, openError);
                        return null;
                    });
            });
    }

    private void updateMenuItemSlot(Player player, PlayerMenuItemConfig config) {
        UUID playerId = player.getUniqueId();
        persistConfig(playerId, config)
            .thenCompose(ignored -> placeMenuItem(player, config))
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to move player menu item for " + playerId, throwable);
                player.sendMessage("§cCould not move your menu item.");
                return null;
            });
    }

    private void updateMenuItemVisibility(Player player, PlayerMenuItemConfig config, boolean enable) {
        UUID playerId = player.getUniqueId();
        PlayerMenuItemConfig updated = config.withEnabled(enable);
        persistConfig(playerId, updated)
            .thenCompose(ignored -> placeMenuItem(player, updated))
            .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                player.sendMessage("§eYou can open the player menu anytime with /menu.");
            }))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to update player menu item visibility for " + playerId, throwable);
                player.sendMessage("§cCould not update your player menu item.");
                return null;
            });
    }

    private List<MenuItemPlacement> buildMenuPlacements(Player player, PlayerMenuItemConfig config) {
        PlayerInventory inventory = player.getInventory();
        List<MenuItemPlacement> placements = new ArrayList<>();

        // Inventory rows (slots 9-35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int playerSlot = 9 + row * 9 + col;
                int menuSlot = row * 9 + col;
                placements.add(createSlotButton(inventory, config, playerSlot, menuSlot));
            }
        }

        // Hotbar row (slots 0-8) at menu row index 4
        int hotbarRowIndex = 4;
        for (int col = 0; col < 9; col++) {
            int playerSlot = col;
            int menuSlot = hotbarRowIndex * 9 + col;
            placements.add(createSlotButton(inventory, config, playerSlot, menuSlot));
        }

        return placements;
    }

    private MenuItemPlacement createSlotButton(PlayerInventory inventory, PlayerMenuItemConfig config, int playerSlot, int menuSlot) {
        ItemStack occupant = inventory.getItem(playerSlot);
        boolean free = occupant == null || occupant.getType().isAir();

        MenuButton.Builder builder = MenuButton.builder(free ? Material.STONE_BUTTON : occupant.getType())
            .name(free ? "&aPlace menu item here" : "&7Select slot")
            .slot(menuSlot)
            .cooldown(SETTINGS_COOLDOWN)
            .sound(Sound.UI_BUTTON_CLICK)
            .skipClickPrompt()
            .onClick(viewer -> {
                if (!free) {
                    return;
                }
                updateMenuItemSlot(viewer, config.withSlot(playerSlot).withEnabled(true));
            });

        if (free) {
            builder.description("Move the player menu item here.");
        } else {
            builder.lore("&cCannot move here!", "&cMove this item first.");
        }

        MenuButton button = builder.build();

        if (!free) {
            // Use a sanitized copy so menu-item markers do not trigger other listeners
            if (isMenuItem(occupant)) {
                button.setDisplayItem(buildDisplayItem(config.material()));
            } else {
                button.setDisplayItem(stripMenuMarker(occupant));
            }
        }

        return new MenuItemPlacement(menuSlot, button);
    }

    private ItemStack stripMenuMarker(ItemStack source) {
        ItemStack clone = source.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().remove(markerKey);
            meta.getPersistentDataContainer().remove(displayMaterialKey);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    private void fillDividerRow(sh.harold.fulcrum.api.menu.CustomMenuBuilder builder) {
        int dividerRow = 3;
        for (int col = 0; col < 9; col++) {
            int slot = dividerRow * 9 + col;
            builder.addItem(MenuDisplayItem.builder(Material.GRAY_STAINED_GLASS_PANE).name("").slot(slot).build(), slot);
        }
    }

    private CompletionStage<PlayerMenuItemConfig> loadConfig(UUID playerId) {
        return players.load(playerId.toString())
            .thenCompose(this::resolveConfig);
    }

    private CompletionStage<Void> persistConfig(UUID playerId, PlayerMenuItemConfig config) {
        return players.load(playerId.toString())
            .thenCompose(document -> document.set(PlayerMenuItemConfig.MATERIAL_PATH, config.material().name()).toCompletableFuture()
                .thenCompose(ignored -> document.set(PlayerMenuItemConfig.SLOT_PATH, config.slot()).toCompletableFuture())
                .thenCompose(ignored -> document.set(PlayerMenuItemConfig.ENABLED_PATH, config.enabled()).toCompletableFuture())
                .thenApply(ignored -> null));
    }

    private CompletionStage<PlayerMenuItemConfig> resolveConfig(Document document) {
        Optional<String> rawMaterial = document.get(PlayerMenuItemConfig.MATERIAL_PATH, String.class);
        Optional<Integer> rawSlot = document.get(PlayerMenuItemConfig.SLOT_PATH, Integer.class);
        Optional<Boolean> rawEnabled = document.get(PlayerMenuItemConfig.ENABLED_PATH, Boolean.class);
        Material material = rawMaterial
            .map(this::materialFrom)
            .filter(candidate -> candidate != null && !candidate.isAir())
            .orElse(PlayerMenuItemConfig.DEFAULT.material());
        int slot = rawSlot.orElse(PlayerMenuItemConfig.DEFAULT.slot());
        boolean enabled = rawEnabled.orElse(true);

        PlayerMenuItemConfig resolved = new PlayerMenuItemConfig(material, slot, enabled);
        if (rawMaterial.isPresent() && rawSlot.isPresent() && rawEnabled.isPresent()) {
            return CompletableFuture.completedFuture(resolved);
        }

        CompletionStage<Void> materialStage = rawMaterial.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.MATERIAL_PATH, resolved.material().name()).toCompletableFuture();
        CompletionStage<Void> slotStage = rawSlot.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.SLOT_PATH, resolved.slot()).toCompletableFuture();
        CompletionStage<Void> enabledStage = rawEnabled.isPresent()
            ? CompletableFuture.completedFuture(null)
            : document.set(PlayerMenuItemConfig.ENABLED_PATH, resolved.enabled()).toCompletableFuture();

        return CompletableFuture.allOf(materialStage.toCompletableFuture(), slotStage.toCompletableFuture(), enabledStage.toCompletableFuture())
            .thenApply(ignored -> resolved)
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to store menu item config", throwable);
            });
    }

    private CompletionStage<Void> placeMenuItem(Player player, PlayerMenuItemConfig config) {
        return CompletableFuture.runAsync(() -> placeMenuItemSync(player, config), plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    private void placeMenuItemSync(Player player, PlayerMenuItemConfig config) {
        if (!player.isOnline()) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int existingMenuSlot = findMenuItemSlot(inventory);
        if (!config.enabled()) {
            if (existingMenuSlot >= 0) {
                inventory.setItem(existingMenuSlot, null);
            }
            return;
        }
        int targetSlot = config.slot();
        ItemStack current = inventory.getItem(targetSlot);
        ItemStack menuItem = buildMenuItem(config.material());

        if (existingMenuSlot == targetSlot) {
            inventory.setItem(targetSlot, menuItem);
            spoofMenuItemAppearance(player, targetSlot, config.material());
            return;
        }

        ItemStack displaced = current == null ? null : current.clone();
        inventory.setItem(targetSlot, menuItem);

        if (existingMenuSlot >= 0) {
            if (displaced != null && !displaced.getType().isAir()) {
                inventory.setItem(existingMenuSlot, displaced);
            } else {
                inventory.setItem(existingMenuSlot, null);
            }
        } else if (displaced != null && !displaced.getType().isAir()) {
            inventory.setItem(targetSlot, menuItem);
            stashDisplaced(player, displaced);
        }

        spoofMenuItemAppearance(player, targetSlot, config.material());
    }

    public boolean isMenuItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(markerKey, PersistentDataType.BYTE);
    }

    private ItemStack buildMenuItem(Material displayMaterial) {
        ItemStack stack = new ItemStack(BASE_MENU_ITEM);
        applyMenuMeta(stack, true, displayMaterial);
        return stack;
    }

    private ItemStack buildDisplayItem(Material displayMaterial) {
        ItemStack stack = new ItemStack(normalizeDisplayMaterial(displayMaterial));
        applyMenuMeta(stack, false, displayMaterial);
        return stack;
    }

    private void applyMenuMeta(ItemStack stack, boolean includeMarker, Material displayMaterial) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Component displayName = LEGACY.deserialize(DISPLAY_NAME).decoration(TextDecoration.ITALIC, false);
        List<Component> lore = LORE_LINES.stream()
            .map(line -> (Component) LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false))
            .toList();
        meta.displayName(displayName);
        meta.lore(lore);
        if (includeMarker) {
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(displayMaterialKey, PersistentDataType.STRING, normalizeDisplayMaterial(displayMaterial).name());
        }
        stack.setItemMeta(meta);
    }

    private Material normalizeDisplayMaterial(Material material) {
        if (material == null || material.isAir()) {
            return PlayerMenuItemConfig.DEFAULT.material();
        }
        return material;
    }

    private Material displayMaterialFrom(ItemStack item) {
        if (item == null) {
            return PlayerMenuItemConfig.DEFAULT.material();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return PlayerMenuItemConfig.DEFAULT.material();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String raw = container.get(displayMaterialKey, PersistentDataType.STRING);
        Material material = materialFrom(raw);
        if (material == null || material.isAir()) {
            return PlayerMenuItemConfig.DEFAULT.material();
        }
        return material;
    }

    private void spoofMenuItemAppearance(Player player, int slot, Material displayMaterial) {
        if (protocolManager == null) {
            return;
        }

        ItemStack visual = buildDisplayItem(displayMaterial);
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);

        var integers = packet.getIntegers();
        if (integers.size() > 0) {
            integers.writeSafely(0, 0);
        }
        if (integers.size() > 1) {
            integers.writeSafely(1, 0);
        }
        if (integers.size() > 2) {
            integers.writeSafely(2, slot);
        } else {
            packet.getShorts().writeSafely(0, (short) slot);
        }

        packet.getItemModifier().writeSafely(0, visual);

        protocolManager.sendServerPacket(player, packet);
    }

    private void stashDisplaced(Player player, ItemStack displaced) {
        stashService.stash(player, List.of(displaced))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to stash displaced hotbar item for " + player.getUniqueId(), throwable);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.getInventory().addItem(displaced);
                    }
                });
                return null;
            });
    }

    private Material materialFrom(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }

    private int findMenuItemSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isMenuItem(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private record MenuItemPlacement(int menuSlot, MenuButton button) {
    }

    private void registerSpoofingAdapter() {
        if (protocolManager == null) {
            return;
        }

        protocolManager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player target = event.getPlayer();
                if (target == null) {
                    return;
                }

                PacketContainer packet = event.getPacket();

                if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                    ItemStack stack = packet.getItemModifier().readSafely(0);
                    if (isMenuItem(stack)) {
                        Material displayMaterial = displayMaterialFrom(stack);
                        packet.getItemModifier().writeSafely(0, buildDisplayItem(displayMaterial));
                    }
                    return;
                }

                var itemLists = packet.getItemListModifier();
                if (itemLists.size() == 0) {
                    return;
                }

                List<ItemStack> items = new ArrayList<>(itemLists.readSafely(0));
                boolean mutated = false;
                for (int i = 0; i < items.size(); i++) {
                    ItemStack stack = items.get(i);
                    if (isMenuItem(stack)) {
                        items.set(i, buildDisplayItem(displayMaterialFrom(stack)));
                        mutated = true;
                    }
                }

                if (mutated) {
                    itemLists.writeSafely(0, items);
                }
            }
        });
    }

    private static final class BankSession {
        private ItemStack stagedItem;
        private boolean confirmPending;
        private int stagedSlot = -1;

        ItemStack stagedItem() {
            return stagedItem;
        }

        void stage(ItemStack item, int slot) {
            this.stagedItem = item;
            this.confirmPending = false;
            this.stagedSlot = slot;
        }

        void clear() {
            this.stagedItem = null;
            this.confirmPending = false;
            this.stagedSlot = -1;
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

        int stagedSlot() {
            return stagedSlot;
        }
    }
}
