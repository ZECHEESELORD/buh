package sh.harold.fulcrum.common.data;

import java.util.Objects;

public record DocumentKey(String collection, String id) {

    public DocumentKey {
        Objects.requireNonNull(collection, "collection");
        Objects.requireNonNull(id, "id");
        if (collection.isBlank()) {
            throw new IllegalArgumentException("Collection cannot be blank");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("Id cannot be blank");
        }
        if (collection.contains("/") || id.contains("/")) {
            throw new IllegalArgumentException("Collection and id may not contain path separators");
        }
    }

    public static DocumentKey of(String collection, String id) {
        return new DocumentKey(collection, id);
    }
}
