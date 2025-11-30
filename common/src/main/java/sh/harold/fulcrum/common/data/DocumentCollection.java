package sh.harold.fulcrum.common.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface DocumentCollection {

    String name();

    CompletionStage<Document> load(String id);

    CompletionStage<Document> create(String id, Map<String, Object> data);

    CompletionStage<Boolean> delete(String id);

    CompletionStage<List<Document>> all();

    CompletionStage<Long> count();

    default CompletionStage<Map<String, Document>> loadAll(java.util.Collection<String> ids) {
        throw new UnsupportedOperationException("loadAll not implemented");
    }

    default CompletionStage<Void> updateAll(Map<String, UnaryOperator<Map<String, Object>>> updates) {
        throw new UnsupportedOperationException("updateAll not implemented");
    }
}
