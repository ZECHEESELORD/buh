package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;

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
    private final UsernameDisplayService usernameDisplayService;

    public StaffChatFormatter(Plugin plugin, ChatFormatService formatService, UsernameDisplayService usernameDisplayService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.formatService = formatService;
        this.useLuckPerms = formatService != null;
        this.usernameDisplayService = usernameDisplayService;
    }

    public Component format(Player sender, Component message, Audience viewer) {
        ChatFormatService.Format format = resolveFormat(sender);
        return STAFF_PREFIX.append(render(format, message, sender, viewer));
    }

    private ChatFormatService.Format resolveFormat(Player sender) {
        if (!useLuckPerms) {
            return fallbackFormat(sender);
        }
        try {
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

    private Component render(ChatFormatService.Format format, Component message, Player sender, Audience viewer) {
        TextColor chatColor = format.chatColor() == null ? NamedTextColor.WHITE : format.chatColor();
        Component coloredMessage = message.color(chatColor);
        Component prefixPart = format.prefix();
        TextColor nameColor = format.name().color();
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }
        String displayName = resolveDisplayName(sender, viewer);
        Component nameComponent = Component.text(displayName, nameColor).decoration(TextDecoration.ITALIC, false);
        if (!prefixPart.equals(Component.empty())) {
            return prefixPart.append(Component.space())
                .append(nameComponent)
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(coloredMessage);
        }
        return nameComponent
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(coloredMessage);
    }

    private String resolveDisplayName(Player sender, Audience viewer) {
        if (usernameDisplayService == null || !(viewer instanceof Player viewerPlayer)) {
            return sender.getName();
        }
        return usernameDisplayService.displayName(viewerPlayer.getUniqueId(), sender);
    }
}
