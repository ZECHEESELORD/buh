package sh.harold.fulcrum.common.data;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface DocumentStore extends AutoCloseable {

    CompletionStage<DocumentSnapshot> read(DocumentKey key);

    CompletionStage<Void> write(DocumentKey key, Map<String, Object> data);

    CompletionStage<DocumentSnapshot> update(DocumentKey key, UnaryOperator<Map<String, Object>> mutator);

    default CompletionStage<Void> patch(DocumentKey key, Map<String, Object> setValues, Iterable<String> removePaths) {
        Objects.requireNonNull(key, "key");
        Map<String, Object> copy = deepCopy(setValues);
        java.util.List<String> removals = new java.util.ArrayList<>();
        if (removePaths != null) {
            removePaths.forEach(removals::add);
        }
        return update(key, current -> {
            Map<String, Object> working = deepCopy(current);
            copy.forEach((path, value) -> writePath(working, path, value));
            for (String path : removals) {
                removePath(working, path);
            }
            return working;
        }).thenApply(ignored -> null);
    }

    CompletionStage<Boolean> delete(DocumentKey key);

    CompletionStage<List<DocumentSnapshot>> all(String collection);

    CompletionStage<Long> count(String collection);

    @Override
    void close();

    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((key, value) -> copy.put(String.valueOf(key), deepCopyValue(value)));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopy((Map<String, Object>) map);
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(DocumentStore::deepCopyValue).toList();
        }
        return value;
    }

    private static void writePath(Map<String, Object> root, String path, Object value) {
        if (path == null || path.isBlank()) {
            root.clear();
            if (value instanceof Map<?, ?> map) {
                root.putAll(deepCopy((Map<String, Object>) map));
            }
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map<?, ?> nested)) {
                Map<String, Object> created = new java.util.LinkedHashMap<>();
                current.put(part, created);
                current = created;
            } else {
                current = (Map<String, Object>) nested;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    private static void removePath(Map<String, Object> root, String path) {
        if (path == null || path.isBlank()) {
            root.clear();
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map<?, ?> nested)) {
                return;
            }
            current = (Map<String, Object>) nested;
        }
        current.remove(parts[parts.length - 1]);
    }
}
