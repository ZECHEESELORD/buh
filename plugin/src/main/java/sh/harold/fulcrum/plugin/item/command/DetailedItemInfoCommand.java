package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.common.data.ledger.item.ItemInstanceRecord;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DetailedItemInfoCommand {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
    private final Plugin plugin;
    private final ItemEngine itemEngine;

    public DetailedItemInfoCommand(Plugin plugin, ItemEngine itemEngine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("viewdetailediteminfo")
            .requires(stack -> stack.getSender().hasPermission("fulcrum.item.details"))
            .executes(context -> inspect(context.getSource()))
            .build();
    }

    private int inspect(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can inspect items.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir()) {
            sender.sendMessage(Component.text("Hold an item to inspect.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            sender.sendMessage(Component.text("This item has no metadata to inspect.", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        String itemId = itemEngine.resolver().readItemId(stack);
        int version = itemEngine.itemPdc().readVersion(stack).orElse(0);
        Optional<UUID> instanceId = itemEngine.itemPdc().readInstanceId(stack);

        sender.sendMessage(Component.text("=== Item Details ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Material: ", NamedTextColor.GRAY)
            .append(Component.text(stack.getType().name(), NamedTextColor.AQUA))
            .append(Component.text(" x" + stack.getAmount(), NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("Item ID: ", NamedTextColor.GRAY)
            .append(Component.text(itemId == null ? "(unknown)" : itemId, NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Instance ID: ", NamedTextColor.GRAY)
            .append(Component.text(instanceId.map(UUID::toString).orElse("(none)"), NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("Version: ", NamedTextColor.GRAY)
            .append(Component.text(version, NamedTextColor.AQUA)));

        PersistentDataContainer container = meta.getPersistentDataContainer();
        sender.sendMessage(Component.text("--- PDC Keys ---", NamedTextColor.GOLD));
        container.getKeys().stream()
            .sorted((a, b) -> (a.getNamespace() + a.getKey()).compareTo(b.getNamespace() + b.getKey()))
            .forEach(key -> sender.sendMessage(renderPdcEntry(container, key)));

        if (instanceId.isPresent()) {
            itemEngine.itemLedger().ifPresentOrElse(
                ledger -> appendLedgerInfo(ledger, instanceId.get(), player),
                () -> sender.sendMessage(Component.text("Ledger: (not configured)", NamedTextColor.DARK_GRAY))
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private Component renderPdcEntry(PersistentDataContainer container, NamespacedKey key) {
        List<PersistentDataType<?, ?>> types = List.of(
            PersistentDataType.STRING,
            PersistentDataType.INTEGER,
            PersistentDataType.LONG,
            PersistentDataType.DOUBLE,
            PersistentDataType.FLOAT,
            PersistentDataType.SHORT,
            PersistentDataType.BYTE,
            PersistentDataType.BYTE_ARRAY,
            PersistentDataType.INTEGER_ARRAY,
            PersistentDataType.LONG_ARRAY
        );
        for (PersistentDataType<?, ?> type : types) {
            if (container.has(key, type)) {
                Object value = container.get(key, (PersistentDataType<Object, Object>) type);
                return Component.text(key.toString(), NamedTextColor.GRAY)
                    .append(Component.text(" = ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(formatValue(value), NamedTextColor.AQUA));
            }
        }
        return Component.text(key.toString(), NamedTextColor.GRAY)
            .append(Component.text(" = ", NamedTextColor.DARK_GRAY))
            .append(Component.text("(unknown type)", NamedTextColor.RED));
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "(null)";
        }
        if (value instanceof byte[] bytes) {
            return "byte[" + bytes.length + "]";
        }
        if (value instanceof int[] ints) {
            return "int[" + ints.length + "]";
        }
        if (value instanceof long[] longs) {
            return "long[" + longs.length + "]";
        }
        return value.toString();
    }

    private void appendLedgerInfo(ItemLedgerRepository ledger, UUID instanceId, Player player) {
        player.sendMessage(Component.text("Ledger: disabled (item holds authoritative data)", NamedTextColor.DARK_GRAY));
    }
}
