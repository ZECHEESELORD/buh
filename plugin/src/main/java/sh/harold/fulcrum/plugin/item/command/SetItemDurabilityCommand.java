package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.plugin.item.runtime.DurabilityData;
import sh.harold.fulcrum.plugin.item.runtime.ItemPdc;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

public final class SetItemDurabilityCommand {

    private final StaffGuard staffGuard;
    private final ItemResolver resolver;
    private final ItemPdc itemPdc;
    private final ItemStatBridge statBridge;

    public SetItemDurabilityCommand(StaffGuard staffGuard, ItemResolver resolver, ItemPdc itemPdc, ItemStatBridge statBridge) {
        this.staffGuard = staffGuard;
        this.resolver = resolver;
        this.itemPdc = itemPdc;
        this.statBridge = statBridge;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("setitemdurability")
            .requires(source -> staffGuard.isStaff(source))
            .then(Commands.argument("current", IntegerArgumentType.integer())
                .executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), null, EquipmentSlot.HAND))
                .then(Commands.argument("max", IntegerArgumentType.integer(1))
                    .executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), IntegerArgumentType.getInteger(context, "max"), EquipmentSlot.HAND))
                    .then(Commands.literal("offhand").executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), IntegerArgumentType.getInteger(context, "max"), EquipmentSlot.OFF_HAND)))
                    .then(Commands.literal("mainhand").executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), IntegerArgumentType.getInteger(context, "max"), EquipmentSlot.HAND)))
                )
                .then(Commands.literal("offhand").executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), null, EquipmentSlot.OFF_HAND)))
                .then(Commands.literal("mainhand").executes(context -> apply(context.getSource(), IntegerArgumentType.getInteger(context, "current"), null, EquipmentSlot.HAND)))
            );
        return root.build();
    }

    private int apply(CommandSourceStack source, int current, Integer max, EquipmentSlot slot) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        ItemStack stack = slot == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            player.sendMessage(Component.text("No item in that slot.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        return resolver.resolve(stack).map(instance -> {
            var durability = instance.durability().orElse(null);
            if (durability == null || durability.data().max() <= 0) {
                player.sendMessage(Component.text("Item has no durability to set.", NamedTextColor.RED));
                return Command.SINGLE_SUCCESS;
            }
            int targetMax = max == null ? durability.data().max() : max;
            try {
                DurabilityData updated = new DurabilityData(current, targetMax);
                ItemStack updatedStack = itemPdc.writeDurability(instance.stack(), updated);
                if (slot == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(updatedStack);
                } else {
                    player.getInventory().setItemInMainHand(updatedStack);
                }
                statBridge.refreshPlayer(player);
                player.sendMessage(Component.text("Durability set to " + updated.current() + "/" + updated.max() + ".", NamedTextColor.GREEN));
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text("Invalid durability values.", NamedTextColor.RED));
            }
            return Command.SINGLE_SUCCESS;
        }).orElseGet(() -> {
            player.sendMessage(Component.text("Failed to resolve item.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        });
    }
}
