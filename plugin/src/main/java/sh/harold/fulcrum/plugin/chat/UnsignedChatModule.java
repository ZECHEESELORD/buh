package sh.harold.fulcrum.plugin.chat;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class UnsignedChatModule implements FulcrumModule {

    private static final ModuleDescriptor DESCRIPTOR = new ModuleDescriptor(
        ModuleId.of("unsigned-chat"),
        Set.of(),
        ModuleCategory.ADMIN
    );

    private final JavaPlugin plugin;

    public UnsignedChatModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CompletionStage<Void> enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new UnsignedChatListener(plugin), plugin);
        return CompletableFuture.completedFuture(null);
    }
}

