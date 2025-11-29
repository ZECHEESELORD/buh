package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.unlockable.PlayerUnlockable;
import sh.harold.fulcrum.plugin.unlockable.UnlockableDefinition;
import sh.harold.fulcrum.plugin.unlockable.UnlockableRegistry;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableTier;
import sh.harold.fulcrum.plugin.unlockable.UnlockableType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PerkMenuView {

    private static final int ROWS = 6;
    private static final int PERMANENT_SLOT = 20;
    private static final int TIERED_SLOT = 24;
    private static final int PERMANENT_ROW_START = 11;
    private static final int TIERED_ROW_START = 29;

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final UnlockableService unlockableService;
    private final UnlockableRegistry unlockableRegistry;
    private final DocumentCollection players;
    private final Logger logger;

    PerkMenuView(
        JavaPlugin plugin,
        MenuService menuService,
        UnlockableService unlockableService,
        UnlockableRegistry unlockableRegistry,
        DocumentCollection players
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.unlockableRegistry = Objects.requireNonNull(unlockableRegistry, "unlockableRegistry");
        this.players = Objects.requireNonNull(players, "players");
        this.logger = plugin.getLogger();
    }

    void openHub(Player player, Consumer<Player> backAction) {
        UUID playerId = player.getUniqueId();
        unlockableService.loadState(playerId).whenComplete((state, throwable) -> {
            if (throwable != null) {
                logger.log(Level.SEVERE, "Failed to open perks for " + playerId, throwable);
                player.sendMessage("§cCould not load your perks right now.");
                return;
            }
            renderHub(player, state, backAction);
        });
    }

    private void renderHub(Player player, sh.harold.fulcrum.plugin.unlockable.PlayerUnlockableState state, Consumer<Player> backAction) {
        List<UnlockableDefinition> singleUnlocks = unlockableRegistry.definitions(UnlockableType.PERK).stream()
            .filter(UnlockableDefinition::singleTier)
            .toList();
        List<UnlockableDefinition> tieredUnlocks = unlockableRegistry.definitions(UnlockableType.PERK).stream()
            .filter(definition -> !definition.singleTier())
            .toList();

        long unlockedSingle = state.unlocked().stream().filter(perk -> perk.definition().singleTier()).count();
        long unlockedTiered = state.unlocked().stream().filter(perk -> !perk.definition().singleTier()).count();

        int closeSlot = MenuButton.getCloseSlot(ROWS);
        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .description("Return to the player menu.")
            .slot(closeSlot - 1)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(backAction::accept)
            .build();

        MenuButton permanentButton = MenuButton.builder(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
            .name("&bPermanent Perks")
            .secondary("Single Unlocks")
            .description(progressLine(unlockedSingle, singleUnlocks.size()))
            .slot(PERMANENT_SLOT)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openCategory(viewer, false))
            .build();

        MenuButton tieredButton = MenuButton.builder(Material.PURPLE_STAINED_GLASS_PANE)
            .name("&dTiered Perks")
            .secondary("Progressive Upgrades")
            .description(progressLine(unlockedTiered, tieredUnlocks.size()))
            .slot(TIERED_SLOT)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openCategory(viewer, true))
            .build();

        menuService.createMenuBuilder()
            .title("Perks")
            .rows(ROWS)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
            .addButton(MenuButton.createPositionedClose(ROWS))
            .addButton(backButton)
            .addButton(permanentButton)
            .addButton(tieredButton)
            .buildAsync(player)
            .exceptionally(openError -> {
                logger.log(Level.SEVERE, "Failed to open perks for " + player.getUniqueId(), openError);
                player.sendMessage("§cCould not load your perks right now.");
                return null;
            });
    }

    private void openCategory(Player player, boolean tiered) {
        UUID playerId = player.getUniqueId();
        CompletableFuture<Long> shardBalance = players.load(playerId.toString())
            .thenApply(document -> document.get("bank.shards", Number.class).map(Number::longValue).orElse(0L))
            .toCompletableFuture();
        unlockableService.loadState(playerId).toCompletableFuture()
            .thenCombine(shardBalance, PerkMenuState::new)
            .whenComplete((state, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "Failed to open perks for " + playerId, throwable);
                    player.sendMessage("§cCould not load your perks right now.");
                    return;
                }
                renderCategory(player, state, tiered);
            });
    }

    private void renderCategory(Player player, PerkMenuState state, boolean tiered) {
        List<UnlockableDefinition> definitions = tiered
            ? unlockableRegistry.definitions(UnlockableType.PERK).stream().filter(def -> !def.singleTier()).toList()
            : unlockableRegistry.definitions(UnlockableType.PERK).stream().filter(UnlockableDefinition::singleTier).toList();

        int startSlot = tiered ? TIERED_ROW_START : PERMANENT_ROW_START;
        int closeSlot = MenuButton.getCloseSlot(ROWS);
        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .description("Return to perk categories.")
            .slot(closeSlot - 1)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openHubFromBack)
            .build();

        var builder = menuService.createMenuBuilder()
            .title(tiered ? "Tiered Perks" : "Permanent Perks")
            .rows(ROWS)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
            .addButton(MenuButton.createPositionedClose(ROWS))
            .addButton(backButton);

        for (int i = 0; i < definitions.size() && i < 7; i++) {
            UnlockableDefinition definition = definitions.get(i);
            PlayerUnlockable perk = state.state().unlockable(definition.id())
                .orElse(new PlayerUnlockable(definition, 0, false));
            MenuButton button = buildPerkButton(definition, perk, state.shards(), startSlot + i);
            builder.addButton(button);
        }

        int filled = Math.min(definitions.size(), 7);
        for (int slot = startSlot + filled; slot <= startSlot + 6; slot++) {
            builder.addItem(emptyPerkSlot(slot), slot);
        }

        builder.buildAsync(player)
            .exceptionally(openError -> {
                logger.log(Level.SEVERE, "Failed to open perks for " + player.getUniqueId(), openError);
                player.sendMessage("§cCould not load your perks right now.");
                return null;
            });
    }

    private void openHubFromBack(Player player) {
        openHub(player, ignored -> {
        });
    }

    private MenuButton buildPerkButton(UnlockableDefinition definition, PlayerUnlockable perk, long shards, int slot) {
        boolean locked = !perk.unlocked();
        boolean canUpgrade = perk.tier() < definition.maxTier();
        boolean toggleable = definition.toggleable();
        long nextCost = definition.tier(Math.min(perk.tier() + 1, definition.maxTier()))
            .map(UnlockableTier::costInShards)
            .orElse(0L);

        String namePrefix = locked ? "&c" : "&a";
        String displayName = locked
            ? "&c&m" + definition.name() + "&r &8(&c\uD83D\uDD12&8)" // strikethrough locked name with lock
            : namePrefix + definition.name();

        MenuButton.Builder builder = MenuButton.builder(definition.displayMaterial())
            .name(displayName)
            .secondary(definition.singleTier() ? "Single Unlock" : "Tiered")
            .description(definition.description())
            .slot(slot)
            .sound(Sound.UI_BUTTON_CLICK)
            .skipClickPrompt()
            .onClick(viewer -> handlePerkClick(viewer, definition, perk));

        List<String> lore = new ArrayList<>();
        if (definition.singleTier()) {
            lore.add(perk.unlocked()
                ? "&7Status: &aUnlocked"
                : "&7Status: &cLocked");
        } else {
            lore.add(perk.unlocked()
                ? "&7Status: &aUnlocked &7(Tier " + perk.tier() + "/" + definition.maxTier() + ")"
                : "&7Status: &cLocked");
        }
        if (canUpgrade) {
            lore.add("&7Next cost: &b" + nextCost + " shards &7(You have &b" + shards + "&7)");
        }
        if (locked || canUpgrade) {
            lore.add("&eClick to unlock" + (definition.singleTier() ? "" : " next tier") + ".");
        } else if (toggleable) {
            lore.add(perk.enabled() ? "&7Click to disable." : "&7Click to enable.");
        } else {
            lore.add("&7Maxed out.");
        }
        if (toggleable && perk.unlocked()) {
            lore.add("&7Toggle: " + (perk.enabled() ? "&aOn" : "&cOff"));
        }

        lore.forEach(builder::lore);

        return builder.build();
    }

    private void handlePerkClick(Player player, UnlockableDefinition definition, PlayerUnlockable perk) {
        UUID playerId = player.getUniqueId();
        boolean tieredCategory = !definition.singleTier();
        if (!perk.unlocked() || perk.tier() < definition.maxTier()) {
            unlockableService.unlockNextTier(playerId, definition.id())
                .whenComplete((updated, throwable) -> {
                    if (throwable != null) {
                        player.sendMessage("§c" + cleanError(throwable));
                        return;
                    }
                    player.sendMessage("§aUnlocked tier " + updated.tier() + " of " + definition.name() + ".");
                    openCategory(player, tieredCategory);
                });
            return;
        }

        if (definition.toggleable()) {
            unlockableService.toggle(playerId, definition.id())
                .whenComplete((updated, throwable) -> {
                    if (throwable != null) {
                        player.sendMessage("§c" + cleanError(throwable));
                        return;
                    }
                    player.sendMessage(updated.enabled()
                        ? "§aEnabled " + definition.name() + "."
                        : "§eDisabled " + definition.name() + ".");
                    openCategory(player, tieredCategory);
                });
            return;
        }

        player.sendMessage("§eYou already own all tiers of this perk.");
    }

    private MenuDisplayItem emptyPerkSlot(int slot) {
        return MenuDisplayItem.builder(Material.GRAY_STAINED_GLASS_PANE)
            .name("&8Empty Perk Slot")
            .secondary("")
            .description("&7Future perk will land here.")
            .slot(slot)
            .build();
    }

    private String cleanError(Throwable throwable) {
        Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        return root == null ? "Unknown error" : root.getMessage();
    }

    private String progressLine(long unlocked, int total) {
        if (total <= 0) {
            return "&7No perks in this category.";
        }
        long percent = Math.round((unlocked / (double) total) * 100);
        return "&7Unlocked: &b" + unlocked + "&7/&b" + total + " &7(" + percent + "%)";
    }

    private record PerkMenuState(sh.harold.fulcrum.plugin.unlockable.PlayerUnlockableState state, long shards) {
    }
}
