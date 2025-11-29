package sh.harold.fulcrum.plugin.osu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import static io.papermc.paper.command.brigadier.Commands.literal;

final class SkipOsuCommand {

    LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = literal("skiposu")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> {
                Player player = (Player) context.getSource().getSender();
                player.performCommand("skiposu");
                return Command.SINGLE_SUCCESS;
            });
        return builder.build();
    }
}
