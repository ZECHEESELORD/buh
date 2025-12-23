package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Objects;
import java.util.UUID;

public final class NametagDecorator {
    private static final NamedTextColor LEVEL_BRACKET_COLOR = NamedTextColor.DARK_GRAY;

    private final PlayerSettingsService settingsService;
    private final boolean pvpBadgesEnabled;

    public NametagDecorator(PlayerSettingsService settingsService) {
        this(settingsService, true);
    }

    public NametagDecorator(PlayerSettingsService settingsService, boolean pvpBadgesEnabled) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.pvpBadgesEnabled = pvpBadgesEnabled;
    }

    public Component decorateForNametag(UUID targetId, Component baseName, int level) {
        TextColor levelColor = LevelTier.colorFor(level);
        Component name = baseName == null ? Component.empty() : baseName.color(levelColor);
        Component decorated = buildLevelPrefix(level).append(Component.space())
            .append(UsernameDecorations.normalizeBase(name, levelColor));
        if (pvpBadgesEnabled) {
            decorated = UsernameDecorations.appendPvpBadge(decorated, targetId, settingsService);
        }
        return decorated;
    }

    public boolean hasNametagDecorations(UUID targetId) {
        return true;
    }

    private Component buildLevelPrefix(int level) {
        TextColor levelColor = LevelTier.colorFor(level);
        return Component.text("[", LEVEL_BRACKET_COLOR)
            .append(Component.text(level, levelColor))
            .append(Component.text("]", LEVEL_BRACKET_COLOR))
            .decoration(TextDecoration.ITALIC, false);
    }
}
