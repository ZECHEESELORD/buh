package sh.harold.fulcrum.plugin.fun.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.argument.ArgumentTypes.entities;

/**
 * Launches players with a bit of lightning flair.
 */
public final class KaboomCommand {

    private static final double LAUNCH_VERTICAL_VELOCITY = 1.2D;
    private static final double PARTICLE_HORIZONTAL_OFFSET = 0.6D;
    private static final double PARTICLE_VERTICAL_OFFSET = 1.0D;
    private static final double PARTICLE_SPEED = 0.01D;
    private static final int PARTICLE_COUNT = 40;

    private final StaffGuard staffGuard;

    public KaboomCommand(StaffGuard staffGuard) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kaboom")
            .requires(staffGuard::isStaff)
            .then(argument("targets", entities()).executes(this::execute))
            .build();
    }

    private int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();

        List<Player> targets = resolveTargets(context, source);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return 0;
        }

        for (Player target : targets) {
            triggerKaboom(target);
            sender.sendMessage(Component.text("Kaboom: " + target.getName() + " took flight.", NamedTextColor.GREEN));
        }

        return Command.SINGLE_SUCCESS;
    }

    private void triggerKaboom(Player target) {
        World world = target.getWorld();
        Location location = target.getLocation();

        world.strikeLightningEffect(location);

        Vector currentVelocity = target.getVelocity();
        Vector launchVelocity = currentVelocity.clone().setY(Math.max(currentVelocity.getY(), 0.0D) + LAUNCH_VERTICAL_VELOCITY);
        target.setVelocity(launchVelocity);

        world.spawnParticle(
            Particle.END_ROD,
            location,
            PARTICLE_COUNT,
            PARTICLE_HORIZONTAL_OFFSET,
            PARTICLE_VERTICAL_OFFSET,
            PARTICLE_HORIZONTAL_OFFSET,
            PARTICLE_SPEED
        );
    }

    private List<Player> resolveTargets(CommandContext<CommandSourceStack> context, CommandSourceStack source) throws CommandSyntaxException {
        String raw = rawTargetArgument(context.getInput());
        if (isAllSelector(raw)) {
            return List.copyOf(source.getSender().getServer().getOnlinePlayers());
        }
        EntitySelectorArgumentResolver resolver = context.getArgument("targets", EntitySelectorArgumentResolver.class);
        return resolver.resolve(source).stream()
            .filter(Player.class::isInstance)
            .map(Player.class::cast)
            .toList();
    }

    private boolean isAllSelector(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return "@a".equalsIgnoreCase(input.trim());
    }

    private String rawTargetArgument(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.startsWith("/") ? input.substring(1) : input;
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex < 0 || spaceIndex + 1 >= normalized.length()) {
            return "";
        }
        return normalized.substring(spaceIndex + 1).trim();
    }
}
