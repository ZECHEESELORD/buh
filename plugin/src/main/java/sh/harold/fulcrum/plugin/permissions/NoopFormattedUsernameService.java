package sh.harold.fulcrum.plugin.permissions;

import net.kyori.adventure.text.Component;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class NoopFormattedUsernameService implements FormattedUsernameService {

    @Override
    public CompletionStage<FormattedUsername> username(UUID playerId, String username) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Component name = Component.text(username, LuckPermsTextFormat.DEFAULT_COLOR);
        return CompletableFuture.completedFuture(new FormattedUsername(Component.empty(), name));
    }
}
