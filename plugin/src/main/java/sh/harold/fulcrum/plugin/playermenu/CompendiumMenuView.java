package sh.harold.fulcrum.plugin.playermenu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

final class CompendiumMenuView {

    private static final int HUB_ROWS = 6;
    private static final int ENCHANT_ROWS = 6;
    private static final int BIOME_ROWS = 6;
    private static final String BIOME_ROOT = "travel.biomes";

    private final JavaPlugin plugin;
    private final MenuService menuService;
    private final EnchantRegistry enchantRegistry;
    private final DocumentCollection players;
    private final Logger logger;
    private Consumer<Player> hubBackAction = player -> {
    };

    CompendiumMenuView(JavaPlugin plugin, MenuService menuService, EnchantRegistry enchantRegistry, DocumentCollection players) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.enchantRegistry = enchantRegistry;
        this.players = players;
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
        MenuButton biomesButton = MenuButton.builder(Material.MAP)
            .name("&aExplored Biomes")
            .secondary("Knowledge")
            .description("See which biomes you have already charted.")
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(this::openBiomes)
            .build();

        menuService.createListMenu()
            .title("Compendium")
            .rows(HUB_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .showPageIndicator(false)
            .addButton(MenuButton.createPositionedClose(HUB_ROWS))
            .addButton(backButton)
            .addItems(List.of(enchantmentsButton, biomesButton))
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

    private void openBiomes(Player player) {
        Objects.requireNonNull(player, "player");
        if (players == null) {
            player.sendMessage(Component.text("Biome records are offline right now.", NamedTextColor.RED));
            return;
        }
        players.load(player.getUniqueId().toString())
            .thenAccept(document -> renderBiomes(player, document))
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open biomes for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("The biome atlas is missing a page; try again soon.", NamedTextColor.RED));
                return null;
            });
    }

    private void renderBiomes(Player player, Document document) {
        Set<String> discovered = discoveredBiomes(document);
        List<BiomeEntry> entries = loadBiomeEntries(discovered);

        MenuButton backButton = MenuButton.builder(Material.ARROW)
            .name("&7Back")
            .secondary("Compendium")
            .description("Return to the compendium shelf.")
            .slot(MenuButton.getBackSlot(BIOME_ROWS))
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> renderHub(viewer, hubBackAction))
            .build();

        menuService.createListMenu()
            .title("Explored Biomes")
            .rows(BIOME_ROWS)
            .addBorder(Material.BLACK_STAINED_GLASS_PANE)
            .contentSlots(10, 43)
            .addButton(MenuButton.createPositionedClose(BIOME_ROWS))
            .addButton(backButton)
            .addItems(entries, this::buildBiomeItem)
            .emptyMessage(Component.text("No biome records yet; step outside and explore.", NamedTextColor.GRAY))
            .buildAsync(player)
            .exceptionally(throwable -> {
                logger.log(Level.SEVERE, "Failed to open biome list for " + player.getUniqueId(), throwable);
                player.sendMessage(Component.text("The biome atlas is missing a page; try again soon.", NamedTextColor.RED));
                return null;
            });
    }

    private List<BiomeEntry> loadBiomeEntries(Set<String> discovered) {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        if (registry == null) {
            return List.of();
        }
        return StreamSupport.stream(registry.spliterator(), false)
            .map(biome -> biomeEntry(biome, discovered))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(BiomeEntry::sortKey, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private BiomeEntry biomeEntry(Biome biome, Set<String> discovered) {
        if (biome == null) {
            return null;
        }
        NamespacedKey key = biome.getKey();
        if (key == null) {
            return null;
        }
        String biomeId = key.asString();
        boolean isDiscovered = discovered.contains(biomeId);
        String displayName = humanizeBiomeName(key);
        String typeLabel = key.getNamespace().equalsIgnoreCase("minecraft") ? "Vanilla" : "Custom";
        return new BiomeEntry(displayName, typeLabel, isDiscovered);
    }

    private MenuDisplayItem buildBiomeItem(BiomeEntry entry) {
        Material material = entry.discovered() ? Material.GRASS_BLOCK : Material.GRAY_DYE;
        String description = entry.discovered() ? entry.typeLabel() : "Undiscovered";
        return MenuDisplayItem.builder(material)
            .name(entry.displayName())
            .secondary("Explored Biomes")
            .description(description)
            .build();
    }

    private Set<String> discoveredBiomes(Document document) {
        if (document == null) {
            return Set.of();
        }
        Map<?, ?> biomes = document.get(BIOME_ROOT, Map.class).orElse(Map.of());
        return biomes.keySet().stream()
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String humanizeBiomeName(NamespacedKey key) {
        String raw = key == null ? "" : key.getKey();
        if (raw == null || raw.isBlank()) {
            return "Unknown Biome";
        }
        String[] parts = raw.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.length() == 0 ? raw : builder.toString();
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

    private record BiomeEntry(String displayName, String typeLabel, boolean discovered, String sortKey) {
        private BiomeEntry(String displayName, String typeLabel, boolean discovered) {
            this(displayName, typeLabel, discovered, displayName);
        }
    }

    private record EnchantEntry(EnchantDefinition definition, String sortKey, String description) {
    }
}
