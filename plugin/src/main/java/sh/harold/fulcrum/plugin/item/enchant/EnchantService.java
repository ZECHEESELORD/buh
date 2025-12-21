package sh.harold.fulcrum.plugin.item.enchant;

import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EnchantService {

    private static final Map<String, Enchantment> VANILLA_ENCHANTS = buildVanillaEnchantIds();
    private static final Set<Enchantment> OVERRIDDEN_ENCHANTS = buildOverriddenEnchants();

    private final EnchantRegistry registry;
    private final ItemPdc itemPdc;

    public EnchantService(EnchantRegistry registry, ItemPdc itemPdc) {
        this.registry = registry;
        this.itemPdc = itemPdc;
    }

    public ItemStack applyEnchant(ItemStack stack, String enchantId, int level) {
        if (stack == null || enchantId == null) {
            return stack;
        }
        ItemStack working = stack;
        int clamped = Math.max(0, level);
        Optional<EnchantDefinition> definition = registry.get(enchantId);
        if (definition.isEmpty() || clamped == 0) {
            return working;
        }
        Map<String, Integer> enchants = new HashMap<>(itemPdc.readEnchants(working).orElse(Map.of()));
        ItemMeta meta = working.getItemMeta();
        boolean metaChanged = false;
        for (String incompatible : definition.get().incompatibleWith()) {
            enchants.remove(incompatible);
            metaChanged |= removeVanillaEnchant(meta, incompatible);
        }
        if (metaChanged) {
            working.setItemMeta(meta);
        }
        enchants.put(enchantId, clamped);
        working = itemPdc.writeEnchants(working, enchants);
        return applyVanillaEnchant(working, enchantId, clamped);
    }

    public ItemStack clearEnchants(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        return itemPdc.clearEnchants(stack);
    }

    public EnchantRegistry registry() {
        return registry;
    }

    private ItemStack applyVanillaEnchant(ItemStack stack, String enchantId, int level) {
        Enchantment vanilla = VANILLA_ENCHANTS.get(enchantId);
        if (vanilla == null || OVERRIDDEN_ENCHANTS.contains(vanilla) || stack == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean metaChanged;
        if (meta instanceof EnchantmentStorageMeta storage) {
            metaChanged = storage.addStoredEnchant(vanilla, level, true);
        } else {
            metaChanged = meta.addEnchant(vanilla, level, true);
        }
        if (metaChanged) {
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean removeVanillaEnchant(ItemMeta meta, String enchantId) {
        if (meta == null) {
            return false;
        }
        Enchantment vanilla = VANILLA_ENCHANTS.get(enchantId);
        if (vanilla == null) {
            return false;
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            return storage.removeStoredEnchant(vanilla);
        }
        return meta.removeEnchant(vanilla);
    }

    private static Map<String, Enchantment> buildVanillaEnchantIds() {
        Map<String, Enchantment> enchants = new LinkedHashMap<>();
        enchants.put("fulcrum:sharpness", Enchantment.SHARPNESS);
        enchants.put("fulcrum:smite", Enchantment.SMITE);
        enchants.put("fulcrum:bane_of_arthropods", Enchantment.BANE_OF_ARTHROPODS);
        enchants.put("fulcrum:protection", Enchantment.PROTECTION);
        enchants.put("fulcrum:fire_protection", Enchantment.FIRE_PROTECTION);
        enchants.put("fulcrum:projectile_protection", Enchantment.PROJECTILE_PROTECTION);
        enchants.put("fulcrum:blast_protection", Enchantment.BLAST_PROTECTION);
        enchants.put("fulcrum:feather_falling", Enchantment.FEATHER_FALLING);
        enchants.put("fulcrum:power", Enchantment.POWER);
        enchants.put("fulcrum:punch", Enchantment.PUNCH);
        enchants.put("fulcrum:knockback", Enchantment.KNOCKBACK);
        enchants.put("fulcrum:looting", Enchantment.LOOTING);
        enchants.put("fulcrum:sweeping_edge", Enchantment.SWEEPING_EDGE);
        enchants.put("fulcrum:fire_aspect", Enchantment.FIRE_ASPECT);
        enchants.put("fulcrum:flame", Enchantment.FLAME);
        enchants.put("fulcrum:infinity", Enchantment.INFINITY);
        enchants.put("fulcrum:loyalty", Enchantment.LOYALTY);
        enchants.put("fulcrum:channeling", Enchantment.CHANNELING);
        enchants.put("fulcrum:riptide", Enchantment.RIPTIDE);
        enchants.put("fulcrum:impaling", Enchantment.IMPALING);
        enchants.put("fulcrum:multishot", Enchantment.MULTISHOT);
        enchants.put("fulcrum:piercing", Enchantment.PIERCING);
        enchants.put("fulcrum:quick_charge", Enchantment.QUICK_CHARGE);
        enchants.put("fulcrum:depth_strider", Enchantment.DEPTH_STRIDER);
        enchants.put("fulcrum:frost_walker", Enchantment.FROST_WALKER);
        enchants.put("fulcrum:soul_speed", Enchantment.SOUL_SPEED);
        enchants.put("fulcrum:swift_sneak", Enchantment.SWIFT_SNEAK);
        enchants.put("fulcrum:aqua_affinity", Enchantment.AQUA_AFFINITY);
        enchants.put("fulcrum:respiration", Enchantment.RESPIRATION);
        enchants.put("fulcrum:unbreaking", Enchantment.UNBREAKING);
        enchants.put("fulcrum:mending", Enchantment.MENDING);
        enchants.put("fulcrum:silk_touch", Enchantment.SILK_TOUCH);
        enchants.put("fulcrum:fortune", Enchantment.FORTUNE);
        enchants.put("fulcrum:lure", Enchantment.LURE);
        enchants.put("fulcrum:luck_of_the_sea", Enchantment.LUCK_OF_THE_SEA);
        enchants.put("fulcrum:thorns", Enchantment.THORNS);
        enchants.put("fulcrum:curse_of_binding", Enchantment.BINDING_CURSE);
        enchants.put("fulcrum:curse_of_vanishing", Enchantment.VANISHING_CURSE);
        enchants.put("fulcrum:efficiency", Enchantment.EFFICIENCY);
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("density"))).ifPresent(enchant -> enchants.put("fulcrum:density", enchant));
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("breach"))).ifPresent(enchant -> enchants.put("fulcrum:breach", enchant));
        Optional.ofNullable(Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"))).ifPresent(enchant -> enchants.put("fulcrum:wind_burst", enchant));
        return Map.copyOf(enchants);
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
        return Set.copyOf(enchants);
    }
}
