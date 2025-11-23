package sh.harold.fulcrum.plugin.fun.quickmaths;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.harold.fulcrum.plugin.fun.quickmaths.QuickMathsManager.Difficulty;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Brigadier command builder for /quickmaths.
 */
public final class QuickMathsCommand {

    private static final SuggestionProvider<CommandSourceStack> DIFFICULTY_SUGGESTIONS = (context, builder) -> {
        for (Difficulty value : Difficulty.values()) {
            builder.suggest(value.name().toLowerCase(Locale.ROOT));
        }
        return builder.buildFuture();
    };

    private final QuickMathsManager manager;
    private final StaffGuard staffGuard;

    public QuickMathsCommand(QuickMathsManager manager, StaffGuard staffGuard) {
        this.manager = manager;
        this.staffGuard = staffGuard;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("quickmaths")
            .requires(staffGuard::isStaff)
            .then(Commands.literal("cancel").executes(this::executeCancel))
            .then(Commands.argument("difficulty", StringArgumentType.word())
                .suggests(DIFFICULTY_SUGGESTIONS)
                .then(Commands.argument("winners", IntegerArgumentType.integer(1, manager.maxWinnersPerRound()))
                    .executes(this::executeStart)))
            .build();
    }

    private int executeStart(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String difficultyRaw = StringArgumentType.getString(context, "difficulty");
        var difficulty = Difficulty.parse(difficultyRaw);
        if (difficulty.isEmpty()) {
            sender.sendMessage(Component.text("Unknown difficulty; try " + readableDifficulties() + ".", NamedTextColor.RED));
            return 0;
        }

        int winners = IntegerArgumentType.getInteger(context, "winners");
        boolean started = manager.startRound(sender, difficulty.get(), winners);
        return started ? Command.SINGLE_SUCCESS : 0;
    }

    private int executeCancel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        boolean cancelled = manager.cancelRound(sender);
        return cancelled ? Command.SINGLE_SUCCESS : 0;
    }

    private String readableDifficulties() {
        return Arrays.stream(Difficulty.values())
            .map(value -> value.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));
    }
}
