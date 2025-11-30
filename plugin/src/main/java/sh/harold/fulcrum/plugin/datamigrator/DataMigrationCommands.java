package sh.harold.fulcrum.plugin.datamigrator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;
import java.util.Optional;

final class DataMigrationCommands {

    private final DataMigratorService legacyMigrator;
    private final Optional<DataStoreMigrationService> storeMigrator;
    private final Optional<Runnable> migrationCompleteCallback;

    DataMigrationCommands(DataMigratorService legacyMigrator, DataStoreMigrationService storeMigrator, DataMigratorModule.MigrationGate gate) {
        this.legacyMigrator = Objects.requireNonNull(legacyMigrator, "legacyMigrator");
        this.storeMigrator = Optional.ofNullable(storeMigrator);
        this.migrationCompleteCallback = Optional.ofNullable(gate).map(g -> (Runnable) g::markComplete);
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("datamigrate")
            .requires(stack -> stack.getSender().hasPermission("fulcrum.datamigrate"))
            .executes(context -> migrateAll(context.getSource()))
            .then(Commands.literal("legacy").executes(context -> migrateLegacy(context.getSource())))
            .then(Commands.literal("mysql").executes(context -> migrateToMySql(context.getSource())))
            .build();
    }

    private int migrateAll(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.text("Starting full migration (Nitrite/SQLite, then legacy JSON -> MySQL)...", NamedTextColor.GOLD));
        if (storeMigrator.isEmpty()) {
            sender.sendMessage(Component.text("MySQL migration not available; check data config.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        storeMigrator.get().migrateToMySql()
            .whenComplete((storeReport, storeThrowable) -> {
                if (storeThrowable != null) {
                    sender.sendMessage(Component.text("MySQL migration failed: " + storeThrowable.getMessage(), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                    "MySQL migration done. Collections: " + storeReport.collectionsMigrated()
                        + ", documents migrated: " + storeReport.documentsMigrated()
                        + ", documents skipped: " + storeReport.documentsSkipped()
                        + ", ledger entries: " + storeReport.ledgerEntriesMigrated(),
                    NamedTextColor.GREEN
                ));
                legacyMigrator.migrateAllPlayers()
                    .whenComplete((legacyReport, legacyThrowable) -> {
                        if (legacyThrowable != null) {
                            sender.sendMessage(Component.text("Legacy JSON migration failed: " + legacyThrowable.getMessage(), NamedTextColor.RED));
                        } else {
                            sender.sendMessage(Component.text(
                                "Legacy JSON migration done. Legacy files: " + legacyReport.legacyFiles()
                                    + ", migrated: " + legacyReport.migrated()
                                    + ", unchanged: " + legacyReport.unchanged()
                                    + ", missing: " + legacyReport.missing(),
                                NamedTextColor.GREEN
                            ));
                        }
                        migrationCompleteCallback.ifPresent(Runnable::run);
                    });
            });
        return Command.SINGLE_SUCCESS;
    }

    private int migrateLegacy(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        sender.sendMessage(Component.text("Starting legacy player data migration...", NamedTextColor.GOLD));
        legacyMigrator.migrateAllPlayers()
            .whenComplete((report, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("Migration failed: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                    "Legacy migration done. Legacy files: " + report.legacyFiles() +
                        ", migrated: " + report.migrated() +
                        ", unchanged: " + report.unchanged() +
                        ", missing: " + report.missing(),
                    NamedTextColor.GREEN
                ));
            });
        return Command.SINGLE_SUCCESS;
    }

    private int migrateToMySql(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (storeMigrator.isEmpty()) {
            sender.sendMessage(Component.text("MySQL migration not available; check data config.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(Component.text("Starting data + ledger migration to MySQL...", NamedTextColor.GOLD));
        storeMigrator.get().migrateToMySql()
            .whenComplete((report, throwable) -> {
                if (throwable != null) {
                    sender.sendMessage(Component.text("MySQL migration failed: " + throwable.getMessage(), NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text(
                    "MySQL migration done. Collections: " + report.collectionsMigrated()
                        + ", documents: " + report.documentsMigrated()
                        + ", ledger entries: " + report.ledgerEntriesMigrated(),
                    NamedTextColor.GREEN
                ));
            });
        return Command.SINGLE_SUCCESS;
    }
}
