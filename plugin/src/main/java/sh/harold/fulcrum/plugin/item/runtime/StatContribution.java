package sh.harold.fulcrum.plugin.item.runtime;

import sh.harold.fulcrum.stats.core.StatCondition;

public record StatContribution(double value, StatCondition condition) {

    public StatContribution {
        if (condition == null) {
            condition = StatCondition.always();
        }
    }

    public static StatContribution of(double value) {
        return new StatContribution(value, StatCondition.always());
    }
}
