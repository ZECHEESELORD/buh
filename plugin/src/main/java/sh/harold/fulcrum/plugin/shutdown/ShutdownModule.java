package sh.harold.fulcrum.plugin.shutdown;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.message.scoreboard.ScoreboardService;
import sh.harold.fulcrum.common.loader.FulcrumModule;
import sh.harold.fulcrum.common.loader.ModuleDescriptor;
import sh.harold.fulcrum.common.loader.ModuleId;

import java.time.Duration;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ShutdownModule implements FulcrumModule, Listener {

    private static final Duration EVICT_BUFFER = Duration.ofSeconds(3);
    private static final int FINAL_REMINDER_SECOND = 10;
    private static final int DEFAULT_COUNTDOWN_SECONDS = 30;
    private static final String DEFAULT_REASON = "Scheduled Reboot";

    private final JavaPlugin plugin;
    private final ScoreboardService scoreboardService;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean trackPlaying = new AtomicBoolean(false);
    private EvacuationContext currentContext;

    public ShutdownModule(JavaPlugin plugin, ScoreboardService scoreboardService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scoreboardService = Objects.requireNonNull(scoreboardService, "scoreboardService");
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(ModuleId.of("shutdown"), Set.of());
    }

    @Override
    public CompletionStage<Void> enable() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> disable() {
        cancelContext();
        return CompletableFuture.completedFuture(null);
    }

    @EventHandler
    public void handlePlayerJoin(PlayerJoinEvent event) {
        if (currentContext == null) {
            return;
        }
        sendCountdownPrompt(event.getPlayer(), currentContext.secondsRemaining().get());
        if (trackPlaying.get()) {
            playShutdownTrack(event.getPlayer());
        }
    }

    private void registerCommands(ReloadableRegistrarEvent<Commands> event) {
        Commands registrar = event.registrar();
        LiteralCommandNode<CommandSourceStack> stop = buildShutdownCommand("stop");
        LiteralCommandNode<CommandSourceStack> restart = buildShutdownCommand("restart");
        LiteralCommandNode<CommandSourceStack> evacuate = buildEvacuateCommand();
        registrar.register(plugin.getPluginMeta(), stop, "stop", java.util.List.of());
        registrar.register(plugin.getPluginMeta(), restart, "restart", java.util.List.of());
        registrar.register(plugin.getPluginMeta(), evacuate, "evacuate", java.util.List.of());
    }

    private LiteralCommandNode<CommandSourceStack> buildShutdownCommand(String name) {
        return literal(name)
            .requires(this::canIssueStop)
            .executes(context -> startShutdown(context.getSource(), name, DEFAULT_COUNTDOWN_SECONDS, DEFAULT_REASON))
            .then(Commands.literal("cancel").executes(context -> cancelShutdown(context.getSource(), name)))
            .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                .executes(context -> startShutdown(
                    context.getSource(),
                    name,
                    IntegerArgumentType.getInteger(context, "seconds"),
                    DEFAULT_REASON
                ))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                    .executes(context -> startShutdown(
                        context.getSource(),
                        name,
                        IntegerArgumentType.getInteger(context, "seconds"),
                        StringArgumentType.getString(context, "reason")
                    ))))
            .then(Commands.argument("reason", StringArgumentType.greedyString())
                .executes(context -> startShutdown(
                    context.getSource(),
                    name,
                    DEFAULT_COUNTDOWN_SECONDS,
                    StringArgumentType.getString(context, "reason")
                )))
            .build();
    }

    private LiteralCommandNode<CommandSourceStack> buildEvacuateCommand() {
        return literal("evacuate")
            .requires(stack -> stack.getSender() instanceof Player)
            .executes(context -> {
                Player player = (Player) context.getSource().getSender();
                evacuatePlayer(player);
                return Command.SINGLE_SUCCESS;
            })
            .build();
    }

    private boolean canIssueStop(CommandSourceStack stack) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp()
            || sender.hasPermission("bukkit.command.stop")
            || sender.hasPermission("minecraft.command.stop");
    }

    private int startShutdown(CommandSourceStack source, String commandLabel, int seconds, String reason) {
        if (currentContext != null) {
            source.getSender().sendMessage(Component.text("A shutdown is already scheduled. Use /" + commandLabel + " cancel first.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        ShutdownRequest request = new ShutdownRequest(reason, seconds);
        currentContext = new EvacuationContext(request);
        shuttingDown.set(false);

        source.getSender().sendMessage(Component.text(
            "Scheduled shutdown in " + currentContext.secondsRemaining().get() + "s.",
            NamedTextColor.YELLOW
        ));

        Bukkit.getOnlinePlayers().forEach(player -> showInitialTitle(player, request));
        sendCountdownBroadcast(currentContext.secondsRemaining().get());
        playShutdownTrack();
        startCountdown();
        refreshScoreboards();
        return Command.SINGLE_SUCCESS;
    }

    private int cancelShutdown(CommandSourceStack source, String commandLabel) {
        if (currentContext == null) {
            source.getSender().sendMessage(Component.text("No shutdown is currently scheduled.", NamedTextColor.GRAY));
            return Command.SINGLE_SUCCESS;
        }
        stopShutdownTrack();
        cancelContext();
        source.getSender().sendMessage(Component.text("Cancelled the pending shutdown.", NamedTextColor.GREEN));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text("Shutdown cancelled.", NamedTextColor.YELLOW));
        }
        refreshScoreboards();
        return Command.SINGLE_SUCCESS;
    }

    private void startCountdown() {
        if (currentContext == null) {
            return;
        }

        Runnable countdownTick = () -> {
            EvacuationContext context = currentContext;
            if (context == null) {
                return;
            }
            int secondsBeforeTick = context.secondsRemaining().get();
            if (secondsBeforeTick <= 0) {
                beginEviction();
                return;
            }

            int remaining = context.secondsRemaining().decrementAndGet();
            if (remaining <= 0) {
                beginEviction();
                return;
            }

            if (secondsBeforeTick == FINAL_REMINDER_SECOND) {
                sendCountdownBroadcast(secondsBeforeTick);
            }

            refreshScoreboards();
        };

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, countdownTick, 20L, 20L);
        currentContext.countdownTask().set(task);
    }

    private void beginEviction() {
        EvacuationContext context = currentContext;
        if (context == null || !shuttingDown.compareAndSet(false, true)) {
            return;
        }
        cancelTask(context.countdownTask());

        for (Player player : Bukkit.getOnlinePlayers()) {
            evacuatePlayer(player);
        }

        BukkitTask shutdownTask = Bukkit.getScheduler()
            .runTaskLater(plugin, this::finalizeShutdown, EVICT_BUFFER.getSeconds() * 20L);
        context.shutdownTask().set(shutdownTask);
    }

    private void finalizeShutdown() {
        if (currentContext == null) {
            return;
        }
        Bukkit.getServer().shutdown();
    }

    private void evacuatePlayer(Player player) {
        if (currentContext == null) {
            player.sendMessage(Component.text("No evacuation in progress.", NamedTextColor.GRAY));
            return;
        }

        Component evacuateNotice = Component.text("Evacuating you to the menu screen...", NamedTextColor.GRAY);
        player.sendMessage(evacuateNotice);
    }

    private void sendCountdownBroadcast(int secondsRemaining) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendCountdownPrompt(player, secondsRemaining);
        }
    }

    private void sendCountdownPrompt(Player player, int secondsRemaining) {
        if (currentContext == null) {
            return;
        }
        Component reasonLine = Component.text("[DAEMON] ", NamedTextColor.DARK_BLUE)
            .append(Component.text("This server will restart soon: ", NamedTextColor.YELLOW))
            .append(Component.text(currentContext.request().reason(), NamedTextColor.AQUA));
        player.sendMessage(reasonLine);

        TextComponent clickable = Component.text("You have ", NamedTextColor.YELLOW)
            .append(Component.text(secondsRemaining + " seconds", NamedTextColor.GREEN))
            .append(Component.text(" to warp out! ", NamedTextColor.YELLOW))
            .append(Component.text("CLICK", NamedTextColor.GREEN, TextDecoration.BOLD, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Run /evacuate", NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.runCommand("/evacuate")))
            .append(Component.text(" to warp now!", NamedTextColor.YELLOW));
        // player.sendMessage(clickable);
    }

    private void showInitialTitle(Player player, ShutdownRequest request) {
        Component titleComponent = Component.text("SERVER REBOOT!", NamedTextColor.YELLOW)
            .decoration(TextDecoration.BOLD, true);
        Component subtitle = Component.text("Scheduled Reboot ", NamedTextColor.GREEN)
            .append(Component.text("(in ", NamedTextColor.GRAY))
            .append(Component.text(formatTime(request.countdownSeconds()), NamedTextColor.YELLOW))
            .append(Component.text(")", NamedTextColor.GRAY));
        Title title = Title.title(titleComponent, subtitle);
        player.showTitle(title);
    }

    private String formatTime(int seconds) {
        if (seconds <= 0) {
            return "0:00";
        }
        int minutes = seconds / 60;
        int rem = seconds % 60;
        String secondsPart = rem < 10 ? "0" + rem : Integer.toString(rem);
        return minutes + ":" + secondsPart;
    }

    private void playShutdownTrack() {
        trackPlaying.set(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            playShutdownTrack(player);
        }
    }

    private void playShutdownTrack(Player player) {
        player.playSound(player, Sound.MUSIC_DISC_STAL, SoundCategory.MASTER, 1.0f, 1.0f);
    }

    private void stopShutdownTrack() {
        if (!trackPlaying.getAndSet(false)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.stopSound(Sound.MUSIC_DISC_STAL, SoundCategory.MASTER);
        }
    }

    private void cancelContext() {
        if (currentContext != null) {
            cancelTask(currentContext.countdownTask());
            cancelTask(currentContext.shutdownTask());
        }
        stopShutdownTrack();
        currentContext = null;
        shuttingDown.set(false);
    }

    private void cancelTask(AtomicReference<BukkitTask> taskReference) {
        if (taskReference == null) {
            return;
        }
        BukkitTask task = taskReference.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }

    public OptionalInt secondsUntilShutdown() {
        EvacuationContext context = currentContext;
        if (context == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, context.secondsRemaining().get()));
    }

    private void refreshScoreboards() {
        Bukkit.getOnlinePlayers()
            .forEach(player -> scoreboardService.refreshPlayerScoreboard(player.getUniqueId()));
    }

    private record ShutdownRequest(String reason, int countdownSeconds) {
        private ShutdownRequest {
            reason = reason == null || reason.isBlank() ? DEFAULT_REASON : reason;
            countdownSeconds = countdownSeconds <= 0 ? DEFAULT_COUNTDOWN_SECONDS : countdownSeconds;
        }
    }

    private record EvacuationContext(
        ShutdownRequest request,
        AtomicInteger secondsRemaining,
        AtomicReference<BukkitTask> countdownTask,
        AtomicReference<BukkitTask> shutdownTask
    ) {
        private EvacuationContext(ShutdownRequest request) {
            this(
                request,
                new AtomicInteger(Math.max(1, request.countdownSeconds())),
                new AtomicReference<>(),
                new AtomicReference<>()
            );
        }
    }
}
