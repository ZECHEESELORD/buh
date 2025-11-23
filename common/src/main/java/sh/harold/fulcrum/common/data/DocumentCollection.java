package sh.harold.fulcrum.common.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface DocumentCollection {

    String name();

    CompletionStage<Document> load(String id);

    CompletionStage<Document> create(String id, Map<String, Object> data);

    CompletionStage<Boolean> delete(String id);

    CompletionStage<List<Document>> all();

    CompletionStage<Long> count();
}
