package sh.harold.fulcrum.common.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface DocumentStore extends AutoCloseable {

    CompletionStage<DocumentSnapshot> read(DocumentKey key);

    CompletionStage<Void> write(DocumentKey key, Map<String, Object> data);

    CompletionStage<Boolean> delete(DocumentKey key);

    CompletionStage<List<DocumentSnapshot>> all(String collection);

    CompletionStage<Long> count(String collection);

    @Override
    void close();
}
