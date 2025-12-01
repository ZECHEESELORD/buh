package sh.harold.fulcrum.plugin.item;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.item.ability.AbilityService;
import sh.harold.fulcrum.plugin.item.registry.ItemDefinitionProvider;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.registry.VanillaWrapperFactory;
import sh.harold.fulcrum.plugin.item.registry.YamlItemDefinitionLoader;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.VanillaStatResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;
import sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer;
import sh.harold.fulcrum.plugin.item.visual.ProtocolLoreAdapter;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.plugin.item.enchant.EnchantService;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.plugin.item.listener.AbilityListener;
import sh.harold.fulcrum.plugin.item.listener.HelmetEquipListener;
import sh.harold.fulcrum.plugin.item.listener.ItemEquipListener;
import sh.harold.fulcrum.plugin.stats.StatsModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ItemEngine {

    private final JavaPlugin plugin;
    private final StatsModule statsModule;
    private final ItemRegistry registry;
    private final VanillaWrapperFactory wrapperFactory;
    private final ItemResolver resolver;
    private final ItemPdc itemPdc;
    private final EnchantRegistry enchantRegistry;
    private final EnchantService enchantService;
    private final AbilityService abilityService;
    private final ItemStatBridge statBridge;
    private ProtocolLoreAdapter loreAdapter;

    public ItemEngine(JavaPlugin plugin, StatsModule statsModule, List<ItemDefinitionProvider> providers) {
        this.plugin = plugin;
        this.statsModule = statsModule;
        this.registry = new ItemRegistry();
        this.wrapperFactory = new VanillaWrapperFactory();
        this.itemPdc = new ItemPdc(plugin);
        this.enchantRegistry = new EnchantRegistry();
        this.enchantService = new EnchantService(enchantRegistry, itemPdc);
        registerDefaultEnchants();
        this.resolver = new ItemResolver(registry, wrapperFactory, itemPdc, new VanillaStatResolver(), enchantRegistry);
        this.abilityService = new AbilityService();
        this.statBridge = new ItemStatBridge(resolver, statsModule.statService());
        loadDefinitions(providers);
    }

    public ItemResolver resolver() {
        return resolver;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public EnchantService enchantService() {
        return enchantService;
    }

    public EnchantRegistry enchantRegistry() {
        return enchantRegistry;
    }

    public ItemPdc itemPdc() {
        return itemPdc;
    }

    public ItemStatBridge statBridge() {
        return statBridge;
    }

    public java.util.Optional<org.bukkit.inventory.ItemStack> createItem(String id) {
        return registry.get(id).map(definition -> {
            org.bukkit.inventory.ItemStack initialized = resolver.initializeItem(definition);
            return initialized;
        });
    }

    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        ItemLoreRenderer loreRenderer = new ItemLoreRenderer(resolver, enchantRegistry);
        loreAdapter = ProtocolLoreAdapter.register(plugin, loreRenderer);
        pluginManager.registerEvents(new ItemEquipListener(plugin, statBridge), plugin);
        pluginManager.registerEvents(new AbilityListener(abilityService, resolver), plugin);
        pluginManager.registerEvents(new HelmetEquipListener(resolver, statBridge), plugin);
    }

    public void disable() {
        if (loreAdapter != null) {
            loreAdapter.unregister();
        }
    }

    private void loadDefinitions(List<ItemDefinitionProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        for (ItemDefinitionProvider provider : providers) {
            provider.definitions().forEach(registry::register);
        }
        Path itemsPath = plugin.getDataFolder().toPath().resolve("config").resolve("items");
        new YamlItemDefinitionLoader(itemsPath, plugin.getLogger()).loadInto(registry);
        plugin.getLogger().info("Item engine loaded");
    }

    private void registerDefaultEnchants() {
        register("fulcrum:sharpness", "Sharpness", 5, StatIds.ATTACK_DAMAGE, 1.0);
        register("fulcrum:smite", "Smite", 5, StatIds.ATTACK_DAMAGE, 1.0);
        register("fulcrum:bane_of_arthropods", "Bane of Arthropods", 5, StatIds.ATTACK_DAMAGE, 1.0);

        register("fulcrum:protection", "Protection", 4, StatIds.ARMOR, 1.5);
        register("fulcrum:fire_protection", "Fire Protection", 4, StatIds.ARMOR, 1.0);
        register("fulcrum:projectile_protection", "Projectile Protection", 4, StatIds.ARMOR, 1.0);
        register("fulcrum:blast_protection", "Blast Protection", 4, StatIds.ARMOR, 1.0);
        register("fulcrum:feather_falling", "Feather Falling", 4, StatIds.ARMOR, 0.5);

        register("fulcrum:power", "Power", 5, StatIds.ATTACK_DAMAGE, 0.5);
        register("fulcrum:punch", "Punch", 2, StatIds.ATTACK_DAMAGE, 0.25);

        register("fulcrum:knockback", "Knockback", 2, null, 0);
        register("fulcrum:looting", "Looting", 3, null, 0);
        register("fulcrum:sweeping_edge", "Sweeping Edge", 3, null, 0);
        register("fulcrum:fire_aspect", "Fire Aspect", 2, null, 0);
        register("fulcrum:flame", "Flame", 1, null, 0);
        register("fulcrum:infinity", "Infinity", 1, null, 0);
        register("fulcrum:loyalty", "Loyalty", 3, null, 0);
        register("fulcrum:channeling", "Channeling", 1, null, 0);
        register("fulcrum:riptide", "Riptide", 3, null, 0);
        register("fulcrum:impaling", "Impaling", 5, null, 0);
        register("fulcrum:multishot", "Multishot", 1, null, 0);
        register("fulcrum:piercing", "Piercing", 4, null, 0);
        register("fulcrum:quick_charge", "Quick Charge", 3, null, 0);
        register("fulcrum:depth_strider", "Depth Strider", 3, null, 0);
        register("fulcrum:frost_walker", "Frost Walker", 2, null, 0);
        register("fulcrum:soul_speed", "Soul Speed", 3, null, 0);
        register("fulcrum:swift_sneak", "Swift Sneak", 3, null, 0);
        register("fulcrum:aqua_affinity", "Aqua Affinity", 1, null, 0);
        register("fulcrum:respiration", "Respiration", 3, null, 0);
        register("fulcrum:unbreaking", "Unbreaking", 3, null, 0);
        register("fulcrum:mending", "Mending", 1, null, 0);
        register("fulcrum:silk_touch", "Silk Touch", 1, null, 0);
        register("fulcrum:fortune", "Fortune", 3, null, 0);
        register("fulcrum:lure", "Lure", 3, null, 0);
        register("fulcrum:luck_of_the_sea", "Luck of the Sea", 3, null, 0);
        register("fulcrum:thorns", "Thorns", 3, null, 0);
        register("fulcrum:curse_of_binding", "Curse of Binding", 1, null, 0);
        register("fulcrum:curse_of_vanishing", "Curse of Vanishing", 1, null, 0);
        register("fulcrum:efficiency", "Efficiency", 5, null, 0);
    }

    private void register(String id, String name, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel) {
        enchantRegistry.register(new EnchantDefinition(
            id,
            net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.AQUA),
            maxLevel,
            statId == null ? java.util.Map.of() : java.util.Map.of(statId, perLevel)
        ));
    }
}
