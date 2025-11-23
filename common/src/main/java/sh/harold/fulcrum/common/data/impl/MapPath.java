package sh.harold.fulcrum.common.data.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class MapPath {

    private MapPath() {
    }

    @SuppressWarnings("unchecked")
    static Object read(Map<String, Object> root, String path) {
        Objects.requireNonNull(root, "root");
        if (path == null || path.isBlank()) {
            return deepCopy(root);
        }
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        if (current instanceof Map<?, ?> || current instanceof List<?>) {
            return deepCopyValue(current);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    static void write(Map<String, Object> root, String path, Object value) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            root.clear();
            if (value instanceof Map<?, ?> map) {
                root.putAll(deepCopy(map));
            }
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map<?, ?> map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(part, created);
                current = created;
            } else {
                current = (Map<String, Object>) map;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    static void remove(Map<String, Object> root, String path) {
        Objects.requireNonNull(root, "root");
        if (path == null || path.isBlank()) {
            root.clear();
            return;
        }
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (!(next instanceof Map<?, ?> map)) {
                return;
            }
            current = (Map<String, Object>) map;
        }
        current.remove(parts[parts.length - 1]);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> deepCopy(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((key, value) -> copy.put(String.valueOf(key), deepCopyValue(value)));
        return copy;
    }

    static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopy(map);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                .map(MapPath::deepCopyValue)
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return value;
    }
}
