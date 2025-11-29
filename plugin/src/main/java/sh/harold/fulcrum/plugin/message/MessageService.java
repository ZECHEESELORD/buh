package sh.harold.fulcrum.plugin.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;
import sh.harold.fulcrum.plugin.playerdata.UsernameDisplayService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class MessageService {

    private final Plugin plugin;
    private final java.util.function.Supplier<FormattedUsernameService> usernameServiceSupplier;
    private final Supplier<UsernameDisplayService> usernameDisplayServiceSupplier;

    public MessageService(
        Plugin plugin,
        java.util.function.Supplier<FormattedUsernameService> usernameServiceSupplier,
        Supplier<UsernameDisplayService> usernameDisplayServiceSupplier
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.usernameServiceSupplier = Objects.requireNonNull(usernameServiceSupplier, "usernameServiceSupplier");
        this.usernameDisplayServiceSupplier = usernameDisplayServiceSupplier == null ? () -> null : usernameDisplayServiceSupplier;
    }

    public void sendMessage(Player sender, Player target, String content) {
        FormattedUsernameService usernameService = usernameServiceSupplier.get();
        CompletionStage<FormattedUsernameService.FormattedUsername> senderNameStage = usernameService.username(sender);
        CompletionStage<FormattedUsernameService.FormattedUsername> targetNameStage = usernameService.username(target);

        CompletableFuture.allOf(senderNameStage.toCompletableFuture(), targetNameStage.toCompletableFuture())
            .whenComplete((ignored, throwable) -> {
                FormattedUsernameService.FormattedUsername senderName = defaultName(sender);
                FormattedUsernameService.FormattedUsername targetName = defaultName(target);
                if (throwable == null) {
                    try {
                        senderName = senderNameStage.toCompletableFuture().join();
                        targetName = targetNameStage.toCompletableFuture().join();
                    } catch (RuntimeException runtimeException) {
                        plugin.getLogger().severe("Failed to fetch formatted usernames: " + runtimeException.getMessage());
                    }
                } else {
                    plugin.getLogger().severe("Failed to fetch formatted usernames: " + throwable.getMessage());
                }
                dispatchMessages(sender, target, senderName, targetName, content);
            });
    }

    private void dispatchMessages(Player sender, Player target, FormattedUsernameService.FormattedUsername senderName, FormattedUsernameService.FormattedUsername targetName, String content) {
        Component messageComponent = Component.text(content, NamedTextColor.GRAY);
        UsernameDisplayService displayService = usernameDisplayService();
        Component toLine = Component.text("To ", NamedTextColor.LIGHT_PURPLE)
            .append(resolveDisplayName(displayService, target, targetName, sender))
            .append(Component.text(": ", NamedTextColor.LIGHT_PURPLE))
            .append(messageComponent);
        Component fromLine = Component.text("From ", NamedTextColor.LIGHT_PURPLE)
            .append(resolveDisplayName(displayService, sender, senderName, target))
            .append(Component.text(": ", NamedTextColor.LIGHT_PURPLE))
            .append(messageComponent);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            sender.sendMessage(toLine);
            target.sendMessage(fromLine);
        });
    }

    private FormattedUsernameService.FormattedUsername defaultName(Player player) {
        return new FormattedUsernameService.FormattedUsername(Component.empty(), Component.text(player.getName(), NamedTextColor.WHITE));
    }

    private UsernameDisplayService usernameDisplayService() {
        try {
            return usernameDisplayServiceSupplier.get();
        } catch (RuntimeException runtimeException) {
            plugin.getLogger().warning("Failed to resolve UsernameDisplayService: " + runtimeException.getMessage());
            return null;
        }
    }

    private Component resolveDisplayName(UsernameDisplayService displayService, Player subject, FormattedUsernameService.FormattedUsername formatted, Player viewer) {
        if (formatted == null) {
            return Component.text(subject == null ? "Unknown" : subject.getName(), NamedTextColor.WHITE);
        }
        Component prefix = formatted.prefix();
        Component name = formatted.name();
        if (displayService != null && subject != null && viewer != null) {
            Component adjusted = displayService.displayComponent(viewer.getUniqueId(), subject, name.color() == null ? NamedTextColor.WHITE : name.color());
            name = adjusted == null ? name : adjusted;
        } else if (name.color() == null) {
            name = name.color(NamedTextColor.WHITE);
        }
        if (prefix.equals(Component.empty())) {
            return name;
        }
        return prefix.append(Component.space()).append(name);
    }
}
