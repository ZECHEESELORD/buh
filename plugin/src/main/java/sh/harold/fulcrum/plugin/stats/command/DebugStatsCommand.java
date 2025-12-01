package sh.harold.fulcrum.plugin.stats.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.stats.core.StatSnapshot;
import sh.harold.fulcrum.stats.core.StatSourceId;
import sh.harold.fulcrum.stats.service.EntityKey;
import sh.harold.fulcrum.stats.service.StatService;

import java.util.Map;

public final class DebugStatsCommand {

    private final StaffGuard staffGuard;
    private final StatService statService;

    public DebugStatsCommand(StaffGuard staffGuard, StatService statService) {
        this.staffGuard = staffGuard;
        this.statService = statService;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("debugstats")
            .requires(staffGuard::isStaff)
            .executes(context -> execute(context.getSource()));
        return root.build();
    }

    private int execute(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        var container = statService.getContainer(EntityKey.fromUuid(player.getUniqueId()));
        player.sendMessage(Component.text("Stats for " + player.getName() + ":", NamedTextColor.GOLD));
        for (StatSnapshot snapshot : container.debugView()) {
            player.sendMessage(Component.text(snapshot.statId().value() + ": " + snapshot.finalValue(), NamedTextColor.AQUA));
            for (Map.Entry<StatSourceId, java.util.List<sh.harold.fulcrum.stats.core.StatModifier>> entry : snapshot.modifiers().getOrDefault(sh.harold.fulcrum.stats.core.ModifierOp.FLAT, Map.of()).entrySet()) {
                double sum = entry.getValue().stream().mapToDouble(sh.harold.fulcrum.stats.core.StatModifier::value).sum();
                player.sendMessage(Component.text("  - " + entry.getKey().value() + ": " + sum, NamedTextColor.GRAY));
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
