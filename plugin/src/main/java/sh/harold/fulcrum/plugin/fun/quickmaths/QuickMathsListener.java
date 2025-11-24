package sh.harold.fulcrum.plugin.fun.quickmaths;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Routes player chat into Quick Maths guessing.
 */
public final class QuickMathsListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final QuickMathsManager manager;

    public QuickMathsListener(QuickMathsManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncChat(AsyncChatEvent event) {
        String plain = PLAIN.serialize(event.originalMessage());
        manager.handleChat(event.getPlayer(), plain);
    }
}
