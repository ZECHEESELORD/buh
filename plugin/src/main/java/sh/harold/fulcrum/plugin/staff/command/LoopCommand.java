package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Objects;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * Brigadier command builder for /loop.
 */
public final class LoopCommand {

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;

    public LoopCommand(JavaPlugin plugin, StaffGuard staffGuard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("loop")
            .requires(staffGuard::isStaff)
            .then(argument("iterations", integer(1))
                .then(argument("delay", integer(1))
                    .then(argument("command", greedyString())
                        .executes(this::execute))))
            .executes(ctx -> {
                ctx.getSource().getSender().sendMessage(Component.text("Usage: /loop <iterations> <delayTicks> <command>", NamedTextColor.RED));
                return 0;
            })
            .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        int iterations = context.getArgument("iterations", Integer.class);
        int delay = context.getArgument("delay", Integer.class);
        String rawCommand = context.getArgument("command", String.class);
        String command = sanitizeCommand(rawCommand);

        if (command.isBlank()) {
            sender.sendMessage(Component.text("Provide a command to loop.", NamedTextColor.RED));
            return 0;
        }

        scheduleLoop(sender, iterations, delay, command);
        sender.sendMessage(Component.text("Looping \"" + command + "\" " + iterations + " times with " + delay + " tick delay.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private void scheduleLoop(CommandSender sender, int iterations, int delay, String command) {
        new BukkitRunnable() {
            private int executed = 0;

            @Override
            public void run() {
                if (!plugin.isEnabled() || executed >= iterations) {
                    cancel();
                    return;
                }
                boolean success = plugin.getServer().dispatchCommand(sender, command);
                executed++;
                if (!success) {
                    sender.sendMessage(Component.text("Loop cancelled; \"" + command + "\" failed on iteration " + executed + ".", NamedTextColor.RED));
                    cancel();
                    return;
                }
                if (executed >= iterations) {
                    sender.sendMessage(Component.text("Loop finished after " + executed + " runs of \"" + command + "\".", NamedTextColor.GOLD));
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, delay);
    }

    private String sanitizeCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.startsWith("/")) {
            return trimmed.substring(1).stripLeading();
        }
        return trimmed;
    }
}
