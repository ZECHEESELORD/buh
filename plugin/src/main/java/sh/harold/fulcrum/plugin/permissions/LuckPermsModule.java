package sh.harold.fulcrum.plugin.permissions;

import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.ConfigurableModule;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleCategory;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.permissions.StaffService;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.config.FeatureConfigDefinition;
import sh.harold.fulcrum.plugin.config.FeatureConfigOption;
import sh.harold.fulcrum.plugin.config.FeatureConfigOptions;
import sh.harold.fulcrum.plugin.config.FeatureConfigService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class LuckPermsModule implements FulcrumModule, ConfigurableModule {

    private static final FeatureConfigOption<List<String>> STAFF_GROUPS_OPTION = FeatureConfigOptions.stringListOption(
        "staff-groups",
        List.of("staff")
    );
    private static final FeatureConfigOption<String> STAFF_PERMISSION_OPTION = FeatureConfigOptions.stringOption(
        "staff-permission",
        "fulcrum.staff"
    );
    private static final FeatureConfigDefinition CONFIG_DEFINITION = FeatureConfigDefinition.feature("luckperms")
        .option(STAFF_GROUPS_OPTION)
        .option(STAFF_PERMISSION_OPTION)
        .build();

    private final JavaPlugin plugin;
    private final FeatureConfigService configService;
    private StaffService staffService;
    private FormattedUsernameService formattedUsernameService;
    private LuckPerms luckPerms;

    public LuckPermsModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = new FeatureConfigService(plugin);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("luckperms"), ModuleCategory.STAFF);
    }

    @Override
    public CompletionStage<Void> enable() {
        var configuration = configService.load(CONFIG_DEFINITION);
        Set<String> staffGroups = Set.copyOf(configuration.value(STAFF_GROUPS_OPTION));
        String staffPermission = configuration.value(STAFF_PERMISSION_OPTION);

        LuckPerms resolved = resolveLuckPerms();
        if (resolved == null) {
            throw new IllegalStateException("LuckPerms not found; it is a declared dependency and must be present.");
        }

        bindLuckPerms(resolved, staffGroups, staffPermission);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        configService.close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> reloadConfig() {
        var configuration = configService.load(CONFIG_DEFINITION);
        Set<String> staffGroups = Set.copyOf(configuration.value(STAFF_GROUPS_OPTION));
        String staffPermission = configuration.value(STAFF_PERMISSION_OPTION);

        LuckPerms resolved = resolveLuckPerms();
        if (resolved == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("LuckPerms not found; cannot reload configuration."));
        }

        bindLuckPerms(resolved, staffGroups, staffPermission);
        return CompletableFuture.completedFuture(null);
    }

    public Optional<StaffService> staffService() {
        return Optional.ofNullable(staffService);
    }

    public Optional<FormattedUsernameService> formattedUsernameService() {
        return Optional.ofNullable(formattedUsernameService);
    }

    public Optional<LuckPerms> luckPerms() {
        return Optional.ofNullable(luckPerms);
    }

    private LuckPerms resolveLuckPerms() {
        ServicesManager services = plugin.getServer().getServicesManager();
        return services.load(LuckPerms.class);
    }

    private void bindLuckPerms(LuckPerms api, Set<String> staffGroups, String staffPermission) {
        this.luckPerms = api;
        staffService = new LuckPermsStaffService(api, staffGroups, staffPermission);
        formattedUsernameService = new LuckPermsFormattedUsernameService(api);
        plugin.getLogger().info("LuckPerms found; staff checks enabled.");
    }
}
