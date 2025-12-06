package sh.harold.fulcrum.plugin.unlockable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class GetOffMyHeadCommand {

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("getoffmyhead")
            .executes(this::execute)
            .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return 0;
        }
        List<Player> ejected = ejectPassengers(player);
        if (ejected.isEmpty()) {
            player.sendMessage(Component.text("Nobody is riding you.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(Component.text("You shook everyone off.", NamedTextColor.GREEN));
        ejected.forEach(target -> target.sendMessage(Component.text("You were knocked off " + player.getName() + ".", NamedTextColor.YELLOW)));
        return Command.SINGLE_SUCCESS;
    }

    private List<Player> ejectPassengers(Entity root) {
        List<Player> removed = new ArrayList<>();
        List<Entity> passengers = List.copyOf(root.getPassengers());
        for (Entity passenger : passengers) {
            removed.addAll(ejectPassengers(passenger));
            passenger.leaveVehicle();
            passenger.teleport(root.getLocation().add(0, 0.5, 0));
            if (passenger instanceof Player player) {
                removed.add(player);
            }
        }
        return removed;
    }
}
