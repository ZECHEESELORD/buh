package sh.harold.fulcrum.plugin.scoreboard;

import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardBuilder;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardDefinition;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;
import sh.harold.fulcrum.plugin.version.VersionService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class ScoreboardFeature implements FulcrumModule, ConfigurableModule, Listener {

    private static final String SCOREBOARD_ID = "default";
    private static final String DEFAULT_DATE_FORMAT = "MM/dd/yy";
    private static final long REFRESH_PERIOD_TICKS = 40L;

    private final JavaPlugin plugin;
    private final ScoreboardService scoreboardService;
    private final VersionService versionService;
    private final DataModule dataModule;

    private FeatureConfigService configService;
    private ScoreboardConfig config;
    private BukkitTask refreshTask;
    private FeatureVoteScoreboardModule voteScoreboardModule;

    public ScoreboardFeature(JavaPlugin plugin, ScoreboardService scoreboardService, VersionService versionService, DataModule dataModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
        this.versionService = Objects.requireNonNull(versionService, "versionService");
        this.dataModule = Objects.requireNonNull(dataModule, "dataModule");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("scoreboard"), Set.of(ModuleId.of("data")));
    }

    @Override
    public CompletionStage<Void> enable() {
        configService = new FeatureConfigService(plugin);
        config = ScoreboardConfig.from(configService.load(ScoreboardConfig.CONFIG_DEFINITION));

        DataApi dataApi = dataModule.dataApi().orElseThrow(() -> new IllegalStateException("DataApi not available for scoreboard"));
        voteScoreboardModule = new FeatureVoteScoreboardModule(plugin.getLogger(), dataApi.collection("feature_votes"));
        voteScoreboardModule.refreshTallies();

        registerScoreboardDefinition();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers()
            .forEach(player -> scoreboardService.showScoreboard(player.getUniqueId(), SCOREBOARD_ID));

        startRefreshTask();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        HandlerList.unregisterAll(this);
        plugin.getServer().getOnlinePlayers()
            .forEach(player -> scoreboardService.hideScoreboard(player.getUniqueId()));
        scoreboardService.unregisterScoreboard(SCOREBOARD_ID);
        if (configService != null) {
            configService.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> reloadConfig() {
        if (configService == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Scoreboard config service not initialized."));
        }

        config = ScoreboardConfig.from(configService.load(ScoreboardConfig.CONFIG_DEFINITION));
        registerScoreboardDefinition();
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (scoreboardService.hasScoreboardDisplayed(player.getUniqueId())) {
                scoreboardService.showScoreboard(player.getUniqueId(), SCOREBOARD_ID);
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scoreboardService.showScoreboard(event.getPlayer().getUniqueId(), SCOREBOARD_ID);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboardService.hideScoreboard(event.getPlayer().getUniqueId());
    }

    private ScoreboardDefinition buildDefinition() {
        ScoreboardBuilder builder = new ScoreboardBuilder(SCOREBOARD_ID)
            .title(config.title())
            .headerSupplier(this::headerLine)
            .module(voteScoreboardModule)
            .module(new VoteCommandScoreboardModule());

        if (config.footer() != null && !config.footer().isBlank()) {
            builder.footerSupplier(config::footer);
        }

        return builder.build();
    }

    private void registerScoreboardDefinition() {
        if (scoreboardService.isScoreboardRegistered(SCOREBOARD_ID)) {
            scoreboardService.unregisterScoreboard(SCOREBOARD_ID);
        }
        ScoreboardDefinition definition = buildDefinition();
        scoreboardService.registerScoreboard(SCOREBOARD_ID, definition);
    }

    private void startRefreshTask() {
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            () -> {
                voteScoreboardModule.refreshTallies();
                plugin.getServer().getOnlinePlayers()
                    .forEach(player -> scoreboardService.refreshPlayerScoreboard(player.getUniqueId()));
            },
            REFRESH_PERIOD_TICKS,
            REFRESH_PERIOD_TICKS
        );
    }

    private String headerLine() {
        String date = formatDate(config.headerDateFormat());
        String version = sanitizeVersion(versionService.version());
        return config.headerPattern()
            .replace("{date}", date)
            .replace("{version}", version);
    }

    private String formatDate(String pattern) {
        try {
            return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, "Invalid scoreboard date format '" + pattern + "', falling back to " + DEFAULT_DATE_FORMAT, exception);
            return LocalDate.now().format(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
        }
    }

    private String sanitizeVersion(String raw) {
        if (raw == null) {
            return "dev";
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank() || trimmed.contains("${")) {
            return "dev";
        }
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }
}
