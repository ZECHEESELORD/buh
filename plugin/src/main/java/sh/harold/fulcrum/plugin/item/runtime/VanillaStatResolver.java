package sh.harold.fulcrum.plugin.item.runtime;

import org.bukkit.Material;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatIds;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class VanillaStatResolver {

    private final Map<Material, Map<StatId, Double>> table = new EnumMap<>(Material.class);

    public VanillaStatResolver() {
        registerSword(Material.WOODEN_SWORD, 4.0);
        registerSword(Material.STONE_SWORD, 5.0);
        registerSword(Material.IRON_SWORD, 6.0);
        registerSword(Material.DIAMOND_SWORD, 7.0);
        registerSword(Material.NETHERITE_SWORD, 8.0);

        registerAxe(Material.WOODEN_AXE, 7.0);
        registerAxe(Material.STONE_AXE, 9.0);
        registerAxe(Material.IRON_AXE, 9.0);
        registerAxe(Material.DIAMOND_AXE, 9.0);
        registerAxe(Material.NETHERITE_AXE, 10.0);

        registerHoe(Material.WOODEN_HOE);
        registerHoe(Material.STONE_HOE);
        registerHoe(Material.IRON_HOE);
        registerHoe(Material.DIAMOND_HOE);
        registerHoe(Material.NETHERITE_HOE);

        registerArmor(Material.LEATHER_HELMET, 1.0);
        registerArmor(Material.LEATHER_CHESTPLATE, 3.0);
        registerArmor(Material.LEATHER_LEGGINGS, 2.0);
        registerArmor(Material.LEATHER_BOOTS, 1.0);

        registerArmor(Material.IRON_HELMET, 2.0);
        registerArmor(Material.IRON_CHESTPLATE, 6.0);
        registerArmor(Material.IRON_LEGGINGS, 5.0);
        registerArmor(Material.IRON_BOOTS, 2.0);

        registerArmor(Material.DIAMOND_HELMET, 3.0);
        registerArmor(Material.DIAMOND_CHESTPLATE, 8.0);
        registerArmor(Material.DIAMOND_LEGGINGS, 6.0);
        registerArmor(Material.DIAMOND_BOOTS, 3.0);

        registerArmor(Material.NETHERITE_HELMET, 3.0);
        registerArmor(Material.NETHERITE_CHESTPLATE, 8.0);
        registerArmor(Material.NETHERITE_LEGGINGS, 6.0);
        registerArmor(Material.NETHERITE_BOOTS, 3.0);
    }

    public Map<StatId, Double> statsFor(Material material) {
        Map<StatId, Double> stats = table.get(material);
        return stats == null ? Map.of() : stats;
    }

    private void registerSword(Material material, double damage) {
        register(material, damage, 1.6);
    }

    private void registerAxe(Material material, double damage) {
        register(material, damage, 1.0);
    }

    private void registerHoe(Material material) {
        register(material, 1.0, 4.0);
    }

    private void registerArmor(Material material, double armor) {
        Map<StatId, Double> stats = new HashMap<>();
        stats.put(StatIds.ARMOR, armor);
        table.put(material, Map.copyOf(stats));
    }

    private void register(Material material, double attackDamage, double attackSpeed) {
        Map<StatId, Double> stats = new HashMap<>();
        stats.put(StatIds.ATTACK_DAMAGE, attackDamage);
        stats.put(StatIds.ATTACK_SPEED, attackSpeed);
        table.put(material, Map.copyOf(stats));
    }
}
