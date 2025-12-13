package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;
import sh.harold.fulcrum.plugin.unlockable.ChatCosmeticPrefixService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

public record JoinMessageListener(
    JavaPlugin plugin,
    Supplier<FormattedUsernameService> usernameServiceSupplier,
    ChatCosmeticPrefixService cosmeticPrefixService,
    UsernameDisplayService usernameDisplayService
) implements Listener {

    private static final Map<UUID, Instant> SESSION_STARTS = new ConcurrentHashMap<>();

    public JoinMessageListener {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(usernameServiceSupplier, "usernameServiceSupplier");
        Objects.requireNonNull(cosmeticPrefixService, "cosmeticPrefixService");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        boolean firstJoin = !event.getPlayer().hasPlayedBefore();
        if (event.joinMessage() == null && !firstJoin) {
            return;
        }
        SESSION_STARTS.put(event.getPlayer().getUniqueId(), Instant.now());
        FormattedUsernameService.FormattedUsername username = resolveUsername(event.getPlayer());
        Component cosmeticPrefix = resolveCosmeticPrefix(event.getPlayer().getUniqueId());
        event.joinMessage(null);
        plugin.getServer().getScheduler().runTask(plugin, () -> sendJoinMessage(event.getPlayer(), username, cosmeticPrefix));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player leaving = event.getPlayer();
        if (event.quitMessage() == null) {
            SESSION_STARTS.remove(leaving.getUniqueId());
            return;
        }
        Duration sessionDuration = sessionDuration(leaving.getUniqueId());
        FormattedUsernameService.FormattedUsername username = resolveUsername(leaving);
        Component cosmeticPrefix = resolveCosmeticPrefix(leaving.getUniqueId());
        event.quitMessage(null);
        plugin.getServer().getScheduler().runTask(plugin, () -> sendQuitMessage(leaving, username, cosmeticPrefix, sessionDuration));
    }

    private FormattedUsernameService.FormattedUsername resolveUsername(Player player) {
        FormattedUsernameService.FormattedUsername fallback = fallbackUsername(player);
        FormattedUsernameService service = usernameServiceSupplier.get();
        if (service == null) {
            return fallback;
        }
        try {
            return service.username(player)
                .toCompletableFuture()
                .getNow(fallback);
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.WARNING, "Failed to format join message for " + player.getName(), runtimeException);
            return fallback;
        }
    }

    private FormattedUsernameService.FormattedUsername fallbackUsername(Player player) {
        return new FormattedUsernameService.FormattedUsername(
            Component.empty(),
            Component.text(player.getName(), LuckPermsTextFormat.DEFAULT_COLOR)
        );
    }

    private void sendJoinMessage(Player joining, FormattedUsernameService.FormattedUsername formatted, Component cosmeticPrefix) {
        if (joining == null || !joining.isOnline()) {
            return;
        }
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            boolean self = viewer.getUniqueId().equals(joining.getUniqueId());
            if (!self && !viewer.canSee(joining)) {
                continue;
            }
            Component nameComponent = displayNameForViewer(viewer, joining, formatted, cosmeticPrefix);
            Component joinMessage = nameComponent.append(Component.text(" joined the game!", NamedTextColor.GRAY));
            viewer.sendMessage(joinMessage);
        }
    }

    private void sendQuitMessage(Player leaving, FormattedUsernameService.FormattedUsername formatted, Component cosmeticPrefix, Duration sessionDuration) {
        if (leaving == null) {
            return;
        }
        Component sessionNote = Component.text(" (Played for " + formatDuration(sessionDuration) + "!)", NamedTextColor.DARK_GRAY);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.equals(leaving)) {
                continue;
            }
            if (!viewer.canSee(leaving)) {
                continue;
            }
            Component nameComponent = displayNameForViewer(viewer, leaving, formatted, cosmeticPrefix);
            Component quitMessage = nameComponent
                .append(Component.text(" left the game!", NamedTextColor.GRAY))
                .append(sessionNote);
            viewer.sendMessage(quitMessage);
        }
    }

    private Duration sessionDuration(UUID playerId) {
        Instant joinedAt = SESSION_STARTS.remove(playerId);
        if (joinedAt == null) {
            return Duration.ZERO;
        }
        Duration duration = Duration.between(joinedAt, Instant.now());
        if (duration.isNegative()) {
            return Duration.ZERO;
        }
        return duration;
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds <= 0) {
            return "0 seconds";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        java.util.List<String> parts = new java.util.ArrayList<>(3);
        if (hours > 0) {
            parts.add(hours + " hour" + (hours == 1 ? "" : "s"));
        }
        if (minutes > 0) {
            parts.add(minutes + " minute" + (minutes == 1 ? "" : "s"));
        }
        if (remainingSeconds > 0 || parts.isEmpty()) {
            parts.add(remainingSeconds + " second" + (remainingSeconds == 1 ? "" : "s"));
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        if (parts.size() == 2) {
            return parts.getFirst() + " and " + parts.getLast();
        }
        return parts.getFirst() + ", " + parts.get(1) + ", and " + parts.getLast();
    }

    private Component displayNameForViewer(Player viewer, Player joining, FormattedUsernameService.FormattedUsername formatted, Component cosmeticPrefix) {
        Component prefix = ChatCosmeticPrefixService.combinePrefixes(cosmeticPrefix, formatted.prefix());
        Component name = formatted.name();
        if (usernameDisplayService != null) {
            name = usernameDisplayService.displayComponent(
                viewer.getUniqueId(),
                joining,
                name.color()
            );
        } else if (name.color() == null) {
            name = name.color(LuckPermsTextFormat.DEFAULT_COLOR);
        }
        if (prefix.equals(Component.empty())) {
            return name;
        }
        return prefix.append(Component.space()).append(name);
    }

    private Component resolveCosmeticPrefix(UUID playerId) {
        try {
            return cosmeticPrefixService.prefix(playerId).getNow(Component.empty());
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().log(Level.WARNING, "Failed to resolve chat cosmetic prefix for " + playerId, runtimeException);
            return Component.empty();
        }
    }
}
