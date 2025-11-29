package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.UUID;

public final class ChatNameDecorator {

    private final PlayerSettingsService settingsService;
    private final boolean pvpBadgesEnabled;

    public ChatNameDecorator(PlayerSettingsService settingsService) {
        this(settingsService, true);
    }

    public ChatNameDecorator(PlayerSettingsService settingsService, boolean pvpBadgesEnabled) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.pvpBadgesEnabled = pvpBadgesEnabled;
    }

    public Component decorateForChat(UUID targetId, Component baseName, TextColor nameColor) {
        TextColor resolvedColor = nameColor == null ? NamedTextColor.WHITE : nameColor;
        Component decorated = UsernameDecorations.normalizeBase(baseName, resolvedColor);
        if (pvpBadgesEnabled) {
            decorated = UsernameDecorations.appendPvpBadge(decorated, targetId, settingsService);
        }
        return decorated;
    }

    public boolean hasChatDecorations(UUID targetId) {
        return UsernameDecorations.hasPvpBadge(targetId, pvpBadgesEnabled);
    }
}
