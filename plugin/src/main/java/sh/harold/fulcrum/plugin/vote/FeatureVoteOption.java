package sh.harold.fulcrum.plugin.vote;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;

public enum FeatureVoteOption {

    BOUNTIES(
        "bounties",
        "Bounties System",
        "Hunt marked targets and collect glittering rewards for the takedown.",
        NamedTextColor.RED,
        Material.IRON_SWORD,
        Material.RED_STAINED_GLASS,
        2
    ),
    CUSTOM_ITEMS(
        "custom_items",
        "Custom Items Engine",
        "Forge bespoke gear and curios that twist encounters in your favour.",
        NamedTextColor.LIGHT_PURPLE,
        Material.COMMAND_BLOCK,
        Material.MAGENTA_STAINED_GLASS,
        4
    ),
    SETTLEMENTS(
        "settlements",
        "Settlements",
        "Grow shared towns, unlock comforts, and rally neighbours around your banner.",
        NamedTextColor.GREEN,
        Material.GRASS_BLOCK,
        Material.GREEN_STAINED_GLASS,
        6
    ),
    ECONOMY(
        "economy",
        "Economy",
        "Shape auctions, trading posts, and markets so gold keeps flowing.",
        NamedTextColor.GOLD,
        Material.GOLD_INGOT,
        Material.YELLOW_STAINED_GLASS,
        8
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final NamedTextColor color;
    private final Material material;
    private final Material barMaterial;
    private final int column;

    FeatureVoteOption(String id, String displayName, String description, NamedTextColor color, Material material, Material barMaterial, int column) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.color = color;
        this.material = material;
        this.barMaterial = barMaterial;
        this.column = column;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public NamedTextColor color() {
        return color;
    }

    public Material material() {
        return material;
    }

    public Material barMaterial() {
        return barMaterial;
    }

    public int column() {
        return column;
    }

    public int zeroBasedColumn() {
        return column - 1;
    }

    public static Optional<FeatureVoteOption> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(option -> option.id.equalsIgnoreCase(id))
            .findFirst();
    }
}
