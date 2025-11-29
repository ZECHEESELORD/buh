package sh.harold.fulcrum.common.data.ledger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
    UUID playerId,
    LedgerType type,
    long amount,
    long resultingBalance,
    String source,
    Instant createdAt
) {
    public LedgerEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public enum LedgerType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT
    }
}
