package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Objects;
import java.util.UUID;

public final class NametagDecorator {

    private final PlayerSettingsService settingsService;
    private final boolean pvpBadgesEnabled;

    public NametagDecorator(PlayerSettingsService settingsService) {
        this(settingsService, true);
    }

    public NametagDecorator(PlayerSettingsService settingsService, boolean pvpBadgesEnabled) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.pvpBadgesEnabled = pvpBadgesEnabled;
    }

    public Component decorateForNametag(UUID targetId, Component baseName) {
        Component decorated = UsernameDecorations.normalizeBase(baseName, NamedTextColor.WHITE);
        if (pvpBadgesEnabled) {
            decorated = UsernameDecorations.appendPvpBadge(decorated, targetId, settingsService);
        }
        return decorated;
    }

    public boolean hasNametagDecorations(UUID targetId) {
        return UsernameDecorations.hasPvpBadge(targetId, pvpBadgesEnabled);
    }
}
