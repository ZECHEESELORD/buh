package sh.harold.fulcrum.plugin.item.ability;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;

public record AbilityContext(
    Player player,
    ItemInstance item,
    EquipmentSlot hand
) {
}
