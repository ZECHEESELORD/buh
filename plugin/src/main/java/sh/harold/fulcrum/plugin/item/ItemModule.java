package sh.harold.fulcrum.plugin.item;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.ledger.item.ItemLedgerRepository;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.item.registry.ItemDefinitionProvider;
import sh.harold.fulcrum.plugin.item.registry.SampleItemProvider;
import sh.harold.fulcrum.plugin.item.command.ItemBrowserCommand;
import sh.harold.fulcrum.plugin.item.command.DetailedItemInfoCommand;
import sh.harold.fulcrum.plugin.item.command.ToggleItemPacketViewCommand;
import sh.harold.fulcrum.plugin.item.menu.ItemBrowserService;
import sh.harold.fulcrum.plugin.stats.StatsModule;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ItemModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final StatsModule statsModule;
    private final DataModule dataModule;
    private ItemEngine engine;
    private ItemBrowserService itemBrowserService;

    public ItemModule(JavaPlugin plugin, StatsModule statsModule, DataModule dataModule) {
        this.plugin = plugin;
        this.statsModule = statsModule;
        this.dataModule = dataModule;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("item-engine"), ModuleCategory.GAMEPLAY, ModuleId.of("rpg-stats"), ModuleId.of("menu"));
    }

    @Override
    public CompletionStage<Void> enable() {
        List<ItemDefinitionProvider> providers = List.of(new SampleItemProvider());
        ItemLedgerRepository itemLedgerRepository = dataModule.dataApi()
            .flatMap(DataApi::itemLedger)
            .orElse(null);
        engine = new ItemEngine(plugin, statsModule, providers, itemLedgerRepository);
        registerSampleAbilities();
        MenuService menuService = plugin.getServer().getServicesManager().load(MenuService.class);
        if (menuService == null) {
            throw new IllegalStateException("MenuService not available for item catalog");
        }
        itemBrowserService = new ItemBrowserService(plugin, engine, menuService);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        engine.enable();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (engine != null) {
            engine.disable();
        }
        return CompletableFuture.completedFuture(null);
    }

    public ItemEngine engine() {
        return engine;
    }

    public ItemBrowserService itemBrowserService() {
        return itemBrowserService;
    }

    private void registerSampleAbilities() {
        engine.abilityService().registerExecutor("fulcrum:blink", (definition, context) -> {
            var player = context.player();
            player.sendMessage(definition.displayName().append(Component.text(" activated.", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        });
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        if (itemBrowserService == null) {
            return;
        }
        Commands registrar = event.registrar();
        ItemBrowserCommand itemBrowserCommand = new ItemBrowserCommand(itemBrowserService, engine);
        registrar.register(itemBrowserCommand.build(), "item", java.util.List.of());
        DetailedItemInfoCommand detailedItemInfoCommand = new DetailedItemInfoCommand(plugin, engine);
        registrar.register(detailedItemInfoCommand.build(), "viewdetailediteminfo", java.util.List.of());
        ToggleItemPacketViewCommand toggleItemPacketViewCommand = new ToggleItemPacketViewCommand(plugin, engine);
        registrar.register(toggleItemPacketViewCommand.build(), "toggleitempacketview", java.util.List.of());
    }
}
