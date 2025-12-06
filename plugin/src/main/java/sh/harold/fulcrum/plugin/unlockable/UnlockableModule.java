package sh.harold.fulcrum.plugin.unlockable;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.cooldown.CooldownRegistry;
import sh.harold.fulcrum.common.cooldown.InMemoryCooldownRegistry;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.economy.EconomyModule;
import sh.harold.fulcrum.plugin.perk.PerkCommand;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class UnlockableModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final DataModule dataModule;
    private final EconomyModule economyModule;
    private UnlockableRegistry registry;
    private CosmeticRegistry cosmeticRegistry;
    private UnlockableService unlockableService;
    private CooldownRegistry cooldownRegistry;
    private ActionCosmeticListener actionCosmeticListener;

    public UnlockableModule(JavaPlugin plugin, DataModule dataModule, EconomyModule economyModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
        this.economyModule = Objects.requireNonNull(economyModule, "economyModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
            ModuleId.of("perks"),
            Set.of(ModuleId.of("data"), ModuleId.of("economy")),
            ModuleCategory.GAMEPLAY
        );
    }

    @Override
    public CompletionStage<Void> enable() {
        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available"));
        registry = new UnlockableRegistry();
        cosmeticRegistry = new CosmeticRegistry();
        UnlockableCatalog.registerDefaults(registry, cosmeticRegistry);
        cooldownRegistry = new InMemoryCooldownRegistry();
        unlockableService = new UnlockableService(
            dataApi,
            registry,
            cosmeticRegistry,
            cooldownRegistry,
            economyModule::economyService,
            plugin.getLogger()
        );
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new UnlockableSessionListener(unlockableService, plugin.getLogger()), plugin);
        actionCosmeticListener = new ActionCosmeticListener(unlockableService, cosmeticRegistry, plugin, plugin.getLogger());
        pluginManager.registerEvents(actionCosmeticListener, plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (actionCosmeticListener != null) {
            actionCosmeticListener.clearSeats();
        }
        if (cooldownRegistry != null) {
            cooldownRegistry.close();
        }
        return FulcrumModule.super.disable();
    }

    public UnlockableService unlockableService() {
        return unlockableService;
    }

    public UnlockableRegistry registry() {
        return registry;
    }

    public CosmeticRegistry cosmeticRegistry() {
        return cosmeticRegistry;
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        registrar.register(new PerkCommand(unlockableService, registry).build(), "perks", List.of("perk", "upgrade"));
        registrar.register(new CraftCommand(plugin, unlockableService).build(), "craft", List.of("workbench"));
        registrar.register(new GetOffMyHeadCommand().build(), "getoffmyhead", List.of("offmyhead", "unstack"));
    }
}
