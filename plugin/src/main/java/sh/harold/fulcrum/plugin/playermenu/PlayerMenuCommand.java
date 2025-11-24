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

public final class PlayerMenuCommand {

    private final PlayerMenuService menuService;

    public PlayerMenuCommand(PlayerMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("menu")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> execute(context.getSource()))
            .build();
    }

    private int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the menu.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        menuService.openMenu(player)
            .exceptionally(throwable -> {
                player.sendMessage(Component.text("Failed to open the player menu; try again soon.", NamedTextColor.RED));
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }
}
