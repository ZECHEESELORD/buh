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

final class DiscordLinkCommand {

    private final OsuLinkService service;

    DiscordLinkCommand(OsuLinkService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return literal("linkdiscordaccount")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(this::link)
            .build();
    }

    private int link(CommandContext<CommandSourceStack> context) {
        Player player = (Player) context.getSource().getSender();
        if (service.hasDiscordLink(player.getUniqueId())) {
            player.sendMessage(service.errorMessage("ERROR!", "You've already linked your Discord account."));
            return Command.SINGLE_SUCCESS;
        }
        String url = service.createDiscordLink(player.getUniqueId(), player.getName());
        player.sendMessage(service.linkPrompt("LINK!", "Link your Discord account here!", url));
        return Command.SINGLE_SUCCESS;
    }
}
