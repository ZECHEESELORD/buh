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

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;

public record JoinMessageListener(
    JavaPlugin plugin,
    Supplier<FormattedUsernameService> usernameServiceSupplier
) implements Listener {

    public JoinMessageListener {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(usernameServiceSupplier, "usernameServiceSupplier");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        FormattedUsernameService.FormattedUsername username = resolveUsername(event.getPlayer());
        Component joinMessage = username.displayName()
            .append(Component.text(" joined the game!", NamedTextColor.GRAY));
        event.joinMessage(joinMessage);
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
}
