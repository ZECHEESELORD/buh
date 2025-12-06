package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PerkMenuView {

    private static final int HUB_ROWS = 6;

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final UnlockableService unlockableService;
    private final UnlockableRegistry unlockableRegistry;
    private final DocumentCollection players;
    private final Logger logger;
    private Consumer<Player> hubBackAction = player -> {
    };

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
        this.hubBackAction = backAction != null ? backAction : viewer -> {
        };
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
        Consumer<Player> safeBack = backAction != null ? backAction : hubBackAction;
        List<UnlockableDefinition> singleUnlocks = unlockableRegistry.definitions(UnlockableType.PERK).stream()
            .filter(UnlockableDefinition::singleTier)
            .toList();
        List<UnlockableDefinition> tieredUnlocks = unlockableRegistry.definitions(UnlockableType.PERK).stream()
            .filter(definition -> !definition.singleTier())
            .toList();

        long unlockedSingle = state.unlocked().stream()
            .filter(perk -> perk.definition().type() == UnlockableType.PERK && perk.definition().singleTier())
            .count();
        long unlockedTiered = state.unlocked().stream()
            .filter(perk -> perk.definition().type() == UnlockableType.PERK && !perk.definition().singleTier())
            .count();

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .description("Return to the player menu.")
            .slot(MenuButton.getBackSlot(HUB_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(safeBack::accept)
            .build();

        MenuButton permanentButton = MenuButton.builder(Material.CHEST)
            .name("&bPermanent Perks")
            .secondary("Single Unlocks")
            .description(progressLine(unlockedSingle, singleUnlocks.size()))
            .slot(20)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openCategoryList(viewer, false))
            .build();

        MenuButton tieredButton = MenuButton.builder(Material.ANVIL)
            .name("&dTiered Perks")
            .secondary("Progressive Upgrades")
            .description(progressLine(unlockedTiered, tieredUnlocks.size()))
            .slot(24)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openCategoryList(viewer, true))
            .build();

        menuService.createMenuBuilder()
            .title("Perks")
            .rows(HUB_ROWS)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
            .addButton(MenuButton.createPositionedClose(HUB_ROWS))
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

    private void openCategoryList(Player player, boolean tiered) {
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
                renderList(player, state, tiered);
            });
    }

    private void renderList(Player player, PerkMenuState state, boolean tiered) {
        List<UnlockableDefinition> definitions = tiered
            ? unlockableRegistry.definitions(UnlockableType.PERK).stream().filter(def -> !def.singleTier()).toList()
            : unlockableRegistry.definitions(UnlockableType.PERK).stream().filter(UnlockableDefinition::singleTier).toList();

        MenuButton back = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .description("Return to perk categories.")
            .slot(MenuButton.getBackSlot(6))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openHubFromBack)
            .build();

        menuService.createListMenu()
            .title(tiered ? "Tiered Perks" : "Permanent Perks")
            .rows(6)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .contentSlots(10, 43)
            .addButton(MenuButton.createPositionedClose(6))
            .addButton(back)
            .addItems(definitions, definition -> {
                PlayerUnlockable perk = state.state().unlockable(definition.id())
                    .orElse(new PlayerUnlockable(definition, 0, false));
                return buildPerkButton(definition, perk, state.shards());
            })
            .buildAsync(player)
            .exceptionally(openError -> {
                logger.log(Level.SEVERE, "Failed to open perks for " + player.getUniqueId(), openError);
                player.sendMessage("§cCould not load your perks right now.");
                return null;
            });
    }

    private void openHubFromBack(Player player) {
        openHub(player, hubBackAction);
    }

    private MenuButton buildPerkButton(UnlockableDefinition definition, PlayerUnlockable perk, long shards) {
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
            .sound(Sound.UI_BUTTON_CLICK)
            .skipClickPrompt()
            .onClick(viewer -> handlePerkClick(viewer, definition, perk));
        if (perk.enabled()) {
            builder.glow(true);
        }

        List<String> lore = new ArrayList<>();
        lore.add("&f");
        lore.add(definition.singleTier()
            ? (perk.unlocked() ? "&7Status: &aUnlocked" : "&7Status: &cLocked")
            : "&7Status: " + (perk.unlocked() ? "&aUnlocked " : "&cLocked ") + "&7(Tier " + perk.tier() + "/" + definition.maxTier() + ")");
        if (toggleable && perk.unlocked()) {
            lore.add("&f");
            lore.add("&7Toggle: " + (perk.enabled() ? "&aEnabled" : "&cDisabled"));
        }
        if (canUpgrade) {
            lore.add("&f");
            lore.add("&7Cost: &3" + nextCost + " Shards");
        }
        lore.add("&f");
        if (locked || canUpgrade) {
            lore.add("&eClick to unlock" + (definition.singleTier() ? "" : " next tier") + ".");
        } else if (toggleable) {
            lore.add(perk.enabled() ? "&7Click to disable." : "&7Click to enable.");
        } else {
            lore.add("&7Maxed out.");
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
                    openCategoryList(player, tieredCategory);
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
                    openCategoryList(player, tieredCategory);
                });
            return;
        }

        player.sendMessage("§eYou already own all tiers of this perk.");
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
