package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.menu.impl.DefaultCustomMenu;
import sh.harold.fulcrum.plugin.playerdata.LevelProgress;
import sh.harold.fulcrum.plugin.playerdata.LevelTier;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Level;

final class LevelMenuView {

    private static final int ROWS = 6;
    private static final int PROGRESS_SLOT = 4;
    private static final int TIER_INFO_SLOT = 50;
    private static final int[] TIER_SLOTS = {11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 25, 31};
    private static final int LEVEL_ROW = 2;
    private static final int LEVEL_MIN_OFFSET = 1;
    private static final int LEVEL_START_COLUMN = LEVEL_MIN_OFFSET + 1;
    private static final Material UNLOCKED_PANE = Material.GREEN_STAINED_GLASS_PANE;
    private static final Material CURRENT_PANE = Material.YELLOW_STAINED_GLASS_PANE;
    private static final Material LOCKED_PANE = Material.RED_STAINED_GLASS_PANE;

    private final Plugin plugin;
    private final MenuService menuService;
    private final PlayerLevelingService levelingService;
    private final Consumer<Player> backToMenu;

    LevelMenuView(Plugin plugin, MenuService menuService, PlayerLevelingService levelingService, Consumer<Player> backToMenu) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.levelingService = Objects.requireNonNull(levelingService, "levelingService");
        this.backToMenu = Objects.requireNonNull(backToMenu, "backToMenu");
    }

    void open(Player player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUniqueId();
        loadProgress(playerId)
            .thenCompose(progress -> openMenu(player, progress))
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to open levels menu for " + playerId, throwable);
                player.sendMessage("§cLevels are snoozing; try again soon.");
                return null;
            });
    }

    private CompletionStage<Void> openMenu(Player player, LevelProgress progress) {
        MenuButton progressItem = MenuButton.builder(Material.EXPERIENCE_BOTTLE)
            .name("&aLevel " + progress.level())
            .secondary("Progression")
            .description("Keep earning XP; each level opens new momentum.")
            .lore("")
            .lore(PlayerMenuService.progressBlock(progress).toArray(String[]::new))
            .slot(PROGRESS_SLOT)
            .anchor(true)
            .build();

        TierRange tierRange = resolveTierRange(progress);
        int virtualColumns = Math.max(9, tierRange.totalLevels() + 3);
        int scrollLeftSlot = (ROWS - 1) * 9;
        int scrollRightSlot = (ROWS - 1) * 9 + 8;

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to your player menu hub.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(backToMenu)
            .build();

        MenuButton tierInfoButton = MenuButton.builder(Material.BOOK)
            .name("&bLevel Colors")
            .secondary("Progression")
            .description("Levels show your journey; XP keeps them climbing. Colors mark your tier; each range has its own shade.")
            .slot(TIER_INFO_SLOT)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openTierMenuClick)
            .build();

        MenuButton closeButton = MenuButton.createPositionedClose(ROWS);

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            var builder = menuService.createMenuBuilder()
                .title("Level Overview")
                .viewPort(ROWS)
                .rows(ROWS)
                .columns(virtualColumns)
                .initialOffset(0, LEVEL_MIN_OFFSET)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addScrollButtons(-1, -1, scrollLeftSlot, scrollRightSlot)
                .addButton(closeButton)
                .addButton(backButton)
                .addButton(tierInfoButton)
                .addButton(progressItem);

            buildProgressItems(progress, tierRange).forEach(item -> builder.addItem(item.item(), item.row(), item.column()));
            addBottomRowFillers(builder, scrollLeftSlot, scrollRightSlot, backButton.getSlot(), closeButton.getSlot(), tierInfoButton.getSlot());

            builder.buildAsync(player)
                .thenAccept(menu -> {
                    if (menu instanceof DefaultCustomMenu customMenu) {
                        MenuButton scrollLeft = MenuButton.builder(Material.ARROW)
                            .name("&eScroll Left")
                            .skipClickPrompt()
                            .sound(Sound.UI_BUTTON_CLICK)
                            .onClick(viewer -> {
                                int columnOffset = customMenu.getContext()
                                    .getProperty("viewportColumnOffset", Integer.class)
                                    .orElse(0);
                                if (columnOffset <= LEVEL_MIN_OFFSET) {
                                    return;
                                }
                                customMenu.scrollViewport(0, -1);
                            })
                            .build();
                        MenuButton scrollRight = MenuButton.builder(Material.ARROW)
                            .name("&eScroll Right")
                            .skipClickPrompt()
                            .sound(Sound.UI_BUTTON_CLICK)
                            .onClick(viewer -> customMenu.scrollViewport(0, 1))
                            .build();
                        customMenu.setCustomScrollButtons(null, null, scrollLeft, scrollRight);
                        customMenu.update();
                    }
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        openFuture.completeExceptionally(throwable);
                        return;
                    }
                    openFuture.complete(null);
                });
        } catch (Throwable throwable) {
            openFuture.completeExceptionally(throwable);
        }
        return openFuture;
    }

    private void openTierMenuClick(Player player) {
        loadProgress(player.getUniqueId())
            .thenApply(progress -> resolveTier(progress.level()))
            .thenCompose(tier -> openTierMenu(player, tier))
            .exceptionally(throwable -> {
                player.sendMessage("§cCould not open level colors; try again soon.");
                return null;
            });
    }

    private CompletionStage<Void> openTierMenu(Player player, LevelTier activeTier) {
        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Level Overview")
            .description("Return to the level overview.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::open)
            .build();

        MenuButton closeButton = MenuButton.createPositionedClose(ROWS);

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            var builder = menuService.createMenuBuilder()
                .title("Level Colors")
                .rows(ROWS)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addButton(closeButton)
                .addButton(backButton);

            buildTierButtons(activeTier).forEach(builder::addButton);

            builder.buildAsync(player)
                .whenComplete((menu, throwable) -> {
                    if (throwable != null) {
                        openFuture.completeExceptionally(throwable);
                        return;
                    }
                    openFuture.complete(null);
                });
        } catch (Throwable throwable) {
            openFuture.completeExceptionally(throwable);
        }
        return openFuture;
    }

    private java.util.List<MenuButton> buildTierButtons(LevelTier activeTier) {
        LevelTier[] tiers = LevelTier.values();
        java.util.List<MenuButton> items = new java.util.ArrayList<>();
        int maxLevel = levelingService.maxLevel();
        for (int index = 0; index < tiers.length && index < TIER_SLOTS.length; index++) {
            LevelTier tier = tiers[index];
            int min = tier.minLevel();
            int max = index + 1 < tiers.length ? tiers[index + 1].minLevel() - 1 : maxLevel;
            MenuButton item = MenuButton.builder(dyeForTier(tier))
                .name(tierDisplayName(tier))
                .secondary(tierRangeLabel(min, max))
                .description("Tint used for your level prefix and name.")
                .slot(TIER_SLOTS[index])
                .sound(Sound.UI_BUTTON_CLICK)
                .glow(tier == activeTier)
                .onClick(viewer -> openTierPreviewMenuClick(viewer, tier))
                .build();
            items.add(item);
        }
        return items;
    }

    private void openTierPreviewMenuClick(Player player, LevelTier tier) {
        loadProgress(player.getUniqueId())
            .thenCompose(progress -> openTierPreviewMenu(player, tier, progress))
            .exceptionally(throwable -> {
                player.sendMessage("§cCould not open that tier preview; try again soon.");
                return null;
            });
    }

    private CompletionStage<Void> openTierPreviewMenu(Player player, LevelTier tier, LevelProgress progress) {
        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Level Colors")
            .description("Return to the color overview.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openTierMenuClick)
            .build();

        MenuButton closeButton = MenuButton.createPositionedClose(ROWS);

        java.util.List<MenuDisplayItem> previewItems = buildTierPreviewItems(tier, progress.level());
        int virtualColumns = Math.max(9, LEVEL_START_COLUMN + previewItems.size() + LEVEL_MIN_OFFSET);
        int scrollLeftSlot = (ROWS - 1) * 9;
        int scrollRightSlot = (ROWS - 1) * 9 + 8;

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            var builder = menuService.createMenuBuilder()
                .title("Level Color Preview")
                .viewPort(ROWS)
                .rows(ROWS)
                .columns(virtualColumns)
                .initialOffset(0, LEVEL_MIN_OFFSET)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addScrollButtons(-1, -1, scrollLeftSlot, scrollRightSlot)
                .addButton(closeButton)
                .addButton(backButton);

            int column = LEVEL_START_COLUMN;
            for (MenuDisplayItem item : previewItems) {
                builder.addItem(item, LEVEL_ROW, column++);
            }
            addBottomRowFillers(builder, scrollLeftSlot, scrollRightSlot, backButton.getSlot(), closeButton.getSlot());

            builder.buildAsync(player)
                .thenAccept(menu -> {
                    if (menu instanceof DefaultCustomMenu customMenu) {
                        MenuButton scrollLeft = MenuButton.builder(Material.ARROW)
                            .name("&eScroll Left")
                            .skipClickPrompt()
                            .sound(Sound.UI_BUTTON_CLICK)
                            .onClick(viewer -> {
                                int columnOffset = customMenu.getContext()
                                    .getProperty("viewportColumnOffset", Integer.class)
                                    .orElse(0);
                                if (columnOffset <= LEVEL_MIN_OFFSET) {
                                    return;
                                }
                                customMenu.scrollViewport(0, -1);
                            })
                            .build();
                        MenuButton scrollRight = MenuButton.builder(Material.ARROW)
                            .name("&eScroll Right")
                            .skipClickPrompt()
                            .sound(Sound.UI_BUTTON_CLICK)
                            .onClick(viewer -> customMenu.scrollViewport(0, 1))
                            .build();
                        customMenu.setCustomScrollButtons(null, null, scrollLeft, scrollRight);
                        customMenu.update();
                    }
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        openFuture.completeExceptionally(throwable);
                        return;
                    }
                    openFuture.complete(null);
                });
        } catch (Throwable throwable) {
            openFuture.completeExceptionally(throwable);
        }
        return openFuture;
    }

    private java.util.List<VirtualItem> buildProgressItems(LevelProgress progress, TierRange tierRange) {
        int currentLevel = Math.max(0, progress.level());
        LevelTier currentTier = tierRange.tier();
        int minLevel = tierRange.minLevel();
        int maxLevel = tierRange.maxLevel();
        int totalLevels = tierRange.totalLevels();

        java.util.List<VirtualItem> items = new java.util.ArrayList<>();
        for (int index = 0; index < totalLevels; index++) {
            int levelValue = minLevel + index;
            boolean isCurrent = levelValue == currentLevel;
            boolean isUnlocked = levelValue < currentLevel;
            boolean isTierCap = levelValue == maxLevel;

            Material material = isTierCap ? dyeForTier(currentTier) : paneForStatus(isUnlocked, isCurrent);
            String nameColor = isTierCap ? tierLegacyColor(currentTier) : statusLegacyColor(isUnlocked, isCurrent);
            String secondary = statusLabel(isUnlocked, isCurrent);
            String description = isTierCap
                ? "Tier cap; reach this level to claim the next color."
                : statusDescription(isUnlocked, isCurrent, levelValue);

            MenuDisplayItem item = MenuDisplayItem.builder(material)
                .name(nameColor + "Level " + levelValue)
                .secondary(secondary)
                .description(description)
                .amount(index + 1)
                .build();
            items.add(new VirtualItem(item, LEVEL_ROW, LEVEL_START_COLUMN + index));
        }
        return items;
    }

    private TierRange resolveTierRange(LevelProgress progress) {
        int currentLevel = Math.max(0, progress.level());
        LevelTier resolved = LevelTier.DEFAULT;
        int safeLevel = Math.max(0, currentLevel);
        for (LevelTier tier : LevelTier.values()) {
            if (safeLevel >= tier.minLevel()) {
                resolved = tier;
            }
        }

        LevelTier[] tiers = LevelTier.values();
        int minLevel = resolved.minLevel();
        int maxLevel = levelingService.maxLevel();
        for (int index = 0; index < tiers.length; index++) {
            if (tiers[index] == resolved) {
                if (index + 1 < tiers.length) {
                    maxLevel = tiers[index + 1].minLevel() - 1;
                }
                break;
            }
        }
        int totalLevels = Math.max(0, maxLevel - minLevel + 1);
        return new TierRange(resolved, minLevel, maxLevel, totalLevels);
    }

    private Material paneForStatus(boolean unlocked, boolean current) {
        if (current) {
            return CURRENT_PANE;
        }
        return unlocked ? UNLOCKED_PANE : LOCKED_PANE;
    }

    private String statusLegacyColor(boolean unlocked, boolean current) {
        if (current) {
            return "&e";
        }
        return unlocked ? "&a" : "&c";
    }

    private String statusLabel(boolean unlocked, boolean current) {
        if (current) {
            return "Current Level";
        }
        return unlocked ? "Unlocked" : "Locked";
    }

    private String statusDescription(boolean unlocked, boolean current, int levelValue) {
        if (current) {
            return "You are here; earn XP to push onward.";
        }
        if (unlocked) {
            return "Already earned; the path keeps opening.";
        }
        return "Reach level " + levelValue + "; the path opens then.";
    }

    private String tierLegacyColor(LevelTier tier) {
        return switch (tier) {
            case DEFAULT -> "&7";
            case WHITE -> "&f";
            case YELLOW -> "&e";
            case GREEN -> "&a";
            case DARK_GREEN -> "&2";
            case AQUA -> "&b";
            case CYAN -> "&3";
            case BLUE -> "&9";
            case PINK -> "&d";
            case PURPLE -> "&5";
            case GOLD -> "&6";
            case RED -> "&c";
            case DARK_RED -> "&4";
        };
    }

    private Material dyeForTier(LevelTier tier) {
        return switch (tier) {
            case DEFAULT -> Material.GRAY_DYE;
            case WHITE -> Material.WHITE_DYE;
            case YELLOW -> Material.YELLOW_DYE;
            case GREEN -> Material.LIME_DYE;
            case DARK_GREEN -> Material.GREEN_DYE;
            case AQUA -> Material.LIGHT_BLUE_DYE;
            case CYAN -> Material.CYAN_DYE;
            case BLUE -> Material.BLUE_DYE;
            case PINK -> Material.PINK_DYE;
            case PURPLE -> Material.PURPLE_DYE;
            case GOLD -> Material.ORANGE_DYE;
            case RED -> Material.RED_DYE;
            case DARK_RED -> Material.BROWN_DYE;
        };
    }

    private Material paneForTier(LevelTier tier) {
        return switch (tier) {
            case DEFAULT -> Material.GRAY_STAINED_GLASS_PANE;
            case WHITE -> Material.WHITE_STAINED_GLASS_PANE;
            case YELLOW -> Material.YELLOW_STAINED_GLASS_PANE;
            case GREEN -> Material.LIME_STAINED_GLASS_PANE;
            case DARK_GREEN -> Material.GREEN_STAINED_GLASS_PANE;
            case AQUA -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case CYAN -> Material.CYAN_STAINED_GLASS_PANE;
            case BLUE -> Material.BLUE_STAINED_GLASS_PANE;
            case PINK -> Material.PINK_STAINED_GLASS_PANE;
            case PURPLE -> Material.PURPLE_STAINED_GLASS_PANE;
            case GOLD -> Material.ORANGE_STAINED_GLASS_PANE;
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case DARK_RED -> Material.BROWN_STAINED_GLASS_PANE;
        };
    }

    private String tierDisplayName(LevelTier tier) {
        return switch (tier) {
            case DEFAULT -> "&7Stone";
            case WHITE -> "&fQuartz";
            case YELLOW -> "&eTopaz";
            case GREEN -> "&aPeridot";
            case DARK_GREEN -> "&2Jade";
            case AQUA -> "&bAquamarine";
            case CYAN -> "&3Turquoise";
            case BLUE -> "&9Sapphire";
            case PINK -> "&dKunzite";
            case PURPLE -> "&5Amethyst";
            case GOLD -> "&6Amber";
            case RED -> "&cRuby";
            case DARK_RED -> "&4Garnet";
        };
    }

    private String tierRangeLabel(int min, int max) {
        return min == max ? "Level " + min : "Levels " + min + " to " + max;
    }

    private java.util.List<MenuDisplayItem> buildTierPreviewItems(LevelTier tier, int currentLevel) {
        LevelTier[] tiers = LevelTier.values();
        int min = tier.minLevel();
        int max = resolveTierMax(tier, tiers);
        int nextLevel = max + 1;
        LevelTier nextTier = resolveNextTier(tier, tiers);

        java.util.List<MenuDisplayItem> items = new java.util.ArrayList<>();
        items.add(MenuDisplayItem.builder(dyeForTier(tier))
            .name(tierDisplayName(tier) + " Dye")
            .secondary(tierRangeLabel(min, max))
            .description("Preview for this tier's color range.")
            .build());

        for (int level = min; level <= max; level++) {
            boolean isCurrent = level == currentLevel;
            boolean isUnlocked = level < currentLevel;
            MenuDisplayItem item = MenuDisplayItem.builder(paneForStatus(isUnlocked, isCurrent))
                .name(statusLegacyColor(isUnlocked, isCurrent) + "Level " + level)
                .secondary(statusLabel(isUnlocked, isCurrent))
                .description(statusDescription(isUnlocked, isCurrent, level))
                .amount(level - min + 1)
                .build();
            items.add(item);
        }

        if (nextTier != null) {
            items.add(MenuDisplayItem.builder(dyeForTier(nextTier))
                .name(tierLegacyColor(nextTier) + "Level " + nextLevel)
                .secondary("Next Tier")
                .description("First level of the next color bracket.")
                .build());
        }

        return items;
    }

    private int resolveTierMax(LevelTier tier, LevelTier[] tiers) {
        for (int index = 0; index < tiers.length; index++) {
            if (tiers[index] == tier) {
                return index + 1 < tiers.length ? tiers[index + 1].minLevel() - 1 : levelingService.maxLevel();
            }
        }
        return levelingService.maxLevel();
    }

    private LevelTier resolveNextTier(LevelTier tier, LevelTier[] tiers) {
        for (int index = 0; index < tiers.length; index++) {
            if (tiers[index] == tier) {
                return index + 1 < tiers.length ? tiers[index + 1] : null;
            }
        }
        return null;
    }

    private LevelTier resolveTier(int level) {
        LevelTier resolved = LevelTier.DEFAULT;
        int safeLevel = Math.max(0, level);
        for (LevelTier tier : LevelTier.values()) {
            if (safeLevel >= tier.minLevel()) {
                resolved = tier;
            }
        }
        return resolved;
    }

    private void addBottomRowFillers(
        sh.harold.fulcrum.api.menu.CustomMenuBuilder builder,
        int scrollLeftSlot,
        int scrollRightSlot,
        int... reserved
    ) {
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>();
        reservedSlots.add(scrollLeftSlot);
        reservedSlots.add(scrollRightSlot);
        for (int slot : reserved) {
            reservedSlots.add(slot);
        }
        int startSlot = (ROWS - 1) * 9;
        int endSlot = ROWS * 9;
        for (int slot = startSlot; slot < endSlot; slot++) {
            if (reservedSlots.contains(slot)) {
                continue;
            }
            MenuButton filler = MenuButton.builder(Material.BLACK_STAINED_GLASS_PANE)
                .name("")
                .skipClickPrompt()
                .slot(slot)
                .build();
            builder.addButton(filler);
        }
    }

    private record TierRange(LevelTier tier, int minLevel, int maxLevel, int totalLevels) {
    }

    private record VirtualItem(MenuDisplayItem item, int row, int column) {
    }

    private CompletionStage<LevelProgress> loadProgress(UUID playerId) {
        return levelingService.loadProgress(playerId)
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, "Failed to load level progress for " + playerId, throwable);
                return levelingService.progressFor(0L);
            });
    }
}
