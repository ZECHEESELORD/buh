package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.message.MessageService;

import java.util.logging.Level;

final class ChatListener implements Listener {

    private final Plugin plugin;
    private final ChatFormatService formatService;
    private final boolean useLuckPerms;
    private final ChatChannelService channelService;
    private final MessageService messageService;

    ChatListener(Plugin plugin, LuckPerms luckPerms, ChatChannelService channelService, MessageService messageService) {
        this.plugin = plugin;
        this.channelService = channelService;
        this.messageService = messageService;
        this.useLuckPerms = luckPerms != null;
        this.formatService = luckPerms == null ? null : new ChatFormatService(luckPerms);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatChannelService.ChatChannel channel = channelService.channel(event.getPlayer().getUniqueId());
        if (channel.isDirect()) {
            handleDirect(event, channel);
            return;
        }
        if (channel.isStaff()) {
            handleStaff(event);
            return;
        }
        if (!useLuckPerms) {
            ChatFormatService.Format fallback = new ChatFormatService.Format(
                Component.empty(),
                Component.text(event.getPlayer().getName(), NamedTextColor.WHITE),
                NamedTextColor.WHITE
            );
            event.renderer((source, sourceDisplayName, message, viewer) -> render(fallback, message));
            return;
        }
        ChatFormatService.Format format;
        try {
            format = formatService.format(event.getPlayer()).join();
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to format chat message", runtimeException);
            format = new ChatFormatService.Format(
                Component.empty(),
                Component.text(event.getPlayer().getName(), NamedTextColor.WHITE),
                NamedTextColor.WHITE
            );
        }
        ChatFormatService.Format captured = format;
        event.renderer((source, sourceDisplayName, message, viewer) -> render(captured, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        channelService.clear(event.getPlayer().getUniqueId());
    }

    private void handleDirect(AsyncChatEvent event, ChatChannelService.ChatChannel channel) {
        event.setCancelled(true);
        if (channel.directTarget() == null) {
            channelService.setAll(event.getPlayer().getUniqueId());
            return;
        }
        var target = plugin.getServer().getPlayer(channel.directTarget());
        if (target == null) {
            event.getPlayer().sendMessage(Component.text("That player is no longer online.", NamedTextColor.RED));
            channelService.setAll(event.getPlayer().getUniqueId());
            return;
        }
        messageService.sendMessage(event.getPlayer(), target, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()));
    }

    private void handleStaff(AsyncChatEvent event) {
        if (!channelService.isStaff(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(Component.text("You are no longer in staff chat.", NamedTextColor.RED));
            channelService.setAll(event.getPlayer().getUniqueId());
            return;
        }
        event.viewers().removeIf(viewer -> viewer instanceof org.bukkit.entity.Player player && !channelService.isStaff(player.getUniqueId()));
        Component staffPrefix = Component.text("Staff > ", NamedTextColor.AQUA);
        if (!useLuckPerms) {
            ChatFormatService.Format fallback = new ChatFormatService.Format(
                Component.empty(),
                Component.text(event.getPlayer().getName(), NamedTextColor.WHITE),
                NamedTextColor.WHITE
            );
            event.renderer((source, sourceDisplayName, message, viewer) -> staffPrefix.append(render(fallback, message)));
            return;
        }
        ChatFormatService.Format format;
        try {
            format = formatService.format(event.getPlayer()).join();
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.SEVERE, "Failed to format chat message", runtimeException);
            format = new ChatFormatService.Format(
                Component.empty(),
                Component.text(event.getPlayer().getName(), NamedTextColor.WHITE),
                NamedTextColor.WHITE
            );
        }
        ChatFormatService.Format captured = format;
        event.renderer((source, sourceDisplayName, message, viewer) -> staffPrefix.append(render(captured, message)));
    }

    private Component render(ChatFormatService.Format format, Component message) {
        TextColor chatColor = format.chatColor() == null ? NamedTextColor.WHITE : format.chatColor();
        Component coloredMessage = message.color(chatColor);
        Component prefixPart = format.prefix();
        if (!prefixPart.equals(Component.empty())) {
            return prefixPart.append(Component.space())
                .append(format.name())
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(coloredMessage);
        }
        return format.name()
            .append(Component.text(": ", NamedTextColor.GRAY))
            .append(coloredMessage);
    }
}
