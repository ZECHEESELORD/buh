package sh.harold.fulcrum.plugin.playerhead;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import sh.harold.fulcrum.plugin.item.ItemEngine;

import java.util.Objects;

record PlayerHeadFactory(ItemEngine itemEngine) {

    PlayerHeadFactory {
        Objects.requireNonNull(itemEngine, "itemEngine");
    }

    ItemStack createHead(Player player) {
        Objects.requireNonNull(player, "player");
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Player Head"));
            PlayerProfile profile = player.getPlayerProfile();
            if (profile != null) {
                meta.setPlayerProfile(profile.clone());
            }
            head.setItemMeta(meta);
        }
        return itemEngine.sanitizeStackable(head);
    }
}
