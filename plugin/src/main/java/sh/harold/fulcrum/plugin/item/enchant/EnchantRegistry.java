package sh.harold.fulcrum.plugin.item.enchant;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class EnchantRegistry {

    private final Map<String, EnchantDefinition> definitions = new ConcurrentHashMap<>();

    public void register(EnchantDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        definitions.put(definition.id(), definition);
    }

    public Optional<EnchantDefinition> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public java.util.Set<String> ids() {
        return java.util.Set.copyOf(definitions.keySet());
    }
}
