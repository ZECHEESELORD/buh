package sh.harold.fulcrum.plugin.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.mob.pdc.MobPdc;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobNameplateService {

    private static final long UPDATE_COOLDOWN_MILLIS = 150L;
    private static final float ENGINE_LABEL_SCALE = 0.85f;
    private static final float ENGINE_LABEL_OFFSET_Y = -0.22f;
    private static final float ENGINE_LABEL_VIEW_RANGE = 48.0f;

    private final Plugin plugin;
    private final MobPdc mobPdc;
    private final MobRegistry registry;
    private final MobDifficultyRater difficultyRater;
    private final NamespacedKey engineLabelOwnerKey;
    private final Map<UUID, Long> recentUpdates = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> engineLabelsByOwner = new ConcurrentHashMap<>();

    public MobNameplateService(Plugin plugin, MobPdc mobPdc, MobRegistry registry, MobDifficultyRater difficultyRater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mobPdc = Objects.requireNonNull(mobPdc, "mobPdc");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.difficultyRater = Objects.requireNonNull(difficultyRater, "difficultyRater");
        this.engineLabelOwnerKey = new NamespacedKey(plugin, "mob-engine-label-owner");
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

        String mobId = mobPdc.readId(entity).orElseGet(() -> entity.getType().getKey().toString());
        MobDefinition definition = registry.get(mobId).orElse(null);

        MobTier tier = mobPdc.readTier(entity)
            .or(() -> definition == null ? Optional.empty() : Optional.of(definition.tier()))
            .orElse(MobTier.VANILLA);

        String baseName = mobPdc.readNameBase(entity).orElse(null);
        boolean isNameTaggedVanilla = tier == MobTier.VANILLA && baseName != null && !baseName.isBlank();
        if (isNameTaggedVanilla) {
            ensureBaseName(entity, baseName);
            if (forceVisible) {
                TextDisplay labelEntity = ensureEngineLabel(entity);
                labelEntity.text(buildEngineLine(entity, definition, tier, true));
            } else {
                removeEngineLabel(entity);
            }
            mobPdc.writeNameMode(entity, MobNameMode.BASE);
            return;
        }

        removeEngineLabel(entity);
        entity.customName(buildEngineLine(entity, definition, tier, false));
        entity.setCustomNameVisible(forceVisible);
        mobPdc.writeNameMode(entity, MobNameMode.ENGINE);
    }

    public void restoreBaseName(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        removeEngineLabel(entity);
        String stored = mobPdc.readNameBase(entity).orElse(null);
        if (stored == null || stored.isBlank()) {
            entity.customName(null);
            entity.setCustomNameVisible(false);
        } else {
            ensureBaseName(entity, stored);
        }
        mobPdc.writeNameMode(entity, MobNameMode.BASE);
        recentUpdates.remove(entity.getUniqueId());
    }

    public void forget(LivingEntity entity) {
        if (entity != null) {
            recentUpdates.remove(entity.getUniqueId());
            removeEngineLabel(entity);
        }
    }

    public void cleanupLoadedLabels() {
        for (var world : plugin.getServer().getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                cleanupLabelEntity(display);
            }
        }
    }

    public boolean cleanupLabelEntity(Entity entity) {
        if (!(entity instanceof TextDisplay display)) {
            return false;
        }
        PersistentDataContainer container = display.getPersistentDataContainer();
        String ownerRaw = container.get(engineLabelOwnerKey, PersistentDataType.STRING);
        if (ownerRaw == null || ownerRaw.isBlank()) {
            return false;
        }
        try {
            UUID ownerId = UUID.fromString(ownerRaw);
            engineLabelsByOwner.remove(ownerId, display.getUniqueId());
        } catch (IllegalArgumentException ignored) {
        }
        display.remove();
        return true;
    }

    private Component buildEngineLine(LivingEntity entity, MobDefinition definition, MobTier tier, boolean nameBaseIsSeparateLine) {
        Map<StatId, Double> bases = mobPdc.readStatBases(entity).orElse(Map.of());
        int level = difficultyRater.level(bases);

        Component name = resolveName(entity, definition, tier, nameBaseIsSeparateLine);
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

    private Component resolveName(LivingEntity entity, MobDefinition definition, MobTier tier, boolean nameBaseIsSeparateLine) {
        if (tier != MobTier.VANILLA && definition != null && !definition.displayName().equals(Component.empty())) {
            return definition.displayName().decoration(TextDecoration.ITALIC, false);
        }
        if (!nameBaseIsSeparateLine) {
            String stored = mobPdc.readNameBase(entity).orElse(null);
            if (stored != null && !stored.isBlank()) {
                return Component.text(stored, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
            }
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

    private void ensureBaseName(LivingEntity entity, String baseName) {
        MobNameMode mode = mobPdc.readNameMode(entity).orElse(MobNameMode.BASE);
        Component current = entity.customName();
        if (mode == MobNameMode.ENGINE || current == null || current.equals(Component.empty())) {
            entity.customName(Component.text(baseName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        }
        entity.setCustomNameVisible(true);
    }

    private TextDisplay ensureEngineLabel(LivingEntity owner) {
        UUID ownerId = owner.getUniqueId();
        UUID existingId = engineLabelsByOwner.get(ownerId);
        if (existingId != null) {
            Entity resolved = Bukkit.getEntity(existingId);
            if (resolved instanceof TextDisplay display && display.isValid()) {
                return display;
            }
            engineLabelsByOwner.remove(ownerId);
        }

        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedEngineLabel(display, ownerId)) {
                engineLabelsByOwner.put(ownerId, display.getUniqueId());
                return display;
            }
        }

        TextDisplay created = owner.getWorld().spawn(owner.getLocation(), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setDefaultBackground(false);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setGravity(false);
            display.setPersistent(false);
            display.setTextOpacity((byte) 0xFF);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setViewRange(ENGINE_LABEL_VIEW_RANGE);
            display.getPersistentDataContainer().set(engineLabelOwnerKey, PersistentDataType.STRING, ownerId.toString());
            applyDefaultTransform(display);
        });

        owner.addPassenger(created);
        engineLabelsByOwner.put(ownerId, created.getUniqueId());
        return created;
    }

    private void removeEngineLabel(LivingEntity owner) {
        if (owner == null) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        UUID labelId = engineLabelsByOwner.remove(ownerId);
        if (labelId != null) {
            Entity resolved = Bukkit.getEntity(labelId);
            if (resolved != null) {
                resolved.remove();
            }
        }
        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedEngineLabel(display, ownerId)) {
                display.remove();
            }
        }
    }

    private boolean isOwnedEngineLabel(TextDisplay display, UUID ownerId) {
        String stored = display.getPersistentDataContainer().get(engineLabelOwnerKey, PersistentDataType.STRING);
        return ownerId.toString().equals(stored);
    }

    private void applyDefaultTransform(TextDisplay display) {
        org.bukkit.util.Transformation current = display.getTransformation();
        if (current == null) {
            return;
        }
        org.joml.Vector3f translation = new org.joml.Vector3f(0.0f, ENGINE_LABEL_OFFSET_Y, 0.0f);
        org.joml.Vector3f scale = new org.joml.Vector3f(ENGINE_LABEL_SCALE, ENGINE_LABEL_SCALE, ENGINE_LABEL_SCALE);
        display.setTransformation(new org.bukkit.util.Transformation(translation, current.getLeftRotation(), scale, current.getRightRotation()));
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
