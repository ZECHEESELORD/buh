package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.util.Objects;

public final class ToggleItemPacketViewCommand {

    private static final String PERMISSION = "fulcrum.item.packetview.toggle";

    private final Plugin plugin;
    private final ItemEngine itemEngine;

    public ToggleItemPacketViewCommand(Plugin plugin, ItemEngine itemEngine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("toggleitempacketview")
            .requires(stack -> stack.getSender().hasPermission(PERMISSION))
            .executes(context -> toggle(context.getSource()))
            .build();
    }

    private int toggle(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle their item packet view.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (!itemEngine.hasPacketLoreAdapter()) {
            sender.sendMessage(Component.text("Packet lore rendering is not active; PacketEvents may not be initialized.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        itemEngine.togglePacketLore(player.getUniqueId());
        if (!itemEngine.isPacketLoreDisabled(player.getUniqueId())) {
            sender.sendMessage(Component.text("Packet-rendered item lore is now ON.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Packet-rendered item lore is now OFF; items will appear vanilla.", NamedTextColor.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }
}
