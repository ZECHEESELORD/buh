package sh.harold.fulcrum.plugin.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

final class UnsignedChatListener implements Listener {

    private final Plugin plugin;

    UnsignedChatListener(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Set<Audience> viewers = new LinkedHashSet<>(event.viewers());
        viewers.add(plugin.getServer().getConsoleSender());
        if (viewers.isEmpty()) {
            return;
        }

        Player sender = event.getPlayer();
        Component message = event.message();
        var renderer = event.renderer();

        event.setCancelled(true);

        Runnable broadcast = () -> {
            Component sourceDisplayName = sender.displayName();
            viewers.forEach(viewer -> {
                try {
                    Component rendered = renderer.render(sender, sourceDisplayName, message, viewer);
                    viewer.sendMessage(rendered);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "Failed to broadcast unsigned chat", exception);
                }
            });
        };

        if (plugin.getServer().isPrimaryThread()) {
            broadcast.run();
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, broadcast);
    }
}

