package sh.harold.fulcrum.plugin.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.fulcrum.common.permissions.FormattedUsernameService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MessageService {

    private final Plugin plugin;
    private final java.util.function.Supplier<FormattedUsernameService> usernameServiceSupplier;

    public MessageService(Plugin plugin, java.util.function.Supplier<FormattedUsernameService> usernameServiceSupplier) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.usernameServiceSupplier = Objects.requireNonNull(usernameServiceSupplier, "usernameServiceSupplier");
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
                dispatchMessages(sender, target, senderName.displayName(), targetName.displayName(), content);
            });
    }

    private void dispatchMessages(Player sender, Player target, Component senderName, Component targetName, String content) {
        Component messageComponent = Component.text(content, NamedTextColor.GRAY);
        Component toLine = Component.text("To ", NamedTextColor.LIGHT_PURPLE)
            .append(targetName)
            .append(Component.text(": ", NamedTextColor.LIGHT_PURPLE))
            .append(messageComponent);
        Component fromLine = Component.text("From ", NamedTextColor.LIGHT_PURPLE)
            .append(senderName)
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
}
