package sh.harold.fulcrum.plugin.item;

import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.item.registry.ItemDefinitionProvider;
import sh.harold.fulcrum.plugin.item.registry.SampleItemProvider;
import sh.harold.fulcrum.plugin.stats.StatsModule;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ItemModule implements FulcrumModule {

    private final JavaPlugin plugin;
    private final StatsModule statsModule;
    private ItemEngine engine;

    public ItemModule(JavaPlugin plugin, StatsModule statsModule) {
        this.plugin = plugin;
        this.statsModule = statsModule;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("item-engine"), ModuleCategory.GAMEPLAY, ModuleId.of("rpg-stats"));
    }

    @Override
    public CompletionStage<Void> enable() {
        List<ItemDefinitionProvider> providers = List.of(new SampleItemProvider());
        engine = new ItemEngine(plugin, statsModule, providers);
        registerSampleAbilities();
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

    private void registerSampleAbilities() {
        engine.abilityService().registerExecutor("fulcrum:blink", (definition, context) -> {
            var player = context.player();
            player.sendMessage(definition.displayName().append(Component.text(" activated.", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        });
    }
}
