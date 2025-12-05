package sh.harold.fulcrum.plugin.item.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sh.harold.fulcrum.common.data.ledger.item.ItemCreationSource;
import sh.harold.fulcrum.plugin.item.ItemEngine;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.menu.ItemBrowserService;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ItemBrowserCommand {

    private final ItemBrowserService browserService;
    private final ItemEngine itemEngine;
    private final SuggestionProvider<CommandSourceStack> itemSuggestions;
    private final SuggestionProvider<CommandSourceStack> amountSuggestions;
    private final java.util.Map<String, String> trimmedToId;

    public ItemBrowserCommand(ItemBrowserService browserService, ItemEngine itemEngine) {
        this.browserService = Objects.requireNonNull(browserService, "browserService");
        this.itemEngine = Objects.requireNonNull(itemEngine, "itemEngine");
        this.trimmedToId = buildTrimmedIndex();
        this.itemSuggestions = (context, builder) -> {
            trimmedToId.keySet().forEach(builder::suggest);
            return builder.buildFuture();
        };
        this.amountSuggestions = (context, builder) -> {
            Stream.of("1", "16", "32", "64").forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("item")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> openBrowser(context.getSource()))
            .then(Commands.argument("id", StringArgumentType.word())
                .suggests(itemSuggestions)
                .executes(context -> giveItem(context, StringArgumentType.getString(context, "id"), 1))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                    .suggests(amountSuggestions)
                    .executes(context -> giveItem(
                        context,
                        StringArgumentType.getString(context, "id"),
                        IntegerArgumentType.getInteger(context, "amount")
                    ))
                )
            )
            .build();
    }

    private int openBrowser(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can peek at custom items.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        browserService.open(player)
            .exceptionally(throwable -> {
                player.sendMessage(Component.text("The item catalog is stretching its legs; try again soon.", NamedTextColor.RED));
                return null;
            });
        return Command.SINGLE_SUCCESS;
    }

    private int giveItem(CommandContext<CommandSourceStack> context, String id, int amount) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can grab custom items.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        String canonicalId = resolveId(id);
        if (canonicalId == null) {
            player.sendMessage(Component.text("Unknown item id: " + id, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        itemEngine.createItem(canonicalId, ItemCreationSource.COMMAND, player.getUniqueId()).ifPresentOrElse(stack -> {
            ItemStack copy = stack.clone();
            copy.setAmount(Math.min(amount, copy.getMaxStackSize()));
            var leftover = player.getInventory().addItem(copy);
            leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.sendMessage(Component.text("Spawned " + copy.getAmount() + "x " + canonicalId + ".", NamedTextColor.GREEN));
        }, () -> player.sendMessage(Component.text("Unknown item id: " + id, NamedTextColor.RED)));
        return Command.SINGLE_SUCCESS;
    }

    private Map<String, String> buildTrimmedIndex() {
        ItemRegistry registry = this.itemEngine.registry();
        return registry.definitions().stream()
            .map(sh.harold.fulcrum.plugin.item.model.CustomItem::id)
            .filter(id -> !id.startsWith("vanilla:"))
            .collect(Collectors.toMap(
                this::trimmed,
                java.util.function.Function.identity(),
                (first, second) -> first,
                java.util.TreeMap::new
            ));
    }

    private String trimmed(String id) {
        String raw = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String normalized = raw.replaceAll("[^A-Za-z0-9]", "_");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String resolveId(String raw) {
        ItemRegistry registry = this.itemEngine.registry();
        if (registry.get(raw).isPresent()) {
            return raw;
        }
        String lookup = trimmed(raw);
        return trimmedToId.get(lookup);
    }
}
