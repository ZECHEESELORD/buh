package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.Material;
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
import org.bukkit.NamespacedKey;

import sh.harold.fulcrum.plugin.item.runtime.DurabilityData;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityState;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemResolver {

    private static final Map<Enchantment, String> ENCHANT_IDS = buildEnchantIds();

    private static final Set<Enchantment> OVERRIDDEN_ENCHANTS = Set.of(
        Enchantment.SHARPNESS,
        Enchantment.SMITE,
        Enchantment.BANE_OF_ARTHROPODS,
        Enchantment.PROTECTION,
        Enchantment.FIRE_PROTECTION,
        Enchantment.PROJECTILE_PROTECTION,
        Enchantment.BLAST_PROTECTION,
        Enchantment.FEATHER_FALLING,
        Enchantment.POWER,
        Enchantment.PUNCH
    );

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
        ItemStack working = stack;
        String id = readId(working);
        CustomItem definition = id == null ? null : registry.get(id).orElse(null);
        if (definition == null) {
            definition = registry.getOrCreateVanilla(working.getType(), wrapperFactory);
            working = itemPdc.setIdInPlace(working, definition.id());
        }
        if (blockedItemMasker.isBlocked(working)) {
            working = blockedItemMasker.sanitizeToVanilla(working);
            definition = registry.getOrCreateVanilla(working.getType(), wrapperFactory);
        }
        final String resolvedDefinitionId = definition.id();
        Map<StatId, Double> stats = itemPdc.readStats(working).orElse(null);
        if (stats == null || stats.isEmpty()) {
            stats = computeDefinitionStats(definition);
            working = itemPdc.writeStats(working, stats);
        }
        DurabilityData durabilityData = itemPdc.readDurability(working).orElse(null);
        if (durabilityData == null) {
            durabilityData = computeDurability(definition);
            if (durabilityData != null) {
                working = itemPdc.writeDurability(working, durabilityData);
            }
        }
        Map<String, Integer> enchants = mergeEnchants(working);
        working = itemPdc.writeEnchants(working, enchants);
        working = storeTrim(working);
        final CustomItem finalDefinition = definition;
        if (shouldTagInstance(definition)) {
            Optional<UUID> existingInstanceId = itemPdc.readInstanceId(working);
            if (existingInstanceId.isEmpty()) {
                UUID instanceId = UUID.randomUUID();
                working = itemPdc.ensureInstanceId(working, instanceId);
                working = itemPdc.ensureProvenance(working, ItemCreationSource.MIGRATION, Instant.now());
            } else {
                existingInstanceId.ifPresent(instanceId -> ensureLedgerRecord(instanceId, finalDefinition.id()));
            }
        }
        working = mirrorAttributes(working, definition, stats);
        working = sh.harold.fulcrum.plugin.item.runtime.ItemSanitizer.normalize(working);
        return Optional.of(new ItemInstance(definition, working, stats, enchants, enchantRegistry, DurabilityState.from(durabilityData)));
    }

    public ItemStack applyId(ItemStack stack, String id) {
        return itemPdc.setId(stack, id);
    }

    public String readItemId(ItemStack stack) {
        return readId(stack);
    }

    public ItemStack initializeItem(CustomItem definition) {
        ItemStack base = new ItemStack(definition.material());
        ItemStack withId = itemPdc.setId(base, definition.id());
        Map<StatId, Double> stats = computeDefinitionStats(definition);
        withId = itemPdc.writeStats(withId, stats);
        DurabilityData durability = computeDurability(definition);
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
        if (!(meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta)) {
            return stack;
        }
        if (itemPdc.readTrim(stack).isPresent()) {
            armorMeta.setTrim(null);
            stack.setItemMeta(armorMeta);
            return stack;
        }
        if (!armorMeta.hasTrim()) {
            return stack;
        }
        var trim = armorMeta.getTrim();
        if (trim == null) {
            return stack;
        }
        armorMeta.setTrim(null);
        armorMeta.getPersistentDataContainer().set(
            itemPdc.keys().trimPattern(),
            org.bukkit.persistence.PersistentDataType.STRING,
            trim.getPattern().getKey().getKey()
        );
        armorMeta.getPersistentDataContainer().set(
            itemPdc.keys().trimMaterial(),
            org.bukkit.persistence.PersistentDataType.STRING,
            trim.getMaterial().getKey().getKey()
        );
        stack.setItemMeta(armorMeta);
        return stack;
    }

    private DurabilityData computeDurability(CustomItem definition) {
        DurabilityComponent component = definition.component(ComponentType.DURABILITY, DurabilityComponent.class).orElse(null);
        int max = component != null ? component.max() : definition.material().getMaxDurability();
        if (max <= 0) {
            return null;
        }
        int current = component != null ? component.seededCurrentOrMax() : max;
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
