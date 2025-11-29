package sh.harold.fulcrum.plugin.playerdata;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Objects;
import java.util.UUID;

public final class UsernameBaseNameResolver {

    private final PlayerSettingsService settingsService;
    private final LinkedAccountService linkedAccountService;

    public UsernameBaseNameResolver(PlayerSettingsService settingsService, LinkedAccountService linkedAccountService) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.linkedAccountService = Objects.requireNonNull(linkedAccountService, "linkedAccountService");
    }

    public BaseName resolve(UUID viewerId, UUID targetId, String vanillaName) {
        Objects.requireNonNull(viewerId, "viewerId");
        UsernameView view = settingsService.cachedUsernameView(viewerId);
        return resolve(view, targetId, vanillaName);
    }

    public BaseName resolve(UsernameView view, UUID targetId, String vanillaName) {
        return resolveInternal(view, targetId, vanillaName, false);
    }

    public BaseName resolveWithBestAliasFallback(UsernameView view, UUID targetId, String vanillaName) {
        return resolveInternal(view, targetId, vanillaName, true);
    }

    private BaseName resolveInternal(UsernameView view, UUID targetId, String vanillaName, boolean allowBestAlias) {
        UsernameView effectiveView = view == null ? UsernameView.MINECRAFT : view;
        String resolved = switch (effectiveView) {
            case OSU -> linkedAccountService.osuUsername(targetId).orElse(null);
            case DISCORD -> linkedAccountService.discordDisplayName(targetId).orElse(null);
            case MINECRAFT -> null;
        };
        if ((resolved == null || resolved.isBlank()) && allowBestAlias) {
            resolved = linkedAccountService.bestAlias(targetId).orElse(null);
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = vanillaName;
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = targetId != null ? targetId.toString().substring(0, 8) : "Player";
        }
        Component component = Component.text(resolved)
            .decoration(TextDecoration.ITALIC, false);
        return new BaseName(resolved, component);
    }

    public record BaseName(String value, Component component) {
    }
}
