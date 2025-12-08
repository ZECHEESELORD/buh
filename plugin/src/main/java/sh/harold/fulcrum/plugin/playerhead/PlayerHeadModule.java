package sh.harold.fulcrum.plugin.playerhead;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.item.ItemModule;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class PlayerHeadModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final ItemModule itemModule;
    private PlayerHeadDropListener headListener;

    public PlayerHeadModule(JavaPlugin plugin, ItemModule itemModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.itemModule = Objects.requireNonNull(itemModule, "itemModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("player-heads"), ModuleCategory.GAMEPLAY, ModuleId.of("item-engine"));
    }

    @Override
    public CompletionStage<Void> enable() {
        if (itemModule.engine() == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Item engine not initialized for player head drops."));
        }
        PlayerHeadFactory headFactory = new PlayerHeadFactory(itemModule.engine());
        headListener = new PlayerHeadDropListener(headFactory);
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(headListener, plugin);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (headListener != null) {
            HandlerList.unregisterAll(headListener);
            headListener = null;
        }
        return CompletableFuture.completedFuture(null);
    }
}
