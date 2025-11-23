package sh.harold.fulcrum.plugin.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;
import sh.harold.fulcrum.common.permissions.StaffService;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class LuckPermsStaffService implements StaffService {

    private final LuckPerms luckPerms;
    private final Set<String> staffGroups;
    private final String staffPermissionNode;
    private final QueryOptions queryOptions;

    LuckPermsStaffService(LuckPerms luckPerms, Set<String> staffGroups, String staffPermissionNode) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.staffGroups = staffGroups.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.staffPermissionNode = staffPermissionNode == null || staffPermissionNode.isBlank() ? null : staffPermissionNode;
        QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
        this.queryOptions = Objects.requireNonNullElseGet(options, () -> luckPerms.getContextManager().getQueryOptions(luckPerms.getContextManager().getStaticContext()));
    }

    @Override
    public CompletionStage<Boolean> isStaff(UUID playerId) {
        UserManager userManager = luckPerms.getUserManager();
        User cached = userManager.getUser(playerId);
        CompletableFuture<User> userFuture = cached != null
            ? CompletableFuture.completedFuture(cached)
            : userManager.loadUser(playerId);

        return userFuture.thenApply(this::isStaffUser);
    }

    private boolean isStaffUser(User user) {
        if (user == null) {
            return false;
        }
        if (staffPermissionNode != null) {
            boolean hasPermission = user.getCachedData()
                .getPermissionData(queryOptions)
                .checkPermission(staffPermissionNode)
                .asBoolean();
            if (hasPermission) {
                return true;
            }
        }
        return user.getNodes(NodeType.INHERITANCE).stream()
            .map(InheritanceNode::getGroupName)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .anyMatch(staffGroups::contains);
    }
}
