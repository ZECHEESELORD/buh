package sh.harold.fulcrum.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.api.message.scoreboard.impl.DefaultPlayerScoreboardManager;
import sh.harold.fulcrum.api.message.scoreboard.impl.SimpleScoreboardService;
import sh.harold.fulcrum.api.message.scoreboard.registry.DefaultScoreboardRegistry;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.loader.ModuleLoader;
import sh.harold.fulcrum.common.permissions.StaffService;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.chat.ChatChannelService;
import sh.harold.fulcrum.plugin.chat.ChatModule;
import sh.harold.fulcrum.plugin.data.DataModule;
import sh.harold.fulcrum.plugin.message.MessageModule;
import sh.harold.fulcrum.plugin.message.MessageService;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.playerdata.PlayerDataModule;
import sh.harold.fulcrum.plugin.stash.StashModule;
import sh.harold.fulcrum.plugin.stash.StashService;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class BuhPlugin extends JavaPlugin {

    private ModuleLoader moduleLoader;
    private DataModule dataModule;
    private PlayerDataModule playerDataModule;
    private LuckPermsModule luckPermsModule;
    private ChatModule chatModule;
    private MessageModule messageModule;
    private StashModule stashModule;
    private ChatChannelService chatChannelService;
    private MessageService messageService;
    private SimpleScoreboardService scoreboardService;

    @Override
    public void onLoad() {
        createModules();
    }

    @Override
    public void onEnable() {
        if (moduleLoader == null) {
            createModules();
        }

        await(moduleLoader.enableAll(), "enable modules");
        getLogger().info("Fulcrum modules enabled");

        scoreboardService = new SimpleScoreboardService(
            this,
            new DefaultScoreboardRegistry(),
            new DefaultPlayerScoreboardManager()
        );

    }

    @Override
    public void onDisable() {
        if (moduleLoader != null) {
            await(moduleLoader.disableAll(), "disable modules");
        }
    }

    public DataApi dataApi() {
        return dataModule == null ? null : dataModule.dataApi().orElse(null);
    }

    public Optional<StaffService> staffService() {
        return luckPermsModule == null ? Optional.empty() : luckPermsModule.staffService();
    }

    public Optional<FormattedUsernameService> formattedUsernameService() {
        return luckPermsModule == null ? Optional.empty() : luckPermsModule.formattedUsernameService();
    }

    public Optional<SimpleScoreboardService> scoreboardService() {
        return Optional.ofNullable(scoreboardService);
    }

    public Optional<StashService> stashService() {
        return stashModule == null ? Optional.empty() : stashModule.stashService();
    }

    private void createModules() {
        dataModule = new DataModule(dataPath());
        playerDataModule = new PlayerDataModule(this, dataModule);
        luckPermsModule = new LuckPermsModule(this);
        chatChannelService = new ChatChannelService(this::staffService);
        messageService = new MessageService(this, () -> formattedUsernameService().orElseGet(this::noopFormattedUsernameService));
        chatModule = new ChatModule(this, luckPermsModule, chatChannelService, messageService);
        messageModule = new MessageModule(this, luckPermsModule, chatChannelService, messageService);
        stashModule = new StashModule(this, dataModule);
        moduleLoader = new ModuleLoader(List.of(dataModule, playerDataModule, luckPermsModule, chatModule, messageModule, stashModule));
    }

    private Path dataPath() {
        return getDataFolder().toPath().resolve("data");
    }

    private void await(CompletionStage<Void> stage, String action) {
        try {
            stage.toCompletableFuture().join();
        } catch (RuntimeException runtimeException) {
            getLogger().log(Level.SEVERE, "Failed to " + action, runtimeException);
            throw runtimeException;
        }
    }

    private FormattedUsernameService noopFormattedUsernameService() {
        return new FormattedUsernameService() {
            @Override
            public java.util.concurrent.CompletionStage<FormattedUsername> username(java.util.UUID playerId, String username) {
                return java.util.concurrent.CompletableFuture.completedFuture(new FormattedUsername(
                    net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.text.Component.text(username, net.kyori.adventure.text.format.NamedTextColor.WHITE)
                ));
            }
        };
    }
}
