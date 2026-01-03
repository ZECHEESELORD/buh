package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;
import sh.harold.fulcrum.plugin.unlockable.ChatCosmeticPrefixService;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Formats staff chat messages so commands and chat events render identically.
 */
public final class StaffChatFormatter {

    private static final Component STAFF_PREFIX = Component.text("Staff > ", NamedTextColor.AQUA);

    private final Plugin plugin;
    private final ChatFormatService formatService;
    private final boolean useLuckPerms;
    private final ChatCosmeticPrefixService cosmeticPrefixService;
    private final UsernameDisplayService usernameDisplayService;

    public StaffChatFormatter(
        Plugin plugin,
        ChatFormatService formatService,
        ChatCosmeticPrefixService cosmeticPrefixService,
        UsernameDisplayService usernameDisplayService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.formatService = formatService;
        this.useLuckPerms = formatService != null;
        this.cosmeticPrefixService = Objects.requireNonNull(cosmeticPrefixService, "cosmeticPrefixService");
        this.usernameDisplayService = usernameDisplayService;
    }

    public Component format(Player sender, Component message, Audience viewer) {
        ChatFormatService.Format baseFormat = resolveFormat(sender);
        Component cosmeticPrefix = resolveCosmeticPrefix(sender.getUniqueId());
        ChatFormatService.Format combined = combinePrefix(baseFormat, cosmeticPrefix);
        return STAFF_PREFIX.append(render(combined, message, sender, viewer));
    }

    private ChatFormatService.Format resolveFormat(Player sender) {
        if (!useLuckPerms) {
            return fallbackFormat(sender);
        }
        try {
            if (plugin.getServer().isPrimaryThread()) {
                return formatService.format(sender).getNow(fallbackFormat(sender));
            }
            return formatService.format(sender).join();
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to format chat message", runtimeException);
            return fallbackFormat(sender);
        }
    }

    private ChatFormatService.Format fallbackFormat(Player player) {
        return new ChatFormatService.Format(
            Component.empty(),
            Component.text(player.getName(), NamedTextColor.WHITE),
            NamedTextColor.WHITE
        );
    }

    private Component resolveCosmeticPrefix(java.util.UUID playerId) {
        try {
            if (plugin.getServer().isPrimaryThread()) {
                return cosmeticPrefixService.prefix(playerId).getNow(Component.empty());
            }
            return cosmeticPrefixService.prefix(playerId).join();
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.WARNING, "Failed to resolve chat cosmetic prefix for " + playerId, runtimeException);
            return Component.empty();
        }
    }

    private ChatFormatService.Format combinePrefix(ChatFormatService.Format format, Component cosmeticPrefix) {
        Component combined = ChatCosmeticPrefixService.combinePrefixes(cosmeticPrefix, format.prefix());
        return new ChatFormatService.Format(combined, format.name(), format.chatColor());
    }

    private Component render(ChatFormatService.Format format, Component message, Player sender, Audience viewer) {
        TextColor chatColor = format.chatColor() == null ? NamedTextColor.WHITE : format.chatColor();
        Component coloredMessage = message.color(chatColor);
        Component prefixPart = format.prefix();
        TextColor nameColor = format.name().color();
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }
        Component nameComponent = resolveDisplayName(sender, viewer, nameColor);
        if (!prefixPart.equals(Component.empty())) {
            return Component.text()
                .append(prefixPart)
                .append(Component.space())
                .append(nameComponent)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(coloredMessage)
                .build();
        }
        return Component.text()
            .append(nameComponent)
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(coloredMessage)
            .build();
    }

    private Component resolveDisplayName(Player sender, Audience viewer, TextColor nameColor) {
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }
        if (usernameDisplayService == null || !(viewer instanceof Player viewerPlayer)) {
            return Component.text(sender.getName(), nameColor).decoration(TextDecoration.ITALIC, false);
        }
        return usernameDisplayService.displayComponent(viewerPlayer.getUniqueId(), sender, nameColor);
    }
}
