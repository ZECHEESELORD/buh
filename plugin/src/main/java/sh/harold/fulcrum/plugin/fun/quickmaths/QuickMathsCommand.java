package sh.harold.fulcrum.plugin.fun.quickmaths;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import sh.harold.fulcrum.message.Message;
import sh.harold.fulcrum.plugin.fun.quickmaths.QuickMathsManager.Difficulty;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * Brigadier command builder for /quickmaths.
 */
public final class QuickMathsCommand {
    private static final SuggestionProvider<CommandSourceStack> DIFFICULTY_SUGGESTIONS =
            (context, builder) -> {
                for (Difficulty value : Difficulty.values()) {
                    builder.suggest(value.name());
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
        return literal("quickmaths")
                .requires(staffGuard::isStaff)
                .then(literal("cancel")
                        .executes(this::executeCancel))
                .then(argument("difficulty", StringArgumentType.word())
                        .suggests(DIFFICULTY_SUGGESTIONS)
                        .then(argument("winners", IntegerArgumentType.integer(1, manager.maxWinnersPerRound()))
                                .executes(this::execute)))
                .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String difficultyRaw = StringArgumentType.getString(context, "difficulty");
        Optional<Difficulty> difficulty = Difficulty.parse(difficultyRaw);
        if (difficulty.isEmpty()) {
            Message.error("Unknown difficulty '" + difficultyRaw + "'. Use " + readableDifficulties() + ".")
                .builder()
                .send(sender);
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
