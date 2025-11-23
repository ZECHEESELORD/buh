package sh.harold.fulcrum.api.message.scoreboard.player;

public record ModuleOverride(String moduleId, boolean enabled) {
    public ModuleOverride {
        if (moduleId == null || moduleId.isBlank()) {
            throw new IllegalArgumentException("Module ID cannot be null or blank");
        }
    }
}
