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
import sh.harold.fulcrum.plugin.item.durability.DurabilityService;

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
    private final sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer loreRenderer;
    private final DurabilityService durabilityService;
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
        this.loreRenderer = new ItemLoreRenderer(resolver, enchantRegistry, itemPdc);
        this.durabilityService = new DurabilityService(resolver, itemPdc, statBridge);
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
    public sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer loreRenderer() {
        return loreRenderer;
    }

    public java.util.Optional<org.bukkit.inventory.ItemStack> createItem(String id) {
        return registry.get(id).map(definition -> {
            org.bukkit.inventory.ItemStack initialized = resolver.initializeItem(definition);
            return initialized;
        });
    }

    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        loreAdapter = ProtocolLoreAdapter.register(plugin, loreRenderer);
        pluginManager.registerEvents(new ItemEquipListener(plugin, statBridge), plugin);
        pluginManager.registerEvents(new AbilityListener(abilityService, resolver), plugin);
        pluginManager.registerEvents(new HelmetEquipListener(resolver, statBridge), plugin);
        pluginManager.registerEvents(durabilityService, plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.visual.CursorRenderListener(plugin, loreRenderer), plugin);
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
        register("fulcrum:sharpness", "Sharpness", "Increases melee damage.", 7, StatIds.ATTACK_DAMAGE, 0.05, sharpnessCurve(), true, java.util.Set.of("fulcrum:smite", "fulcrum:bane_of_arthropods"));
        register("fulcrum:smite", "Smite", "More damage to undead foes.", 5, StatIds.ATTACK_DAMAGE, 0.05, EnchantDefinition.LevelCurve.linear(), true, java.util.Set.of("fulcrum:sharpness", "fulcrum:bane_of_arthropods"));
        register("fulcrum:bane_of_arthropods", "Bane of Arthropods", "More damage to arthropods.", 5, StatIds.ATTACK_DAMAGE, 0.05, EnchantDefinition.LevelCurve.linear(), true, java.util.Set.of("fulcrum:sharpness", "fulcrum:smite"));

        register("fulcrum:protection", "Protection", "Reduces incoming damage.", 4, StatIds.ARMOR, 1.5, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:fire_protection", "Fire Protection", "Reduces fire and lava damage.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:projectile_protection", "Projectile Protection", "Reduces projectile damage.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:blast_protection", "Blast Protection", "Reduces explosion damage.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:feather_falling", "Feather Falling", "Softer landings from falls.", 4, StatIds.ARMOR, 0.5, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());

        register("fulcrum:power", "Power", "Increases bow damage.", 5, StatIds.ATTACK_DAMAGE, 0.5, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:punch", "Punch", "Increases bow knockback.", 2, StatIds.ATTACK_DAMAGE, 0.25, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());

        register("fulcrum:knockback", "Knockback", "Extra melee knockback.", 2, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:looting", "Looting", "More drops from mobs.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:sweeping_edge", "Sweeping Edge", "Boosts sweeping hit damage.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:fire_aspect", "Fire Aspect", "Sets targets ablaze.", 2, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:flame", "Flame", "Ignites arrow hits.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:infinity", "Infinity", "Fired arrows are not consumed.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:loyalty", "Loyalty", "Trident returns after throwing.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:channeling", "Channeling", "Summons lightning on hit in storms.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:riptide", "Riptide", "Launches with trident in water or rain.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:impaling", "Impaling", "More damage to aquatic mobs.", 5, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:multishot", "Multishot", "Fires three arrows at once.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:piercing", "Piercing", "Arrows pass through targets.", 4, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:quick_charge", "Quick Charge", "Faster crossbow reload.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:depth_strider", "Depth Strider", "Faster swimming speed.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:frost_walker", "Frost Walker", "Freezes water underfoot.", 2, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:soul_speed", "Soul Speed", "Faster on soul blocks.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:swift_sneak", "Swift Sneak", "Faster while sneaking.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:aqua_affinity", "Aqua Affinity", "Mine faster underwater.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:respiration", "Respiration", "Longer underwater breathing.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:unbreaking", "Unbreaking", "Items lose durability slower.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:mending", "Mending", "XP repairs this item.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:silk_touch", "Silk Touch", "Drops blocks themselves.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:fortune", "Fortune", "Chance for extra drops.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:lure", "Lure", "Faster fish bites.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:luck_of_the_sea", "Luck of the Sea", "Better fishing treasure.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:thorns", "Thorns", "Chance to return damage.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:curse_of_binding", "Curse of Binding", "Cannot remove once equipped.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:curse_of_vanishing", "Curse of Vanishing", "Item disappears on death.", 1, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:efficiency", "Efficiency", "Faster mining speed.", 5, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
    }

    private void register(String id, String name, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel) {
        register(id, name, "", maxLevel, statId, perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
    }

    private void register(String id, String name, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve curve, boolean scaleAttackDamage) {
        register(id, name, "", maxLevel, statId, perLevel, curve, scaleAttackDamage, java.util.Set.of());
    }

    private void register(String id, String name, String description, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve curve, boolean scaleAttackDamage, java.util.Set<String> incompatibilities) {
        enchantRegistry.register(new EnchantDefinition(
            id,
            net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.AQUA),
            net.kyori.adventure.text.Component.text(description, net.kyori.adventure.text.format.NamedTextColor.GRAY),
            maxLevel,
            statId == null ? java.util.Map.of() : java.util.Map.of(statId, perLevel),
            curve,
            scaleAttackDamage,
            incompatibilities
        ));
    }

    private sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve sharpnessCurve() {
        return (perLevel, level) -> {
            if (level <= 0) {
                return 0.0;
            }
            if (level <= 4) {
                return level * 0.05;
            }
            double bonus = 0.20; // level 4 value
            double extra = ((level - 1.0) * (level - 4.0)) / 2.0; // sum of (level-3) from 5..level
            return bonus + 0.05 * extra;
        };
    }
}
