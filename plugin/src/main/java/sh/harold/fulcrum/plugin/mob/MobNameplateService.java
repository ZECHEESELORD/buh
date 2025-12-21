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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MobNameplateService {

    private static final long UPDATE_COOLDOWN_MILLIS = 150L;
    private static final float LABEL_SCALE = 0.85f;
    private static final float NAME_LABEL_OFFSET_Y = -0.12f;
    private static final float HEALTH_LABEL_OFFSET_Y = -0.32f;
    private static final float LABEL_VIEW_RANGE = 48.0f;

    private static final LabelSlot NAME_LABEL = new LabelSlot("name", NAME_LABEL_OFFSET_Y);
    private static final LabelSlot HEALTH_LABEL = new LabelSlot("health", HEALTH_LABEL_OFFSET_Y);

    private record LabelSlot(String type, float offsetY) {}

    private final Plugin plugin;
    private final MobPdc mobPdc;
    private final MobRegistry registry;
    private final MobDifficultyRater difficultyRater;
    private final NamespacedKey labelOwnerKey;
    private final NamespacedKey labelTypeKey;
    private final Map<UUID, Long> recentUpdates = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> nameLabelsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> healthLabelsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, Component> pendingNameText = new ConcurrentHashMap<>();
    private final Map<UUID, Component> pendingHealthText = new ConcurrentHashMap<>();
    private final Set<UUID> pendingNameSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingHealthSpawn = ConcurrentHashMap.newKeySet();

    public MobNameplateService(Plugin plugin, MobPdc mobPdc, MobRegistry registry, MobDifficultyRater difficultyRater) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mobPdc = Objects.requireNonNull(mobPdc, "mobPdc");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.difficultyRater = Objects.requireNonNull(difficultyRater, "difficultyRater");
        this.labelOwnerKey = new NamespacedKey(plugin, "mob-engine-label-owner");
        this.labelTypeKey = new NamespacedKey(plugin, "mob-engine-label-type");
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
        boolean renamed = baseName != null && !baseName.isBlank();

        if (!forceVisible) {
            restoreBaseName(entity);
            return;
        }

        if (renamed) {
            Component nameLine = buildNameLine(entity, definition, tier, baseName);
            entity.customName(nameLine);
            entity.setCustomNameVisible(true);
            updateLabel(entity, NAME_LABEL, nameLabelsByOwner, pendingNameText, pendingNameSpawn, null);
        } else {
            suppressVanillaName(entity);
            updateLabel(
                entity,
                NAME_LABEL,
                nameLabelsByOwner,
                pendingNameText,
                pendingNameSpawn,
                buildNameLine(entity, definition, tier, null)
            );
        }

        updateLabel(
            entity,
            HEALTH_LABEL,
            healthLabelsByOwner,
            pendingHealthText,
            pendingHealthSpawn,
            buildHealthLine(entity)
        );
        mobPdc.writeNameMode(entity, MobNameMode.ENGINE);
    }

    public void restoreBaseName(LivingEntity entity) {
        if (entity == null || entity.isDead() || !entity.isValid()) {
            return;
        }
        removeLabel(entity, nameLabelsByOwner, NAME_LABEL);
        removeLabel(entity, healthLabelsByOwner, HEALTH_LABEL);
        pendingNameText.remove(entity.getUniqueId());
        pendingHealthText.remove(entity.getUniqueId());
        pendingNameSpawn.remove(entity.getUniqueId());
        pendingHealthSpawn.remove(entity.getUniqueId());
        String stored = mobPdc.readNameBase(entity).orElse(null);
        if (stored == null || stored.isBlank()) {
            suppressVanillaName(entity);
        } else {
            entity.customName(buildBaseLine(stored));
            entity.setCustomNameVisible(true);
        }
        mobPdc.writeNameMode(entity, MobNameMode.BASE);
        recentUpdates.remove(entity.getUniqueId());
    }

    public void forget(LivingEntity entity) {
        if (entity != null) {
            recentUpdates.remove(entity.getUniqueId());
            removeLabel(entity, nameLabelsByOwner, NAME_LABEL);
            removeLabel(entity, healthLabelsByOwner, HEALTH_LABEL);
            pendingNameText.remove(entity.getUniqueId());
            pendingHealthText.remove(entity.getUniqueId());
            pendingNameSpawn.remove(entity.getUniqueId());
            pendingHealthSpawn.remove(entity.getUniqueId());
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
        String ownerRaw = container.get(labelOwnerKey, PersistentDataType.STRING);
        if (ownerRaw == null || ownerRaw.isBlank()) {
            return false;
        }
        String labelType = container.get(labelTypeKey, PersistentDataType.STRING);
        try {
            UUID ownerId = UUID.fromString(ownerRaw);
            if (NAME_LABEL.type().equals(labelType)) {
                nameLabelsByOwner.remove(ownerId, display.getUniqueId());
            } else if (HEALTH_LABEL.type().equals(labelType)) {
                healthLabelsByOwner.remove(ownerId, display.getUniqueId());
            } else {
                nameLabelsByOwner.remove(ownerId, display.getUniqueId());
                healthLabelsByOwner.remove(ownerId, display.getUniqueId());
            }
        } catch (IllegalArgumentException ignored) {
        }
        display.remove();
        return true;
    }

    private Component buildNameLine(LivingEntity entity, MobDefinition definition, MobTier tier, String customName) {
        Map<StatId, Double> bases = mobPdc.readStatBases(entity).orElse(Map.of());
        int level = difficultyRater.level(bases);

        Component name = resolveName(entity, definition, customName);

        Component root = Component.empty();
        Component tierMarker = tierMarker(tier);
        if (!tierMarker.equals(Component.empty())) {
            root = root.append(tierMarker).append(Component.space());
        }

        Component levelLabel = Component.text("Lvl" + level, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
        return root
            .append(levelLabel)
            .append(Component.space())
            .append(name)
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component buildHealthLine(LivingEntity entity) {
        return healthComponent(entity);
    }

    private Component resolveName(LivingEntity entity, MobDefinition definition, String customName) {
        Component baseName = resolveBaseName(entity, definition);
        if (customName == null || customName.isBlank()) {
            return baseName;
        }
        Component customLabel = Component.text("\"" + customName + "\"", NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false);
        Component parenthesized = Component.text("(", NamedTextColor.GRAY)
            .append(baseName)
            .append(Component.text(")", NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false);
        return customLabel
            .append(Component.space())
            .append(parenthesized)
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component resolveBaseName(LivingEntity entity, MobDefinition definition) {
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

    private Component buildBaseLine(String baseName) {
        return Component.text(baseName, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    private void suppressVanillaName(LivingEntity entity) {
        entity.customName(null);
        entity.setCustomNameVisible(false);
    }

    private void updateLabel(
        LivingEntity owner,
        LabelSlot slot,
        Map<UUID, UUID> labelMap,
        Map<UUID, Component> pendingText,
        Set<UUID> pendingSpawn,
        Component text
    ) {
        UUID ownerId = owner.getUniqueId();
        if (text == null) {
            pendingText.remove(ownerId);
            pendingSpawn.remove(ownerId);
            removeLabel(owner, labelMap, slot);
            return;
        }
        TextDisplay display = findLabel(owner, slot, labelMap);
        if (display != null) {
            display.text(text);
            pendingText.put(ownerId, text);
            return;
        }
        pendingText.put(ownerId, text);
        scheduleLabelSpawn(owner, slot, labelMap, pendingText, pendingSpawn);
    }

    private void scheduleLabelSpawn(
        LivingEntity owner,
        LabelSlot slot,
        Map<UUID, UUID> labelMap,
        Map<UUID, Component> pendingText,
        Set<UUID> pendingSpawn
    ) {
        UUID ownerId = owner.getUniqueId();
        if (!pendingSpawn.add(ownerId)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSpawn.remove(ownerId);
            if (owner.isDead() || !owner.isValid()) {
                return;
            }
            Component text = pendingText.get(ownerId);
            if (text == null) {
                return;
            }
            TextDisplay display = findLabel(owner, slot, labelMap);
            if (display == null) {
                display = spawnLabel(owner, slot, labelMap);
            }
            if (display != null) {
                display.text(text);
            }
        }, 1L);
    }

    private TextDisplay findLabel(LivingEntity owner, LabelSlot slot, Map<UUID, UUID> labelMap) {
        UUID ownerId = owner.getUniqueId();
        UUID existingId = labelMap.get(ownerId);
        if (existingId != null) {
            Entity resolved = Bukkit.getEntity(existingId);
            if (resolved instanceof TextDisplay display && display.isValid()) {
                applyLabelDefaults(display, slot);
                return display;
            }
            labelMap.remove(ownerId);
        }

        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedLabel(display, ownerId, slot)) {
                applyLabelDefaults(display, slot);
                labelMap.put(ownerId, display.getUniqueId());
                return display;
            }
        }

        return null;
    }

    private TextDisplay spawnLabel(LivingEntity owner, LabelSlot slot, Map<UUID, UUID> labelMap) {
        UUID ownerId = owner.getUniqueId();
        TextDisplay created = owner.getWorld().spawn(owner.getLocation(), TextDisplay.class, display -> {
            applyLabelDefaults(display, slot);
            PersistentDataContainer container = display.getPersistentDataContainer();
            container.set(labelOwnerKey, PersistentDataType.STRING, ownerId.toString());
            container.set(labelTypeKey, PersistentDataType.STRING, slot.type());
        });

        owner.addPassenger(created);
        labelMap.put(ownerId, created.getUniqueId());
        return created;
    }

    private void removeLabel(LivingEntity owner, Map<UUID, UUID> labelMap, LabelSlot slot) {
        if (owner == null) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        UUID labelId = labelMap.remove(ownerId);
        if (labelId != null) {
            Entity resolved = Bukkit.getEntity(labelId);
            if (resolved != null) {
                resolved.remove();
            }
        }
        for (Entity passenger : owner.getPassengers()) {
            if (passenger instanceof TextDisplay display && isOwnedLabel(display, ownerId, slot)) {
                display.remove();
            }
        }
    }

    private boolean isOwnedLabel(TextDisplay display, UUID ownerId, LabelSlot slot) {
        PersistentDataContainer container = display.getPersistentDataContainer();
        String storedOwner = container.get(labelOwnerKey, PersistentDataType.STRING);
        String storedType = container.get(labelTypeKey, PersistentDataType.STRING);
        return ownerId.toString().equals(storedOwner) && slot.type().equals(storedType);
    }

    private void applyLabelDefaults(TextDisplay display, LabelSlot slot) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setShadowed(true);
        display.setSeeThrough(false);
        display.setGravity(false);
        display.setPersistent(false);
        display.setTextOpacity((byte) 0xFF);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setViewRange(LABEL_VIEW_RANGE);
        applyDefaultTransform(display, slot.offsetY());
    }

    private void applyDefaultTransform(TextDisplay display, float offsetY) {
        org.bukkit.util.Transformation current = display.getTransformation();
        if (current == null) {
            return;
        }
        org.joml.Vector3f translation = new org.joml.Vector3f(0.0f, offsetY, 0.0f);
        org.joml.Vector3f scale = new org.joml.Vector3f(LABEL_SCALE, LABEL_SCALE, LABEL_SCALE);
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
