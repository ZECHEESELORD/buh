package sh.harold.fulcrum.plugin.vote;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class FeatureVoteCommand {

    private final FeatureVoteService voteService;

    public FeatureVoteCommand(FeatureVoteService voteService) {
        this.voteService = Objects.requireNonNull(voteService, "voteService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vote")
            .executes(this::openVoteMenu)
            .build();
    }

    private int openVoteMenu(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the voting board.", NamedTextColor.RED));
            return 0;
        }

        voteService.openMenu(player);
        return Command.SINGLE_SUCCESS;
    }
}
