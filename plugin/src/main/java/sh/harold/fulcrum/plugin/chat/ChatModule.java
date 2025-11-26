package sh.harold.fulcrum.plugin.chat;

import net.luckperms.api.LuckPerms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.message.MessageService;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class ChatModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final LuckPermsModule luckPermsModule;
    private final ChatChannelService channelService;
    private final MessageService messageService;
    private StaffChatFormatter staffChatFormatter;
    private StaffChatBossBarService staffChatBossBarService;

    public ChatModule(JavaPlugin plugin, LuckPermsModule luckPermsModule, ChatChannelService channelService, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.luckPermsModule = Objects.requireNonNull(luckPermsModule, "luckPermsModule");
        this.channelService = Objects.requireNonNull(channelService, "channelService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("chat"), Set.of(ModuleId.of("luckperms")), ModuleCategory.SOCIAL);
    }

    @Override
    public CompletionStage<Void> enable() {
        LuckPerms luckPerms = luckPermsModule.luckPerms().orElse(null);
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms not available; chat will use fallback formatting.");
        }
        ChatFormatService chatFormatService = luckPerms == null ? null : new ChatFormatService(luckPerms);
        staffChatFormatter = new StaffChatFormatter(plugin, chatFormatService);
        staffChatBossBarService = new StaffChatBossBarService(plugin, channelService);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Supplier<FormattedUsernameService> usernameServiceSupplier = () -> luckPermsModule.formattedUsernameService().orElse(null);
        pluginManager.registerEvents(new JoinMessageListener(plugin, usernameServiceSupplier), plugin);
        pluginManager.registerEvents(new ChatListener(plugin, chatFormatService, channelService, messageService, staffChatFormatter), plugin);
        pluginManager.registerEvents(staffChatBossBarService, plugin);
        channelService.addListener(staffChatBossBarService);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        LiteralCommandNode<CommandSourceStack> root = Commands.literal("chat")
            .requires(stack -> stack.getSender() instanceof org.bukkit.entity.Player)
            .then(Commands.literal("all").executes(context -> {
                var sender = (org.bukkit.entity.Player) context.getSource().getSender();
                channelService.setAll(sender.getUniqueId());
                sender.sendMessage(Component.text("You are now chatting in global.", NamedTextColor.GREEN));
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("staff").executes(context -> {
                var sender = (org.bukkit.entity.Player) context.getSource().getSender();
                if (!channelService.isStaff(sender.getUniqueId())) {
                    sender.sendMessage(Component.text("You are not staff.", NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                }
                channelService.setStaff(sender.getUniqueId());
                sender.sendMessage(Component.text("You are now chatting in staff channel.", NamedTextColor.AQUA));
                return Command.SINGLE_SUCCESS;
            }))
            .build();
        registrar.register(root, "chat", java.util.List.of());

        LiteralCommandNode<CommandSourceStack> staffChat = Commands.literal("sc")
            .requires(stack -> stack.getSender() instanceof org.bukkit.entity.Player player && channelService.isStaff(player.getUniqueId()))
            .then(Commands.argument("message", StringArgumentType.greedyString())
                .executes(context -> {
                    var sender = (org.bukkit.entity.Player) context.getSource().getSender();
                    String raw = context.getArgument("message", String.class);
                    Component formatted = staffChatFormatter.format(sender, Component.text(raw));
                    plugin.getServer().getOnlinePlayers().forEach(player -> {
                        if (channelService.isStaff(player.getUniqueId())) {
                            player.sendMessage(formatted);
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                }))
            .build();
        registrar.register(staffChat, "sc", java.util.List.of("staffchat"));
    }
}
