package sh.harold.fulcrum.plugin.osu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.literal;

final class OsuLinkCommand {

    private final OsuLinkService service;

    OsuLinkCommand(OsuLinkService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return literal("linkosuaccount")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(this::link)
            .build();
    }

    private int link(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        String url = service.createLink(player.getUniqueId(), player.getName());
        Component message = Component.text("Link your osu! account: ", NamedTextColor.AQUA)
            .append(Component.text("[Open osu! login]", NamedTextColor.GREEN).clickEvent(ClickEvent.openUrl(url)));
        player.sendMessage(message);
        return Command.SINGLE_SUCCESS;
    }
}
