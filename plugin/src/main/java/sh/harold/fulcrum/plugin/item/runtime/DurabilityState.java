package sh.harold.fulcrum.plugin.item.runtime;

import net.kyori.adventure.text.format.NamedTextColor;

public record DurabilityState(DurabilityData data, String grade, double percent, NamedTextColor color) {

    public static DurabilityState from(DurabilityData data) {
        if (data == null) {
            return null;
        }
        double percent = data.max() <= 0 ? 100.0 : Math.max(0.0, Math.min(100.0, (data.current() * 100.0) / data.max()));
        Grade grade = Grade.forPercent(data.current(), percent);
        return new DurabilityState(data, grade.label, percent, grade.color);
    }

    public boolean defunct() {
        return data != null && data.defunct();
    }

    public boolean brokenOrLower() {
        Grade grade = Grade.forPercent(data.current(), percent);
        return grade.isBrokenOrWorse;
    }

    public int displayedCurrent() {
        return Math.max(0, data.current());
    }

    private enum Grade {
        PERFECT("PERFECT", 100.0, NamedTextColor.GREEN, false),
        LIGHTLY_USED("LIGHTLY USED", 80.0, NamedTextColor.GREEN, false),
        STURDY("STURDY", 50.0, NamedTextColor.YELLOW, false),
        WEATHERED("WEATHERED", 20.0, NamedTextColor.GOLD, false),
        FALLING_APART("FALLING APART", 1.0, NamedTextColor.RED, false),
        DEFUNCT("DEFUNCT", Double.NEGATIVE_INFINITY, NamedTextColor.DARK_GRAY, true);

        private final String label;
        private final double threshold;
        private final NamedTextColor color;
        private final boolean isBrokenOrWorse;

        Grade(String label, double threshold, NamedTextColor color, boolean isBrokenOrWorse) {
            this.label = label;
            this.threshold = threshold;
            this.color = color;
            this.isBrokenOrWorse = isBrokenOrWorse;
        }

        private static Grade forPercent(int current, double percent) {
            if (current <= 0) {
                return DEFUNCT;
            }
            for (Grade grade : values()) {
                if (grade == DEFUNCT) {
                    continue;
                }
                if (percent >= grade.threshold) {
                    return grade;
                }
            }
            return DEFUNCT;
        }
    }
}
