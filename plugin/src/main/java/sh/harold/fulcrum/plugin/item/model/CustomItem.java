package sh.harold.fulcrum.plugin.item.model;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CustomItem {

    private final String id;
    private final ItemCategory category;
    private final Set<ItemTrait> traits;
    private final Material material;
    private final Map<ComponentType, ItemComponent> components;
    private final List<LoreSection> loreLayout;

    private CustomItem(
        String id,
        ItemCategory category,
        Set<ItemTrait> traits,
        Material material,
        Map<ComponentType, ItemComponent> components,
        List<LoreSection> loreLayout
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = Objects.requireNonNull(category, "category");
        this.traits = Set.copyOf(traits == null ? Set.of() : traits);
        this.material = Objects.requireNonNull(material, "material");
        this.components = Map.copyOf(components == null ? Map.of() : components);
        this.loreLayout = List.copyOf(loreLayout == null ? defaultLoreLayout() : loreLayout);
    }

    public String id() {
        return id;
    }

    public ItemCategory category() {
        return category;
    }

    public Set<ItemTrait> traits() {
        return traits;
    }

    public Material material() {
        return material;
    }

    public Map<ComponentType, ItemComponent> components() {
        return components;
    }

    public List<LoreSection> loreLayout() {
        return loreLayout;
    }

    public <T extends ItemComponent> Optional<T> component(ComponentType type, Class<T> componentType) {
        ItemComponent component = components.get(type);
        if (componentType.isInstance(component)) {
            return Optional.of(componentType.cast(component));
        }
        return Optional.empty();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    private static List<LoreSection> defaultLoreLayout() {
        return List.of(
            LoreSection.HEADER,
            LoreSection.RARITY,
            LoreSection.TAGS,
            LoreSection.PRIMARY_STATS,
            LoreSection.ENCHANTS,
            LoreSection.ABILITIES,
            LoreSection.FOOTER
        );
    }

    public static final class Builder {

        private final String id;
        private ItemCategory category;
        private Set<ItemTrait> traits = Set.of();
        private Material material = Material.STONE;
        private final Map<ComponentType, ItemComponent> components = new EnumMap<>(ComponentType.class);
        private List<LoreSection> loreLayout = defaultLoreLayout();

        public Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder category(ItemCategory category) {
            this.category = category;
            return this;
        }

        public Builder traits(Set<ItemTrait> traits) {
            this.traits = traits;
            return this;
        }

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder component(ComponentType type, ItemComponent component) {
            if (component != null) {
                components.put(type, component);
            }
            return this;
        }

        public Builder loreLayout(List<LoreSection> loreLayout) {
            this.loreLayout = loreLayout;
            return this;
        }

        public CustomItem build() {
            if (category == null) {
                category = ItemCategory.fromMaterial(material);
            }
            return new CustomItem(id, category, traits, material, components, loreLayout);
        }
    }
}
