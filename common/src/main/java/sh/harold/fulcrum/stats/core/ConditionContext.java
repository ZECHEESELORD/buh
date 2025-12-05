package sh.harold.fulcrum.stats.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Describes the situational context for evaluating conditional stat modifiers.
 * Tags and attributes are arbitrary strings provided by callers (e.g. "target:undead", "cause:explosion").
 */
public final class ConditionContext {

    private static final ConditionContext EMPTY = new ConditionContext(Set.of(), Map.of());

    private final Set<String> tags;
    private final Map<String, Object> attributes;

    private ConditionContext(Set<String> tags, Map<String, Object> attributes) {
        this.tags = Collections.unmodifiableSet(tags);
        this.attributes = Collections.unmodifiableMap(attributes);
    }

    public static ConditionContext empty() {
        return EMPTY;
    }

    public ConditionContext withTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return this;
        }
        Set<String> merged = new HashSet<>(tags);
        merged.add(tag);
        return new ConditionContext(merged, attributes);
    }

    public ConditionContext withAttribute(String key, Object value) {
        if (key == null || key.isBlank() || value == null) {
            return this;
        }
        Map<String, Object> merged = new HashMap<>(attributes);
        merged.put(key, value);
        return new ConditionContext(tags, merged);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public Set<String> tags() {
        return tags;
    }

    public <T> Optional<T> attribute(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = attributes.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    public boolean isEmpty() {
        return tags.isEmpty() && attributes.isEmpty();
    }
}
