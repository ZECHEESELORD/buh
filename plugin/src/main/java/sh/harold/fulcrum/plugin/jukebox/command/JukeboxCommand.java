package sh.harold.fulcrum.plugin.jukebox.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.jukebox.menu.JukeboxMenuService;

import java.util.Objects;

public final class JukeboxCommand {

    private final JukeboxMenuService menuService;

    public JukeboxCommand(JukeboxMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("jukebox")
            .executes(this::openMenu)
            .build();
    }

    private int openMenu(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the jukebox menu.", NamedTextColor.RED));
            return 0;
        }
        menuService.open(player);
        return Command.SINGLE_SUCCESS;
    }
}

