package sh.harold.fulcrum.plugin.item.registry;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.DurabilityComponent;
import sh.harold.fulcrum.plugin.item.model.ItemTrait;
import sh.harold.fulcrum.plugin.item.model.LoreSection;
import sh.harold.fulcrum.plugin.item.model.StatsComponent;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.stats.core.StatId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class YamlItemDefinitionLoader {

    private final Path itemsPath;
    private final Logger logger;

    public YamlItemDefinitionLoader(Path itemsPath, Logger logger) {
        this.itemsPath = itemsPath;
        this.logger = logger;
    }

    public void loadInto(ItemRegistry registry) {
        try {
            Files.createDirectories(itemsPath);
            try (var files = Files.list(itemsPath)) {
                files
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .forEach(path -> loadFile(path, registry));
            }
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed to scan item definitions at " + itemsPath, exception);
        }
    }

    private void loadFile(Path path, ItemRegistry registry) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        try {
            CustomItem item = parseItem(yaml);
            registry.register(item);
            logger.info("Loaded item definition " + item.id() + " from " + path.getFileName());
        } catch (IllegalArgumentException exception) {
            logger.log(Level.SEVERE, "Failed to load item definition from " + path + ": " + exception.getMessage(), exception);
        }
    }

    private CustomItem parseItem(YamlConfiguration yaml) {
        String id = requireString(yaml, "id");
        Material material = Material.matchMaterial(requireString(yaml, "material"));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material '" + yaml.getString("material") + "'");
        }
        ItemCategory category = Optional.ofNullable(yaml.getString("category"))
            .map(value -> ItemCategory.valueOf(value.toUpperCase(Locale.ROOT)))
            .orElse(ItemCategory.fromMaterial(material));
        Set<ItemTrait> traits = parseTraits(yaml.getStringList("traits"));
        Map<ComponentType, sh.harold.fulcrum.plugin.item.model.ItemComponent> components = new EnumMap<>(ComponentType.class);

        ConfigurationSection statsSection = yaml.getConfigurationSection("stats");
        if (statsSection != null) {
            Map<StatId, Double> stats = new HashMap<>();
            for (String key : statsSection.getKeys(false)) {
                stats.put(new StatId(key), statsSection.getDouble(key));
            }
            components.put(ComponentType.STATS, new StatsComponent(stats));
        }

        ConfigurationSection durabilitySection = yaml.getConfigurationSection("durability");
        if (durabilitySection != null && durabilitySection.contains("max")) {
            int max = durabilitySection.getInt("max");
            Integer seed = durabilitySection.contains("current") ? durabilitySection.getInt("current") : null;
            components.put(ComponentType.DURABILITY, new DurabilityComponent(max, seed));
        }

        List<Map<?, ?>> abilities = yaml.getMapList("abilities");
        if (!abilities.isEmpty()) {
            Map<AbilityTrigger, AbilityDefinition> abilityDefinitions = new EnumMap<>(AbilityTrigger.class);
            for (Map<?, ?> raw : abilities) {
                AbilityDefinition ability = parseAbility(raw);
                abilityDefinitions.put(ability.trigger(), ability);
            }
            components.put(ComponentType.ABILITY, new AbilityComponent(abilityDefinitions));
        }

        List<Component> flavor = yaml.getStringList("flavor").stream()
            .map(line -> (Component) Component.text(line))
            .toList();
        components.put(ComponentType.VISUAL, new VisualComponent(
            Component.text(yaml.getString("display_name", id)),
            flavor,
            null
        ));

        List<LoreSection> layout = yaml.getStringList("lore_layout").stream()
            .map(value -> LoreSection.valueOf(value.toUpperCase(Locale.ROOT)))
            .toList();

        CustomItem.Builder builder = CustomItem.builder(id)
            .material(material)
            .category(category)
            .traits(traits)
            .loreLayout(layout.isEmpty() ? null : layout);
        if (components.get(ComponentType.STATS) != null) {
            builder.component(ComponentType.STATS, components.get(ComponentType.STATS));
        }
        if (components.get(ComponentType.ABILITY) != null) {
            builder.component(ComponentType.ABILITY, components.get(ComponentType.ABILITY));
        }
        if (components.get(ComponentType.DURABILITY) != null) {
            builder.component(ComponentType.DURABILITY, components.get(ComponentType.DURABILITY));
        }
        if (components.get(ComponentType.VISUAL) != null) {
            builder.component(ComponentType.VISUAL, components.get(ComponentType.VISUAL));
        }
        return builder.build();
    }

    private AbilityDefinition parseAbility(Map<?, ?> raw) {
        String id = asString(raw.get("id"), "ability id");
        String triggerRaw = asString(raw.get("trigger"), "ability trigger");
        AbilityTrigger trigger = AbilityTrigger.valueOf(triggerRaw.toUpperCase(Locale.ROOT));
        Object nameObj = raw.get("name");
        Component displayName = Component.text(nameObj == null ? id : String.valueOf(nameObj));
        Object descriptionObj = raw.get("description");
        List<Component> description = descriptionObj instanceof List<?>
            ? ((List<?>) descriptionObj).stream().map(String::valueOf).map(line -> (Component) Component.text(line)).toList()
            : List.of();
        Object cooldownObj = raw.get("cooldown");
        Duration cooldown = parseDuration(cooldownObj == null ? "PT0S" : String.valueOf(cooldownObj));
        Object cooldownKeyObj = raw.get("cooldown_key");
        String cooldownKey = cooldownKeyObj == null ? id : String.valueOf(cooldownKeyObj);
        return new AbilityDefinition(id, trigger, displayName, description, cooldown, cooldownKey);
    }

    private Duration parseDuration(String raw) {
        try {
            return Duration.parse(raw);
        } catch (Exception exception) {
            logger.warning("Invalid duration '" + raw + "', defaulting to PT0S");
            return Duration.ZERO;
        }
    }

    private String requireString(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required property '" + path + "'");
        }
        return value;
    }

    private String asString(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is missing");
        }
        return String.valueOf(value);
    }

    private Set<ItemTrait> parseTraits(List<String> rawTraits) {
        Set<ItemTrait> traits = new HashSet<>();
        for (String raw : rawTraits) {
            try {
                traits.add(ItemTrait.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                logger.warning("Unknown trait '" + raw + "', skipping");
            }
        }
        return traits;
    }
}
