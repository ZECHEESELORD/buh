package sh.harold.fulcrum.plugin.item.enchant;

import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class EnchantService {

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
        int clamped = Math.max(0, level);
        Optional<EnchantDefinition> definition = registry.get(enchantId);
        if (definition.isEmpty() || clamped == 0) {
            return stack;
        }
        Map<String, Integer> enchants = new HashMap<>(itemPdc.readEnchants(stack).orElse(Map.of()));
        for (String incompatible : definition.get().incompatibleWith()) {
            enchants.remove(incompatible);
        }
        enchants.put(enchantId, clamped);
        return itemPdc.writeEnchants(stack, enchants);
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
}
