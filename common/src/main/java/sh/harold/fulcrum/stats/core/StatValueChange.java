package sh.harold.fulcrum.stats.core;

public record StatValueChange(StatId statId, double oldValue, double newValue) {

    public boolean hasChanged(double epsilon) {
        return Math.abs(newValue - oldValue) > epsilon;
    }
}
