package sh.harold.fulcrum.plugin.playerhead;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class PlayerHeadDropListener implements Listener {

    private final PlayerHeadFactory headFactory;

    PlayerHeadDropListener(PlayerHeadFactory headFactory) {
        this.headFactory = Objects.requireNonNull(headFactory, "headFactory");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        Player killer = player.getKiller();
        if (killer == null) {
            return;
        }
        if (hasExistingHeadDrop(event.getDrops(), player.getUniqueId())) {
            return;
        }
        ItemStack head = headFactory.createHead(player);
        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return;
        }
        event.getDrops().add(head);
    }

    private boolean hasExistingHeadDrop(List<ItemStack> drops, UUID playerId) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() != Material.PLAYER_HEAD) {
                continue;
            }
            if (!(drop.getItemMeta() instanceof SkullMeta skullMeta)) {
                continue;
            }
            if (isMatchingPlayer(skullMeta, playerId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMatchingPlayer(SkullMeta skullMeta, UUID playerId) {
        PlayerProfile profile = skullMeta.getPlayerProfile();
        UUID profileId = profile == null ? null : profile.getId();
        UUID owningId = skullMeta.getOwningPlayer() == null ? null : skullMeta.getOwningPlayer().getUniqueId();
        return playerId.equals(profileId) || playerId.equals(owningId);
    }
}
