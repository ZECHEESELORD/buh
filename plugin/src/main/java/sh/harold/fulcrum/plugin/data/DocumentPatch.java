package sh.harold.fulcrum.plugin.data;

import sh.harold.fulcrum.common.data.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Collects a set of path mutations and applies them in a single patch call.
 */
public final class DocumentPatch {

    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<String> removePaths = new ArrayList<>();

    public DocumentPatch set(String path, Object value) {
        Objects.requireNonNull(path, "path");
        setValues.put(path, value);
        return this;
    }

    public DocumentPatch remove(String path) {
        Objects.requireNonNull(path, "path");
        removePaths.add(path);
        setValues.remove(path);
        return this;
    }

    public CompletionStage<Void> apply(Document document) {
        Objects.requireNonNull(document, "document");
        if (setValues.isEmpty() && removePaths.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return document.patch(setValues, removePaths);
    }
}
