package sh.harold.fulcrum.plugin.playermenu;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.plugin.playerdata.LevelProgress;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.logging.Level;

final class LevelMenuView {

    private static final int ROWS = 6;
    private static final int PROGRESS_SLOT = 13;

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
        MenuDisplayItem progressItem = MenuDisplayItem.builder(Material.EXPERIENCE_BOTTLE)
            .name("&aLevel " + progress.level())
            .secondary("Progression")
            .description("Keep earning XP; each level opens new momentum.")
            .lore("")
            .lore(PlayerMenuService.progressBlock(progress).toArray(String[]::new))
            .slot(PROGRESS_SLOT)
            .build();

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to your player menu hub.")
            .slot(MenuButton.getBackSlot(ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(backToMenu)
            .build();

        MenuButton closeButton = MenuButton.createPositionedClose(ROWS);

        CompletableFuture<Void> openFuture = new CompletableFuture<>();
        try {
            menuService.createMenuBuilder()
                .title("Level Overview")
                .rows(ROWS)
                .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
                .addButton(closeButton)
                .addButton(backButton)
                .addItem(progressItem, PROGRESS_SLOT)
                .buildAsync(player)
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

    private CompletionStage<LevelProgress> loadProgress(UUID playerId) {
        return levelingService.loadProgress(playerId)
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, "Failed to load level progress for " + playerId, throwable);
                return levelingService.progressFor(0L);
            });
    }
}
