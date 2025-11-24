package sh.harold.fulcrum.plugin.permissions;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Lightweight helper for staff gating.
 */
public record StaffGuard(LuckPermsModule luckPermsModule) {

    private static final String STAFF_PERMISSION = "fulcrum.staff";

    public StaffGuard {
        Objects.requireNonNull(luckPermsModule, "luckPermsModule");
    }

    public boolean isStaff(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            return true;
        }
        return isStaff(player);
    }

    public boolean isStaff(Player player) {
        if (player.hasPermission(STAFF_PERMISSION)) {
            return true;
        }
        return luckPermsModule.staffService()
            .map(service -> service.isStaff(player.getUniqueId()).toCompletableFuture().getNow(false))
            .orElse(false);
    }
}
