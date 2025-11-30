package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.message.Message;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Locale;
import java.util.Objects;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class HealCommand {

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

    public HealCommand(StaffGuard staffGuard) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("heal")
            .requires(staffGuard::isStaff)
            .executes(this::healSelf)
            .then(argument("target", word())
                .suggests(ONLINE_PLAYER_SUGGESTIONS)
                .executes(this::healTarget))
            .build();
    }

    private int healSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Message.error("Pick a player to heal from here.").staff().send(sender);
            return 0;
        }
        heal(player);
        Message.success("You feel restored and steady.").staff().send(player);
        return Command.SINGLE_SUCCESS;
    }

    private int healTarget(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        heal(selection.target());
        sendFeedback(sender, selection);
        return Command.SINGLE_SUCCESS;
    }

    private TargetSelection resolveTarget(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);
        Player target = sender.getServer().getPlayerExact(targetName);
        if (target == null) {
            Message.error("No player named {0} is online.", targetName).staff().send(sender);
            return null;
        }
        boolean selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        return new TargetSelection(target, selfTarget);
    }

    private void sendFeedback(CommandSender sender, TargetSelection selection) {
        if (selection.selfTarget()) {
            Message.success("You feel restored and steady.").staff().send(sender);
            return;
        }
        Message.success("Healed {0}.", selection.target().getName())
            .staff()
            .send(sender);
        Message.info("{0} healed you; aches vanish.", sender.getName())
            .staff()
            .send(selection.target());
    }

    private void heal(Player target) {
        AttributeInstance maxHealthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? target.getHealth() : maxHealthAttribute.getValue();
        double safeHealth = Math.max(1.0D, maxHealth);
        target.setHealth(safeHealth);
        target.setFireTicks(0);
        target.setFreezeTicks(0);
    }

    private record TargetSelection(Player target, boolean selfTarget) {
    }
}
