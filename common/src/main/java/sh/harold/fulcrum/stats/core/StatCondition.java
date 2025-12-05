package sh.harold.fulcrum.stats.core;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Predicate describing when a stat modifier should apply.
 */
@FunctionalInterface
public interface StatCondition extends Predicate<ConditionContext> {

    StatCondition ALWAYS = new SimpleCondition("Always", context -> true);

    boolean test(ConditionContext context);

    /**
     * Human-readable description for debugging.
     */
    default String describe() {
        return "Always";
    }

    /**
     * Checks if this condition always applies.
     */
    default boolean isAlways() {
        return this == ALWAYS;
    }

    static StatCondition always() {
        return ALWAYS;
    }

    static StatCondition whenTag(String tag) {
        String lowered = tag == null ? "" : tag;
        return new SimpleCondition("Requires tag '" + lowered + "'", ctx -> ctx != null && ctx.hasTag(lowered));
    }

    record SimpleCondition(String label, java.util.function.Predicate<ConditionContext> predicate) implements StatCondition {
        @Override
        public boolean test(ConditionContext context) {
            return predicate.test(context == null ? ConditionContext.empty() : context);
        }

        @Override
        public String describe() {
            return label;
        }
    }
}
