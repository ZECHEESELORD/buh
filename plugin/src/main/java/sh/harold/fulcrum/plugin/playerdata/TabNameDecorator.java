package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class TabNameDecorator {

    private static final NamedTextColor TAB_COLOR = NamedTextColor.WHITE;

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

    public Component decorateForTab(UUID targetId, Player target, Component baseName) {
        Component decorated = UsernameDecorations.normalizeBase(baseName, TAB_COLOR);
        if (pvpBadgesEnabled) {
            decorated = UsernameDecorations.appendPvpBadge(decorated, targetId, settingsService);
        }
        if (healthEnabled && target != null && !settingsService.cachedPvpEnabled(target.getUniqueId())) {
            decorated = appendHealth(decorated, target);
        }
        return decorated;
    }

    public boolean hasTabDecorations(UUID targetId, Player target) {
        if (UsernameDecorations.hasPvpBadge(targetId, pvpBadgesEnabled)) {
            return true;
        }
        return healthEnabled && target != null && !settingsService.cachedPvpEnabled(target.getUniqueId());
    }

    private Component appendHealth(Component base, Player target) {
        int health = Math.max(0, (int) Math.ceil(target.getHealth()));
        Component heart = Component.text(health + "❤", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false);
        return base.append(Component.space()).append(heart);
    }
}
