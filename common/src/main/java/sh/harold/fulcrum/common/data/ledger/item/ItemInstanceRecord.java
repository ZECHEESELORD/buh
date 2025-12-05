package sh.harold.fulcrum.common.data.ledger.item;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ItemInstanceRecord(
    UUID instanceId,
    String itemId,
    ItemCreationSource source,
    UUID creatorId,
    Instant createdAt
) {
    public ItemInstanceRecord {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
