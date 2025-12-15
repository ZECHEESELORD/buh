package sh.harold.fulcrum.plugin.jukebox.disc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JukeboxDiscService {

    private final NamespacedKey trackIdKey;

    public JukeboxDiscService(NamespacedKey trackIdKey) {
        this.trackIdKey = Objects.requireNonNull(trackIdKey, "trackIdKey");
    }

    public Optional<String> readTrackId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String trackId = container.get(trackIdKey, PersistentDataType.STRING);
        if (trackId == null || trackId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(trackId);
    }

    public ItemStack createDisc(String trackId, String title) {
        Objects.requireNonNull(trackId, "trackId");
        ItemStack stack = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(title == null || title.isBlank() ? "Minted Disc" : title, NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Jukebox Track", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Drop this into a jukebox", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("and let voice chat do the rest.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Track ID: ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(trackId, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
        ));
        meta.getPersistentDataContainer().set(trackIdKey, PersistentDataType.STRING, trackId);
        stack.setItemMeta(meta);
        return stack;
    }
}
