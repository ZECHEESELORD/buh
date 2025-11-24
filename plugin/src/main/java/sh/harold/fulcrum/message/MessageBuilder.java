package sh.harold.fulcrum.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simple builder used for Quick Maths messaging parity.
 */
public final class MessageBuilder {
    private final MessageStyle style;
    private final String text;
    private final List<MessageTag> tags = new ArrayList<>();

    MessageBuilder(MessageStyle style, String text) {
        this.style = Objects.requireNonNull(style, "style");
        this.text = Objects.requireNonNullElse(text, "");
    }

    public MessageBuilder builder() {
        return this;
    }

    public MessageBuilder tag(MessageTag tag) {
        if (tag != null) {
            tags.add(tag);
        }
        return this;
    }

    public MessageBuilder tags(Iterable<MessageTag> identifiers) {
        if (identifiers != null) {
            for (MessageTag tag : identifiers) {
                tag(tag);
            }
        }
        return this;
    }

    public MessageBuilder staff() {
        return tag(MessageTag.STAFF);
    }

    public void send(Audience audience) {
        if (audience == null) {
            return;
        }
        audience.sendMessage(component());
    }

    public void send(CommandSender sender) {
        if (sender == null) {
            return;
        }
        sender.sendMessage(component());
    }

    public Component component() {
        Component base = Component.text(text, style.bodyColor());
        if (tags.isEmpty()) {
            return base;
        }
        TextComponent.Builder prefix = Component.text();
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) {
                prefix.append(Component.space());
            }
            prefix.append(tags.get(i).component());
        }
        return prefix.append(Component.space()).append(base).build();
    }

    public String plain() {
        return text;
    }
}
