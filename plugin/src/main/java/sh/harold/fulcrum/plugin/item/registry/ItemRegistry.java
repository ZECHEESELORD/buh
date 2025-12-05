package sh.harold.fulcrum.plugin.item.registry;

import org.bukkit.Material;
import sh.harold.fulcrum.plugin.item.model.CustomItem;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemRegistry {

    private final Map<String, CustomItem> definitions = new ConcurrentHashMap<>();

    public void register(CustomItem item) {
        Objects.requireNonNull(item, "item");
        definitions.put(item.id(), item);
    }

    public Optional<CustomItem> get(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public CustomItem getOrCreateVanilla(Material material, VanillaWrapperFactory wrapperFactory) {
        String id = "vanilla:" + material.getKey().getKey();
        return definitions.computeIfAbsent(id, ignored -> wrapperFactory.wrap(material, id));
    }

    public java.util.Collection<CustomItem> definitions() {
        return java.util.Collections.unmodifiableCollection(definitions.values());
    }
}
