package sh.harold.fulcrum.plugin;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.loader.ModuleLoader;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;

public final class ModulesCommand {

    private static final String MODULE_MANAGE_PERMISSION = "fulcrum.module.manage";

    private final ModuleLoader moduleLoader;

    public ModulesCommand(ModuleLoader moduleLoader) {
        this.moduleLoader = Objects.requireNonNull(moduleLoader, "moduleLoader");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("module")
            .requires(this::canManageModules)
            .executes(context -> execute(context.getSource()))
            .then(Commands.literal("list").executes(context -> execute(context.getSource())))
            .then(Commands.literal("reload")
                .then(Commands.argument("module", StringArgumentType.word())
                    .executes(context -> reload(
                        context.getSource(),
                        StringArgumentType.getString(context, "module")
                    ))))
            .build();
    }

    private boolean canManageModules(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.hasPermission(MODULE_MANAGE_PERMISSION);
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

    private int reload(CommandSourceStack source, String moduleName) {
        CommandSender sender = source.getSender();
        ModuleId moduleId;
        try {
            moduleId = ModuleId.of(moduleName);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(Component.text("Module names cannot be blank.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Optional<FulcrumModule> moduleOptional = moduleLoader.module(moduleId);
        if (moduleOptional.isEmpty()) {
            sender.sendMessage(Component.text("No module is registered as '" + moduleName + "'.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (!moduleLoader.enabledModules().contains(moduleId)) {
            sender.sendMessage(Component.text("Module '" + moduleName + "' is currently disabled.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        FulcrumModule module = moduleOptional.get();
        if (!(module instanceof ConfigurableModule configurableModule)) {
            sender.sendMessage(Component.text("Module '" + moduleName + "' does not expose reloadable configuration.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        sender.sendMessage(Component.text("Reloading '" + moduleId.value() + "' configuration...", NamedTextColor.GOLD));
        try {
            configurableModule.reloadConfig().toCompletableFuture().join();
            sender.sendMessage(Component.text("Reloaded '" + moduleId.value() + "'.", NamedTextColor.GREEN));
        } catch (CompletionException completionException) {
            Throwable cause = completionException.getCause() == null ? completionException : completionException.getCause();
            sender.sendMessage(Component.text("Failed to reload '" + moduleId.value() + "': " + cause.getMessage(), NamedTextColor.RED));
        } catch (RuntimeException exception) {
            sender.sendMessage(Component.text("Failed to reload '" + moduleId.value() + "': " + exception.getMessage(), NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }
}
