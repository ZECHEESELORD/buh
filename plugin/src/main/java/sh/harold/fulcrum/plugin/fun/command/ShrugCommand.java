package sh.harold.fulcrum.plugin.fun.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ShrugCommand {

    private static final String SHRUG = "¯\\_(ツ)_/¯";

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("shrug")
            .executes(this::execute)
            .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return 0;
        }

        player.chat(SHRUG);
        return Command.SINGLE_SUCCESS;
    }
}
