package sh.harold.fulcrum.plugin.message;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.chat.ChatChannelService;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MessageModule implements FulcrumModule {

    private static final List<String> COMMAND_ALIASES = List.of("tell", "whisper", "w", "message", "m");

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private final ChatChannelService channelService;
    private final MessageService messageService;

    public MessageModule(JavaPlugin plugin, LuckPermsModule luckPermsModule, ChatChannelService channelService, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
        this.channelService = Objects.requireNonNull(channelService, "channelService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("message"), Set.of(ModuleId.of("luckperms")));
    }

    @Override
    public CompletableFuture<Void> enable() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> registerCommands(event, messageService));
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event, MessageService service) {
        Commands registrar = event.registrar();
        String primary = "msg";
        LiteralCommandNode<CommandSourceStack> root = commandNode(primary, service);
        registrar.register(plugin.getPluginMeta(), root, primary, COMMAND_ALIASES);
    }

    private LiteralCommandNode<CommandSourceStack> commandNode(String name, MessageService service) {
        return Commands.literal(name)
            .requires(stack -> stack.getSender() instanceof Player)
            .then(Commands.argument("target", StringArgumentType.word())
                .executes(context -> switchChannel(context))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> executeMessage(service, context))))
            .build();
    }

    private int switchChannel(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        String targetName = context.getArgument("target", String.class);
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        channelService.setDirect(player.getUniqueId(), target.getUniqueId(), target.getName());
        player.sendMessage(Component.text("You are now chatting privately with " + target.getName() + ".", NamedTextColor.LIGHT_PURPLE));
        return Command.SINGLE_SUCCESS;
    }

    private int executeMessage(MessageService service, CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String targetName = context.getArgument("target", String.class);
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        String message = context.getArgument("message", String.class);
        service.sendMessage(player, target, message);
        return Command.SINGLE_SUCCESS;
    }
}
