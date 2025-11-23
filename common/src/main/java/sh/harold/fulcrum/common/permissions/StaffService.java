package sh.harold.fulcrum.common.permissions;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface StaffService {

    CompletionStage<Boolean> isStaff(UUID playerId);
}
