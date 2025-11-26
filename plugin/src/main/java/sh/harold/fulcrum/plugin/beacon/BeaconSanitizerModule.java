package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.stash.StashModule;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class BeaconSanitizerModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final StashModule stashModule;
    private final StaffGuard staffGuard;
    private BeaconSanitizerService sanitizerService;

    public BeaconSanitizerModule(JavaPlugin plugin, StashModule stashModule, StaffGuard staffGuard) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stashModule = Objects.requireNonNull(stashModule, "stashModule");
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("beacon-sanitizer"), Set.of(ModuleId.of("stash")), ModuleCategory.WORLD);
    }

    @Override
    public CompletionStage<Void> enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        sanitizerService = new BeaconSanitizerService(plugin, stashModule.stashService()
            .orElseThrow(() -> new IllegalStateException("StashService not available")));
        pluginManager.registerEvents(new BeaconSanitizerListener(sanitizerService), plugin);
        sanitizerService.start();
        plugin.getLifecycleManager().registerEventHandler(
            io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
            event -> event.registrar().register(
                new BeaconDebugCommand(sanitizerService, staffGuard).build(),
                "getlegitnetherstar",
                java.util.List.of()
            )
        );
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (sanitizerService != null) {
            sanitizerService.stop();
        }
        return CompletableFuture.completedFuture(null);
    }
}
