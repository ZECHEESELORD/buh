package sh.harold.fulcrum.plugin.stash;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;

import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class StashModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private StashService stashService;

    public StashModule(JavaPlugin plugin, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("stash"), Set.of(ModuleId.of("data")));
    }

    @Override
    public CompletableFuture<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        stashService = new StashService(plugin, dataApi);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> disable() {
        if (stashService != null) {
            stashService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<StashService> stashService() {
        return Optional.ofNullable(stashService);
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        LiteralCommandNode<CommandSourceStack> view = Commands.literal("viewstash")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> executeView(context.getSource()))
            .build();

        LiteralCommandNode<CommandSourceStack> pickup = Commands.literal("pickupstash")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> executePickup(context.getSource()))
            .build();

        registrar.register(view, "viewstash", java.util.List.of());
        registrar.register(pickup, "pickupstash", java.util.List.of());
    }

    private int executeView(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can peek at stash contents.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        stashService.view(player.getUniqueId())
            .whenComplete((view, throwable) -> runSync(() -> {
                if (throwable != null) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to view stash for " + player.getUniqueId(), throwable);
                    player.sendMessage(Component.text("Stash drawer jammed; try again soon.", NamedTextColor.RED));
                    return;
                }
                sendView(player, view);
            }));
        return Command.SINGLE_SUCCESS;
    }

    private int executePickup(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can pull from a stash.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        stashService.pickup(player)
            .whenComplete((result, throwable) -> runSync(() -> {
                if (throwable != null) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to withdraw stash for " + player.getUniqueId(), throwable);
                    player.sendMessage(Component.text("Stash stayed shut; try again in a moment.", NamedTextColor.RED));
                    return;
                }
                sendPickupSummary(player, result);
            }));
        return Command.SINGLE_SUCCESS;
    }

    private void sendView(Player player, StashView view) {
        if (view.items().isEmpty()) {
            player.sendMessage(Component.text("Stash is empty; not a trinket in sight.", NamedTextColor.GRAY));
            return;
        }

        player.sendMessage(Component.text("Stash: " + view.items().size() + " stack(s) resting.", NamedTextColor.GOLD));
        int index = 1;
        for (ItemStack item : view.items()) {
            Component label = describe(item);
            player.sendMessage(Component.text(index + ": ", NamedTextColor.DARK_GRAY).append(label));
            index++;
        }
    }

    private void sendPickupSummary(Player player, PickupResult result) {
        if (result.movedItems() <= 0) {
            if (result.remainingStacks() <= 0) {
                player.sendMessage(Component.text("Stash was empty; nothing to collect.", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("Inventory had no room; stash stays cozy.", NamedTextColor.RED));
            }
            return;
        }

        Component summary = Component.text()
            .append(Component.text("Pocketed " + result.movedItems() + " item(s)", NamedTextColor.GREEN))
            .append(Component.text("; ", NamedTextColor.DARK_GRAY))
            .append(Component.text(result.remainingStacks() + " stack(s) remain.", NamedTextColor.GOLD))
            .build();
        player.sendMessage(summary);
    }

    private Component describe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Component name = meta != null && meta.hasDisplayName()
            ? meta.displayName()
            : Component.text(item.getI18NDisplayName(), NamedTextColor.WHITE);
        return Component.text(item.getAmount() + "x ", NamedTextColor.YELLOW).append(name.color(NamedTextColor.WHITE));
    }

    private void runSync(Runnable task) {
        if (plugin.getServer().isPrimaryThread()) {
            task.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
