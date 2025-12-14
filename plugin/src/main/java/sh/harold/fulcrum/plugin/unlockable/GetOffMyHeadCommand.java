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

    private static final String RIDE_SEAT_TAG = "fulcrumRideSeat";

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
        return ejectPassengers(root, root.getLocation().add(0, 0.5, 0));
    }

    private List<Player> ejectPassengers(Entity root, org.bukkit.Location teleportDestination) {
        List<Player> removed = new ArrayList<>();
        List<Entity> passengers = List.copyOf(root.getPassengers());
        for (Entity passenger : passengers) {
            removed.addAll(ejectPassengers(passenger, teleportDestination));
            if (isRideSeat(passenger)) {
                passenger.remove();
                continue;
            }
            passenger.leaveVehicle();
            passenger.teleport(teleportDestination);
            if (passenger instanceof Player player) {
                removed.add(player);
            }
        }
        return removed;
    }

    private boolean isRideSeat(Entity entity) {
        return entity.getScoreboardTags().contains(RIDE_SEAT_TAG);
    }
}
