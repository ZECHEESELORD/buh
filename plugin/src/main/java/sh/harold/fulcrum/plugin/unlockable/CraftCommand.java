package sh.harold.fulcrum.plugin.unlockable;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class CraftCommand {

    private final JavaPlugin plugin;
    private final UnlockableService unlockableService;

    public CraftCommand(JavaPlugin plugin, UnlockableService unlockableService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.unlockableService = Objects.requireNonNull(unlockableService, "unlockableService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("craft")
            .executes(this::openCrafting)
            .build();
    }

    private int openCrafting(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use the crafting perk.", NamedTextColor.RED));
            return 0;
        }
        UUID playerId = player.getUniqueId();
        unlockableService.isEnabled(playerId, UnlockableCatalog.POCKET_CRAFTER)
            .whenComplete((enabled, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Could not check your perks right now.", NamedTextColor.RED));
                    return;
                }
                if (!enabled) {
                    sender.sendMessage(Component.text("Unlock and enable Pocket Crafter in /perks first.", NamedTextColor.RED));
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.openWorkbench(null, true);
                    player.sendMessage(Component.text("Opening your pocket crafting grid.", NamedTextColor.AQUA));
                });
            });
        return Command.SINGLE_SUCCESS;
    }
}
