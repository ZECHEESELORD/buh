package sh.harold.fulcrum.plugin.tab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class TabFeature implements FulcrumModule, ConfigurableModule, Listener {

    private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.of(ModuleId.of("tab"), ModuleCategory.HUD);
    private static final long REFRESH_PERIOD_TICKS = 20L;
    private static final String PADDING = "   ";
    private static final double MAX_TPS = 20.0D;

    private final JavaPlugin plugin;
    private FeatureConfigService configService;
    private TabConfig config;
    private BukkitTask refreshTask;

    public TabFeature(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public CompletionStage<Void> enable() {
        configService = new FeatureConfigService(plugin);
        config = TabConfig.from(configService.load(TabConfig.CONFIG_DEFINITION));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startRefreshTask();
        plugin.getServer().getOnlinePlayers().forEach(this::sendTab);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        HandlerList.unregisterAll(this);
        plugin.getServer().getOnlinePlayers().forEach(this::clearTab);
        if (configService != null) {
            configService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> reloadConfig() {
        if (configService == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Tab config service not initialized."));
        }

        config = TabConfig.from(configService.load(TabConfig.CONFIG_DEFINITION));
        refreshTabs();
        return CompletableFuture.completedFuture(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendTab(event.getPlayer());
    }

    private void startRefreshTask() {
        refreshTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::refreshTabs, 1L, REFRESH_PERIOD_TICKS);
    }

    private void refreshTabs() {
        TabContent content = composeContent();
        plugin.getServer().getOnlinePlayers()
            .forEach(player -> player.sendPlayerListHeaderAndFooter(content.header(), content.footer()));
    }

    private void sendTab(Player player) {
        TabContent content = composeContent();
        player.sendPlayerListHeaderAndFooter(content.header(), content.footer());
    }

    private void clearTab(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    private TabContent composeContent() {
        Component header = Component.text(PADDING + resolveServerName() + PADDING, NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false);

        Component footer = Component.text(PADDING + "TPS " + formatTps(currentTps()) + PADDING, NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false);

        return new TabContent(header, footer);
    }

    private String resolveServerName() {
        String configured = config == null ? null : config.serverName();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return plugin.getServer().getName();
    }

    private double currentTps() {
        double[] tps = plugin.getServer().getTPS();
        if (tps.length == 0) {
            return MAX_TPS;
        }
        return Math.min(MAX_TPS, tps[0]);
    }

    private String formatTps(double tps) {
        return String.format(Locale.US, "%.2f", tps);
    }

    private record TabContent(Component header, Component footer) {
    }
}
