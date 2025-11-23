package sh.harold.fulcrum.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DocumentSnapshot(DocumentKey key, Map<String, Object> data, boolean exists) {

    public DocumentSnapshot {
        Objects.requireNonNull(key, "key");
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (data != null) {
            normalized.putAll(data);
        }
        data = Collections.unmodifiableMap(normalized);
    }

    public Map<String, Object> copy() {
        return new LinkedHashMap<>(data);
    }
}
