package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class TabNameDecorator {
    private static final NamedTextColor LEVEL_BRACKET_COLOR = NamedTextColor.DARK_GRAY;

    private final PlayerSettingsService settingsService;
    private final boolean pvpBadgesEnabled;
    private final boolean healthEnabled;

    public TabNameDecorator(PlayerSettingsService settingsService) {
        this(settingsService, true, true);
    }

    public TabNameDecorator(PlayerSettingsService settingsService, boolean pvpBadgesEnabled, boolean healthEnabled) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.pvpBadgesEnabled = pvpBadgesEnabled;
        this.healthEnabled = healthEnabled;
    }

    public Component decorateForTab(UUID targetId, Player target, Component baseName, int level) {
        TextColor levelColor = LevelTier.colorFor(level);
        Component name = baseName == null ? Component.empty() : baseName.color(levelColor);
        Component decorated = buildLevelPrefix(level).append(Component.space())
            .append(UsernameDecorations.normalizeBase(name, levelColor));
        if (pvpBadgesEnabled) {
            decorated = UsernameDecorations.appendPvpBadge(decorated, targetId, settingsService);
        }
        if (healthEnabled && target != null && !settingsService.cachedPvpEnabled(target.getUniqueId())) {
            decorated = appendHealth(decorated, target);
        }
        return decorated;
    }

    public boolean hasTabDecorations(UUID targetId, Player target) {
        return true;
    }

    private Component appendHealth(Component base, Player target) {
        int health = Math.max(0, (int) Math.ceil(target.getHealth()));
        Component heart = Component.text(health + "❤", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        return base.append(Component.space()).append(heart);
    }

    private Component buildLevelPrefix(int level) {
        TextColor levelColor = LevelTier.colorFor(level);
        return Component.text("[", LEVEL_BRACKET_COLOR)
            .append(Component.text(level, levelColor))
            .append(Component.text("]", LEVEL_BRACKET_COLOR))
            .decoration(TextDecoration.ITALIC, false);
    }
}
