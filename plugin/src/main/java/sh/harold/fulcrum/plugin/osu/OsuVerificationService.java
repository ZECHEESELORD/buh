package sh.harold.fulcrum.plugin.osu;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class OsuVerificationService implements Listener, AutoCloseable {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("linkosuaccount", "linkdiscordaccount", "skiposu", "menu", "settings", "help");
    private final JavaPlugin plugin;
    private final Logger logger;
    private final DocumentCollection players;
    private final VerificationWorld verificationWorld;
    private final World primaryWorld;
    private final Location primarySpawn;
    private final NamespacedKey playerMenuMarkerKey;
    private final boolean requireOsuLink;
    private final OsuLinkService linkService;
    private final Set<UUID> quarantined = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<Boolean>> preloginChecks = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> skipOsu = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SkipStage> skipStages = new ConcurrentHashMap<>();

    OsuVerificationService(JavaPlugin plugin, DocumentCollection players, VerificationWorld verificationWorld, boolean requireOsuLink, OsuLinkService linkService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.players = Objects.requireNonNull(players, "players");
        this.verificationWorld = Objects.requireNonNull(verificationWorld, "verificationWorld");
        this.primaryWorld = Objects.requireNonNull(plugin.getServer().getWorlds().getFirst(), "primaryWorld");
        this.primarySpawn = primaryWorld.getSpawnLocation();
        this.playerMenuMarkerKey = new NamespacedKey(plugin, "player_menu");
        this.requireOsuLink = requireOsuLink;
        this.linkService = Objects.requireNonNull(linkService, "linkService");
    }

    void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        pluginManager.registerEvents(this, plugin);
    }

    void handleLinkCompleted(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        players.load(playerId.toString()).whenComplete((document, throwable) -> {
            if (throwable != null) {
                logger.log(Level.WARNING, "Failed to check link completion for " + playerId, throwable);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isQuarantined(playerId)) {
                    return;
                }
                boolean released = completeIfReady(playerId, document, false);
                if (!released) {
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null) {
                        sendPrompts(player, document);
                    }
                }
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID playerId = event.getUniqueId();
        long startedAt = System.nanoTime();
        logger.info(() -> "[login:data] prelogin link check load for " + playerId);
        CompletableFuture<Boolean> check = players.load(playerId.toString())
            .thenApply(this::isLinked)
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "Failed to read link status for " + playerId, throwable);
                return false;
            })
            .toCompletableFuture();
        preloginChecks.put(playerId, check);
        check.whenComplete((linked, throwable) -> {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            if (throwable != null) {
                logger.log(Level.WARNING, "[login:data] link status load failed for " + playerId + " after " + elapsedMillis + "ms", throwable);
                return;
            }
            logger.info(() -> "[login:data] link status load completed for " + playerId + " (linked=" + linked + ") in " + elapsedMillis + "ms");
            if (!linked) {
                quarantined.add(playerId);
            } else {
                quarantined.remove(playerId);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (!needsQuarantine(playerId)) {
            hideQuarantinedFrom(player);
            return;
        }
        quarantine(player);
        CompletableFuture<Boolean> check = preloginChecks.get(playerId);
        if (check != null) {
            check.thenAccept(linked -> {
                if (linked) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> releasePlayer(playerId, false));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isQuarantined(event.getPlayer())) {
            return;
        }
        String command = event.getMessage().toLowerCase();
        String root = command.startsWith("/") ? command.substring(1) : command;
        String rootCommand = root.split("\\s+")[0];
        if (ALLOWED_COMMANDS.contains(rootCommand)) {
            event.setCancelled(true);
            if (rootCommand.equals("menu") || rootCommand.equals("settings")) {
                logger.info(() -> "[verification] rerouting " + rootCommand + " to verification prompts for " + event.getPlayer().getUniqueId());
                sendPromptsWithLoad(event.getPlayer());
            } else if (rootCommand.equals("skiposu")) {
                String[] parts = root.split("\\s+");
                handleSkipOsu(event.getPlayer(), parts.length > 1 ? parts[1] : null);
            } else if (rootCommand.equals("linkosuaccount")) {
                sendPrompts(event.getPlayer(), null); // will regenerate fresh links
            } else if (rootCommand.equals("linkdiscordaccount")) {
                sendPrompts(event.getPlayer(), null);
            }
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("You need to link your account before playing. Use /linkosuaccount or /linkdiscordaccount.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!isQuarantined(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("Chat is disabled until you link your account. Use /linkosuaccount or /linkdiscordaccount.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!isQuarantined(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        if (event.getItem() != null && isPlayerMenuHotkey(event.getItem()) && event.getAction().isRightClick()) {
            logger.info(() -> "[verification] intercept menu hotkey for " + event.getPlayer().getUniqueId());
            sendPromptsWithLoad(event.getPlayer());
        } else {
            logger.info(() -> "[verification] blocked interaction for " + event.getPlayer().getUniqueId() + " action=" + event.getAction());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isQuarantined(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (isQuarantined(event.getPlayer()) && !verificationWorld.world().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Complete verification before leaving this area.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isQuarantined(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        if (isQuarantined(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().setAllowFlight(true);
            event.getPlayer().setFlying(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (isQuarantined(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isQuarantined(event.getPlayer())) {
            return;
        }
        if (event.getFrom().getX() == event.getTo().getX()
            && event.getFrom().getY() == event.getTo().getY()
            && event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }
        event.setTo(event.getFrom());
        plugin.getServer().getScheduler().runTask(plugin, () -> event.getPlayer().teleportAsync(verificationWorld.spawn()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        quarantined.remove(playerId);
        preloginChecks.remove(playerId);
        snapshots.remove(playerId);
        skipOsu.remove(playerId);
        skipStages.remove(playerId);
    }

    @Override
    public void close() {
        var toRelease = java.util.List.copyOf(quarantined);
        toRelease.forEach(id -> releasePlayer(id, false));
        quarantined.clear();
        preloginChecks.clear();
        snapshots.clear();
    }

    private boolean needsQuarantine(UUID playerId) {
        CompletableFuture<Boolean> check = preloginChecks.get(playerId);
        if (check == null) {
            return true;
        }
        if (check.isDone()) {
            return !Boolean.TRUE.equals(check.getNow(Boolean.FALSE));
        }
        return true;
    }

    private boolean isQuarantined(Player player) {
        return quarantined.contains(player.getUniqueId());
    }

    private boolean isQuarantined(UUID playerId) {
        return quarantined.contains(playerId);
    }

    private void quarantine(Player player) {
        UUID playerId = player.getUniqueId();
        quarantined.add(playerId);
        snapshots.put(playerId, snapshot(player));
        hideFromOthers(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.setCollidable(false);
        player.teleportAsync(verificationWorld.spawn());
        sendPromptsWithLoad(player);
    }

    private void releasePlayer(UUID playerId, boolean announce) {
        quarantined.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            snapshots.remove(playerId);
            return;
        }
        skipStages.remove(playerId);
        skipOsu.remove(playerId);
        PlayerSnapshot snapshot = snapshots.remove(playerId);
        PlayerSnapshot targetState = snapshot != null
            ? snapshot
            : new PlayerSnapshot(player.getGameMode(), player.getAllowFlight(), player.isFlying(), player.isInvulnerable(), player.isCollidable(), player.getLocation().clone());
        unhideFromOthers(player);
        player.setInvulnerable(targetState.invulnerable());
        player.setAllowFlight(targetState.allowFlight());
        player.setFlying(targetState.flying());
        player.setCollidable(targetState.collidable());
        player.setGameMode(targetState.gameMode());
        Location destination = targetState.returnLocation() != null ? targetState.returnLocation() : primarySpawn;
        if (!destination.getWorld().equals(player.getWorld()) || player.getLocation().distanceSquared(destination) > 1.0) {
            player.teleportAsync(destination).whenComplete((success, throwable) -> {
                if (Boolean.TRUE.equals(success)) {
                    return;
                }
                if (throwable != null) {
                    logger.log(Level.WARNING, "Async teleport failed for " + playerId + " back to " + destination, throwable);
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    boolean syncSuccess = player.teleport(destination);
                    if (!syncSuccess) {
                        logger.warning("Sync teleport failed for " + playerId + " back to " + destination);
                    }
                });
            });
        }
        if (announce) {
            player.sendMessage(alert("LINKED!", NamedTextColor.GREEN, "Welcome to the server."));
        }
    }

    private PlayerSnapshot snapshot(Player player) {
        return new PlayerSnapshot(
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isInvulnerable(),
            player.isCollidable(),
            player.getLocation().clone()
        );
    }

    private boolean isLinked(Document document) {
        if (document == null || !document.exists()) {
            return false;
        }
        boolean hasDiscord = hasDiscordLink(document);
        boolean hasOsu = hasOsuLink(document);
        return hasDiscord && (hasOsu || !requireOsuLink);
    }

    private void hideFromOthers(Player player) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            other.hidePlayer(plugin, player);
            player.hidePlayer(plugin, other);
        }
    }

    private void hideQuarantinedFrom(Player player) {
        for (UUID quarantinedId : quarantined) {
            Player quarantinedPlayer = plugin.getServer().getPlayer(quarantinedId);
            if (quarantinedPlayer == null) {
                continue;
            }
            player.hidePlayer(plugin, quarantinedPlayer);
            quarantinedPlayer.hidePlayer(plugin, player);
        }
    }

    private void unhideFromOthers(Player player) {
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            other.showPlayer(plugin, player);
            player.showPlayer(plugin, other);
        }
    }

    private void sendPromptsWithLoad(Player player) {
        UUID playerId = player.getUniqueId();
        long startedAt = System.nanoTime();
        logger.info(() -> "[login:data] verification prompt load for " + playerId);
        players.load(playerId.toString()).whenComplete((document, throwable) -> {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            if (throwable != null) {
                logger.log(Level.WARNING, "[login:data] verification prompt load failed for " + playerId + " after " + elapsedMillis + "ms", throwable);
                plugin.getServer().getScheduler().runTask(plugin, () -> sendFallbackPrompts(player));
                return;
            }
            logger.info(() -> "[login:data] verification prompt load completed for " + playerId + " in " + elapsedMillis + "ms");
            plugin.getServer().getScheduler().runTask(plugin, () -> sendPrompts(player, document));
        });
    }

    private void sendPrompts(Player player, Document document) {
        if (!player.isOnline() || !isQuarantined(player)) {
            return;
        }
        boolean hasDiscord = hasDiscordLink(document);
        boolean hasOsu = hasOsuLink(document);
        if (!hasDiscord) {
            String url = linkService.createDiscordLink(player.getUniqueId(), player.getName());
            player.sendMessage(linkLine("Link your Discord account (required) here!", url));
        }
        if (!hasOsu) {
            String body = requireOsuLink
                ? "Link your osu! account (required)."
                : "Link your osu! account (optional).";
            String url = linkService.createOsuLink(player.getUniqueId(), player.getName());
            player.sendMessage(linkLine(body, url));
            if (!requireOsuLink && hasDiscord) {
                SkipStage current = skipStages.getOrDefault(player.getUniqueId(), SkipStage.NONE);
                if (current == SkipStage.NONE) {
                    player.sendMessage(skipLine("Are you sure you don't want to link your osu! account?", "/skiposu"));
                } else if (current == SkipStage.ASK_ONE) {
                    player.sendMessage(skipLine("Positive?", "/skiposu confirm1"));
                } else if (current == SkipStage.ASK_TWO) {
                    player.sendMessage(skipLine("Really sure?", "/skiposu confirm2"));
                }
            }
        } else {
            skipStages.remove(player.getUniqueId());
        }
    }

    private void sendFallbackPrompts(Player player) {
        if (!player.isOnline() || !isQuarantined(player)) {
            return;
        }
        String discordUrl = linkService.createDiscordLink(player.getUniqueId(), player.getName());
        player.sendMessage(linkLine("Link your Discord account (required) here!", discordUrl));
        String osuUrl = linkService.createOsuLink(player.getUniqueId(), player.getName());
        if (requireOsuLink) {
            player.sendMessage(linkLine("Link your osu! account (required).", osuUrl));
        } else {
            player.sendMessage(linkLine("Link your osu! account (optional).", osuUrl));
            // skip only after Discord is linked; fallback prompt skips showing skip.
        }
    }

    private void handleSkipOsu(Player player, String stage) {
        if (requireOsuLink) {
            player.sendMessage(alert("LINK!", NamedTextColor.RED, "osu! link is required; skipping is disabled."));
            return;
        }
        players.load(player.getUniqueId().toString()).whenComplete((document, throwable) -> {
            if (throwable != null) {
                logger.log(Level.WARNING, "Failed to load doc for skip request for " + player.getUniqueId(), throwable);
                player.sendMessage(alert("LINK!", NamedTextColor.RED, "Could not process skip; try again in a moment."));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!isQuarantined(player) || !player.isOnline()) {
                    return;
                }
                if (!hasDiscordLink(document)) {
                    player.sendMessage(alert("LINK!", NamedTextColor.AQUA, "Link Discord first, then you can skip osu!."));
                    return;
                }
                if (stage == null || stage.isBlank()) {
                    skipStages.put(player.getUniqueId(), SkipStage.ASK_ONE);
                    player.sendMessage(alert("SKIP!", NamedTextColor.GOLD, "Are you absolutely sure?"));
                    player.sendMessage(skipLine("YES I'M SURE", "/skiposu confirm1"));
                    return;
                }
                switch (stage.toLowerCase()) {
                    case "confirm1" -> {
                        skipStages.put(player.getUniqueId(), SkipStage.ASK_TWO);
                        player.sendMessage(alert("SKIP!", NamedTextColor.GOLD, "Positive?"));
                        player.sendMessage(skipLine("YES I'M SURE", "/skiposu confirm2"));
                    }
                    case "confirm2" -> {
                        skipStages.put(player.getUniqueId(), SkipStage.ASK_THREE);
                        player.sendMessage(alert("SKIP!", NamedTextColor.GOLD, "Really sure?"));
                        player.sendMessage(skipLine("YES I'M SURE", "/skiposu confirm3"));
                    }
                    case "confirm3" -> {
                        skipStages.remove(player.getUniqueId());
                        skipOsu.add(player.getUniqueId());
                        player.sendMessage(alert("SKIP!", NamedTextColor.GOLD, "Skipping osu! link. You can still link later with /linkosuaccount."));
                        boolean released = completeIfReady(player.getUniqueId(), document, true);
                        if (!released) {
                            sendPrompts(player, document);
                        }
                    }
                    default -> player.sendMessage(alert("SKIP!", NamedTextColor.RED, "Unknown skip step. Click the prompts again."));
                }
            });
        });
    }

    private Component alert(String subject, NamedTextColor subjectColor, String body) {
        return Component.text()
            .append(Component.text(subject, subjectColor, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text(body, NamedTextColor.GRAY))
            .build();
    }

    private Component linkLine(String body, String url) {
        return Component.text()
            .append(Component.text("LINK!", NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text(body + " ", NamedTextColor.GRAY))
            .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD).clickEvent(ClickEvent.openUrl(url)))
            .build();
    }

    private Component skipLine(String body, String command) {
        return Component.text()
            .append(Component.text("SKIP!", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.space())
            .append(Component.text(body + " ", NamedTextColor.GRAY))
            .append(Component.text("YES I'M SURE", NamedTextColor.YELLOW, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand(command)))
            .build();
    }

    private boolean completeIfReady(UUID playerId, Document document, boolean fromSkip) {
        boolean hasDiscord = hasDiscordLink(document);
        boolean hasOsu = hasOsuLink(document);
        boolean allowSkip = skipOsu.contains(playerId) && !requireOsuLink;
        if (hasDiscord && (hasOsu || allowSkip)) {
            releasePlayer(playerId, true);
            return true;
        }
        return false;
    }

    private enum SkipStage {
        NONE,
        ASK_ONE,
        ASK_TWO,
        ASK_THREE
    }

    private boolean isPlayerMenuHotkey(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(playerMenuMarkerKey, PersistentDataType.BYTE);
    }

    private boolean hasDiscordLink(Document document) {
        if (document == null || !document.exists()) {
            return false;
        }
        return document.get("linking.discord.userId", Number.class)
            .map(value -> value.longValue() > 0)
            .orElseGet(() -> document.get("linking.discord.userId", String.class)
                .map(value -> !value.isBlank())
                .orElse(false))
            || document.get("linking.discord.username", String.class)
            .map(value -> !value.isBlank())
            .orElse(false)
            || document.get("linking.discordId", Number.class)
            .map(value -> value.longValue() > 0)
            .orElseGet(() -> document.get("linking.discordId", String.class)
                .map(value -> !value.isBlank())
                .orElse(false));
    }

    private boolean hasOsuLink(Document document) {
        if (document == null || !document.exists()) {
            return false;
        }
        return document.get("linking.osu.userId", Number.class)
            .map(value -> value.longValue() > 0)
            .orElseGet(() -> document.get("linking.osu.username", String.class)
                .map(value -> !value.isBlank())
                .orElse(false))
            || document.get("osu.userId", Number.class)
            .map(value -> value.longValue() > 0)
            .orElseGet(() -> document.get("osu.username", String.class)
                .map(value -> !value.isBlank())
                .orElse(false));
    }

    private record PlayerSnapshot(
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        boolean invulnerable,
        boolean collidable,
        Location returnLocation
    ) {
    }
}
