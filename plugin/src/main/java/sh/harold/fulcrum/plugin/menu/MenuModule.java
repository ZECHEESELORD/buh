package sh.harold.fulcrum.plugin.menu;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.menu.MenuRegistry;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.impl.DefaultMenuRegistry;
import sh.harold.fulcrum.api.menu.impl.DefaultMenuService;
import sh.harold.fulcrum.api.menu.impl.MenuInventoryListener;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.cooldown.CooldownRegistry;
import sh.harold.fulcrum.common.cooldown.InMemoryCooldownRegistry;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MenuModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private DefaultMenuService menuService;
    private MenuRegistry menuRegistry;
    private CooldownRegistry cooldownRegistry;

    public MenuModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("menu"), Set.of(), ModuleCategory.API);
    }

    @Override
    public CompletionStage<Void> enable() {
        menuRegistry = new DefaultMenuRegistry();
        menuService = new DefaultMenuService(plugin, menuRegistry);
        menuService.registerPlugin(plugin);
        cooldownRegistry = new InMemoryCooldownRegistry();
        MenuButton.bindCooldownRegistry(cooldownRegistry);

        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(new MenuInventoryListener(menuService, plugin), plugin);

        plugin.getServer().getServicesManager().register(MenuService.class, menuService, plugin, ServicePriority.Normal);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (menuService != null) {
            plugin.getServer().getServicesManager().unregister(MenuService.class, menuService);
            menuService.unregisterPlugin(plugin);
            menuService.shutdown();
        }
        if (cooldownRegistry != null) {
            cooldownRegistry.close();
            MenuButton.clearCooldownRegistry();
        }
        return CompletableFuture.completedFuture(null);
    }

    public Optional<MenuService> menuService() {
        return Optional.ofNullable(menuService);
    }

    public Optional<MenuRegistry> menuRegistry() {
        return Optional.ofNullable(menuRegistry);
    }
}
