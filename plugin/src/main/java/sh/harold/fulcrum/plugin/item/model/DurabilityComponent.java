package sh.harold.fulcrum.plugin.item.model;

public record DurabilityComponent(int max, Integer seedCurrent) implements ItemComponent {

    public DurabilityComponent {
        if (max < 0) {
            throw new IllegalArgumentException("durability max cannot be negative");
        }
        if (seedCurrent != null && seedCurrent < 0) {
            throw new IllegalArgumentException("durability current cannot be negative");
        }
    }

    public int seededCurrentOrMax() {
        return seedCurrent == null ? max : seedCurrent;
    }
}
