package sh.harold.fulcrum.plugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.loader.ModuleLoader;

import java.util.List;
import java.util.Objects;

public final class ModulesCommand {

    private final ModuleLoader moduleLoader;

    public ModulesCommand(ModuleLoader moduleLoader) {
        this.moduleLoader = Objects.requireNonNull(moduleLoader, "moduleLoader");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("modules")
            .executes(context -> execute(context.getSource()))
            .then(Commands.literal("list").executes(context -> execute(context.getSource())))
            .build();
    }

    private int execute(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        List<ModuleId> enabled = moduleLoader.enabledModules();
        if (enabled.isEmpty()) {
            sender.sendMessage(Component.text("No modules are enabled right now.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Enabled modules (" + enabled.size() + "):", NamedTextColor.GOLD));
        for (ModuleId id : enabled) {
            sender.sendMessage(Component.text(" * " + id.value(), NamedTextColor.GRAY));
        }
        return Command.SINGLE_SUCCESS;
    }
}
