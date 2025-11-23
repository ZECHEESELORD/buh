package sh.harold.fulcrum.plugin.permissions;

import sh.harold.fulcrum.common.permissions.StaffService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class NoopStaffService implements StaffService {

    @Override
    public CompletionStage<Boolean> isStaff(UUID playerId) {
        return CompletableFuture.completedFuture(false);
    }
}
