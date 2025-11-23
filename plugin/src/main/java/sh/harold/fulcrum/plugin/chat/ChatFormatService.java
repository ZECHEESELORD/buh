package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ChatFormatService {

    private static final TextColor DEFAULT_COLOR = LuckPermsTextFormat.DEFAULT_COLOR;

    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;

    public ChatFormatService(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
        this.queryOptions = Objects.requireNonNullElseGet(options, () -> luckPerms.getContextManager()
            .getQueryOptions(luckPerms.getContextManager().getStaticContext()));
    }

    public CompletableFuture<Format> format(Player player) {
        UserManager users = luckPerms.getUserManager();
        User cached = users.getUser(player.getUniqueId());
        CompletableFuture<User> userFuture = cached != null ? CompletableFuture.completedFuture(cached) : users.loadUser(player.getUniqueId());
        return userFuture.thenApply(user -> createFormat(player, user));
    }

    private Format createFormat(Player player, User user) {
        if (user == null) {
            return new Format(Component.empty(), Component.text(player.getName(), DEFAULT_COLOR), DEFAULT_COLOR);
        }
        var meta = user.getCachedData().getMetaData(queryOptions);
        Component prefix = LuckPermsTextFormat.deserializePrefix(meta.getPrefix());

        TextColor nameColor = LuckPermsTextFormat.parseColor(meta.getMetaValue("nameColour")).orElse(DEFAULT_COLOR);
        TextColor chatColor = LuckPermsTextFormat.parseColor(meta.getMetaValue("chatColour")).orElse(DEFAULT_COLOR);

        TextComponent name = Component.text(player.getName(), nameColor);
        return new Format(prefix, name, chatColor);
    }

    public record Format(Component prefix, Component name, TextColor chatColor) {}
}
