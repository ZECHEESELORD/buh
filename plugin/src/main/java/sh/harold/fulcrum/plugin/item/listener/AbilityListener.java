package sh.harold.fulcrum.plugin.item.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import sh.harold.fulcrum.plugin.item.ability.AbilityContext;
import sh.harold.fulcrum.plugin.item.ability.AbilityDefinition;
import sh.harold.fulcrum.plugin.item.ability.AbilityService;
import sh.harold.fulcrum.plugin.item.ability.AbilityTrigger;
import sh.harold.fulcrum.plugin.item.model.AbilityComponent;
import sh.harold.fulcrum.plugin.item.model.ComponentType;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;

import java.util.Optional;

public final class AbilityListener implements Listener {

    private final AbilityService abilityService;
    private final ItemResolver resolver;

    public AbilityListener(AbilityService abilityService, ItemResolver resolver) {
        this.abilityService = abilityService;
        this.resolver = resolver;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        resolver.resolve(player.getInventory().getItemInMainHand()).ifPresent(instance -> {
            boolean defunct = instance.durability().map(sh.harold.fulcrum.plugin.item.runtime.DurabilityState::defunct).orElse(false);
            if (defunct) {
                return;
            }
            AbilityComponent abilityComponent = instance.definition().component(ComponentType.ABILITY, AbilityComponent.class).orElse(null);
            if (abilityComponent == null) {
                return;
            }
            AbilityTrigger trigger = translate(event.getAction(), player.isSneaking());
            Optional<AbilityDefinition> ability = trigger == null ? Optional.empty() : abilityComponent.abilityFor(trigger);
            if (ability.isEmpty()) {
                return;
            }
            boolean executed = abilityService.trigger(
                player,
                ability.get(),
                new AbilityContext(player, instance, EquipmentSlot.HAND)
            );
            if (executed) {
                event.setCancelled(true);
            }
        });
    }

    private AbilityTrigger translate(Action action, boolean sneaking) {
        return switch (action) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> sneaking ? AbilityTrigger.SHIFT_RIGHT_CLICK : AbilityTrigger.RIGHT_CLICK;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> sneaking ? AbilityTrigger.SHIFT_LEFT_CLICK : AbilityTrigger.LEFT_CLICK;
            default -> null;
        };
    }
}
