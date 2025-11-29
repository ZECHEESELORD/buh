package sh.harold.fulcrum.plugin.datamigrator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

final class DataMigratorCommand {

    private final DataMigratorService migrator;

    DataMigratorCommand(DataMigratorService migrator) {
        this.migrator = Objects.requireNonNull(migrator, "migrator");
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("datamigrate")
            .requires(stack -> stack.getSender().hasPermission("fulcrum.datamigrate"))
            .executes(context -> migrate(context.getSource()))
            .build();
    }

    private int migrate(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.text("Starting legacy player data migration...", NamedTextColor.GOLD));
        migrator.migrateAllPlayers()
            .whenComplete((report, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Migration failed: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                    "Migration done. Legacy files: " + report.legacyFiles() +
                        ", migrated: " + report.migrated() +
                        ", unchanged: " + report.unchanged() +
                        ", missing: " + report.missing(),
                    NamedTextColor.GREEN
                ));
            });
        return Command.SINGLE_SUCCESS;
    }
}
