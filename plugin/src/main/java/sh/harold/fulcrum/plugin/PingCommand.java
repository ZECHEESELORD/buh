package sh.harold.fulcrum.plugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static io.papermc.paper.command.brigadier.argument.ArgumentTypes.player;

public final class PingCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("ping")
            .executes(this::sendSelfPing)
            .then(argument("target", player()).executes(this::sendTargetPing))
            .build();
    }

    private int sendSelfPing(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can check their own ping. Try /ping <player>.", NamedTextColor.RED));
            return 0;
        }

        sender.sendMessage(buildSelfPingComponent(player));
        return Command.SINGLE_SUCCESS;
    }

    private int sendTargetPing(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        PlayerSelectorArgumentResolver resolver = context.getArgument("target", PlayerSelectorArgumentResolver.class);
        List<Player> targets = resolver.resolve(context.getSource());
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return 0;
        }

        Player target = targets.getFirst();
        sender.sendMessage(buildTargetPingComponent(target));
        return Command.SINGLE_SUCCESS;
    }

    private Component buildSelfPingComponent(Player player) {
        return pongPrefix()
            .append(nonBoldText("Your ping is ", NamedTextColor.GRAY))
            .append(pingValue(player))
            .append(nonBoldText("ms!", NamedTextColor.GRAY));
    }

    private Component buildTargetPingComponent(Player target) {
        return pongPrefix()
            .append(nonBoldText(target.getName(), NamedTextColor.YELLOW))
            .append(nonBoldText("'s ping is ", NamedTextColor.GRAY))
            .append(pingValue(target))
            .append(nonBoldText("ms!", NamedTextColor.GRAY));
    }

    private Component pingValue(Player player) {
        int ping = player.getPing();
        return Component.text(ping, pingColor(ping))
            .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE);
    }

    private Component pongPrefix() {
        return Component.text()
            .content("PONG! ")
            .color(NamedTextColor.YELLOW)
            .decorate(TextDecoration.BOLD)
            .build();
    }

    private Component nonBoldText(String content, NamedTextColor color) {
        return Component.text(content, color)
            .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE);
    }

    private NamedTextColor pingColor(int ping) {
        if (ping <= 50) {
            return NamedTextColor.GREEN;
        }
        if (ping <= 150) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }
}
