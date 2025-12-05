package sh.harold.fulcrum.stats.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class StatInstance {

    private final StatDefinition definition;
    private final EnumMap<ModifierOp, List<StatModifier>> modifiers;
    private Double baseOverride;
    private double finalValue;
    private boolean dirty = true;

    public StatInstance(StatDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.modifiers = new EnumMap<>(ModifierOp.class);
        for (ModifierOp op : ModifierOp.values()) {
            modifiers.put(op, new ArrayList<>());
        }
        this.finalValue = definition.baseValue();
    }

    public StatDefinition definition() {
        return definition;
    }

    public void setBaseValue(double baseValue) {
        this.baseOverride = baseValue;
        markDirty();
    }

    public void addModifier(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        if (!modifier.statId().equals(definition.id())) {
            throw new IllegalArgumentException("Modifier stat id " + modifier.statId() + " does not match instance id " + definition.id());
        }
        modifiers.get(modifier.op()).add(modifier);
        markDirty();
    }

    public boolean removeModifiersFromSource(StatSourceId sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        boolean removed = false;
        for (ModifierOp op : ModifierOp.values()) {
            List<StatModifier> opModifiers = modifiers.get(op);
            removed |= opModifiers.removeIf(modifier -> modifier.sourceId().equals(sourceId));
        }
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public double getFinalValue() {
        if (dirty) {
            finalValue = recompute();
            dirty = false;
        }
        return finalValue;
    }

    public double compute(ConditionContext context) {
        if (context == null || context.isEmpty()) {
            return getFinalValue();
        }
        return recomputeDefault(context);
    }

    public StatSnapshot snapshot() {
        Map<ModifierOp, Map<StatSourceId, List<StatModifier>>> grouped = new EnumMap<>(ModifierOp.class);
        for (ModifierOp op : ModifierOp.values()) {
            Map<StatSourceId, List<StatModifier>> bySource = modifiers.get(op).stream()
                .collect(Collectors.groupingBy(
                    StatModifier::sourceId,
                    () -> new TreeMap<>(),
                    Collectors.toList()
                ));
            Map<StatSourceId, List<StatModifier>> immutableBySource = bySource.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
            grouped.put(op, Map.copyOf(immutableBySource));
        }

        return new StatSnapshot(definition.id(), effectiveBase(), getFinalValue(), Map.copyOf(grouped));
    }

    public boolean hasCustomizations() {
        if (baseOverride != null && Double.compare(baseOverride, definition.baseValue()) != 0) {
            return true;
        }
        for (ModifierOp op : ModifierOp.values()) {
            if (!modifiers.get(op).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    double effectiveBase() {
        return baseOverride != null ? baseOverride : definition.baseValue();
    }

    private void markDirty() {
        dirty = true;
    }

    private double recompute() {
        return switch (definition.stackingModel()) {
            case DEFAULT -> recomputeDefault(ConditionContext.empty());
        };
    }

    private double recomputeDefault(ConditionContext context) {
        double base = effectiveBase();

        double flatSum = base;
        for (StatModifier modifier : applicable(modifiers.get(ModifierOp.FLAT), context)) {
            flatSum += modifier.value();
        }

        double percentAddFactor = 1.0;
        for (StatModifier modifier : applicable(modifiers.get(ModifierOp.PERCENT_ADD), context)) {
            percentAddFactor += modifier.value();
        }
        double intermediate = flatSum * percentAddFactor;

        double multFactor = 1.0;
        for (StatModifier modifier : applicable(modifiers.get(ModifierOp.PERCENT_MULT), context)) {
            multFactor *= 1.0 + modifier.value();
        }
        double result = intermediate * multFactor;

        return clamp(result, definition.minValue(), definition.maxValue());
    }

    private java.util.List<StatModifier> applicable(java.util.List<StatModifier> candidates, ConditionContext context) {
        ConditionContext ctx = context == null ? ConditionContext.empty() : context;
        return candidates.stream()
            .filter(modifier -> modifier.condition() == null || modifier.condition().test(ctx))
            .toList();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
