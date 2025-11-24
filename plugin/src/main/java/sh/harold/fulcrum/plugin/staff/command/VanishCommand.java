package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.staff.VanishService;
import sh.harold.fulcrum.plugin.staff.VanishService.VanishState;

import java.util.Locale;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class VanishCommand {

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

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final VanishService vanishService;

    public VanishCommand(JavaPlugin plugin, StaffGuard staffGuard, VanishService vanishService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.vanishService = Objects.requireNonNull(vanishService, "vanishService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("vanish")
            .requires(staffGuard::isStaff)
            .executes(this::toggleSelf)
            .then(literal("on").executes(ctx -> setSelf(ctx, true)))
            .then(literal("off").executes(ctx -> setSelf(ctx, false)))
            .then(argument("target", word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .executes(this::toggleOther)
                .then(literal("on").executes(ctx -> setOther(ctx, true)))
                .then(literal("off").executes(ctx -> setOther(ctx, false))))
            .build();
    }

    private int toggleSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return 0;
        }
        VanishState state = vanishService.toggle(player);
        sendSelfFeedback(player, state);
        return Command.SINGLE_SUCCESS;
    }

    private int setSelf(CommandContext<CommandSourceStack> context, boolean vanish) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return 0;
        }
        VanishState state = vanishService.setVanished(player, vanish);
        sendSelfFeedback(player, state);
        return Command.SINGLE_SUCCESS;
    }

    private int toggleOther(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Player target = resolveTarget(context);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return 0;
        }
        if (isSelfTarget(sender, target)) {
            return toggleSelf(context);
        }
        VanishState state = vanishService.toggle(target);
        sendTargetFeedback(sender, target, state);
        return Command.SINGLE_SUCCESS;
    }

    private int setOther(CommandContext<CommandSourceStack> context, boolean vanish) {
        CommandSender sender = context.getSource().getSender();
        Player target = resolveTarget(context);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return 0;
        }
        if (isSelfTarget(sender, target)) {
            return setSelf(context, vanish);
        }
        VanishState state = vanishService.setVanished(target, vanish);
        sendTargetFeedback(sender, target, state);
        return Command.SINGLE_SUCCESS;
    }

    private Player resolveTarget(CommandContext<CommandSourceStack> context) {
        String targetName = context.getArgument("target", String.class);
        return plugin.getServer().getPlayerExact(targetName);
    }

    private void sendSelfFeedback(Player player, VanishState state) {
        if (state.vanished()) {
            NamedTextColor color = state.changed() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            String message = state.changed()
                ? "You fade from view; regular players cannot see you."
                : "You are already vanished; nothing changes.";
            player.sendMessage(Component.text(message, color));
            return;
        }
        NamedTextColor color = state.changed() ? NamedTextColor.GOLD : NamedTextColor.YELLOW;
        String message = state.changed()
            ? "You return to sight; everyone can see you again."
            : "You were already visible.";
        player.sendMessage(Component.text(message, color));
    }

    private void sendTargetFeedback(CommandSender actor, Player target, VanishState state) {
        String stateLabel = state.vanished() ? "vanished" : "visible";
        NamedTextColor actorColor = state.changed() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        actor.sendMessage(Component.text(target.getName() + " is now " + stateLabel + ".", actorColor));

        if (!state.changed()) {
            return;
        }

        String actorName = actor.getName();
        if (state.vanished()) {
            target.sendMessage(Component.text(actorName + " vanished you; regular players will not spot you.", NamedTextColor.AQUA));
        } else {
            target.sendMessage(Component.text(actorName + " revealed you; everyone can see you now.", NamedTextColor.GOLD));
        }
    }

    private boolean isSelfTarget(CommandSender sender, Player target) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        return player.getUniqueId().equals(target.getUniqueId());
    }
}
