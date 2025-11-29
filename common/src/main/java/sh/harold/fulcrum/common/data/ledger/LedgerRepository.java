package sh.harold.fulcrum.common.data.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface LedgerRepository extends AutoCloseable {

    CompletionStage<Void> append(LedgerEntry entry);

    CompletionStage<List<LedgerEntry>> recent(UUID playerId, int limit);

    CompletionStage<Optional<LedgerEntry>> latest(UUID playerId);

    @Override
    void close();
}
