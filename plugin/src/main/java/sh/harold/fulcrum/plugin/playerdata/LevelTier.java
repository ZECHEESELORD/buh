package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Arrays;

public enum LevelTier {
    DEFAULT(1, NamedTextColor.GRAY),
    WHITE(16, NamedTextColor.WHITE),
    YELLOW(32, NamedTextColor.YELLOW),
    GREEN(48, NamedTextColor.GREEN),
    DARK_GREEN(64, NamedTextColor.DARK_GREEN),
    AQUA(80, NamedTextColor.AQUA),
    CYAN(96, NamedTextColor.DARK_AQUA),
    BLUE(112, NamedTextColor.BLUE),
    PINK(128, NamedTextColor.LIGHT_PURPLE),
    PURPLE(144, NamedTextColor.DARK_PURPLE),
    GOLD(160, NamedTextColor.GOLD),
    RED(176, NamedTextColor.RED),
    DARK_RED(192, NamedTextColor.DARK_RED);

    private final int minLevel;
    private final NamedTextColor color;

    LevelTier(int minLevel, NamedTextColor color) {
        this.minLevel = minLevel;
        this.color = color;
    }

    public int minLevel() {
        return minLevel;
    }

    public NamedTextColor color() {
        return color;
    }

    public static TextColor colorFor(int level) {
        int safeLevel = Math.max(0, level);
        return Arrays.stream(values())
            .filter(tier -> safeLevel >= tier.minLevel)
            .reduce((first, second) -> second)
            .map(LevelTier::color)
            .orElse(NamedTextColor.GRAY);
    }
}
