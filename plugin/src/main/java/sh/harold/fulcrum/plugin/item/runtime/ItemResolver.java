package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.common.data.ledger.item.ItemInstanceRecord;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.DurabilityComponent;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.StatsComponent;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.registry.VanillaWrapperFactory;
import sh.harold.fulcrum.plugin.item.enchant.EnchantRegistry;
import sh.harold.fulcrum.stats.core.StatId;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import sh.harold.fulcrum.plugin.item.runtime.DurabilityData;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityState;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemResolver {

    private static final Map<Enchantment, String> ENCHANT_IDS = buildEnchantIds();

    private static final Set<Enchantment> OVERRIDDEN_ENCHANTS = buildOverriddenEnchants();

    private final ItemRegistry registry;
    private final VanillaWrapperFactory wrapperFactory;
    private final ItemPdc itemPdc;
    private final VanillaStatResolver vanillaStatResolver;
    private final EnchantRegistry enchantRegistry;
    private final ItemLedgerRepository itemLedgerRepository;
    private final Logger logger;
    private final BlockedItemMasker blockedItemMasker;
    private final Set<UUID> ledgerChecked = ConcurrentHashMap.newKeySet();

    private static Map<Enchantment, String> buildEnchantIds() {
        Map<Enchantment, String> ids = new LinkedHashMap<>();
        ids.put(Enchantment.SHARPNESS, "fulcrum:sharpness");
        ids.put(Enchantment.SMITE, "fulcrum:smite");
        ids.put(Enchantment.BANE_OF_ARTHROPODS, "fulcrum:bane_of_arthropods");
        ids.put(Enchantment.PROTECTION, "fulcrum:protection");
        ids.put(Enchantment.FIRE_PROTECTION, "fulcrum:fire_protection");
        ids.put(Enchantment.PROJECTILE_PROTECTION, "fulcrum:projectile_protection");
        ids.put(Enchantment.BLAST_PROTECTION, "fulcrum:blast_protection");
        ids.put(Enchantment.FEATHER_FALLING, "fulcrum:feather_falling");
        ids.put(Enchantment.POWER, "fulcrum:power");
        ids.put(Enchantment.PUNCH, "fulcrum:punch");
        ids.put(Enchantment.KNOCKBACK, "fulcrum:knockback");
        ids.put(Enchantment.LOOTING, "fulcrum:looting");
        ids.put(Enchantment.SWEEPING_EDGE, "fulcrum:sweeping_edge");
        ids.put(Enchantment.FIRE_ASPECT, "fulcrum:fire_aspect");
        ids.put(Enchantment.FLAME, "fulcrum:flame");
        ids.put(Enchantment.INFINITY, "fulcrum:infinity");
        ids.put(Enchantment.LOYALTY, "fulcrum:loyalty");
        ids.put(Enchantment.CHANNELING, "fulcrum:channeling");
        ids.put(Enchantment.RIPTIDE, "fulcrum:riptide");
        ids.put(Enchantment.IMPALING, "fulcrum:impaling");
        ids.put(Enchantment.MULTISHOT, "fulcrum:multishot");
        ids.put(Enchantment.PIERCING, "fulcrum:piercing");
        ids.put(Enchantment.QUICK_CHARGE, "fulcrum:quick_charge");
        ids.put(Enchantment.DEPTH_STRIDER, "fulcrum:depth_strider");
        ids.put(Enchantment.FROST_WALKER, "fulcrum:frost_walker");
        ids.put(Enchantment.SOUL_SPEED, "fulcrum:soul_speed");
        ids.put(Enchantment.SWIFT_SNEAK, "fulcrum:swift_sneak");
        ids.put(Enchantment.AQUA_AFFINITY, "fulcrum:aqua_affinity");
        ids.put(Enchantment.RESPIRATION, "fulcrum:respiration");
        ids.put(Enchantment.UNBREAKING, "fulcrum:unbreaking");
        ids.put(Enchantment.MENDING, "fulcrum:mending");
        ids.put(Enchantment.SILK_TOUCH, "fulcrum:silk_touch");
        ids.put(Enchantment.FORTUNE, "fulcrum:fortune");
        ids.put(Enchantment.LURE, "fulcrum:lure");
        ids.put(Enchantment.LUCK_OF_THE_SEA, "fulcrum:luck_of_the_sea");
        ids.put(Enchantment.THORNS, "fulcrum:thorns");
        ids.put(Enchantment.BINDING_CURSE, "fulcrum:curse_of_binding");
        ids.put(Enchantment.VANISHING_CURSE, "fulcrum:curse_of_vanishing");
        ids.put(Enchantment.EFFICIENCY, "fulcrum:efficiency");
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("density"))).ifPresent(enchant -> ids.put(enchant, "fulcrum:density"));
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("breach"))).ifPresent(enchant -> ids.put(enchant, "fulcrum:breach"));
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"))).ifPresent(enchant -> ids.put(enchant, "fulcrum:wind_burst"));
        return Map.copyOf(ids);
    }

    private static Set<Enchantment> buildOverriddenEnchants() {
        Set<Enchantment> enchants = new LinkedHashSet<>();
        enchants.add(Enchantment.SHARPNESS);
        enchants.add(Enchantment.SMITE);
        enchants.add(Enchantment.BANE_OF_ARTHROPODS);
        enchants.add(Enchantment.PROTECTION);
        enchants.add(Enchantment.FIRE_PROTECTION);
        enchants.add(Enchantment.PROJECTILE_PROTECTION);
        enchants.add(Enchantment.BLAST_PROTECTION);
        enchants.add(Enchantment.FEATHER_FALLING);
        enchants.add(Enchantment.POWER);
        enchants.add(Enchantment.PUNCH);
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("density"))).ifPresent(enchants::add);
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("breach"))).ifPresent(enchants::add);
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"))).ifPresent(enchants::add);
        return Set.copyOf(enchants);
    }

    public ItemResolver(
        ItemRegistry registry,
        VanillaWrapperFactory wrapperFactory,
        ItemPdc itemPdc,
        VanillaStatResolver vanillaStatResolver,
        EnchantRegistry enchantRegistry,
        ItemLedgerRepository itemLedgerRepository,
        Logger logger
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.wrapperFactory = Objects.requireNonNull(wrapperFactory, "wrapperFactory");
        this.itemPdc = Objects.requireNonNull(itemPdc, "itemPdc");
        this.vanillaStatResolver = Objects.requireNonNull(vanillaStatResolver, "vanillaStatResolver");
        this.enchantRegistry = Objects.requireNonNull(enchantRegistry, "enchantRegistry");
        this.itemLedgerRepository = itemLedgerRepository;
        this.logger = logger != null ? logger : Logger.getLogger(ItemResolver.class.getName());
        this.blockedItemMasker = new BlockedItemMasker(registry, itemPdc, itemLedgerRepository, this.logger);
    }

    public Optional<ItemInstance> resolve(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return Optional.empty();
        }
        ItemStack working = stack.clone();
        String id = readId(working);
        CustomItem definition = id == null ? null : registry.get(id).orElse(null);
        if (definition == null) {
            definition = registry.getOrCreateVanilla(working.getType(), wrapperFactory);
        }
        if (blockedItemMasker.isBlocked(working)) {
            working = blockedItemMasker.sanitizeToVanilla(working);
            definition = registry.getOrCreateVanilla(working.getType(), wrapperFactory);
        }
        boolean taggingRequired = requiresTagging(working, definition);
        if (!taggingRequired) {
            ItemStack pristine = stripStackableMetadata(working);
            Map<StatId, Double> stats = computeDefinitionStats(definition);
            Map<String, Integer> enchants = itemPdc.readEnchants(pristine).orElse(Map.of());
            return Optional.of(new ItemInstance(definition, pristine, stats, enchants, enchantRegistry, DurabilityState.from(null)));
        }
        ItemStack tagged = itemPdc.setId(working, definition.id());
        Map<StatId, Double> stats = itemPdc.readStats(tagged).orElse(null);
        if (stats == null || stats.isEmpty()) {
            stats = computeDefinitionStats(definition);
            tagged = itemPdc.writeStats(tagged, stats);
        }
        DurabilityData durabilityData = itemPdc.readDurability(tagged).orElse(null);
        if (durabilityData == null) {
            durabilityData = computeDurability(definition, tagged);
            if (durabilityData != null) {
                tagged = itemPdc.writeDurability(tagged, durabilityData);
            }
        }
        Map<String, Integer> enchants = mergeEnchants(tagged);
        if (!enchants.isEmpty()) {
            tagged = itemPdc.writeEnchants(tagged, enchants);
        } else {
            tagged = itemPdc.clearEnchants(tagged);
        }
        tagged = storeTrim(tagged);
        final CustomItem finalDefinition = definition;
        if (shouldTagInstance(definition)) {
            Optional<UUID> existingInstanceId = itemPdc.readInstanceId(tagged);
            if (existingInstanceId.isEmpty()) {
                UUID instanceId = UUID.randomUUID();
                tagged = itemPdc.ensureInstanceId(tagged, instanceId);
                tagged = itemPdc.ensureProvenance(tagged, ItemCreationSource.MIGRATION, Instant.now());
            } else {
                existingInstanceId.ifPresent(instanceId -> ensureLedgerRecord(instanceId, finalDefinition.id()));
            }
        }
        tagged = mirrorAttributes(tagged, definition, stats);
        tagged = sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(tagged);
        return Optional.of(new ItemInstance(definition, tagged, stats, enchants, enchantRegistry, DurabilityState.from(durabilityData)));
    }

    public ItemStack applyId(ItemStack stack, String id) {
        return itemPdc.setId(stack, id);
    }

    public String readItemId(ItemStack stack) {
        return readId(stack);
    }

    public ItemStack initializeItem(CustomItem definition) {
        ItemStack base = new ItemStack(definition.material());
        boolean taggingRequired = requiresTagging(base, definition);
        if (!taggingRequired) {
            return stripStackableMetadata(base);
        }
        ItemStack withId = itemPdc.setId(base, definition.id());
        Map<StatId, Double> stats = computeDefinitionStats(definition);
        withId = itemPdc.writeStats(withId, stats);
        DurabilityData durability = computeDurability(definition, withId);
        if (durability != null) {
            withId = itemPdc.writeDurability(withId, durability);
        }
        if (shouldTagInstance(definition)) {
            withId = itemPdc.ensureInstanceId(withId);
        }
        return sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(withId);
    }

    public boolean shouldTagInstance(CustomItem definition) {
        if (definition == null) {
            return false;
        }
        if (!definition.id().startsWith("vanilla:")) {
            return true;
        }
        return switch (definition.category()) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS,
                 SWORD, AXE, MACE, BOW, WAND, PICKAXE, SHOVEL, HOE, FISHING_ROD, TRIDENT -> true;
            default -> false;
        };
    }

    private String readId(ItemStack stack) {
        return itemPdc.readId(stack).orElse(null);
    }

    private boolean requiresTagging(ItemStack stack, CustomItem definition) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (blockedItemMasker.isBlocked(stack)) {
            return true;
        }
        if (stack.getMaxStackSize() <= 1) {
            return true;
        }
        if (definition == null) {
            return false;
        }
        if (!isVanilla(definition)) {
            return true;
        }
        ItemCategory category = definition.category();
        return switch (category) {
            case MATERIAL, CONSUMABLE -> false;
            default -> true;
        };
    }

    private boolean isVanilla(CustomItem definition) {
        return definition != null && definition.id() != null && definition.id().startsWith("vanilla:");
    }

    private ItemStack stripStackableMetadata(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack working = stack.clone();
        ItemMeta meta = working.getItemMeta();
        if (meta != null) {
            meta.removeItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ARMOR_TRIM
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

    private Map<StatId, Double> computeDefinitionStats(CustomItem definition) {
        Map<StatId, Double> stats = new HashMap<>();
        definition.component(ComponentType.STATS, StatsComponent.class).ifPresent(component -> stats.putAll(component.baseStats()));
        if (stats.isEmpty() && definition.id().startsWith("vanilla:")) {
            stats.putAll(vanillaStatResolver.statsFor(definition.material()));
        }
        return Map.copyOf(stats);
    }

    private void appendLedger(UUID instanceId, String itemId, ItemCreationSource source) {
        if (itemLedgerRepository == null || instanceId == null) {
            return;
        }
        if (!ledgerChecked.add(instanceId)) {
            return;
        }
        ItemInstanceRecord record = new ItemInstanceRecord(instanceId, itemId, source, null, Instant.now());
        // Ledger writes are temporarily disabled while we move to item-first provenance.
    }

    private void ensureLedgerRecord(UUID instanceId, String itemId) {
        if (itemLedgerRepository == null || instanceId == null) {
            return;
        }
        if (!ledgerChecked.add(instanceId)) {
            return;
        }
        // Ledger writes are temporarily disabled while we move to item-first provenance.
    }

    private Map<String, Integer> mergeEnchants(ItemStack stack) {
        Map<String, Integer> enchants = new HashMap<>(itemPdc.readEnchants(stack).orElse(Map.of()));
        var meta = stack.getItemMeta();
        if (meta != null && !meta.getEnchants().isEmpty()) {
            boolean metaChanged = false;
            for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                String customId = ENCHANT_IDS.get(entry.getKey());
                if (customId == null) {
                    continue;
                }
                int level = entry.getValue();
                enchants.merge(customId, level, Math::max);
                if (OVERRIDDEN_ENCHANTS.contains(entry.getKey())) {
                    meta.removeEnchant(entry.getKey());
                    metaChanged = true;
                }
            }
            if (metaChanged) {
                stack.setItemMeta(meta);
            }
        }
        return Map.copyOf(enchants);
    }

    private ItemStack storeTrim(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof ArmorMeta armorMeta)) {
            return stack;
        }
        ArmorTrim vanillaTrim = armorMeta.getTrim();
        Optional<TrimData> storedTrim = itemPdc.readTrim(stack);
        if (vanillaTrim != null) {
            persistTrim(armorMeta, vanillaTrim);
            stack.setItemMeta(armorMeta);
            return stack;
        }
        if (storedTrim.isEmpty()) {
            return stack;
        }
        ArmorTrim resolved = resolveTrim(storedTrim.get());
        if (resolved == null) {
            return stack;
        }
        armorMeta.setTrim(resolved);
        persistTrim(armorMeta, resolved);
        stack.setItemMeta(armorMeta);
        return stack;
    }

    private void persistTrim(ArmorMeta meta, ArmorTrim trim) {
        if (meta == null || trim == null) {
            return;
        }
        meta.getPersistentDataContainer().set(
            itemPdc.keys().trimPattern(),
            org.bukkit.persistence.PersistentDataType.STRING,
            trim.getPattern().getKey().getKey()
        );
        meta.getPersistentDataContainer().set(
            itemPdc.keys().trimMaterial(),
            org.bukkit.persistence.PersistentDataType.STRING,
            trim.getMaterial().getKey().getKey()
        );
    }

    private ArmorTrim resolveTrim(TrimData data) {
        TrimPattern pattern = Registry.TRIM_PATTERN.stream()
            .filter(candidate -> candidate.getKey().getKey().equalsIgnoreCase(data.patternKey()))
            .findFirst()
            .orElse(null);
        TrimMaterial material = Registry.TRIM_MATERIAL.stream()
            .filter(candidate -> candidate.getKey().getKey().equalsIgnoreCase(data.materialKey()))
            .findFirst()
            .orElse(null);
        if (pattern == null || material == null) {
            return null;
        }
        return new ArmorTrim(material, pattern);
    }

    private DurabilityData computeDurability(CustomItem definition, ItemStack stack) {
        DurabilityComponent component = definition.component(ComponentType.DURABILITY, DurabilityComponent.class).orElse(null);
        int max = component != null ? component.max() : definition.material().getMaxDurability();
        if (max <= 0) {
            return null;
        }
        int seeded = component != null ? component.seededCurrentOrMax() : max;
        int inferredFromDamage = seeded;
        if (stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int vanillaDamage = Math.max(0, damageable.getDamage());
            inferredFromDamage = Math.max(0, max - vanillaDamage);
        }
        int current = Math.min(seeded, inferredFromDamage);
        return new DurabilityData(current, max);
    }

    private ItemStack mirrorAttributes(ItemStack stack, CustomItem definition, Map<StatId, Double> stats) {
        if (stack == null) {
            return null;
        }
        var meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setAttributeModifiers(null);
        boolean defunct = itemPdc.readDurability(stack)
            .map(DurabilityData::defunct)
            .orElse(false);
        double attackDamage = defunct ? 0.0 : stats.getOrDefault(sh.harold.fulcrum.stats.core.StatIds.ATTACK_DAMAGE, 0.0);
        double attackSpeed = defunct ? 0.0 : stats.getOrDefault(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, 0.0);
        double armor = defunct ? 0.0 : stats.getOrDefault(sh.harold.fulcrum.stats.core.StatIds.ARMOR, 0.0);
        boolean applyHand = definition.category().defaultSlot() == sh.harold.fulcrum.plugin.item.model.SlotGroup.MAIN_HAND;

        if (!defunct) {
            switch (definition.category()) {
                case HELMET -> addAttribute(meta, Attribute.ARMOR, armor, EquipmentSlot.HEAD);
                case CHESTPLATE -> addAttribute(meta, Attribute.ARMOR, armor, EquipmentSlot.CHEST);
                case LEGGINGS -> addAttribute(meta, Attribute.ARMOR, armor, EquipmentSlot.LEGS);
                case BOOTS -> addAttribute(meta, Attribute.ARMOR, armor, EquipmentSlot.FEET);
                default -> {
                    if (applyHand) {
                        addAttribute(meta, Attribute.ATTACK_DAMAGE, attackDamage, EquipmentSlot.HAND);
                        addAttribute(meta, Attribute.ATTACK_SPEED, attackSpeed, EquipmentSlot.HAND);
                    }
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private void addAttribute(ItemMeta meta, Attribute attribute, double value, EquipmentSlot slot) {
        if (attribute == null || meta == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(
            UUID.nameUUIDFromBytes((attribute.getKey().getKey() + ":" + slot.name()).getBytes()),
            "fulcrum-" + attribute.getKey().getKey(),
            value,
            AttributeModifier.Operation.ADD_NUMBER,
            slot
        );
        meta.addAttributeModifier(attribute, modifier);
    }
}
