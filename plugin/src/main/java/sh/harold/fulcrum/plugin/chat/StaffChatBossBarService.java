package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffChatBossBarService implements Listener, ChatChannelService.ChannelListener {

    private static final Component STAFF_PREFIX = Component.text("[STAFF] ", NamedTextColor.AQUA);
    private static final Component STAFF_CHAT_LABEL = Component.text("Staff Chat: ", NamedTextColor.GRAY);

    private final JavaPlugin plugin;
    private final ChatChannelService channelService;
    private final Map<UUID, BossBar> staffChatBars = new ConcurrentHashMap<>();

    public StaffChatBossBarService(JavaPlugin plugin, ChatChannelService channelService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.channelService = Objects.requireNonNull(channelService, "channelService");
    }

    @Override
    public void onChannelChange(UUID playerId, ChatChannelService.ChatChannel channel) {
        plugin.getServer().getScheduler().runTask(plugin, () -> handleChannelChange(playerId, channel));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideStaffChatBar(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    private void handleChannelChange(UUID playerId, ChatChannelService.ChatChannel channel) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            hideStaffChatBar(playerId, null);
            return;
        }
        if (channel.isStaff() && channelService.isStaff(playerId)) {
            showStaffChatBar(player);
            return;
        }
        hideStaffChatBar(playerId, player);
    }

    private void showStaffChatBar(Player player) {
        BossBar bar = staffChatBars.computeIfAbsent(player.getUniqueId(),
            ignored -> BossBar.bossBar(staffChatTitle(true), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS));
        bar.name(staffChatTitle(true));
        player.showBossBar(bar);
    }

    private void hideStaffChatBar(UUID playerId, Player player) {
        BossBar bar = staffChatBars.remove(playerId);
        if (bar == null) {
            return;
        }
        Player target = player;
        if (target == null) {
            target = plugin.getServer().getPlayer(playerId);
        }
        if (target != null) {
            target.hideBossBar(bar);
        }
    }

    private Component staffChatTitle(boolean inStaffChat) {
        NamedTextColor stateColor = inStaffChat ? NamedTextColor.GREEN : NamedTextColor.RED;
        return STAFF_PREFIX.append(STAFF_CHAT_LABEL)
            .append(stateLabel(inStaffChat, stateColor));
    }

    private Component stateLabel(boolean active, NamedTextColor color) {
        Component value = Component.text(active ? "TRUE" : "false", color);
        return active ? value.decorate(TextDecoration.BOLD) : value;
    }
}
