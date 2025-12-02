package sh.harold.fulcrum.plugin.item.runtime;

public record DurabilityData(int current, int max) {

    public DurabilityData {
        if (max < 0) {
            throw new IllegalArgumentException("durability max cannot be negative");
        }
    }

    public DurabilityData damage(int amount) {
        if (amount <= 0) {
            return this;
        }
        int lowered = current - amount;
        int clamped = Math.max(lowered, -max);
        return new DurabilityData(clamped, max);
    }

    public DurabilityData repair(int amount) {
        if (amount <= 0) {
            return this;
        }
        int repaired = Math.min(current + amount, max);
        return new DurabilityData(repaired, max);
    }

    public double fraction() {
        if (max <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, (double) current / (double) max));
    }

    public boolean defunct() {
        return current <= 0;
    }
}
