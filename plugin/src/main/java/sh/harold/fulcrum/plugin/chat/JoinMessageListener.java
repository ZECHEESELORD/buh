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

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

public record JoinMessageListener(
    JavaPlugin plugin,
    Supplier<FormattedUsernameService> usernameServiceSupplier,
    UsernameDisplayService usernameDisplayService
) implements Listener {

    public JoinMessageListener {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(usernameServiceSupplier, "usernameServiceSupplier");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (event.joinMessage() == null) {
            return;
        }
        FormattedUsernameService.FormattedUsername username = resolveUsername(event.getPlayer());
        event.joinMessage(null);
        plugin.getServer().getScheduler().runTask(plugin, () -> sendJoinMessage(event.getPlayer(), username));
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

    private void sendJoinMessage(Player joining, FormattedUsernameService.FormattedUsername formatted) {
        if (joining == null || !joining.isOnline()) {
            return;
        }
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            boolean self = viewer.getUniqueId().equals(joining.getUniqueId());
            if (!self && !viewer.canSee(joining)) {
                continue;
            }
            Component nameComponent = displayNameForViewer(viewer, joining, formatted);
            Component joinMessage = nameComponent.append(Component.text(" joined the game!", NamedTextColor.GRAY));
            viewer.sendMessage(joinMessage);
        }
    }

    private Component displayNameForViewer(Player viewer, Player joining, FormattedUsernameService.FormattedUsername formatted) {
        Component prefix = formatted.prefix();
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
}
