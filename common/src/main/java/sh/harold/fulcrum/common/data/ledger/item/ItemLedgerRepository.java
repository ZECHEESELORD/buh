package sh.harold.fulcrum.common.data.ledger.item;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface ItemLedgerRepository extends AutoCloseable {

    CompletionStage<Void> append(ItemInstanceRecord record);

    CompletionStage<Optional<ItemInstanceRecord>> find(UUID instanceId);

    @Override
    void close();
}
