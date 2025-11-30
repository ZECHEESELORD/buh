package sh.harold.fulcrum.common.data;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface Document {

    DocumentKey key();

    boolean exists();

    <T> Optional<T> get(String path, Class<T> type);

    CompletionStage<Void> set(String path, Object value);

    CompletionStage<Void> remove(String path);

    CompletionStage<Void> overwrite(Map<String, Object> data);

    CompletionStage<Void> update(UnaryOperator<Map<String, Object>> mutator);

    CompletionStage<Void> patch(Map<String, Object> setValues, Iterable<String> removePaths);

    Map<String, Object> snapshot();

    CompletionStage<Map<String, Object>> snapshotAsync();
}
