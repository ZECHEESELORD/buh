package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.audience.Audience;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.message.MessageService;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryEntry;
import sh.harold.fulcrum.plugin.playerdata.PlayerDirectoryService;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;

import java.util.Objects;
import java.util.logging.Level;

final class ChatListener implements Listener {

    private final Plugin plugin;
    private final ChatFormatService formatService;
    private final boolean useLuckPerms;
    private final ChatChannelService channelService;
    private final MessageService messageService;
    private final StaffChatFormatter staffChatFormatter;
    private final UsernameDisplayService usernameDisplayService;
    private final PlayerDirectoryService playerDirectoryService;

    ChatListener(
        Plugin plugin,
        ChatFormatService formatService,
        ChatChannelService channelService,
        MessageService messageService,
        StaffChatFormatter staffChatFormatter,
        UsernameDisplayService usernameDisplayService,
        PlayerDirectoryService playerDirectoryService
    ) {
        this.plugin = plugin;
        this.channelService = channelService;
        this.messageService = messageService;
        this.formatService = formatService;
        this.useLuckPerms = formatService != null;
        this.staffChatFormatter = Objects.requireNonNull(staffChatFormatter, "staffChatFormatter");
        this.usernameDisplayService = usernameDisplayService;
        this.playerDirectoryService = Objects.requireNonNull(playerDirectoryService, "playerDirectoryService");
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
        Component tooltip = resolveTooltip(event.getPlayer());
        if (!useLuckPerms) {
            ChatFormatService.Format fallback = new ChatFormatService.Format(
                Component.empty(),
                Component.text(event.getPlayer().getName(), NamedTextColor.WHITE),
                NamedTextColor.WHITE
            );
            event.renderer((source, sourceDisplayName, message, viewer) -> render(fallback, message, source, viewer, tooltip));
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
        event.renderer((source, sourceDisplayName, message, viewer) -> render(captured, message, source, viewer, tooltip));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        channelService.clear(event.getPlayer().getUniqueId());
        playerDirectoryService.evict(event.getPlayer().getUniqueId());
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
        event.renderer((source, sourceDisplayName, message, viewer) -> staffChatFormatter.format(event.getPlayer(), event.message(), viewer));
    }

    private Component render(ChatFormatService.Format format, Component message, org.bukkit.entity.Player source, Audience viewer, Component tooltip) {
        TextColor chatColor = format.chatColor() == null ? NamedTextColor.WHITE : format.chatColor();
        Component coloredMessage = message.color(chatColor);
        Component prefixPart = format.prefix();
        TextColor nameColor = format.name().color();
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }
        Component nameComponent = resolveDisplayName(source, viewer, nameColor);
        if (tooltip != null) {
            nameComponent = nameComponent.hoverEvent(HoverEvent.showText(tooltip));
        }
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

    private Component resolveTooltip(org.bukkit.entity.Player source) {
        try {
            PlayerDirectoryEntry entry = playerDirectoryService.loadEntry(source.getUniqueId())
                .toCompletableFuture()
                .join();
            if (entry == null) {
                return null;
            }
            boolean online = plugin.getServer().getPlayer(entry.id()) != null;
            return buildTooltip(entry, online);
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.WARNING, "Failed to build chat tooltip for " + source.getUniqueId(), runtimeException);
            return null;
        }
    }

    private Component buildTooltip(PlayerDirectoryEntry entry, boolean online) {
        Component newline = Component.newline();
        var builder = Component.text();
        builder.append(Component.text(entry.username(), NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        builder.append(newline);
        builder.append(labelledValue("UUID", Component.text(entry.shortId(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        builder.append(newline);
        builder.append(labelledValue("Playtime", Component.text(entry.playtimeLabel(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        builder.append(newline);
        builder.append(labelledValue("First join", Component.text(entry.firstJoinLabel(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        builder.append(newline);
        builder.append(labelledValue("Last seen", Component.text(entry.lastSeenLabel(online), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        builder.append(newline);
        builder.append(newline);
        builder.append(labelledValue("PvP", pvpLabel(entry.pvpEnabled())));
        builder.append(newline);
        builder.append(newline);
        builder.append(labelledValue("osu! username", osuValue(entry.osuUsernameLabel(), entry.hasOsuUsername())));
        builder.append(newline);
        builder.append(labelledValue("osu! rank", osuValue(entry.osuRankLabel(), entry.hasOsuRank())));
        builder.append(newline);
        builder.append(labelledValue("osu! country", osuValue(entry.osuCountryLabel(), entry.hasOsuCountry())));
        builder.append(newline);
        builder.append(newline);
        builder.append(labelledValue("Discord", discordValue(entry)));
        return builder.build();
    }

    private Component labelledValue(String label, Component value) {
        return Component.text()
            .append(Component.text(label + ": ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .append(value)
            .build();
    }

    private Component pvpLabel(boolean enabled) {
        Component status = Component.text(enabled ? "Enabled" : "Disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false);
        Component badge = Component.text(enabled ? "[☠]" : "[☮]", enabled ? NamedTextColor.RED : NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false);
        return status.append(Component.space()).append(badge);
    }

    private Component osuValue(String value, boolean linked) {
        TextColor color = linked ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY;
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private Component discordValue(PlayerDirectoryEntry entry) {
        if (!entry.hasDiscord()) {
            return Component.text("Not linked", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
        }
        String globalName = entry.discordGlobalName();
        String username = entry.discordUsername();
        boolean hasGlobalName = globalName != null && !globalName.isBlank();
        boolean hasUsername = username != null && !username.isBlank();
        var builder = Component.text();
        if (hasGlobalName) {
            builder.append(Component.text(globalName.trim(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        }
        if (hasUsername) {
            if (hasGlobalName) {
                builder.append(Component.space());
            }
            builder.append(Component.text("(", NamedTextColor.DARK_GRAY))
                .append(Component.text(username.trim(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(")", NamedTextColor.DARK_GRAY));
        }
        return builder.build().decoration(TextDecoration.ITALIC, false);
    }

    private Component resolveDisplayName(org.bukkit.entity.Player source, Audience viewer, TextColor nameColor) {
        if (nameColor == null) {
            nameColor = NamedTextColor.WHITE;
        }
        if (usernameDisplayService == null || !(viewer instanceof org.bukkit.entity.Player playerViewer)) {
            return Component.text(source.getName(), nameColor).decoration(TextDecoration.ITALIC, false);
        }
        return usernameDisplayService.displayComponent(playerViewer.getUniqueId(), source, nameColor);
    }
}
