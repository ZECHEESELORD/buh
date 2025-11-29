package sh.harold.fulcrum.plugin.staff.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DocumentSnapshot;
import sh.harold.fulcrum.common.data.impl.NitriteDocumentStore;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * Brigadier command builder for /dumpdata.
 */
public final class DumpDataCommand {

    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final StaffGuard staffGuard;
    private final DataModule dataModule;
    private final ObjectMapper mapper;

    public DumpDataCommand(JavaPlugin plugin, StaffGuard staffGuard, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("dumpdata")
            .requires(staffGuard::isStaff)
            .executes(this::dumpData)
            .build();
    }

    private int dumpData(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        NitriteDocumentStore store = dataModule.documentStore()
            .filter(NitriteDocumentStore.class::isInstance)
            .map(NitriteDocumentStore.class::cast)
            .orElse(null);
        if (store == null) {
            sender.sendMessage(Component.text("Nitrite store not active; swap to nitrite.db before dumping.", NamedTextColor.RED));
            return 0;
        }

        Path debugDirectory = plugin.getDataFolder().toPath().resolve("debug");
        try {
            Files.createDirectories(debugDirectory);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to create debug directory", exception);
            sender.sendMessage(Component.text("Could not ready " + plugin.getName() + "/debug; check the server logs.", NamedTextColor.RED));
            return 0;
        }

        sender.sendMessage(Component.text("Bottling a Nitrite snapshot inside " + plugin.getName() + "/debug; give it a beat.", NamedTextColor.GOLD));
        store.collections()
            .thenCompose(collections -> dumpCollections(store, collections, debugDirectory))
            .whenComplete((output, throwable) -> {
                if (throwable != null) {
                    plugin.getLogger().log(Level.WARNING, "Failed to dump Nitrite data", throwable);
                    sender.sendMessage(Component.text("Data dump stumbled; peek at the logs for the why.", NamedTextColor.RED));
                    return;
                }
                sender.sendMessage(Component.text("Nitrite dump tucked into " + plugin.getName() + "/debug/" + output.getFileName(), NamedTextColor.GREEN));
            });
        return Command.SINGLE_SUCCESS;
    }

    private CompletionStage<Path> dumpCollections(NitriteDocumentStore store, Set<String> collections, Path outputDirectory) {
        List<CompletableFuture<CollectionDump>> tasks = collections.stream()
            .sorted()
            .map(name -> store.all(name)
                .thenApply(snapshots -> new CollectionDump(name, snapshots))
                .toCompletableFuture())
            .toList();

        CompletableFuture<Void> awaitingAll = CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
        return awaitingAll.thenApply(ignored -> tasks.stream()
                .map(CompletableFuture::join)
                .toList())
            .thenApply(dumps -> writeDump(dumps, outputDirectory));
    }

    private Path writeDump(List<CollectionDump> dumps, Path outputDirectory) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());

        Map<String, Object> collectionsNode = new LinkedHashMap<>();
        for (CollectionDump dump : dumps) {
            Map<String, Object> documents = new LinkedHashMap<>();
            for (DocumentSnapshot snapshot : dump.snapshots().stream()
                .sorted(Comparator.comparing(snapshot -> snapshot.key().id()))
                .toList()) {
                documents.put(snapshot.key().id(), snapshot.copy());
            }
            collectionsNode.put(dump.name(), documents);
        }
        root.put("collections", collectionsNode);

        Path outputPath = outputDirectory.resolve("nitrite-dump-" + FILE_NAME_FORMAT.format(Instant.now()) + ".json");
        try {
            mapper.writeValue(outputPath.toFile(), root);
            return outputPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write Nitrite dump", exception);
        }
    }

    private record CollectionDump(String name, List<DocumentSnapshot> snapshots) {
    }
}
