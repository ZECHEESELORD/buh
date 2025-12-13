package sh.harold.fulcrum.plugin.osu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static sh.harold.fulcrum.plugin.osu.VerificationConstants.BYPASS_REGISTRATION_PERMISSION;

final class BypassRegistrationCommand {

    private final OsuVerificationService verificationService;

    BypassRegistrationCommand(OsuVerificationService verificationService) {
        this.verificationService = Objects.requireNonNull(verificationService, "verificationService");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return literal("bypassregistration")
            .requires(this::canBypassRegistration)
            .then(argument("usernames", greedyString()).executes(this::execute))
            .executes(this::usage)
            .build();
    }

    private boolean canBypassRegistration(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.hasPermission(BYPASS_REGISTRATION_PERMISSION);
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        context.getSource().getSender().sendMessage(Component.text("Usage: /bypassregistration <username1, username2, ...>", NamedTextColor.RED));
        return 0;
    }

    private int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String raw = context.getArgument("usernames", String.class);
        Set<String> usernames = parseUsernames(raw);
        if (usernames.isEmpty()) {
            sender.sendMessage(Component.text("Provide at least one username.", NamedTextColor.RED));
            return 0;
        }

        OsuVerificationService.RegistrationBypassUpdate update = verificationService.addRegistrationBypassUsernames(usernames);
        sender.sendMessage(Component.text(
            "Registration bypass updated: +" + update.addedUsernames() + " (total " + update.totalUsernames() + "), released " + update.releasedPlayers() + " player(s).",
            NamedTextColor.GREEN
        ));
        return Command.SINGLE_SUCCESS;
    }

    private Set<String> parseUsernames(String raw) {
        if (raw == null) {
            return Set.of();
        }
        String trimmed = raw.strip();
        if (trimmed.isBlank()) {
            return Set.of();
        }

        Set<String> usernames = new LinkedHashSet<>();
        for (String token : trimmed.split("[,\\s]+")) {
            String username = token.strip();
            if (username.isBlank()) {
                continue;
            }
            usernames.add(username.toLowerCase(Locale.ROOT));
        }
        return usernames;
    }
}
