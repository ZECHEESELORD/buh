package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CompendiumMenuView {

    private static final int HUB_ROWS = 6;
    private static final int ENCHANT_ROWS = 6;

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final EnchantRegistry enchantRegistry;
    private final Logger logger;
    private Consumer<Player> hubBackAction = player -> {
    };

    CompendiumMenuView(JavaPlugin plugin, MenuService menuService, EnchantRegistry enchantRegistry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.enchantRegistry = enchantRegistry;
        this.logger = plugin.getLogger();
    }

    void openHub(Player player, Consumer<Player> backAction) {
        Objects.requireNonNull(player, "player");
        this.hubBackAction = backAction != null ? backAction : hubBackAction;
        renderHub(player, this.hubBackAction);
    }

    private void renderHub(Player player, Consumer<Player> backAction) {
        int entryCount = enchantRegistry == null ? 0 : enchantRegistry.ids().size();
        String description = entryCount == 0
            ? "No entries are ready yet; the shelves are waiting."
            : entryCount == 1
                ? "Browse 1 enchantment, from classics to custom craft."
                : "Browse " + entryCount + " enchantments, from classics to custom craft.";

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Player Menu")
            .description("Return to the player menu.")
            .slot(MenuButton.getBackSlot(HUB_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(backAction::accept)
            .build();

        MenuButton enchantmentsButton = MenuButton.builder(Material.ENCHANTED_BOOK)
            .name("&dEnchantment Compendium")
            .secondary("Knowledge")
            .description(description)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openEnchantments)
            .build();

        menuService.createListMenu()
            .title("Compendium")
            .rows(HUB_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .addButton(MenuButton.createPositionedClose(HUB_ROWS))
            .addButton(backButton)
            .addItems(List.of(enchantmentsButton))
            .emptyMessage(Component.text("No compendium entries yet; the shelves are quiet.", NamedTextColor.GRAY))
            .buildAsync(player)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open compendium for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("The compendium is resting; try again soon.", NamedTextColor.RED));
                return null;
            });
    }

    private void openEnchantments(Player player) {
        Objects.requireNonNull(player, "player");
        List<EnchantEntry> entries = loadEnchantEntries();

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Compendium")
            .description("Return to the compendium shelf.")
            .slot(MenuButton.getBackSlot(ENCHANT_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> renderHub(viewer, hubBackAction))
            .build();

        menuService.createListMenu()
            .title("Enchantments")
            .rows(ENCHANT_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .contentSlots(10, 43)
            .addButton(MenuButton.createPositionedClose(ENCHANT_ROWS))
            .addButton(backButton)
            .addItems(entries, this::buildEnchantButton)
            .emptyMessage(Component.text("No enchantments are ready yet; the ink has not dried.", NamedTextColor.GRAY))
            .buildAsync(player)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open enchantments for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("The enchantment shelves are closed for now; try again soon.", NamedTextColor.RED));
                return null;
            });
    }

    private List<EnchantEntry> loadEnchantEntries() {
        if (enchantRegistry == null) {
            return List.of();
        }
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        return enchantRegistry.ids().stream()
            .flatMap(id -> enchantRegistry.get(id).stream())
            .map(definition -> {
                String name = plain.serialize(definition.displayName()).trim();
                if (name.isBlank()) {
                    name = definition.id();
                }
                String description = resolveDescription(definition, plain);
                return new EnchantEntry(definition, name, description);
            })
            .sorted(Comparator.comparing(EnchantEntry::sortKey, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private MenuButton buildEnchantButton(EnchantEntry entry) {
        EnchantDefinition definition = entry.definition();
        return MenuButton.builder(Material.ENCHANTED_BOOK)
            .name(definition.displayName().decoration(TextDecoration.ITALIC, false))
            .secondary("Enchantment")
            .description(entry.description())
            .lore("&7Max level: &f" + definition.maxLevel())
            .build();
    }

    private String resolveDescription(EnchantDefinition definition, PlainTextComponentSerializer plain) {
        String description = plain.serialize(definition.description()).trim();
        return description.isBlank()
            ? "No notes yet; more details will land soon."
            : description;
    }

    private record EnchantEntry(EnchantDefinition definition, String sortKey, String description) {
    }
}
