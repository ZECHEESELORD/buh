package sh.harold.fulcrum.plugin.playermenu;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static io.papermc.paper.command.brigadier.argument.ArgumentTypes.player;

public final class ClearCommand {

    private final PlayerMenuService menuService;

    public ClearCommand(PlayerMenuService menuService) {
        this.menuService = Objects.requireNonNull(menuService, "menuService");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("clear")
            .requires(stack -> stack.getSender().hasPermission("minecraft.command.clear"))
            .executes(this::clearSelf)
            .then(argument("targets", player()).executes(this::clearTargets))
            .build();
    }

    private int clearSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Choose at least one player to clear.", NamedTextColor.RED));
            return 0;
        }

        ClearResult result = clearInventory(player);
        sendFeedback(sender, player, result, true);
        return Command.SINGLE_SUCCESS;
    }

    private int clearTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        CommandSender sender = source.getSender();
        PlayerSelectorArgumentResolver resolver = context.getArgument("targets", PlayerSelectorArgumentResolver.class);
        List<Player> targets = resolver.resolve(source);
        if (targets.isEmpty()) {
            sender.sendMessage(Component.text("No matching players found.", NamedTextColor.RED));
            return 0;
        }

        for (Player target : targets) {
            ClearResult result = clearInventory(target);
            sendFeedback(sender, target, result, sender.equals(target));
        }

        return Command.SINGLE_SUCCESS;
    }

    private ClearResult clearInventory(Player target) {
        PlayerInventory inventory = target.getInventory();
        ItemStack[] contents = inventory.getContents();
        int removedItems = 0;
        boolean preservedMenuItem = false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (menuService.isMenuItem(stack)) {
                preservedMenuItem = true;
                continue;
            }
            removedItems += stack.getAmount();
            inventory.setItem(slot, null);
        }

        ItemStack cursor = target.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir() && !menuService.isMenuItem(cursor)) {
            removedItems += cursor.getAmount();
            target.setItemOnCursor(null);
        }

        menuService.distribute(target);

        return new ClearResult(removedItems, preservedMenuItem);
    }

    private void sendFeedback(CommandSender sender, Player target, ClearResult result, boolean selfTarget) {
        if (result.removedItems() == 0) {
            sender.sendMessage(Component.text("Nothing to clear for " + target.getName() + ".", NamedTextColor.YELLOW));
            return;
        }

        Component summary = Component.text()
            .append(Component.text("Cleared ", NamedTextColor.GREEN))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text("'s inventory; removed ", NamedTextColor.GRAY))
            .append(Component.text(result.removedItems(), NamedTextColor.YELLOW))
            .append(Component.text(" items", NamedTextColor.GRAY))
            .append(result.preservedMenuItem()
                ? Component.text(" and kept the player menu item.", NamedTextColor.GREEN)
                : Component.text(".", NamedTextColor.GRAY))
            .build();
        sender.sendMessage(summary);

        if (!selfTarget && sender != target) {
            Component notice = Component.text()
                .append(Component.text(sender.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" cleared your inventory; ", NamedTextColor.GRAY))
                .append(Component.text(result.removedItems(), NamedTextColor.YELLOW))
                .append(Component.text(" items removed", NamedTextColor.GRAY))
                .append(result.preservedMenuItem()
                    ? Component.text(", player menu item kept.", NamedTextColor.GREEN)
                    : Component.text(".", NamedTextColor.GRAY))
                .build();
            target.sendMessage(notice);
        }
    }

    private record ClearResult(int removedItems, boolean preservedMenuItem) {
    }
}
