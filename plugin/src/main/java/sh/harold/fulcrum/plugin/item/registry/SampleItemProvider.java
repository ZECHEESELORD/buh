package sh.harold.fulcrum.plugin.item.registry;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.model.CustomItem;
import sh.harold.fulcrum.plugin.item.model.ItemCategory;
import sh.harold.fulcrum.plugin.item.model.ItemRarity;
import sh.harold.fulcrum.plugin.item.model.ItemTrait;
import sh.harold.fulcrum.plugin.item.model.StatsComponent;
import sh.harold.fulcrum.plugin.item.model.VisualComponent;
import sh.harold.fulcrum.plugin.item.model.LoreSection;
import sh.harold.fulcrum.stats.core.StatIds;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SampleItemProvider implements ItemDefinitionProvider {

    @Override
    public List<CustomItem> definitions() {
        CustomItem debugBlade = CustomItem.builder("fulcrum:debug_blade")
            .material(Material.DIAMOND_SWORD)
            .category(ItemCategory.SWORD)
            .traits(Set.of(ItemTrait.MELEE_ENCHANTABLE))
            .component(ComponentType.STATS, new StatsComponent(Map.of(StatIds.ATTACK_DAMAGE, 8.0)))
            .component(ComponentType.ABILITY, new AbilityComponent(Map.of(
                AbilityTrigger.RIGHT_CLICK,
                new AbilityDefinition(
                    "fulcrum:blink",
                    AbilityTrigger.RIGHT_CLICK,
                    Component.text("Blink", NamedTextColor.AQUA),
                    List.of(Component.text("A quick debug blink forward.", NamedTextColor.GRAY)),
                    Duration.ofSeconds(5),
                    "fulcrum:blink"
                )
            )))
            .component(ComponentType.VISUAL, new VisualComponent(
                Component.text("Debug Blade", NamedTextColor.AQUA),
                List.of(),
                ItemRarity.RARE
            ))
            .build();
        CustomItem mask = CustomItem.builder("fulcrum:null_item")
            .material(Material.STRUCTURE_VOID)
            .category(ItemCategory.MATERIAL)
            .loreLayout(List.of(LoreSection.HEADER, LoreSection.TAGS, LoreSection.FOOTER))
            .component(ComponentType.VISUAL, new VisualComponent(
                Component.text("Null Item", NamedTextColor.DARK_GRAY),
                List.of(
                    Component.text("Gone. Reduced to null.", NamedTextColor.GRAY),
                    Component.text("A small price to pay for balance.", NamedTextColor.GRAY)
                ),
                ItemRarity.COMMON
            ))
            .build();
        return List.of(debugBlade, mask);
    }
}
