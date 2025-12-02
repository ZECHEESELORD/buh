package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.stats.core.StatId;

import java.util.Map;

public final class DebugItemDataCommand {

    private final StaffGuard staffGuard;
    private final ItemResolver resolver;
    private final ItemPdc itemPdc;

    public DebugItemDataCommand(StaffGuard staffGuard, ItemResolver resolver, ItemPdc itemPdc) {
        this.staffGuard = staffGuard;
        this.resolver = resolver;
        this.itemPdc = itemPdc;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("debugitemdata")
            .requires(staffGuard::isStaff)
            .executes(context -> execute(context.getSource(), EquipmentSlot.HAND))
            .then(Commands.literal("offhand").executes(context -> execute(context.getSource(), EquipmentSlot.OFF_HAND)))
            .then(Commands.literal("mainhand").executes(context -> execute(context.getSource(), EquipmentSlot.HAND)));
        return root.build();
    }

    private int execute(CommandSourceStack source, EquipmentSlot slot) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        ItemStack stack = slot == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            player.sendMessage(Component.text("No item in that slot.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        resolver.resolve(stack).ifPresentOrElse(instance -> {
            player.sendMessage(Component.text("ID: " + instance.definition().id(), NamedTextColor.GOLD));
            player.sendMessage(Component.text("Material: " + instance.definition().material(), NamedTextColor.GRAY));
            itemPdc.readDurability(stack).ifPresent(durability ->
                player.sendMessage(Component.text("Durability: " + Math.max(0, durability.current()) + "/" + durability.max(), NamedTextColor.YELLOW)));
            itemPdc.readStats(stack).ifPresent(stats -> {
                player.sendMessage(Component.text("Stats:", NamedTextColor.AQUA));
                stats.forEach((StatId id, Double value) ->
                    player.sendMessage(Component.text(" - " + id.value() + ": " + value, NamedTextColor.AQUA)));
            });
            itemPdc.readEnchants(stack).ifPresent(enchants -> {
                player.sendMessage(Component.text("Enchants:", NamedTextColor.BLUE));
                enchants.forEach((id, level) ->
                    player.sendMessage(Component.text(" - " + id + ": " + level, NamedTextColor.BLUE)));
            });
        }, () -> player.sendMessage(Component.text("Failed to resolve item.", NamedTextColor.RED)));
        return Command.SINGLE_SUCCESS;
    }
}
