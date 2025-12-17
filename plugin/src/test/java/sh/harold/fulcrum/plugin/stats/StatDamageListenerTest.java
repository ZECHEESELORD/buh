package sh.harold.fulcrum.plugin.stats;

import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatDamageListenerTest {

    @Mock
    private Plugin plugin;

    @Mock
    private Player attacker;

    @Mock
    private LivingEntity defender;

    @Mock
    private Firework firework;

    @Mock
    private org.bukkit.event.entity.EntityDamageByEntityEvent event;

    @Test
    void fireworkRocketDamageUsesVanillaEventDamage() {
        when(plugin.namespace()).thenReturn("fulcrum");

        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        lenient().when(attacker.getUniqueId()).thenReturn(attackerId);

        when(defender.getUniqueId()).thenReturn(defenderId);
        when(defender.getNoDamageTicks()).thenReturn(0);
        when(defender.getMaximumNoDamageTicks()).thenReturn(20);
        when(defender.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        when(firework.getShooter()).thenReturn(attacker);

        StatService statService = new StatService(StatRegistry.withDefaults());
        statService.getContainer(EntityKey.fromUuid(attackerId)).setBase(StatIds.ATTACK_DAMAGE, 200.0);

        StatDamageListener listener = new StatDamageListener(
            plugin,
            statService,
            new StatMappingConfig(32.0, 0.80, true),
            null,
            null
        );

        AtomicReference<Double> damage = new AtomicReference<>(6.0);
        when(event.getEntity()).thenReturn(defender);
        when(event.getDamager()).thenReturn(firework);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
        when(event.getDamage()).thenAnswer(invocation -> damage.get());
        doAnswer(invocation -> {
            damage.set(invocation.getArgument(0));
            return null;
        }).when(event).setDamage(anyDouble());

        listener.onEntityDamage(event);

        assertThat(damage.get()).isEqualTo(6.0);
    }
}
