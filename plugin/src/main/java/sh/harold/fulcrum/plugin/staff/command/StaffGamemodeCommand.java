package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.StaffCreativeService;

import java.util.Locale;
import java.util.Objects;

public final class StaffGamemodeCommand {

    private static final SuggestionProvider<CommandSourceStack> MODES = (context, builder) -> {
        builder.suggest("survival");
        builder.suggest("spectator");
        builder.suggest("creative");
        return builder.buildFuture();
    };

    private final StaffGuard staffGuard;
    private final StaffCreativeService creativeService;

    public StaffGamemodeCommand(StaffGuard staffGuard, StaffCreativeService creativeService) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.creativeService = Objects.requireNonNull(creativeService, "creativeService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("gamemode")
            .requires(staffGuard::isStaff)
            .executes(this::missingMode)
            .then(Commands.argument("mode", StringArgumentType.word())
                .suggests(MODES)
                .executes(this::execute)
            )
            .build();
    }

    public LiteralCommandNode<CommandSourceStack> alias(String name, String mode) {
        return Commands.literal(name)
            .requires(staffGuard::isStaff)
            .executes(context -> executeMode(context.getSource(), mode))
            .build();
    }

    private int missingMode(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Choose a mode: survival, spectator, creative.", NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        String mode = StringArgumentType.getString(context, "mode");
        return executeMode(context.getSource(), mode);
    }

    private int executeMode(CommandSourceStack source, String rawMode) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can change gamemode.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        String lowered = rawMode.toLowerCase(Locale.ROOT);
        switch (lowered) {
            case "creative", "gmc" -> {
                if (!creativeService.enable(player)) {
                    player.sendMessage(Component.text("You do not have access to staff creative.", NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                player.sendMessage(Component.text("Staff Creative enabled.", NamedTextColor.AQUA));
            }
            case "survival", "gms" -> {
                creativeService.disable(player);
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(Component.text("Set to Survival.", NamedTextColor.YELLOW));
            }
            case "spectator", "gmsp" -> {
                creativeService.disable(player);
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(Component.text("Set to Spectator.", NamedTextColor.YELLOW));
            }
            default -> player.sendMessage(Component.text("Unknown mode. Try survival, spectator, or creative.", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }
}
