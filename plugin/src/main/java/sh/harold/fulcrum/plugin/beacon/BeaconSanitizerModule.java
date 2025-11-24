package sh.harold.fulcrum.plugin.beacon;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.permissions.StaffService;
import sh.harold.fulcrum.plugin.stash.StashModule;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class BeaconSanitizerModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final StashModule stashModule;
    private final Supplier<StaffService> staffService;
    private BeaconSanitizerService sanitizerService;

    public BeaconSanitizerModule(JavaPlugin plugin, StashModule stashModule, Supplier<StaffService> staffService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.stashModule = Objects.requireNonNull(stashModule, "stashModule");
        this.staffService = Objects.requireNonNull(staffService, "staffService");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("beacon-sanitizer"), Set.of(ModuleId.of("stash")));
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
                new BeaconDebugCommand(sanitizerService, () -> resolveAdminId()).build(),
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

    private java.util.UUID resolveAdminId() {
        return java.util.UUID.randomUUID(); // fallback: caller check handles actual sender via requires
    }
}
