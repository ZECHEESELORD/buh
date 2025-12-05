package sh.harold.fulcrum.plugin.playermenu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class StatsMenuCommand {

    private final PlayerMenuService menuService;

    public StatsMenuCommand(PlayerMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("stats")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> execute(context.getSource()))
            .build();
    }

    private int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the stats menu.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        menuService.openStats(player);
        return Command.SINGLE_SUCCESS;
    }
}
