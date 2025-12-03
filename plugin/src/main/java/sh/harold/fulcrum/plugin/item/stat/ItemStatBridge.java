package sh.harold.fulcrum.plugin.item.stat;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.model.SlotGroup;
import sh.harold.fulcrum.plugin.item.runtime.ItemInstance;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatContainer;
import sh.harold.fulcrum.stats.core.StatId;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.Map;
import java.util.Objects;

public final class ItemStatBridge {

    private final ItemResolver resolver;
    private final StatService statService;

    public ItemStatBridge(ItemResolver resolver, StatService statService) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.statService = Objects.requireNonNull(statService, "statService");
    }

    public void refreshPlayer(Player player) {
        EntityKey key = EntityKey.fromUuid(player.getUniqueId());
        StatContainer container = statService.getContainer(key);
        apply(container, SlotGroup.MAIN_HAND, player.getInventory().getItemInMainHand());
        apply(container, SlotGroup.OFF_HAND, player.getInventory().getItemInOffHand());
        apply(container, SlotGroup.HELMET, player.getInventory().getHelmet());
        apply(container, SlotGroup.CHESTPLATE, player.getInventory().getChestplate());
        apply(container, SlotGroup.LEGGINGS, player.getInventory().getLeggings());
        apply(container, SlotGroup.BOOTS, player.getInventory().getBoots());
    }

    private void apply(StatContainer container, SlotGroup slot, ItemStack stack) {
        String slotPrefix = "item:" + slot.name().toLowerCase();
        clearSlotSources(container, slotPrefix);
        if (slot == SlotGroup.MAIN_HAND && (stack == null || stack.getType().isAir())) {
            StatSourceId sourceId = new StatSourceId(slotPrefix + ":empty");
            container.addModifier(new StatModifier(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, sourceId, ModifierOp.FLAT, 4.0));
            return;
        }
        resolver.resolve(stack).ifPresent(instance -> {
            boolean defunct = instance.durability().map(sh.harold.fulcrum.plugin.item.runtime.DurabilityState::defunct).orElse(false);
            if (defunct) {
                return;
            }
            if (instance.definition().category().defaultSlot() != slot) {
                return;
            }
            final boolean[] hasAttackSpeed = {false};
            instance.statSources().forEach((source, values) -> {
                StatSourceId sourceId = new StatSourceId(slotPrefix + ":" + source);
                for (Map.Entry<StatId, Double> entry : values.entrySet()) {
                    if (entry.getKey().equals(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED) && Double.compare(entry.getValue(), 0.0) != 0) {
                        hasAttackSpeed[0] = true;
                    }
                    container.addModifier(new StatModifier(entry.getKey(), sourceId, ModifierOp.FLAT, entry.getValue()));
                }
            });
            if (slot == SlotGroup.MAIN_HAND && !hasAttackSpeed[0]) {
                StatSourceId sourceId = new StatSourceId(slotPrefix + ":default");
                container.addModifier(new StatModifier(sh.harold.fulcrum.stats.core.StatIds.ATTACK_SPEED, sourceId, ModifierOp.FLAT, 4.0));
            }
        });
    }

    private void clearSlotSources(StatContainer container, String slotPrefix) {
        container.debugView().forEach(snapshot -> snapshot.modifiers().forEach((op, bySource) -> {
            bySource.keySet().stream()
                .filter(sourceId -> sourceId.value().startsWith(slotPrefix))
                .forEach(container::clearSource);
        }));
    }
}
