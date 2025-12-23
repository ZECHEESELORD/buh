package sh.harold.fulcrum.plugin.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;
import sh.harold.fulcrum.plugin.playerdata.LevelProgress;
import sh.harold.fulcrum.plugin.playerdata.LevelTier;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ChatFormatService {

    private static final TextColor DEFAULT_COLOR = LuckPermsTextFormat.DEFAULT_COLOR;
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;
    private final PlayerLevelingService levelingService;

    public ChatFormatService(LuckPerms luckPerms, PlayerLevelingService levelingService) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.levelingService = Objects.requireNonNull(levelingService, "levelingService");
        QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
        this.queryOptions = Objects.requireNonNullElseGet(options, () -> luckPerms.getContextManager()
            .getQueryOptions(luckPerms.getContextManager().getStaticContext()));
    }

    public CompletableFuture<Format> format(Player player) {
        UserManager users = luckPerms.getUserManager();
        User cached = users.getUser(player.getUniqueId());
        CompletableFuture<User> userFuture = cached != null ? CompletableFuture.completedFuture(cached) : users.loadUser(player.getUniqueId());
        CompletableFuture<LevelProgress> levelFuture = levelingService.loadProgress(player.getUniqueId())
            .exceptionally(throwable -> levelingService.progressFor(0L))
            .toCompletableFuture();
        return userFuture.thenCombine(levelFuture, (user, progress) -> createFormat(player, user, progress));
    }

    private Format createFormat(Player player, User user, LevelProgress progress) {
        Component levelPrefix = buildLevelPrefix(progress);
        if (user == null) {
            return new Format(levelPrefix, Component.text(player.getName(), DEFAULT_COLOR), DEFAULT_COLOR);
        }
        var meta = user.getCachedData().getMetaData(queryOptions);
        Component rankPrefix = wrapRankPrefix(LuckPermsTextFormat.deserializePrefix(meta.getPrefix()));
        Component prefix = combinePrefixes(levelPrefix, rankPrefix);

        TextColor nameColor = LevelTier.colorFor(progress == null ? 0 : progress.level());
        TextColor chatColor = LuckPermsTextFormat.parseColor(meta.getMetaValue("chatColour")).orElse(DEFAULT_COLOR);

        TextComponent name = Component.text(player.getName(), nameColor);
        return new Format(prefix, name, chatColor);
    }

    private Component buildLevelPrefix(LevelProgress progress) {
        int level = progress == null ? 0 : progress.level();
        TextColor levelColor = LevelTier.colorFor(level);
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(level, levelColor))
            .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    private Component wrapRankPrefix(Component prefix) {
        if (prefix == null || prefix.equals(Component.empty())) {
            return Component.empty();
        }
        Component cleaned = stripSimpleBrackets(prefix);
        String plain = PLAIN_TEXT.serialize(cleaned).trim();
        if (plain.isBlank()) {
            return Component.empty();
        }
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(cleaned)
            .append(Component.text("]", NamedTextColor.DARK_GRAY));
    }

    private Component stripSimpleBrackets(Component prefix) {
        if (prefix instanceof TextComponent text && text.children().isEmpty()) {
            String content = text.content();
            if (content != null && content.length() > 1 && content.startsWith("[") && content.endsWith("]")) {
                return Component.text(content.substring(1, content.length() - 1), text.color());
            }
        }
        return prefix;
    }

    private Component combinePrefixes(Component first, Component second) {
        boolean firstEmpty = first == null || first.equals(Component.empty());
        boolean secondEmpty = second == null || second.equals(Component.empty());
        if (firstEmpty && secondEmpty) {
            return Component.empty();
        }
        if (firstEmpty) {
            return second;
        }
        if (secondEmpty) {
            return first;
        }
        return first.append(Component.space()).append(second);
    }

    public record Format(Component prefix, Component name, TextColor chatColor) {}
}
