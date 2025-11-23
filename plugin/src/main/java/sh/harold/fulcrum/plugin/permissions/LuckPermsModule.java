package sh.harold.fulcrum.plugin.permissions;

import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
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

public final class LuckPermsModule implements FulcrumModule {

    private static final FeatureConfigOption<List<String>> STAFF_GROUPS_OPTION = FeatureConfigOptions.stringListOption(
        "staff-groups",
        List.of(
            "owner",
            "admin",
            "manager",
            "staff",
            "moderator",
            "mod",
            "helper"
        )
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
        return ModuleDescriptor.of(ModuleId.of("luckperms"));
    }

    @Override
    public CompletionStage<Void> enable() {
        var configuration = configService.load(CONFIG_DEFINITION);
        Set<String> staffGroups = Set.copyOf(configuration.value(STAFF_GROUPS_OPTION));
        String staffPermission = configuration.value(STAFF_PERMISSION_OPTION);

        ServicesManager services = plugin.getServer().getServicesManager();
        LuckPerms luckPerms = services.load(LuckPerms.class);
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms not found; staff checks will always return false and usernames will not be decorated.");
            staffService = new NoopStaffService();
            formattedUsernameService = new NoopFormattedUsernameService();
            return CompletableFuture.completedFuture(null);
        }

        this.luckPerms = luckPerms;
        staffService = new LuckPermsStaffService(luckPerms, staffGroups, staffPermission);
        formattedUsernameService = new LuckPermsFormattedUsernameService(luckPerms);
        plugin.getLogger().info("LuckPerms found; staff checks enabled.");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        configService.close();
        return FulcrumModule.super.disable();
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
}
