package sh.harold.fulcrum.plugin.jukebox;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.plugin.jukebox.command.JukeboxCommand;
import sh.harold.fulcrum.plugin.jukebox.disc.JukeboxDiscService;
import sh.harold.fulcrum.plugin.jukebox.menu.JukeboxMenuService;
import sh.harold.fulcrum.plugin.jukebox.mint.JukeboxMintService;
import sh.harold.fulcrum.plugin.jukebox.playback.JukeboxBlockListener;
import sh.harold.fulcrum.plugin.jukebox.playback.JukeboxPlaybackEngine;
import sh.harold.fulcrum.plugin.jukebox.playback.NoopJukeboxPlaybackEngine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class JukeboxModule implements FulcrumModule {

    private final JavaPlugin plugin;

    private FeatureConfigService configService;
    private JukeboxConfig config;
    private JukeboxPlaybackEngine playbackEngine;
    private JukeboxDiscService discService;
    private JukeboxMintService mintService;
    private JukeboxMenuService menuService;

    public JukeboxModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("jukebox"), Set.of(ModuleId.of("menu")), ModuleCategory.GAMEPLAY);
    }

    @Override
    public CompletionStage<Void> enable() {
        configService = new FeatureConfigService(plugin);
        config = JukeboxConfig.load(configService, plugin.getDataFolder().toPath());
        playbackEngine = createPlaybackEngine();
        discService = new JukeboxDiscService(new org.bukkit.NamespacedKey(plugin, "jukebox_track_id"));
        mintService = new JukeboxMintService(plugin.getLogger(), config);
        menuService = resolveMenuService()
            .map(resolved -> new JukeboxMenuService(plugin, resolved, config, mintService, discService))
            .orElse(null);

        try {
            Files.createDirectories(config.tracksDirectory());
            Files.createDirectories(config.tokenDirectory());
            Files.createDirectories(config.slotsDirectory());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to ensure jukebox storage directories exist");
        }

        registerListeners();
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (playbackEngine != null) {
            playbackEngine.close();
            playbackEngine = null;
        }
        if (mintService != null) {
            mintService.close();
            mintService = null;
        }
        if (configService != null) {
            configService.close();
            configService = null;
        }
        discService = null;
        menuService = null;
        config = null;
        return CompletableFuture.completedFuture(null);
    }

    private JukeboxPlaybackEngine createPlaybackEngine() {
        if (plugin.getServer().getPluginManager().getPlugin("voicechat") == null) {
            plugin.getLogger().info("jukebox: Simple Voice Chat not found; playback will be unavailable");
            return new NoopJukeboxPlaybackEngine("Voice chat is not available.");
        }

        try {
            Class<?> rawType = Class.forName("sh.harold.fulcrum.plugin.jukebox.playback.JukeboxPlaybackService");
            if (!JukeboxPlaybackEngine.class.isAssignableFrom(rawType)) {
                plugin.getLogger().warning("jukebox: Voice chat integration missing; playback will be unavailable");
                return new NoopJukeboxPlaybackEngine("Voice chat is not available.");
            }
            @SuppressWarnings("unchecked")
            Class<? extends JukeboxPlaybackEngine> engineType = (Class<? extends JukeboxPlaybackEngine>) rawType;
            Constructor<? extends JukeboxPlaybackEngine> constructor = engineType.getConstructor(JavaPlugin.class, JukeboxConfig.class);
            return constructor.newInstance(plugin, config);
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING, "jukebox: Failed to set up voice chat integration; playback will be unavailable", throwable);
            return new NoopJukeboxPlaybackEngine("Voice chat is not available.");
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new JukeboxBlockListener(plugin, discService, playbackEngine), plugin);
    }

    private java.util.Optional<MenuService> resolveMenuService() {
        return java.util.Optional.ofNullable(plugin.getServer().getServicesManager().load(MenuService.class));
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        if (menuService == null) {
            return;
        }
        Commands registrar = event.registrar();
        registrar.register(new JukeboxCommand(menuService).build(), "jukebox", java.util.List.of("music"));
    }
}
