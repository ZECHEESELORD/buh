package sh.harold.fulcrum.plugin.unlockable;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

final class CosmeticPriceDropRefundListener implements Listener {

    private final CosmeticPriceDropRefund refund;

    CosmeticPriceDropRefundListener(CosmeticPriceDropRefund refund) {
        this.refund = Objects.requireNonNull(refund, "refund");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refund.refundIfNeeded(event.getPlayer().getUniqueId());
    }
}
