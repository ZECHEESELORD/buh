package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.UUID;

final class UsernameDecorations {

    static final Component PVP_ENABLED_BADGE = Component.text("[☠]", NamedTextColor.RED)
        .decoration(TextDecoration.ITALIC, false);
    static final Component PVP_DISABLED_BADGE = Component.text("[☮]", NamedTextColor.GREEN)
        .decoration(TextDecoration.ITALIC, false);

    private UsernameDecorations() {
    }

    static Component normalizeBase(Component base, TextColor fallbackColor) {
        Component normalized = base == null ? Component.empty() : base;
        if (normalized.color() == null && fallbackColor != null) {
            normalized = normalized.color(fallbackColor);
        }
        return normalized.decoration(TextDecoration.ITALIC, false);
    }

    static Component appendPvpBadge(Component base, UUID targetId, PlayerSettingsService settingsService) {
        if (targetId == null || settingsService == null) {
            return base;
        }
        Component badge = settingsService.cachedPvpEnabled(targetId) ? PVP_ENABLED_BADGE : PVP_DISABLED_BADGE;
        return base.append(Component.space()).append(badge);
    }

    static boolean hasPvpBadge(UUID targetId, boolean pvpBadgesEnabled) {
        return pvpBadgesEnabled && targetId != null;
    }
}
