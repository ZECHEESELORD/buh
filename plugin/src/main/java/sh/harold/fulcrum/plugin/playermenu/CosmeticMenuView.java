package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.plugin.unlockable.Cosmetic;
import sh.harold.fulcrum.plugin.unlockable.CosmeticRegistry;
import sh.harold.fulcrum.plugin.unlockable.CosmeticSection;
import sh.harold.fulcrum.plugin.unlockable.PlayerUnlockable;
import sh.harold.fulcrum.plugin.unlockable.PlayerUnlockableState;
import sh.harold.fulcrum.plugin.unlockable.UnlockableId;
import sh.harold.fulcrum.plugin.unlockable.UnlockableDefinition;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CosmeticMenuView {

    private static final int HUB_ROWS = 6;
    private static final int SECTION_ROWS = 6;

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final UnlockableService unlockableService;
    private final CosmeticRegistry cosmeticRegistry;
    private final Logger logger;
    private final Consumer<Player> menuItemRefresher;
    private Consumer<Player> hubBackAction = player -> {
    };

    CosmeticMenuView(
        JavaPlugin plugin,
        MenuService menuService,
        UnlockableService unlockableService,
        CosmeticRegistry cosmeticRegistry,
        Consumer<Player> menuItemRefresher
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.cosmeticRegistry = Objects.requireNonNull(cosmeticRegistry, "cosmeticRegistry");
        this.logger = plugin.getLogger();
        this.menuItemRefresher = menuItemRefresher != null ? menuItemRefresher : player -> {
        };
    }

    void openHub(Player player, Consumer<Player> backAction) {
        UUID playerId = player.getUniqueId();
        this.hubBackAction = backAction != null ? backAction : hubBackAction;
        unlockableService.loadState(playerId).whenComplete((state, throwable) -> {
            if (throwable != null) {
                logger.log(Level.SEVERE, "Failed to open cosmetics for " + playerId, throwable);
                player.sendMessage("§cCould not load your cosmetics right now.");
                return;
            }
            renderHub(player, state, backAction);
        });
    }

    private void renderHub(Player player, PlayerUnlockableState state, Consumer<Player> backAction) {
        Consumer<Player> safeBack = backAction != null ? backAction : hubBackAction;

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to the player menu.")
            .slot(MenuButton.getBackSlot(HUB_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(safeBack::accept)
            .build();

        MenuButton menuSkinButton = buildSectionButton(CosmeticSection.PLAYER_MENU_SKIN, state, 20);
        MenuButton actionsButton = buildSectionButton(CosmeticSection.ACTIONS, state, 21);
        MenuButton chatPrefixButton = buildSectionButton(CosmeticSection.CHAT_PREFIX, state, 22);
        MenuButton statusButton = buildSectionButton(CosmeticSection.STATUS, state, 24);

        menuService.createMenuBuilder()
            .title("Cosmetics")
            .rows(HUB_ROWS)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
            .addButton(MenuButton.createPositionedClose(HUB_ROWS))
            .addButton(backButton)
            .addButton(menuSkinButton)
            .addButton(actionsButton)
            .addButton(chatPrefixButton)
            .addButton(statusButton)
            .buildAsync(player)
            .exceptionally(openError -> {
                logger.log(Level.SEVERE, "Failed to open cosmetics for " + player.getUniqueId(), openError);
                player.sendMessage("§cCould not load your cosmetics right now.");
                return null;
            });
    }

    private MenuButton buildSectionButton(CosmeticSection section, PlayerUnlockableState state, int slot) {
        List<Cosmetic> cosmetics = cosmeticRegistry.cosmetics(section);
        long unlocked = cosmetics.stream()
            .filter(cosmetic -> state.unlockable(cosmetic.id()).map(PlayerUnlockable::unlocked).orElse(false))
            .count();
        boolean equipped = !state.equippedCosmetics(section).isEmpty();
        String description = cosmetics.isEmpty()
            ? "No cosmetics are available here yet."
            : "Unlocked: " + unlocked + "/" + cosmetics.size();

        MenuButton.Builder builder = MenuButton.builder(sectionIcon(section))
            .name(sectionName(section))
            .secondary(sectionSecondary(section))
            .description(description)
            .slot(slot)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> openSection(viewer, section));

        sectionLore(state, section, cosmetics).forEach(builder::lore);

        return builder.build();
    }

    private void openSection(Player player, CosmeticSection section) {
        UUID playerId = player.getUniqueId();
        unlockableService.loadState(playerId).whenComplete((state, throwable) -> {
            if (throwable != null) {
                logger.log(Level.SEVERE, "Failed to open cosmetics for " + playerId, throwable);
                player.sendMessage("§cCould not load your cosmetics right now.");
                return;
            }
            renderSection(player, section, state);
        });
    }

    private void renderSection(Player player, CosmeticSection section, PlayerUnlockableState state) {
        List<Cosmetic> cosmetics = cosmeticRegistry.cosmetics(section);
        boolean hasEquipped = !state.equippedCosmetics(section).isEmpty();

        MenuButton back = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Cosmetics")
            .description("Return to cosmetic categories.")
            .slot(MenuButton.getBackSlot(SECTION_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openHubFromBack)
            .build();

        MenuButton clear = MenuButton.builder(Material.BARRIER)
            .name("&cClear " + sectionSecondary(section))
            .secondary(sectionSecondary(section))
            .description(hasEquipped ? "Remove your current selection." : "Nothing equipped here yet.")
            .slot(MenuButton.getBackSlot(SECTION_ROWS) + 1)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> clearCosmetic(viewer, section))
            .build();

        menuService.createListMenu()
            .title(sectionTitle(section))
            .rows(SECTION_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .contentSlots(10, 43)
            .addButton(MenuButton.createPositionedClose(SECTION_ROWS))
            .addButton(back)
            .addButton(clear)
            .addItems(cosmetics, cosmetic -> buildCosmeticButton(cosmetic, state))
            .emptyMessage(Component.text("No cosmetics available for this section yet."))
            .buildAsync(player)
            .exceptionally(openError -> {
                logger.log(Level.SEVERE, "Failed to open cosmetics for " + player.getUniqueId(), openError);
                player.sendMessage("§cCould not load your cosmetics right now.");
                return null;
            });
    }

    private MenuButton buildCosmeticButton(Cosmetic cosmetic, PlayerUnlockableState state) {
        UnlockableDefinition definition = cosmetic.definition();
        PlayerUnlockable unlockable = state.unlockable(definition.id())
            .orElse(new PlayerUnlockable(definition, 0, false));
        boolean unlocked = unlockable.unlocked();
        boolean equipped = state.equippedCosmetics(cosmetic.section()).contains(definition.id());
        long cost = definition.tier(1).map(UnlockableTier::costInShards).orElse(0L);

        String displayName = unlocked
            ? "&a" + definition.name()
            : "&c&m" + definition.name() + "&r &8(&c\uD83D\uDD12&8)";

        MenuButton.Builder builder = MenuButton.builder(definition.displayMaterial())
            .name(displayName)
            .secondary(sectionSecondary(cosmetic.section()))
            .description(definition.description())
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> {
                if (unlockable.unlocked()) {
                    handleCosmeticToggle(viewer, cosmetic, equipped);
                } else {
                    handleUnlock(viewer, cosmetic);
                }
            });

        if (equipped) {
            builder.glow(true);
        }

        List<String> lore = new ArrayList<>();
        lore.add("&f");
        if (!unlocked) {
            lore.add("&7Cost: &3" + cost + " Shards");
        } else {
            lore.add("&7Status: " + (equipped ? "&aEquipped" : "&cNot Equipped"));
        }
        lore.forEach(builder::lore);

        return builder.build();
    }

    private void handleCosmeticToggle(Player player, Cosmetic cosmetic, boolean equipped) {
        UUID playerId = player.getUniqueId();
        if (cosmetic.section() == CosmeticSection.ACTIONS) {
            if (equipped) {
                unlockableService.removeActionCosmetic(playerId, cosmetic.id())
                    .whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            player.sendMessage("§c" + cleanError(throwable));
                            return;
                        }
                        player.sendMessage("§eUnequipped " + cosmetic.definition().name() + ".");
                        openSection(player, cosmetic.section());
                    });
                return;
            }
        } else if (equipped) {
            clearCosmetic(player, cosmetic.section());
            return;
        }

        unlockableService.equipCosmetic(playerId, cosmetic.section(), cosmetic.id())
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    player.sendMessage("§c" + cleanError(throwable));
                    return;
                }
                player.sendMessage("§aEquipped " + cosmetic.definition().name() + ".");
                if (cosmetic.section() == CosmeticSection.PLAYER_MENU_SKIN) {
                    menuItemRefresher.accept(player);
                }
                openSection(player, cosmetic.section());
            });
    }

    private void handleUnlock(Player player, Cosmetic cosmetic) {
        UUID playerId = player.getUniqueId();
        UnlockableId id = cosmetic.id();
        unlockableService.unlockToTier(playerId, id, 1)
            .whenComplete((updated, throwable) -> {
                if (throwable != null) {
                    player.sendMessage("§c" + cleanError(throwable));
                    return;
                }
                player.sendMessage("§aUnlocked " + updated.definition().name() + ".");
                openSection(player, cosmetic.section());
            });
    }

    private void clearCosmetic(Player player, CosmeticSection section) {
        UUID playerId = player.getUniqueId();
        unlockableService.clearCosmetic(playerId, section)
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    player.sendMessage("§c" + cleanError(throwable));
                    return;
                }
                player.sendMessage("§aCleared your " + sectionSecondary(section).toLowerCase() + ".");
                if (section == CosmeticSection.PLAYER_MENU_SKIN) {
                    menuItemRefresher.accept(player);
                }
                openSection(player, section);
            });
    }

    private void openHubFromBack(Player player) {
        openHub(player, hubBackAction);
    }

    private Material sectionIcon(CosmeticSection section) {
        if (section == CosmeticSection.PLAYER_MENU_SKIN) {
            return Material.NETHER_STAR;
        }
        return cosmeticRegistry.cosmetics(section).stream()
            .findFirst()
            .map(cosmetic -> cosmetic.definition().displayMaterial())
            .orElse(defaultSectionMaterial(section));
    }

    private Material defaultSectionMaterial(CosmeticSection section) {
        return switch (section) {
            case PLAYER_MENU_SKIN -> Material.NETHER_STAR;
            case PARTICLE_TRAIL -> Material.FIREWORK_ROCKET;
            case CHAT_PREFIX -> Material.NAME_TAG;
            case STATUS -> Material.PAPER;
            case ACTIONS -> Material.MAGMA_CREAM;
        };
    }

    private String sectionName(CosmeticSection section) {
        return switch (section) {
            case PLAYER_MENU_SKIN -> "&aPlayer Menu Skins";
            case PARTICLE_TRAIL -> "&bParticle Trails";
            case CHAT_PREFIX -> "&dChat Prefixes";
            case STATUS -> "&eStatus Lines";
            case ACTIONS -> "&6Actions & Emotes";
        };
    }

    private String sectionSecondary(CosmeticSection section) {
        return switch (section) {
            case PLAYER_MENU_SKIN -> "Player Menu Skin";
            case PARTICLE_TRAIL -> "Particle Trail";
            case CHAT_PREFIX -> "Chat Prefix";
            case STATUS -> "Status";
            case ACTIONS -> "Action/Emote";
        };
    }

    private String sectionTitle(CosmeticSection section) {
        return switch (section) {
            case PLAYER_MENU_SKIN -> "Player Menu Skins";
            case PARTICLE_TRAIL -> "Particle Trails";
            case CHAT_PREFIX -> "Chat Prefixes";
            case STATUS -> "Status Lines";
            case ACTIONS -> "Actions & Emotes";
        };
    }

    private List<String> sectionLore(PlayerUnlockableState state, CosmeticSection section, List<Cosmetic> cosmetics) {
        List<String> lore = new ArrayList<>();
        lore.add("&f");
        state.equippedCosmetics(section).stream()
            .findFirst()
            .flatMap(id -> cosmetics.stream().filter(cosmetic -> cosmetic.id().equals(id)).findFirst())
            .ifPresentOrElse(
                equipped -> lore.add("&7Equipped: &a" + equipped.definition().name()),
                () -> lore.add("&7Equipped: &cNone")
            );
        return lore;
    }

    private String cleanError(Throwable throwable) {
        Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        return root == null ? "unknown error" : root.getMessage();
    }
}
