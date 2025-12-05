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
        registerSword(Material.DIAMOND_SWORD, 7.0, 0.35);
        registerSword(Material.NETHERITE_SWORD, 8.0, 0.42);
        registerSword(Material.GOLDEN_SWORD, 4.0);

        registerAxe(Material.WOODEN_AXE, 7.0, 0.8);
        registerAxe(Material.STONE_AXE, 9.0, 0.8);
        registerAxe(Material.IRON_AXE, 9.0, 0.9);
        registerAxe(Material.GOLDEN_AXE, 7.0, 1.0);
        registerAxe(Material.DIAMOND_AXE, 9.0, 1.0);
        registerAxe(Material.NETHERITE_AXE, 10.0, 1.0);

        registerPickaxe(Material.WOODEN_PICKAXE, 2.0, 1.2);
        registerPickaxe(Material.STONE_PICKAXE, 3.0, 1.2);
        registerPickaxe(Material.IRON_PICKAXE, 4.0, 1.2);
        registerPickaxe(Material.GOLDEN_PICKAXE, 2.0, 1.2);
        registerPickaxe(Material.DIAMOND_PICKAXE, 5.0, 1.2);
        registerPickaxe(Material.NETHERITE_PICKAXE, 6.0, 1.2);

        registerShovel(Material.WOODEN_SHOVEL, 2.5, 1.0);
        registerShovel(Material.STONE_SHOVEL, 3.5, 1.0);
        registerShovel(Material.IRON_SHOVEL, 4.5, 1.0);
        registerShovel(Material.GOLDEN_SHOVEL, 2.5, 1.0);
        registerShovel(Material.DIAMOND_SHOVEL, 5.5, 1.0);
        registerShovel(Material.NETHERITE_SHOVEL, 6.5, 1.0);

        registerHoe(Material.WOODEN_HOE, 1.0, 1.0);
        registerHoe(Material.GOLDEN_HOE, 1.0, 1.0);
        registerHoe(Material.STONE_HOE, 1.0, 2.0);
        registerHoe(Material.IRON_HOE, 1.0, 3.0);
        registerHoe(Material.DIAMOND_HOE, 1.0, 4.0);
        registerHoe(Material.NETHERITE_HOE, 1.0, 4.0);

        registerTrident();
        registerMace();

        registerArmor(Material.LEATHER_HELMET, 1.0, 0.0);
        registerArmor(Material.LEATHER_CHESTPLATE, 3.0, 0.0);
        registerArmor(Material.LEATHER_LEGGINGS, 2.0, 0.0);
        registerArmor(Material.LEATHER_BOOTS, 1.0, 0.0);

        registerArmor(Material.IRON_HELMET, 2.0, 0.0);
        registerArmor(Material.IRON_CHESTPLATE, 6.0, 0.0);
        registerArmor(Material.IRON_LEGGINGS, 5.0, 0.0);
        registerArmor(Material.IRON_BOOTS, 2.0, 0.0);

        double diamondCritPiece = 0.0375;
        double diamondHelmetCrit = 0.025;
        double diamondChestCrit = 0.06;
        double diamondLegCrit = 0.04;
        double diamondBootCrit = 0.025;
        registerArmor(Material.DIAMOND_HELMET, 3.0, diamondHelmetCrit);
        registerArmor(Material.DIAMOND_CHESTPLATE, 8.0, diamondChestCrit);
        registerArmor(Material.DIAMOND_LEGGINGS, 6.0, diamondLegCrit);
        registerArmor(Material.DIAMOND_BOOTS, 3.0, diamondBootCrit);

        double netheriteHelmetCrit = 0.0325;
        double netheriteChestCrit = 0.08;
        double netheriteLegCrit = 0.055;
        double netheriteBootCrit = 0.0325;
        registerArmor(Material.NETHERITE_HELMET, 3.0, netheriteHelmetCrit);
        registerArmor(Material.NETHERITE_CHESTPLATE, 8.0, netheriteChestCrit);
        registerArmor(Material.NETHERITE_LEGGINGS, 6.0, netheriteLegCrit);
        registerArmor(Material.NETHERITE_BOOTS, 3.0, netheriteBootCrit);

        registerArmorIfPresent("COPPER_HELMET", 2.0, 0.0);
        registerArmorIfPresent("COPPER_CHESTPLATE", 5.0, 0.0);
        registerArmorIfPresent("COPPER_LEGGINGS", 4.0, 0.0);
        registerArmorIfPresent("COPPER_BOOTS", 1.0, 0.0);

        registerToolIfPresent("COPPER_SWORD", 6.0, 1.6);
        registerAxeIfPresent("COPPER_AXE", 9.0, 0.9);
        registerPickaxeIfPresent("COPPER_PICKAXE", 4.0, 1.2);
        registerShovelIfPresent("COPPER_SHOVEL", 4.5, 1.0);
        registerHoeIfPresent("COPPER_HOE", 1.0, 3.0);
    }

    public Map<StatId, Double> statsFor(Material material) {
        Map<StatId, Double> stats = table.get(material);
        return stats == null ? Map.of() : stats;
    }

    private void registerSword(Material material, double damage) {
        register(material, damage, 1.6, 0.0);
    }

    private void registerSword(Material material, double damage, double critDamage) {
        register(material, damage, 1.6, critDamage);
    }

    private void registerAxe(Material material, double damage, double speed) {
        register(material, damage, speed, 0.0);
    }

    private void registerHoe(Material material, double damage, double speed) {
        register(material, damage, speed, 0.0);
    }

    private void registerPickaxe(Material material, double damage, double speed) {
        register(material, damage, speed, 0.0);
    }

    private void registerShovel(Material material, double damage, double speed) {
        register(material, damage, speed, 0.0);
    }

    private void registerTrident() {
        registerIfPresent("TRIDENT", 9.0, 1.1, 0.0);
    }

    private void registerMace() {
        registerIfPresent("MACE", 7.0, 0.6, 0.0);
    }

    private void registerArmor(Material material, double armor, double critDamage) {
        Map<StatId, Double> stats = new HashMap<>();
        stats.put(StatIds.ARMOR, armor);
        if (critDamage > 0.0) {
            stats.put(StatIds.CRIT_DAMAGE, critDamage);
        }
        table.put(material, Map.copyOf(stats));
    }

    private void registerArmorIfPresent(String materialName, double armor, double critDamage) {
        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            registerArmor(material, armor, critDamage);
        }
    }

    private void register(Material material, double attackDamage, double attackSpeed, double critDamage) {
        Map<StatId, Double> stats = new HashMap<>();
        stats.put(StatIds.ATTACK_DAMAGE, attackDamage);
        stats.put(StatIds.ATTACK_SPEED, attackSpeed);
        if (critDamage > 0.0) {
            stats.put(StatIds.CRIT_DAMAGE, critDamage);
        }
        table.put(material, Map.copyOf(stats));
    }

    private void registerIfPresent(String materialName, double attackDamage, double attackSpeed, double critDamage) {
        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            register(material, attackDamage, attackSpeed, critDamage);
        }
    }

    private void registerToolIfPresent(String materialName, double damage, double speed) {
        Material material = Material.matchMaterial(materialName);
        if (material != null) {
            register(material, damage, speed, 0.0);
        }
    }

    private void registerAxeIfPresent(String materialName, double damage, double speed) {
        registerToolIfPresent(materialName, damage, speed);
    }

    private void registerPickaxeIfPresent(String materialName, double damage, double speed) {
        registerToolIfPresent(materialName, damage, speed);
    }

    private void registerShovelIfPresent(String materialName, double damage, double speed) {
        registerToolIfPresent(materialName, damage, speed);
    }

    private void registerHoeIfPresent(String materialName, double damage, double speed) {
        registerToolIfPresent(materialName, damage, speed);
    }
}
