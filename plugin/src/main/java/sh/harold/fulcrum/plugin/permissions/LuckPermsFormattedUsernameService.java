package sh.harold.fulcrum.plugin.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class LuckPermsFormattedUsernameService implements FormattedUsernameService {

    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;

    LuckPermsFormattedUsernameService(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
        this.queryOptions = Objects.requireNonNullElseGet(options, () -> luckPerms.getContextManager()
            .getQueryOptions(luckPerms.getContextManager().getStaticContext()));
    }

    @Override
    public CompletionStage<FormattedUsername> username(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        UserManager userManager = luckPerms.getUserManager();
        User cached = userManager.getUser(playerId);
        CompletableFuture<User> userFuture = cached != null
            ? CompletableFuture.completedFuture(cached)
            : userManager.loadUser(playerId);

        return userFuture.thenApply(user -> buildUsername(username, user));
    }

    private FormattedUsername buildUsername(String username, User user) {
        if (user == null) {
            Component nameComponent = Component.text(username, LuckPermsTextFormat.DEFAULT_COLOR);
            return new FormattedUsername(Component.empty(), nameComponent);
        }
        var meta = user.getCachedData().getMetaData(queryOptions);
        Component prefix = LuckPermsTextFormat.deserializePrefix(meta.getPrefix());
        TextColor nameColor = LuckPermsTextFormat.parseColor(meta.getMetaValue("nameColour"))
            .orElse(LuckPermsTextFormat.DEFAULT_COLOR);
        Component nameComponent = Component.text(username, nameColor);
        return new FormattedUsername(prefix, nameComponent);
    }
}
