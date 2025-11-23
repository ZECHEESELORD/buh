package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.List;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static io.papermc.paper.command.brigadier.argument.ArgumentTypes.entities;

/**
 * Brigadier command builder for /sudo.
 */
public final class SudoCommand {

    private final StaffGuard staffGuard;

    public SudoCommand(StaffGuard staffGuard) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("sudo")
            .requires(staffGuard::isStaff)
            .then(argument("targets", entities())
                .then(argument("action", greedyString())
                    .executes(this::execute)))
            .executes(ctx -> {
                ctx.getSource().getSender().sendMessage(Component.text("Usage: /sudo <selector> <command|c:message>", NamedTextColor.RED));
                return 0;
            })
            .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        EntitySelectorArgumentResolver resolver = context.getArgument("targets", EntitySelectorArgumentResolver.class);

        List<Player> targets = resolver.resolve(source).stream()
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .toList();

        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return 0;
        }

        SudoAction action = parseAction(context.getArgument("action", String.class));
        if (action.payload().isBlank()) {
            sender.sendMessage(Component.text("Provide a command or use c:<message>.", NamedTextColor.RED));
            return 0;
        }

        if (action.mode() == ActionMode.CHAT) {
            forceChat(targets, action.payload());
            sender.sendMessage(Component.text("Forced " + targets.size() + " player(s) to chat: " + action.payload(), NamedTextColor.GREEN));
        } else {
            int executed = forceCommand(targets, action.payload());
            if (executed == 0) {
                sender.sendMessage(Component.text("Command failed for every target.", NamedTextColor.RED));
                return 0;
            }
            sender.sendMessage(Component.text("Executed \"" + action.payload() + "\" for " + executed + " player(s).", NamedTextColor.GOLD));
        }

        return Command.SINGLE_SUCCESS;
    }

    private void forceChat(List<Player> targets, String message) {
        for (Player target : targets) {
            target.chat(message);
        }
    }

    private int forceCommand(List<Player> targets, String command) {
        int success = 0;
        for (Player target : targets) {
            if (target.performCommand(command)) {
                success++;
            }
        }
        return success;
    }

    private SudoAction parseAction(String rawInput) {
        if (rawInput == null) {
            return new SudoAction(ActionMode.COMMAND, "");
        }
        String trimmed = rawInput.strip();
        if (trimmed.regionMatches(true, 0, "c:", 0, 2)) {
            String chatMessage = trimmed.substring(2).stripLeading();
            return new SudoAction(ActionMode.CHAT, chatMessage);
        }
        String sanitized = trimmed.startsWith("/") ? trimmed.substring(1).stripLeading() : trimmed;
        return new SudoAction(ActionMode.COMMAND, sanitized);
    }

    private enum ActionMode {
        COMMAND,
        CHAT
    }

    private record SudoAction(ActionMode mode, String payload) {
    }
}
