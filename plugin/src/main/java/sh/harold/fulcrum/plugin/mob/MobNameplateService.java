package sh.harold.fulcrum.plugin.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import sh.harold.fulcrum.plugin.mob.pdc.MobPdc;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobNameplateService {

    private static final long UPDATE_COOLDOWN_MILLIS = 150L;

    private final MobPdc mobPdc;
    private final MobRegistry registry;
    private final MobDifficultyRater difficultyRater;
    private final Map<UUID, Long> recentUpdates = new ConcurrentHashMap<>();

    public MobNameplateService(MobPdc mobPdc, MobRegistry registry, MobDifficultyRater difficultyRater) {
        this.mobPdc = Objects.requireNonNull(mobPdc, "mobPdc");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.difficultyRater = Objects.requireNonNull(difficultyRater, "difficultyRater");
    }

    public void refresh(LivingEntity entity, boolean forceVisible) {
        refresh(entity, forceVisible, false);
    }

    public void refresh(LivingEntity entity, boolean forceVisible, boolean forceUpdate) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        UUID id = entity.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentUpdates.get(id);
        if (!forceUpdate && last != null && now - last < UPDATE_COOLDOWN_MILLIS) {
            return;
        }
        recentUpdates.put(id, now);

        Component label = buildLabel(entity);
        entity.customName(label);
        entity.setCustomNameVisible(forceVisible);
        mobPdc.writeNameMode(entity, MobNameMode.ENGINE);
    }

    public void restoreBaseName(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        String stored = mobPdc.readNameBase(entity).orElse(null);
        if (stored == null || stored.isBlank()) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
        } else {
            entity.customName(Component.text(stored).decoration(TextDecoration.ITALIC, false));
            entity.setCustomNameVisible(true);
        }
        mobPdc.writeNameMode(entity, MobNameMode.BASE);
        recentUpdates.remove(entity.getUniqueId());
    }

    public void forget(LivingEntity entity) {
        if (entity != null) {
            recentUpdates.remove(entity.getUniqueId());
        }
    }

    private Component buildLabel(LivingEntity entity) {
        String mobId = mobPdc.readId(entity).orElseGet(() -> entity.getType().getKey().toString());
        MobDefinition definition = registry.get(mobId).orElse(null);

        MobTier tier = mobPdc.readTier(entity)
            .or(() -> definition == null ? Optional.empty() : Optional.of(definition.tier()))
            .orElse(MobTier.VANILLA);

        Map<StatId, Double> bases = mobPdc.readStatBases(entity).orElse(Map.of());
        int level = difficultyRater.level(bases);

        Component name = resolveName(entity, definition, tier);
        Component health = healthComponent(entity);

        Component root = Component.empty();
        Component tierMarker = tierMarker(tier);
        if (!tierMarker.equals(Component.empty())) {
            root = root.append(tierMarker).append(Component.space());
        }

        Component levelLabel = Component.text("Lv " + level, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        return root
            .append(levelLabel)
            .append(Component.space())
            .append(name)
            .append(Component.space())
            .append(health)
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component resolveName(LivingEntity entity, MobDefinition definition, MobTier tier) {
        if (tier != MobTier.VANILLA && definition != null && !definition.displayName().equals(Component.empty())) {
            return definition.displayName().decoration(TextDecoration.ITALIC, false);
        }
        String stored = mobPdc.readNameBase(entity).orElse(null);
        if (stored != null && !stored.isBlank()) {
            return Component.text(stored, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
        }
        if (definition != null && !definition.displayName().equals(Component.empty())) {
            return definition.displayName().decoration(TextDecoration.ITALIC, false);
        }
        return Component.text(humanize(entity.getType()), NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private Component tierMarker(MobTier tier) {
        return switch (tier) {
            case VANILLA -> Component.empty();
            case CUSTOM -> Component.text("Custom", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false);
            case MINIBOSS -> Component.text("Miniboss", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false);
            case BOSS -> Component.text("Boss", NamedTextColor.DARK_RED).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
        };
    }

    private Component healthComponent(LivingEntity entity) {
        double current = Math.max(0.0, entity.getHealth());
        String currentLabel = formatNumber(current);
        return Component.text(currentLabel + "❤", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
    }

    private String formatNumber(double value) {
        double safe = Double.isFinite(value) ? value : 0.0;
        if (safe >= 1_000_000.0) {
            return String.format(java.util.Locale.ROOT, "%.1fm", safe / 1_000_000.0);
        }
        if (safe >= 1_000.0) {
            return String.format(java.util.Locale.ROOT, "%.1fk", safe / 1_000.0);
        }
        if (Math.abs(safe - Math.rint(safe)) < 1.0E-6) {
            return Long.toString((long) Math.rint(safe));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", safe);
    }

    private String humanize(EntityType type) {
        if (type == null) {
            return "Mob";
        }
        String[] tokens = type.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" ");
            }
            builder.append(token.substring(0, 1).toUpperCase(java.util.Locale.ROOT)).append(token.substring(1));
        }
        return builder.isEmpty() ? type.name() : builder.toString();
    }
}
