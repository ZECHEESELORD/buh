package sh.harold.fulcrum.plugin.permissions;

import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;
import sh.harold.fulcrum.common.permissions.StaffService;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class LuckPermsModule implements FulcrumModule {

    private static final Set<String> DEFAULT_STAFF_GROUPS = Set.of(
        "owner",
        "admin",
        "manager",
        "staff",
        "moderator",
        "mod",
        "helper"
    );
    private static final String DEFAULT_STAFF_PERMISSION = "fulcrum.staff";

    private final JavaPlugin plugin;
    private StaffService staffService;

    public LuckPermsModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.of(ModuleId.of("luckperms"));
    }

    @Override
    public CompletionStage<Void> enable() {
        ServicesManager services = plugin.getServer().getServicesManager();
        LuckPerms luckPerms = services.load(LuckPerms.class);
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms not found; staff checks will always return false.");
            staffService = new NoopStaffService();
            return CompletableFuture.completedFuture(null);
        }

        staffService = new LuckPermsStaffService(luckPerms, DEFAULT_STAFF_GROUPS, DEFAULT_STAFF_PERMISSION);
        plugin.getLogger().info("LuckPerms found; staff checks enabled.");
        return CompletableFuture.completedFuture(null);
    }

    public Optional<StaffService> staffService() {
        return Optional.ofNullable(staffService);
    }
}
