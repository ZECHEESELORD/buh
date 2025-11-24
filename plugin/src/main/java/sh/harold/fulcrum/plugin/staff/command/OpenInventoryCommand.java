package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.OpenInventoryService;

import java.util.Locale;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class OpenInventoryCommand {

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player player : context.getSource().getSender().getServer().getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private final StaffGuard staffGuard;
    private final OpenInventoryService inventoryService;

    public OpenInventoryCommand(StaffGuard staffGuard, OpenInventoryService inventoryService) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("openinv")
            .requires(staffGuard::isStaff)
            .then(argument("target", word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .executes(this::open))
            .executes(this::usage)
            .build();
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendMessage(Component.text("Usage: /openinv <player>", NamedTextColor.RED));
        return Command.SINGLE_SUCCESS;
    }

    private int open(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only players can open that view.", NamedTextColor.RED));
            return 0;
        }
        String target = context.getArgument("target", String.class);
        inventoryService.open(viewer, target);
        return Command.SINGLE_SUCCESS;
    }
}
