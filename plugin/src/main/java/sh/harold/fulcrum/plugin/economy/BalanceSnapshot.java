package sh.harold.fulcrum.plugin.economy;

import java.util.Objects;
import java.util.UUID;

public record BalanceSnapshot(UUID playerId, long balance) {

    public BalanceSnapshot {
        Objects.requireNonNull(playerId, "playerId");
    }
}
