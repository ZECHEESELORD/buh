package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Objects;

public final class EmergencyCreativeCommand {

    private static final net.kyori.adventure.text.Component STAFF_PREFIX = net.kyori.adventure.text.Component.text("[STAFF] ", net.kyori.adventure.text.format.NamedTextColor.AQUA);
    private static final net.kyori.adventure.text.format.NamedTextColor STAFF_ACCENT = net.kyori.adventure.text.format.NamedTextColor.AQUA;

    private final StaffGuard staffGuard;

    public EmergencyCreativeCommand(StaffGuard staffGuard) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ireallywantcreativemode")
            .requires(staffGuard::isStaff)
            .executes(context -> execute(context.getSource()))
            .build();
    }

    private int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(STAFF_PREFIX
                .append(Component.text("Only players can use this.", STAFF_ACCENT)));
            return Command.SINGLE_SUCCESS;
        }
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage(STAFF_PREFIX
            .append(Component.text("You are now in true Creative mode.", STAFF_ACCENT)));
        return Command.SINGLE_SUCCESS;
    }
}
