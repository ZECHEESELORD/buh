package sh.harold.fulcrum.plugin.item;

import org.bukkit.Material;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.common.data.ledger.item.ItemInstanceRecord;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.plugin.item.ability.AbilityService;
import sh.harold.fulcrum.plugin.item.registry.ItemDefinitionProvider;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.registry.VanillaWrapperFactory;
import sh.harold.fulcrum.plugin.item.registry.YamlItemDefinitionLoader;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.runtime.VanillaStatResolver;
import sh.harold.fulcrum.plugin.item.recipe.ArmorTrimRecipeBlocker;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;
import sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer;
import sh.harold.fulcrum.plugin.item.visual.ProtocolLoreAdapter;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.plugin.item.enchant.EnchantService;
import sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.plugin.item.listener.AbilityListener;
import sh.harold.fulcrum.plugin.item.listener.CraftProvenanceListener;
import sh.harold.fulcrum.plugin.item.listener.HelmetEquipListener;
import sh.harold.fulcrum.plugin.item.listener.AnvilListener;
import sh.harold.fulcrum.plugin.item.listener.ItemEquipListener;
import sh.harold.fulcrum.plugin.item.listener.NullItemPlacementListener;
import sh.harold.fulcrum.plugin.stats.StatsModule;
import sh.harold.fulcrum.plugin.item.durability.DurabilityService;
import sh.harold.fulcrum.stats.core.StatCondition;
import sh.harold.fulcrum.plugin.item.listener.CreativeSanitizerListener;
import sh.harold.fulcrum.plugin.item.model.CustomItem;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

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
    private final ArmorTrimRecipeBlocker armorTrimRecipeBlocker;
    private final ItemLedgerRepository itemLedgerRepository;
    private final sh.harold.fulcrum.plugin.item.runtime.BlockedItemMasker blockedItemMasker;
    private ProtocolLoreAdapter loreAdapter;
    private static final EnumSet<Material> VANILLA_EXCLUDES = EnumSet.of(
        Material.AIR,
        Material.CAVE_AIR,
        Material.VOID_AIR,
        Material.STRUCTURE_VOID,
        Material.STRUCTURE_BLOCK,
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.COMMAND_BLOCK_MINECART,
        Material.JIGSAW,
        Material.BARRIER,
        Material.LIGHT,
        Material.DEBUG_STICK,
        Material.KNOWLEDGE_BOOK
    );

    public ItemEngine(JavaPlugin plugin, StatsModule statsModule, List<ItemDefinitionProvider> providers, ItemLedgerRepository itemLedgerRepository) {
        this.plugin = plugin;
        this.statsModule = statsModule;
        this.registry = new ItemRegistry();
        this.wrapperFactory = new VanillaWrapperFactory();
        this.itemPdc = new ItemPdc(plugin);
        this.enchantRegistry = new EnchantRegistry();
        this.enchantService = new EnchantService(enchantRegistry, itemPdc);
        registerDefaultEnchants();
        this.resolver = new ItemResolver(
            registry,
            wrapperFactory,
            itemPdc,
            new VanillaStatResolver(),
            enchantRegistry,
            itemLedgerRepository,
            plugin.getLogger()
        );
        this.abilityService = new AbilityService();
        this.statBridge = new ItemStatBridge(resolver, statsModule.statService(), statsModule.statSourceContextRegistry());
        this.loreRenderer = new ItemLoreRenderer(resolver, enchantRegistry, itemPdc, statsModule.statRegistry(), statsModule.playerSettingsService());
        this.durabilityService = new DurabilityService(plugin, resolver, itemPdc, statBridge);
        this.armorTrimRecipeBlocker = new ArmorTrimRecipeBlocker(plugin);
        this.itemLedgerRepository = itemLedgerRepository;
        this.blockedItemMasker = new sh.harold.fulcrum.plugin.item.runtime.BlockedItemMasker(registry, itemPdc, itemLedgerRepository, plugin.getLogger());
        loadDefinitions(providers);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new CreativeSanitizerListener(this), plugin);
        pluginManager.registerEvents(new CraftProvenanceListener(this), plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.listener.ItemLifecycleListener(this), plugin);
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

    public JavaPlugin plugin() {
        return plugin;
    }

    public java.util.Optional<ItemLedgerRepository> itemLedger() {
        return java.util.Optional.ofNullable(itemLedgerRepository);
    }

    public ItemRegistry registry() {
        return registry;
    }

    public java.util.List<CustomItem> ensureVanillaDefinitions(java.util.Collection<Material> materials) {
        Objects.requireNonNull(materials, "materials");
        return materials.stream()
            .filter(Material::isItem)
            .filter(material -> !material.isLegacy())
            .filter(material -> !VANILLA_EXCLUDES.contains(material))
            .map(material -> registry.getOrCreateVanilla(material, wrapperFactory))
            .toList();
    }

    public java.util.Optional<org.bukkit.inventory.ItemStack> createItem(String id) {
        return createItem(id, ItemCreationSource.SYSTEM, null);
    }

    public java.util.Optional<org.bukkit.inventory.ItemStack> createItem(String id, ItemCreationSource source, java.util.UUID creatorId) {
        java.util.Objects.requireNonNull(source, "source");
        return registry.get(id).map(definition -> {
            org.bukkit.inventory.ItemStack initialized = resolver.initializeItem(definition);
            java.util.UUID instanceId = itemPdc.readInstanceId(initialized).orElse(null);
            initialized = itemPdc.ensureProvenance(initialized, source, java.time.Instant.now());
            logInstance(definition.id(), instanceId, source, creatorId);
            return initialized;
        });
    }

    public org.bukkit.inventory.ItemStack tagItem(org.bukkit.inventory.ItemStack stack, ItemCreationSource source) {
        if (stack == null || stack.getType().isAir()) {
            return stack;
        }
        if (stack.getMaxStackSize() > 1) {
            return stack;
        }
        java.time.Instant now = java.time.Instant.now();
        var resolved = resolver.resolve(stack).orElse(null);
        org.bukkit.inventory.ItemStack working = resolved == null ? stack.clone() : resolved.stack();
        if (resolved != null && resolver.shouldTagInstance(resolved.definition())) {
            working = itemPdc.ensureInstanceId(working);
        }
        working = itemPdc.ensureProvenance(working, source == null ? ItemCreationSource.UNKNOWN : source, now);
        return working;
    }

    public org.bukkit.inventory.ItemStack sanitizeStackable(org.bukkit.inventory.ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getMaxStackSize() <= 1) {
            return stack;
        }
        org.bukkit.inventory.ItemStack working = stack;
        org.bukkit.inventory.meta.ItemMeta meta = working.getItemMeta();
        if (meta != null && !meta.getItemFlags().isEmpty()) {
            meta.removeItemFlags(
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ARMOR_TRIM
            );
            working.setItemMeta(meta);
        }
        working = itemPdc.clear(working, itemPdc.keys().itemId());
        working = itemPdc.clear(working, itemPdc.keys().version());
        working = itemPdc.clear(working, itemPdc.keys().stats());
        working = itemPdc.clear(working, itemPdc.keys().enchants());
        working = itemPdc.clear(working, itemPdc.keys().durabilityCurrent());
        working = itemPdc.clear(working, itemPdc.keys().durabilityMax());
        working = itemPdc.clear(working, itemPdc.keys().trimPattern());
        working = itemPdc.clear(working, itemPdc.keys().trimMaterial());
        working = itemPdc.clear(working, itemPdc.keys().instanceId());
        working = itemPdc.clear(working, itemPdc.keys().createdAt());
        working = itemPdc.clear(working, itemPdc.keys().source());
        return working;
    }

    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        loreAdapter = ProtocolLoreAdapter.register(plugin, loreRenderer);
        pluginManager.registerEvents(new ItemEquipListener(plugin, statBridge, resolver), plugin);
        pluginManager.registerEvents(new AbilityListener(abilityService, resolver), plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.listener.LecternBypassListener(), plugin);
        pluginManager.registerEvents(new HelmetEquipListener(resolver, statBridge), plugin);
        pluginManager.registerEvents(durabilityService, plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.visual.CursorRenderListener(plugin, loreRenderer), plugin);
        pluginManager.registerEvents(new AnvilListener(plugin, resolver), plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.listener.GrindstoneListener(itemPdc), plugin);
        pluginManager.registerEvents(new sh.harold.fulcrum.plugin.item.listener.SmithingListener(this), plugin);
        pluginManager.registerEvents(new NullItemPlacementListener(itemPdc), plugin);
        armorTrimRecipeBlocker.register();
    }

    public void disable() {
        if (loreAdapter != null) {
            loreAdapter.unregister();
        }
    }

    public boolean hasPacketLoreAdapter() {
        return loreAdapter != null;
    }

    public boolean setPacketLoreEnabled(UUID playerId, boolean enabled) {
        if (loreAdapter == null || playerId == null) {
            return false;
        }
        if (enabled) {
            loreAdapter.enable(playerId);
        } else {
            loreAdapter.disable(playerId);
        }
        return true;
    }

    public boolean togglePacketLore(UUID playerId) {
        if (loreAdapter == null || playerId == null) {
            return false;
        }
        return loreAdapter.toggle(playerId);
    }

    public boolean isPacketLoreDisabled(UUID playerId) {
        return loreAdapter != null && loreAdapter.isDisabled(playerId);
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

    private void logInstance(String itemId, UUID instanceId, ItemCreationSource source, UUID creatorId) {
        if (itemLedgerRepository == null || instanceId == null) {
            return;
        }
        // Ledger writes are temporarily disabled while we move to item-first provenance.
    }

    private void registerDefaultEnchants() {
        register("fulcrum:sharpness", "Sharpness", "Increases melee damage.", 7, StatIds.ATTACK_DAMAGE, 0.05, sharpnessCurve(), true, java.util.Set.of("fulcrum:smite", "fulcrum:bane_of_arthropods"));
        register("fulcrum:critical_strike", "Critical Strike", "Occasionally doubles damage when swinging swords or axes.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:smite", "Smite", "More damage to undead foes only.", 5, StatIds.ATTACK_DAMAGE, 0.05, EnchantDefinition.LevelCurve.linear(), true, java.util.Set.of("fulcrum:sharpness", "fulcrum:bane_of_arthropods"), StatCondition.whenTag("target:undead"));
        register("fulcrum:bane_of_arthropods", "Bane of Arthropods", "More damage to spiders and arthropods only.", 5, StatIds.ATTACK_DAMAGE, 0.05, EnchantDefinition.LevelCurve.linear(), true, java.util.Set.of("fulcrum:sharpness", "fulcrum:smite"), StatCondition.whenTag("target:arthropod"));

        register("fulcrum:protection", "Protection", "Reduces incoming damage.", 4, StatIds.ARMOR, 1.5, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
        register("fulcrum:fire_protection", "Fire Protection", "Reduces fire and lava damage only.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of(), StatCondition.whenTag("cause:fire"));
        register("fulcrum:projectile_protection", "Projectile Protection", "Reduces projectile damage only.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of(), StatCondition.whenTag("cause:projectile"));
        register("fulcrum:blast_protection", "Blast Protection", "Reduces explosion damage only.", 4, StatIds.ARMOR, 1.0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of(), StatCondition.whenTag("cause:explosion"));
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
        register("fulcrum:dune_speed", "Dune Speed", "Sprinting on sand, gravel, or concrete powder feels lighter with each level.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of("fulcrum:soul_speed"));
        register("fulcrum:density", "Density", "Smash deals more damage per fallen block.", 5, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of(
            "fulcrum:sharpness",
            "fulcrum:smite",
            "fulcrum:bane_of_arthropods",
            "fulcrum:breach"
        ));
        register("fulcrum:breach", "Breach", "Smash pierces armor effectiveness.", 4, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of(
            "fulcrum:sharpness",
            "fulcrum:smite",
            "fulcrum:bane_of_arthropods",
            "fulcrum:density"
        ));
        register("fulcrum:wind_burst", "Wind Burst", "Smash creates a gust on impact.", 3, null, 0, EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
    }

    private void register(String id, String name, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel) {
        register(id, name, "", maxLevel, statId, perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve.linear(), false, java.util.Set.of());
    }

    private void register(String id, String name, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve curve, boolean scaleAttackDamage) {
        register(id, name, "", maxLevel, statId, perLevel, curve, scaleAttackDamage, java.util.Set.of());
    }

    private void register(String id, String name, String description, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve curve, boolean scaleAttackDamage, java.util.Set<String> incompatibilities) {
        register(id, name, description, maxLevel, statId, perLevel, curve, scaleAttackDamage, incompatibilities, sh.harold.fulcrum.stats.core.StatCondition.always());
    }

    private void register(String id, String name, String description, int maxLevel, sh.harold.fulcrum.stats.core.StatId statId, double perLevel, sh.harold.fulcrum.plugin.item.enchant.EnchantDefinition.LevelCurve curve, boolean scaleAttackDamage, java.util.Set<String> incompatibilities, sh.harold.fulcrum.stats.core.StatCondition condition) {
        enchantRegistry.register(new EnchantDefinition(
            id,
            net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.AQUA),
            net.kyori.adventure.text.Component.text(description, net.kyori.adventure.text.format.NamedTextColor.GRAY),
            maxLevel,
            statId == null ? java.util.Map.of() : java.util.Map.of(statId, perLevel),
            curve,
            scaleAttackDamage,
            incompatibilities,
            condition
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
