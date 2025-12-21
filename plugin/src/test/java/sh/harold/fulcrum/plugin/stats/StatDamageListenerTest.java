package sh.harold.fulcrum.plugin.stats;

import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sh.harold.fulcrum.stats.core.ModifierOp;
import sh.harold.fulcrum.stats.core.StatIds;
import sh.harold.fulcrum.stats.core.StatModifier;
import sh.harold.fulcrum.stats.core.StatRegistry;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatDamageListenerTest {

    private DamageType damageType(String minecraftKey) {
        NamespacedKey key = NamespacedKey.minecraft(minecraftKey);
        return (DamageType) java.lang.reflect.Proxy.newProxyInstance(
            DamageType.class.getClassLoader(),
            new Class[]{DamageType.class},
            (proxy, method, args) -> {
                if ("getKey".equals(method.getName())) {
                    return key;
                }
                if ("getTranslationKey".equals(method.getName())) {
                    return "damage." + key.getKey();
                }
                if ("getExhaustion".equals(method.getName())) {
                    return 0.0f;
                }
                if ("toString".equals(method.getName())) {
                    return key.toString();
                }
                if ("hashCode".equals(method.getName())) {
                    return key.hashCode();
                }
                if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                    return proxy == args[0];
                }
                return null;
            }
        );
    }

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

    @Test
    void spearChargeOffHandDoesNotUseMainHandDamageSources() {
        when(plugin.namespace()).thenReturn("fulcrum");

        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        lenient().when(attacker.getUniqueId()).thenReturn(attackerId);

        when(defender.getUniqueId()).thenReturn(defenderId);
        when(defender.getNoDamageTicks()).thenReturn(0);
        when(defender.getMaximumNoDamageTicks()).thenReturn(20);
        when(defender.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(damageType("spear"));
        when(event.getDamageSource()).thenReturn(damageSource);

        ItemStack spear = mock(ItemStack.class);
        when(spear.getType()).thenReturn(Material.WOODEN_SPEAR);
        when(spear.clone()).thenReturn(spear);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(attacker.getEquipment()).thenReturn(equipment);
        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(equipment.getItem(EquipmentSlot.HAND)).thenReturn(mainHand);
        when(equipment.getItem(EquipmentSlot.OFF_HAND)).thenReturn(spear);
        when(attacker.hasActiveItem()).thenReturn(true);
        when(attacker.getActiveItem()).thenReturn(spear);
        Location location = new Location(null, 0, 0, 0);
        location.setDirection(new Vector(1, 0, 0));
        when(attacker.getLocation()).thenReturn(location);
        when(attacker.getVelocity()).thenReturn(new Vector());

        StatService statService = new StatService(StatRegistry.withDefaults());
        var container = statService.getContainer(EntityKey.fromUuid(attackerId));
        container.setBase(StatIds.ATTACK_DAMAGE, 0.0);
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("buff:rage"), ModifierOp.FLAT, 5.0));
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("item:main_hand:base"), ModifierOp.FLAT, 1000.0));
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("item:main_hand:enchant:sharpness"), ModifierOp.FLAT, 500.0));

        StatDamageListener listener = new StatDamageListener(
            plugin,
            statService,
            new StatMappingConfig(32.0, 0.80, true),
            null,
            null
        );

        AtomicReference<Double> damage = new AtomicReference<>(0.0);
        when(event.getEntity()).thenReturn(defender);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(event.getDamage()).thenAnswer(invocation -> damage.get());
        doAnswer(invocation -> {
            damage.set(invocation.getArgument(0));
            return null;
        }).when(event).setDamage(anyDouble());

        listener.onEntityDamage(event);

        assertThat(damage.get()).isGreaterThanOrEqualTo(5.0);
        assertThat(damage.get()).isLessThan(1000.0);
    }

    @Test
    void spearChargeDoesNotRequireActiveItem() {
        when(plugin.namespace()).thenReturn("fulcrum");

        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        lenient().when(attacker.getUniqueId()).thenReturn(attackerId);

        when(defender.getUniqueId()).thenReturn(defenderId);
        when(defender.getNoDamageTicks()).thenReturn(0);
        when(defender.getMaximumNoDamageTicks()).thenReturn(20);
        when(defender.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(damageType("spear"));
        when(event.getDamageSource()).thenReturn(damageSource);

        ItemStack spear = mock(ItemStack.class);
        when(spear.getType()).thenReturn(Material.WOODEN_SPEAR);
        when(spear.clone()).thenReturn(spear);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(attacker.getEquipment()).thenReturn(equipment);
        ItemStack mainHand = mock(ItemStack.class);
        when(mainHand.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(equipment.getItem(EquipmentSlot.HAND)).thenReturn(mainHand);
        when(equipment.getItem(EquipmentSlot.OFF_HAND)).thenReturn(spear);
        when(attacker.hasActiveItem()).thenReturn(false);
        Location location = new Location(null, 0, 0, 0);
        location.setDirection(new Vector(1, 0, 0));
        when(attacker.getLocation()).thenReturn(location);
        when(attacker.getVelocity()).thenReturn(new Vector());

        StatService statService = new StatService(StatRegistry.withDefaults());
        var container = statService.getContainer(EntityKey.fromUuid(attackerId));
        container.setBase(StatIds.ATTACK_DAMAGE, 0.0);
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("buff:rage"), ModifierOp.FLAT, 5.0));
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("item:main_hand:base"), ModifierOp.FLAT, 1000.0));
        container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, new StatSourceId("item:main_hand:enchant:sharpness"), ModifierOp.FLAT, 500.0));

        StatDamageListener listener = new StatDamageListener(
            plugin,
            statService,
            new StatMappingConfig(32.0, 0.80, true),
            null,
            null
        );

        AtomicReference<Double> damage = new AtomicReference<>(0.0);
        when(event.getEntity()).thenReturn(defender);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(event.getDamage()).thenAnswer(invocation -> damage.get());
        doAnswer(invocation -> {
            damage.set(invocation.getArgument(0));
            return null;
        }).when(event).setDamage(anyDouble());

        listener.onEntityDamage(event);

        assertThat(damage.get()).isGreaterThanOrEqualTo(5.0);
        assertThat(damage.get()).isLessThan(1000.0);
    }

    @Test
    void spearChargeBonusUsesHorizontalSpeed() {
        when(plugin.namespace()).thenReturn("fulcrum");

        UUID attackerId = UUID.randomUUID();
        UUID defenderId = UUID.randomUUID();
        lenient().when(attacker.getUniqueId()).thenReturn(attackerId);

        when(defender.getUniqueId()).thenReturn(defenderId);
        when(defender.getNoDamageTicks()).thenReturn(0);
        when(defender.getMaximumNoDamageTicks()).thenReturn(20);
        when(defender.getType()).thenReturn(org.bukkit.entity.EntityType.ZOMBIE);

        DamageSource damageSource = mock(DamageSource.class);
        when(damageSource.getDamageType()).thenReturn(damageType("spear"));
        when(event.getDamageSource()).thenReturn(damageSource);

        ItemStack spear = mock(ItemStack.class);
        when(spear.getType()).thenReturn(Material.WOODEN_SPEAR);
        when(spear.clone()).thenReturn(spear);
        when(attacker.hasActiveItem()).thenReturn(true);
        when(attacker.getActiveItem()).thenReturn(spear);
        when(attacker.getActiveItemHand()).thenReturn(EquipmentSlot.OFF_HAND);
        Location location = new Location(null, 0, 0, 0);
        location.setDirection(new Vector(1, 0, 0));
        when(attacker.getLocation()).thenReturn(location);

        when(event.getEntity()).thenReturn(defender);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);

        StatService statService = new StatService(StatRegistry.withDefaults());
        var container = statService.getContainer(EntityKey.fromUuid(attackerId));
        container.setBase(StatIds.ATTACK_DAMAGE, 0.0);

        StatDamageListener listener = new StatDamageListener(
            plugin,
            statService,
            new StatMappingConfig(32.0, 0.80, true),
            null,
            null
        );

        AtomicReference<Double> damage = new AtomicReference<>(0.0);
        when(event.getDamage()).thenAnswer(invocation -> damage.get());
        doAnswer(invocation -> {
            damage.set(invocation.getArgument(0));
            return null;
        }).when(event).setDamage(anyDouble());

        listener.onPlayerMove(new PlayerMoveEvent(attacker, new Location(null, 0, 0, 0), new Location(null, 0.5, 0, 0)));
        listener.onEntityDamage(event);
        double first = damage.get();

        listener.onPlayerMove(new PlayerMoveEvent(attacker, new Location(null, 0, 0, 0), new Location(null, 0.6, 0, 0)));
        listener.onEntityDamage(event);
        double second = damage.get();

        assertThat(second - first).isEqualTo(1.0);
        verify(attacker, never()).setFallDistance(0.0f);
    }
}
