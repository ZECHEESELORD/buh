package sh.harold.fulcrum.plugin.beacon;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Supplier;

final class BeaconDebugCommand {

    private final BeaconSanitizerService sanitizerService;
    private final Supplier<java.util.UUID> adminIdSupplier;

    BeaconDebugCommand(BeaconSanitizerService sanitizerService, Supplier<java.util.UUID> adminIdSupplier) {
        this.sanitizerService = Objects.requireNonNull(sanitizerService, "sanitizerService");
        this.adminIdSupplier = Objects.requireNonNull(adminIdSupplier, "adminIdSupplier");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("getlegitnetherstar")
            .requires(stack -> stack.getSender() instanceof org.bukkit.entity.Player player && isAdmin(player.getUniqueId()))
            .executes(context -> giveLegit(context.getSource(), "Legitimate Nether Star"))
            .then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(context -> giveLegit(context.getSource(), context.getArgument("name", String.class))));
        return builder.build();
    }

    private int giveLegit(CommandSourceStack source, String name) {
        if (!(source.getSender() instanceof org.bukkit.entity.Player player)) {
            source.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }

        ItemStack star = new ItemStack(Material.NETHER_STAR);
        var meta = star.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE));
            star.setItemMeta(meta);
        }
        sanitizerService.markLegitimate(star);
        player.getInventory().addItem(star);
        player.sendMessage(Component.text("Granted legitimate nether star.", NamedTextColor.GREEN));
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private boolean isAdmin(java.util.UUID playerId) {
        java.util.UUID adminId = adminIdSupplier.get();
        return adminId != null && adminId.equals(playerId);
    }
}
