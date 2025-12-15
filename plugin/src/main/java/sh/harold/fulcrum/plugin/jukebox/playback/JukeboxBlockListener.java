package sh.harold.fulcrum.plugin.jukebox.playback;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.plugin.jukebox.disc.JukeboxDiscService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Map;
import java.util.Objects;

public final class JukeboxBlockListener implements Listener {

    private final JavaPlugin plugin;
    private final JukeboxDiscService discService;
    private final JukeboxPlaybackEngine playbackEngine;

    public JukeboxBlockListener(JavaPlugin plugin, JukeboxDiscService discService, JukeboxPlaybackEngine playbackEngine) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.discService = Objects.requireNonNull(discService, "discService");
        this.playbackEngine = Objects.requireNonNull(playbackEngine, "playbackEngine");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJukeboxInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.JUKEBOX) {
            return;
        }

        Player player = event.getPlayer();
        Jukebox jukebox = (Jukebox) clickedBlock.getState();

        ItemStack held = event.getItem();
        String heldTrackId = discService.readTrackId(held).orElse(null);
        if (heldTrackId != null) {
            event.setCancelled(true);
            insertAndPlay(player, clickedBlock, jukebox, held, heldTrackId);
            return;
        }

        ItemStack record = jukebox.getRecord();
        String recordTrackId = discService.readTrackId(record).orElse(null);
        if (recordTrackId != null) {
            event.setCancelled(true);
            eject(player, clickedBlock, jukebox, record);
            playbackEngine.stop(clickedBlock.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJukeboxBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.JUKEBOX) {
            return;
        }
        playbackEngine.stop(block.getLocation());
    }

    private void insertAndPlay(Player player, Block clickedBlock, Jukebox jukebox, ItemStack held, String trackId) {
        ItemStack existing = jukebox.getRecord();
        if (existing != null && !existing.getType().isAir()) {
            eject(player, clickedBlock, jukebox, existing);
        }

        ItemStack inserted = held.clone();
        inserted.setAmount(1);
        jukebox.setRecord(inserted);
        jukebox.update(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeSingleHeld(player);
        }

        playbackEngine.start(clickedBlock.getLocation(), trackId)
            .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.started() || !player.isOnline()) {
                    return;
                }
                if (!result.message().isBlank()) {
                    player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                }
                if (clickedBlock.getType() != Material.JUKEBOX) {
                    return;
                }
                Jukebox current = (Jukebox) clickedBlock.getState();
                ItemStack record = current.getRecord();
                String recordTrackId = discService.readTrackId(record).orElse(null);
                if (recordTrackId != null && recordTrackId.equals(trackId)) {
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        current.setRecord(new ItemStack(Material.AIR));
                        current.update(true);
                    } else {
                        eject(player, clickedBlock, current, record);
                    }
                    playbackEngine.stop(clickedBlock.getLocation());
                }
            }));
    }

    private void consumeSingleHeld(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return;
        }
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
            return;
        }
        hand.setAmount(amount - 1);
        player.getInventory().setItemInMainHand(hand);
    }

    private void eject(Player player, Block block, Jukebox jukebox, ItemStack record) {
        if (record == null || record.getType().isAir()) {
            return;
        }
        jukebox.setRecord(new ItemStack(Material.AIR));
        jukebox.update(true);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(record);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 1.0D, 0.5D), item));
        }
    }
}
