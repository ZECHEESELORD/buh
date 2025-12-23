package sh.harold.fulcrum.plugin.staff.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.harold.fulcrum.message.Message;
import sh.harold.fulcrum.plugin.permissions.LuckPermsModule;
import sh.harold.fulcrum.plugin.permissions.LuckPermsTextFormat;
import sh.harold.fulcrum.plugin.permissions.StaffGuard;
import sh.harold.fulcrum.plugin.playerdata.LevelTier;
import sh.harold.fulcrum.plugin.playerdata.LevelProgress;
import sh.harold.fulcrum.plugin.playerdata.PlayerLevelingService;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class LevelCommand {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final int CENTER_WIDTH = 56;

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYER_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player player : context.getSource().getSender().getServer().getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private final StaffGuard staffGuard;
    private final PlayerLevelingService levelingService;
    private final LuckPerms luckPerms;
    private final QueryOptions queryOptions;

    public LevelCommand(StaffGuard staffGuard, PlayerLevelingService levelingService, LuckPermsModule luckPermsModule) {
        this.staffGuard = Objects.requireNonNull(staffGuard, "staffGuard");
        this.levelingService = Objects.requireNonNull(levelingService, "levelingService");
        this.luckPerms = luckPermsModule == null ? null : luckPermsModule.luckPerms().orElse(null);
        this.queryOptions = resolveQueryOptions(luckPerms);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return literal("level")
            .requires(staffGuard::isStaff)
            .executes(this::viewSelf)
            .then(literal("get")
                .executes(this::viewSelf)
                .then(argument("target", word())
                    .suggests(ONLINE_PLAYER_SUGGESTIONS)
                    .executes(this::viewTarget)))
            .then(literal("set")
                .then(argument("target", word())
                    .suggests(ONLINE_PLAYER_SUGGESTIONS)
                    .then(argument("level", integer(0))
                        .executes(this::setLevel))))
            .then(literal("setxp")
                .then(argument("target", word())
                    .suggests(ONLINE_PLAYER_SUGGESTIONS)
                    .then(argument("xp", longArg(0))
                        .executes(this::setXp))))
            .then(literal("addxp")
                .then(argument("target", word())
                    .suggests(ONLINE_PLAYER_SUGGESTIONS)
                    .then(argument("xp", longArg())
                        .executes(this::addXp))))
            .then(literal("prestige")
                .then(argument("target", word())
                    .suggests(ONLINE_PLAYER_SUGGESTIONS)
                    .executes(this::prestige)))
            .build();
    }

    private int viewSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            Message.error("Pick a player to inspect.").staff().send(sender);
            return 0;
        }
        showStatus(sender, player, true);
        return Command.SINGLE_SUCCESS;
    }

    private int viewTarget(CommandContext<CommandSourceStack> context) {
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        showStatus(context.getSource().getSender(), selection.target(), selection.selfTarget());
        return Command.SINGLE_SUCCESS;
    }

    private int setLevel(CommandContext<CommandSourceStack> context) {
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        int requested = context.getArgument("level", Integer.class);
        int clamped = Math.min(requested, levelingService.maxLevel());
        long xpTotal = levelingService.totalXpForLevel(clamped);
        applyXp(selection, xpTotal, "Level updated");
        return Command.SINGLE_SUCCESS;
    }

    private int setXp(CommandContext<CommandSourceStack> context) {
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        long xp = context.getArgument("xp", Long.class);
        applyXp(selection, xp, "XP updated");
        return Command.SINGLE_SUCCESS;
    }

    private int addXp(CommandContext<CommandSourceStack> context) {
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        long xp = context.getArgument("xp", Long.class);
        applyXp(selection, xp, "XP adjusted", true);
        return Command.SINGLE_SUCCESS;
    }

    private int prestige(CommandContext<CommandSourceStack> context) {
        TargetSelection selection = resolveTarget(context);
        if (selection == null) {
            return 0;
        }
        levelingService.prestige(selection.target().getUniqueId())
            .whenComplete((result, throwable) -> {
                CommandSender sender = context.getSource().getSender();
                if (throwable != null) {
                    Message.error("Prestige failed: check the logs.").staff().send(sender);
                    return;
                }
                if (!result.prestiged()) {
                    Message.info("{0} is not at the cap yet.", selection.target().getName()).staff().send(sender);
                    return;
                }
                Message.success("{0} prestiged to {1}.", selection.target().getName(), result.prestigeCount())
                    .staff()
                    .send(sender);
                if (!selection.selfTarget()) {
                    Message.info("You prestiged to {0}.", result.prestigeCount())
                        .staff()
                        .send(selection.target());
                }
            });
        return Command.SINGLE_SUCCESS;
    }

    private void applyXp(TargetSelection selection, long xp, String label) {
        applyXp(selection, xp, label, false);
    }

    private void applyXp(TargetSelection selection, long xp, String label, boolean additive) {
        UUID playerId = selection.target().getUniqueId();
        levelingService.loadProgress(playerId)
            .thenCompose(before -> {
                CompletableFuture<LevelProgress> updateStage = additive
                    ? levelingService.addXp(playerId, xp).toCompletableFuture()
                    : levelingService.setXp(playerId, xp).toCompletableFuture();
                return updateStage.thenCombine(levelingService.loadPrestige(playerId), (after, prestige) -> new LevelChange(before, after, prestige));
            })
            .whenComplete((snapshot, throwable) -> {
                if (throwable != null) {
                    Message.error("Leveling update failed: check the logs.").staff().send(selection.sender());
                    return;
                }
                sendUpdate(selection.sender(), selection, new LevelSnapshot(snapshot.after(), snapshot.prestige()), label);
                sendLevelUpMessage(selection.target(), snapshot.before(), snapshot.after());
            });
    }

    private void showStatus(CommandSender sender, Player target, boolean selfTarget) {
        CompletableFuture<LevelProgress> progressStage = levelingService.loadProgress(target.getUniqueId()).toCompletableFuture();
        CompletableFuture<Integer> prestigeStage = levelingService.loadPrestige(target.getUniqueId()).toCompletableFuture();
        progressStage.thenCombine(prestigeStage, LevelSnapshot::new)
            .whenComplete((snapshot, throwable) -> {
                if (throwable != null) {
                    Message.error("Could not read level data: check the logs.").staff().send(sender);
                    return;
                }
                sendStatus(sender, target, snapshot, selfTarget);
            });
    }

    private void sendStatus(CommandSender sender, Player target, LevelSnapshot snapshot, boolean selfTarget) {
        LevelProgress progress = snapshot.progress();
        String summary = "Level " + progress.level() + " with " + progress.xpIntoLevel() + "/" + progress.xpForNextLevel()
            + " XP. Prestige " + snapshot.prestige() + ".";
        if (selfTarget) {
            Message.info(summary).staff().send(sender);
            return;
        }
        Message.info("{0}: {1}", target.getName(), summary).staff().send(sender);
    }

    private void sendUpdate(CommandSender sender, TargetSelection selection, LevelSnapshot snapshot, String action) {
        LevelProgress progress = snapshot.progress();
        String summary = "Level " + progress.level() + " with " + progress.xpIntoLevel() + "/" + progress.xpForNextLevel()
            + " XP. Prestige " + snapshot.prestige() + ".";
        Message.success("{0}: {1}. {2}", selection.target().getName(), action, summary)
            .staff()
            .send(sender);
        if (!selection.selfTarget()) {
            Message.info("Your level was updated: {0}", summary).staff().send(selection.target());
        }
    }

    private void sendLevelUpMessage(Player target, LevelProgress before, LevelProgress after) {
        if (before.level() >= after.level()) {
            return;
        }
        Component message = buildLevelUpMessage(before.level(), after.level(), target);
        target.sendMessage(message);
    }

    private Component buildLevelUpMessage(int beforeLevel, int afterLevel, Player target) {
        Component title = centerLine(Component.text("SMP LEVEL UP", NamedTextColor.AQUA)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        Component levelLine = centerLine(Component.text("Level: ", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(beforeLevel, NamedTextColor.GRAY))
            .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
            .append(Component.text(afterLevel, NamedTextColor.YELLOW)));

        java.util.List<Component> rewardLines = new java.util.ArrayList<>();
        rewardLines.add(Component.text("Level Prefix ", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false)
            .append(levelPrefix(afterLevel)));
        Component rankPrefix = resolveRankPrefix(target);
        if (!rankPrefix.equals(Component.empty())) {
            rewardLines.add(Component.text("Rank Prefix ", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .append(rankPrefix));
        }

        Component rewardsHeader = centerLine(Component.text("REWARDS", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));

        var builder = Component.text();
        builder.append(title)
            .append(Component.newline())
            .append(levelLine)
            .append(Component.newline())
            .append(Component.newline());
        if (!rewardLines.isEmpty()) {
            builder.append(rewardsHeader);
            for (Component line : rewardLines) {
                builder.append(Component.newline()).append(centerLine(line));
            }
        }
        return builder.build();
    }

    private Component levelPrefix(int level) {
        TextColor color = LevelTier.colorFor(level);
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(level, color))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .decoration(TextDecoration.ITALIC, false);
    }

    private Component centerLine(Component line) {
        String plain = PLAIN_TEXT.serialize(line);
        int padding = Math.max((CENTER_WIDTH - plain.length()) / 2, 0);
        if (padding == 0) {
            return line;
        }
        return Component.text(" ".repeat(padding), NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false)
            .append(line);
    }

    private Component resolveRankPrefix(Player target) {
        if (luckPerms == null || queryOptions == null) {
            return Component.empty();
        }
        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(target.getUniqueId());
        if (user == null) {
            return Component.empty();
        }
        var meta = user.getCachedData().getMetaData(queryOptions);
        Component rawPrefix = LuckPermsTextFormat.deserializePrefix(meta.getPrefix());
        Component cleaned = stripSimpleBrackets(rawPrefix);
        String plain = PLAIN_TEXT.serialize(cleaned).trim();
        if (plain.isBlank()) {
            return Component.empty();
        }
        return Component.text("[", NamedTextColor.DARK_GRAY)
            .append(cleaned)
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .decoration(TextDecoration.ITALIC, false);
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

    private QueryOptions resolveQueryOptions(LuckPerms luckPerms) {
        if (luckPerms == null) {
            return null;
        }
        QueryOptions options = luckPerms.getContextManager().getStaticQueryOptions();
        return Objects.requireNonNullElseGet(options, () -> luckPerms.getContextManager()
            .getQueryOptions(luckPerms.getContextManager().getStaticContext()));
    }

    private TargetSelection resolveTarget(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String targetName = context.getArgument("target", String.class);
        Player target = sender.getServer().getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("No player named " + targetName + " is online.", NamedTextColor.RED));
            return null;
        }
        boolean selfTarget = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
        return new TargetSelection(sender, target, selfTarget);
    }

    private record TargetSelection(CommandSender sender, Player target, boolean selfTarget) {
    }

    private record LevelChange(LevelProgress before, LevelProgress after, int prestige) {
    }

    private record LevelSnapshot(LevelProgress progress, int prestige) {
    }
}
