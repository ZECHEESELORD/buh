package sh.harold.fulcrum.plugin.item;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.item.ability.AbilityService;
import sh.harold.fulcrum.plugin.item.registry.ItemDefinitionProvider;
import sh.harold.fulcrum.plugin.item.registry.ItemRegistry;
import sh.harold.fulcrum.plugin.item.registry.VanillaWrapperFactory;
import sh.harold.fulcrum.plugin.item.registry.YamlItemDefinitionLoader;
import sh.harold.fulcrum.plugin.item.runtime.ItemResolver;
import sh.harold.fulcrum.plugin.item.stat.ItemStatBridge;
import sh.harold.fulcrum.plugin.item.visual.ItemLoreRenderer;
import sh.harold.fulcrum.plugin.item.visual.ProtocolLoreAdapter;
import sh.harold.fulcrum.plugin.item.listener.AbilityListener;
import sh.harold.fulcrum.plugin.item.listener.HelmetEquipListener;
import sh.harold.fulcrum.plugin.item.listener.ItemEquipListener;
import sh.harold.fulcrum.plugin.stats.StatsModule;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ItemEngine {

    private final JavaPlugin plugin;
    private final StatsModule statsModule;
    private final ItemRegistry registry;
    private final VanillaWrapperFactory wrapperFactory;
    private final ItemResolver resolver;
    private final AbilityService abilityService;
    private final ItemStatBridge statBridge;
    private ProtocolLoreAdapter loreAdapter;

    public ItemEngine(JavaPlugin plugin, StatsModule statsModule, List<ItemDefinitionProvider> providers) {
        this.plugin = plugin;
        this.statsModule = statsModule;
        this.registry = new ItemRegistry();
        this.wrapperFactory = new VanillaWrapperFactory();
        this.resolver = new ItemResolver(plugin, registry, wrapperFactory);
        this.abilityService = new AbilityService();
        this.statBridge = new ItemStatBridge(resolver, statsModule.statService());
        loadDefinitions(providers);
    }

    public ItemResolver resolver() {
        return resolver;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public ItemStatBridge statBridge() {
        return statBridge;
    }

    public java.util.Optional<org.bukkit.inventory.ItemStack> createItem(String id) {
        return registry.get(id).map(definition -> {
            org.bukkit.inventory.ItemStack base = new org.bukkit.inventory.ItemStack(definition.material());
            return resolver.applyId(base, definition.id());
        });
    }

    public void enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        ItemLoreRenderer loreRenderer = new ItemLoreRenderer(resolver);
        loreAdapter = ProtocolLoreAdapter.register(plugin, loreRenderer);
        pluginManager.registerEvents(new ItemEquipListener(plugin, statBridge), plugin);
        pluginManager.registerEvents(new AbilityListener(abilityService, resolver), plugin);
        pluginManager.registerEvents(new HelmetEquipListener(resolver, statBridge), plugin);
    }

    public void disable() {
        if (loreAdapter != null) {
            loreAdapter.unregister();
        }
    }

    private void loadDefinitions(List<ItemDefinitionProvider> providers) {
        Objects.requireNonNull(providers, "providers");
        for (ItemDefinitionProvider provider : providers) {
            provider.definitions().forEach(registry::register);
        }
        Path itemsPath = plugin.getDataFolder().toPath().resolve("config").resolve("items");
        new YamlItemDefinitionLoader(itemsPath, plugin.getLogger()).loadInto(registry);
        plugin.getLogger().info("Item engine loaded");
    }
}
