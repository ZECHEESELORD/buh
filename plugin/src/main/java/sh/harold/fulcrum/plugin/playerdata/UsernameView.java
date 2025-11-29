package sh.harold.fulcrum.plugin.playerdata;

public enum UsernameView {
    MINECRAFT("Minecraft Usernames"),
    OSU("osu!Username"),
    DISCORD("Discord Display Name");

    private final String label;

    UsernameView(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public UsernameView next() {
        return switch (this) {
            case MINECRAFT -> OSU;
            case OSU -> DISCORD;
            case DISCORD -> MINECRAFT;
        };
    }

    public static UsernameView fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return MINECRAFT;
        }
        try {
            return UsernameView.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return MINECRAFT;
        }
    }
}
