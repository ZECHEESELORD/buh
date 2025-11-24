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
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.util.Objects;

final class BeaconDebugCommand {

    private final BeaconSanitizerService sanitizerService;
    private final StaffGuard staffGuard;

    BeaconDebugCommand(BeaconSanitizerService sanitizerService, StaffGuard staffGuard) {
        this.sanitizerService = Objects.requireNonNull(sanitizerService, "sanitizerService");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("getlegitnetherstar")
            .requires(staffGuard::isStaff)
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
}
