package sh.harold.fulcrum.plugin.perk;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.unlockable.PlayerUnlockable;
import sh.harold.fulcrum.plugin.unlockable.UnlockableDefinition;
import sh.harold.fulcrum.plugin.unlockable.UnlockableId;
import sh.harold.fulcrum.plugin.unlockable.UnlockableRegistry;
import sh.harold.fulcrum.plugin.unlockable.UnlockableService;
import sh.harold.fulcrum.plugin.unlockable.UnlockableType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class PerkCommand {

    private final UnlockableService unlockableService;
    private final UnlockableRegistry registry;

    public PerkCommand(UnlockableService unlockableService, UnlockableRegistry registry) {
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("perks")
            .executes(this::listPerks)
            .then(literal("toggle")
                .then(argument("perk", word()).executes(this::togglePerk)))
            .then(literal("unlock")
                .requires(stack -> stack.getSender().hasPermission("fulcrum.perk.unlock"))
                .then(argument("perk", word())
                    .executes(context -> unlock(context, 1))
                    .then(argument("tier", integer(1))
                        .executes(context -> unlock(context, context.getArgument("tier", Integer.class))))))
            .build();
    }

    private int listPerks(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can view their perks.", NamedTextColor.RED));
            return 0;
        }
        UUID playerId = player.getUniqueId();
        unlockableService.loadState(playerId).whenComplete((state, throwable) -> {
            if (throwable != null) {
                sender.sendMessage(Component.text("Could not load your perks: " + errorMessage(throwable), NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("Perks:", NamedTextColor.GOLD, TextDecoration.BOLD));
            registry.definitions(UnlockableType.PERK)
                .forEach(definition -> {
                    PlayerUnlockable perk = state.unlockable(definition.id()).orElse(new PlayerUnlockable(definition, 0, false));
                    Component status = perk.unlocked()
                        ? Component.text("Tier " + perk.tier() + "/" + definition.maxTier(), NamedTextColor.AQUA)
                        : Component.text("Locked", NamedTextColor.RED);
                    Component toggleState = perk.unlocked()
                        ? Component.text(perk.enabled() ? "State: On" : "State: Off", perk.enabled() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                        : Component.empty();
                    Component line = Component.text()
                        .append(Component.text("* ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(definition.name(), NamedTextColor.YELLOW))
                        .append(Component.text(" :: ", NamedTextColor.DARK_GRAY))
                        .append(status)
                        .append(perk.unlocked() ? Component.text("; ", NamedTextColor.DARK_GRAY) : Component.empty())
                        .append(toggleState)
                        .append(Component.text(" :: ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(definition.description(), NamedTextColor.GRAY))
                        .build();
                    sender.sendMessage(line);
                });
        });
        return Command.SINGLE_SUCCESS;
    }

    private int togglePerk(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle perks.", NamedTextColor.RED));
            return 0;
        }
        String rawId = context.getArgument("perk", String.class);
        Optional<UnlockableDefinition> definition = parseDefinition(sender, rawId);
        if (definition.isEmpty()) {
            return 0;
        }

        unlockableService.toggle(player.getUniqueId(), definition.get().id())
            .whenComplete((perk, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Could not toggle that perk: " + errorMessage(throwable), NamedTextColor.RED));
                    return;
                }
                String state = perk.enabled() ? "On" : "Off";
                sender.sendMessage(Component.text(
                    "Set " + perk.definition().name() + " to " + state + ".",
                    perk.enabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY
                ));
            });
        return Command.SINGLE_SUCCESS;
    }

    private int unlock(CommandContext<CommandSourceStack> context, int tier) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can unlock perks for themselves.", NamedTextColor.RED));
            return 0;
        }
        String rawId = context.getArgument("perk", String.class);
        Optional<UnlockableDefinition> definition = parseDefinition(sender, rawId);
        if (definition.isEmpty()) {
            return 0;
        }
        unlockableService.unlockToTier(player.getUniqueId(), definition.get().id(), tier)
            .whenComplete((perk, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Unlock failed: " + errorMessage(throwable), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                    "Unlocked tier " + perk.tier() + " of " + perk.definition().name() + ".",
                    NamedTextColor.GREEN
                ));
            });
        return Command.SINGLE_SUCCESS;
    }

    private Optional<UnlockableDefinition> parseDefinition(CommandSender sender, String rawId) {
        UnlockableId perkId;
        try {
            perkId = new UnlockableId(rawId);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Unknown perk id: " + rawId, NamedTextColor.RED));
            return Optional.empty();
        }
        return registry.definition(perkId)
            .filter(definition -> definition.type() == UnlockableType.PERK)
            .or(() -> {
                sender.sendMessage(Component.text("No perk registered with id " + rawId + ".", NamedTextColor.RED));
                return Optional.empty();
            });
    }

    private String errorMessage(Throwable throwable) {
        Throwable root = throwable instanceof CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        return root == null ? "unknown error" : root.getMessage();
    }
}
