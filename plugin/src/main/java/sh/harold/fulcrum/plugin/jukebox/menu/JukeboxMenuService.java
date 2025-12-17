package sh.harold.fulcrum.plugin.jukebox.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.menu.CustomMenuBuilder;
import sh.harold.fulcrum.api.menu.Menu;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuDisplayItem;
import sh.harold.fulcrum.api.menu.component.MenuItem;
import sh.harold.fulcrum.api.menu.impl.DefaultCustomMenu;
import sh.harold.fulcrum.plugin.jukebox.JukeboxConfig;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackFiles;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackMetadata;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackStatus;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackValidation;
import sh.harold.fulcrum.plugin.jukebox.JukeboxTrackValidator;
import sh.harold.fulcrum.plugin.jukebox.disc.JukeboxDiscService;
import sh.harold.fulcrum.plugin.jukebox.mint.JukeboxMintService;
import sh.harold.fulcrum.plugin.jukebox.mint.JukeboxMintTokenFile;
import sh.harold.fulcrum.plugin.jukebox.mint.JukeboxPlayerSlots;
import sh.harold.fulcrum.plugin.jukebox.mint.JukeboxTokenStore;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

public final class JukeboxMenuService {

    private static final int MENU_ROWS = 6;
    private static final long POLL_PERIOD_TICKS = 40L;
    private static final int INFO_SLOT = 4;
    private static final int VOICECHAT_STATUS_SLOT = 8;
    private static final int TRACK_SLOT = 22;
    private static final int CLOSE_SLOT = 49;
    private static final int REFRESH_SLOT = 53;

    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final MenuService menuService;
    private final JukeboxConfig config;
    private final JukeboxMintService mintService;
    private final JukeboxDiscService discService;
    private final sh.harold.fulcrum.plugin.jukebox.JukeboxTrackReader trackReader;
    private final JukeboxTokenStore tokenStore;

    public JukeboxMenuService(
        org.bukkit.plugin.java.JavaPlugin plugin,
        MenuService menuService,
        JukeboxConfig config,
        JukeboxMintService mintService,
        JukeboxDiscService discService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.menuService = Objects.requireNonNull(menuService, "menuService");
        this.config = Objects.requireNonNull(config, "config");
        this.mintService = Objects.requireNonNull(mintService, "mintService");
        this.discService = Objects.requireNonNull(discService, "discService");
        this.trackReader = new sh.harold.fulcrum.plugin.jukebox.JukeboxTrackReader();
        this.tokenStore = new JukeboxTokenStore(config.tokenDirectory());
    }

    public CompletionStage<Void> open(Player player) {
        Objects.requireNonNull(player, "player");
        UUID ownerUuid = player.getUniqueId();
        return mintService.loadSlots(ownerUuid)
            .thenApply(this::slotView)
            .thenCompose(view -> openOnMainThread(player, view))
            .exceptionally(throwable -> {
                plugin.getLogger().log(Level.WARNING, "Failed to open jukebox menu for " + ownerUuid, throwable);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.sendMessage(Component.text("Could not open the jukebox menu right now.", NamedTextColor.RED));
                });
                return null;
            });
    }

    private CompletionStage<Void> openOnMainThread(Player player, SlotView view) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> openWithView(player, view).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
            } else {
                future.complete(null);
            }
        }));
        return future;
    }

    private CompletionStage<Void> openWithView(Player player, SlotView view) {
        CustomMenuBuilder builder = menuService.createMenuBuilder()
            .title(Component.text("Jukebox"))
            .viewPort(MENU_ROWS)
            .rows(MENU_ROWS)
            .autoCloseButton(false)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE);

        decorate(builder);

        builder.addItem(infoItem(), INFO_SLOT);
        builder.addItem(voiceChatStatusItem(), VOICECHAT_STATUS_SLOT);
        builder.addButton(trackButton(view));
        builder.addButton(closeButton());
        builder.addButton(refreshButton());

        return builder.buildAsync(player)
            .thenAccept(menu -> attachPolling(menu, player.getUniqueId()));
    }

    private void decorate(CustomMenuBuilder builder) {
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_13), 0);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_CAT), 1);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_BLOCKS), 2);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_CHIRP), 3);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_FAR), 5);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_MALL), 6);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_STAL), 7);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_STRAD), 9);
        builder.addItem(decorativeDisc(Material.MUSIC_DISC_WARD), 10);
    }

    private MenuItem decorativeDisc(Material material) {
        return MenuDisplayItem.builder(material)
            .name(" ")
            .build();
    }

    private MenuItem infoItem() {
        return MenuDisplayItem.builder(Material.BOOK)
            .name("&eHow It Works")
            .secondary("Jukebox")
            .description("Mint a track to receive a one time upload link. Upload your audio; the worker transcodes it into a 48 kHz mono PCM stream. Once ready, click your track to press a disc.")
            .build();
    }

    private MenuItem voiceChatStatusItem() {
        boolean available = plugin.getServer().getPluginManager().getPlugin("voicechat") != null;
        if (!available) {
            return MenuDisplayItem.builder(Material.GRAY_DYE)
                .name("&7Voice Chat")
                .secondary("Playback")
                .description("Simple Voice Chat is not installed. Minting still works, but you will not hear tracks in game.")
                .build();
        }

        return MenuDisplayItem.builder(Material.LIME_DYE)
            .name("&aVoice Chat")
            .secondary("Playback")
            .description("Simple Voice Chat is installed. Jukeboxes can stream minted tracks to nearby listeners.")
            .build();
    }

    private MenuButton closeButton() {
        return MenuButton.builder(Material.BARRIER)
            .name("&cClose")
            .secondary("Jukebox")
            .description("Close this menu and return to the world.")
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(CLOSE_SLOT)
            .onClick(Player::closeInventory)
            .build();
    }

    private MenuButton refreshButton() {
        return MenuButton.builder(Material.SUNFLOWER)
            .name("&bRefresh")
            .secondary("Jukebox")
            .description("Pull the latest status from the worker.")
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(REFRESH_SLOT)
            .onClick(this::refreshOpenMenu)
            .build();
    }

    private void refreshOpenMenu(Player player) {
        Menu menu = menuService.getOpenMenu(player).orElse(null);
        if (menu == null) {
            return;
        }
        refreshMenu(menu, player.getUniqueId());
    }

    private void attachPolling(Menu menu, UUID ownerUuid) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> refreshMenu(menu, ownerUuid), POLL_PERIOD_TICKS, POLL_PERIOD_TICKS);
        menu.onClose(task::cancel);
    }

    private void refreshMenu(Menu menu, UUID ownerUuid) {
        if (!menu.isOpen()) {
            return;
        }
        mintService.loadSlots(ownerUuid)
            .thenApply(this::slotView)
            .thenAccept(view -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!menu.isOpen()) {
                    return;
                }
                if (menu instanceof DefaultCustomMenu customMenu) {
                    customMenu.setButton(trackButton(view), TRACK_SLOT);
                }
            }));
    }

    private SlotView slotView(JukeboxPlayerSlots slots) {
        long now = Instant.now().getEpochSecond();
        String trackId = slots.slots().isEmpty() ? null : slots.slots().getFirst();
        if (trackId == null || trackId.isBlank()) {
            return SlotView.empty();
        }

        JukeboxTrackFiles trackFiles = JukeboxTrackFiles.forTrack(config.tracksDirectory(), trackId);
        JukeboxTrackMetadata metadata;
        try {
            metadata = trackReader.read(trackFiles.jsonPath()).orElse(null);
        } catch (IOException exception) {
            return SlotView.error(trackId, "Track metadata unreadable.");
        }
        if (metadata == null) {
            return SlotView.error(trackId, "Track metadata missing.");
        }
        if (!slots.ownerUuid().equals(metadata.ownerUuid())) {
            return SlotView.error(trackId, "Track ownership mismatch.");
        }
        if (!trackId.equals(metadata.trackId())) {
            return SlotView.error(trackId, "Track ID mismatch.");
        }

        if (metadata.status() == JukeboxTrackStatus.READY) {
            JukeboxTrackValidation validation = JukeboxTrackValidator.validateReady(metadata, config, trackFiles.pcmPath());
            if (!validation.valid()) {
                return SlotView.error(trackId, validation.message().isBlank() ? "Track is not playable." : validation.message());
            }
        }

        String uploadUrl = "";
        long expiresAt = 0L;
        if (metadata.status() == JukeboxTrackStatus.WAITING_UPLOAD) {
            try {
                JukeboxMintTokenFile tokenFile = tokenStore.load(trackId).orElse(null);
                if (tokenFile != null && !tokenFile.isExpired(now) && !tokenFile.isUsed()) {
                    uploadUrl = config.uploadUrlTemplate()
                        .replace("{trackId}", trackId)
                        .replace("{token}", tokenFile.token());
                    expiresAt = tokenFile.expiresAtEpochSeconds();
                }
            } catch (IOException ignored) {
            }
        }

        return new SlotView(trackId, metadata, uploadUrl, expiresAt, "");
    }

    private MenuButton trackButton(SlotView view) {
        if (view.trackId == null) {
            return MenuButton.builder(Material.MUSIC_DISC_13)
                .name("&aMint Track")
                .secondary("Minting")
                .description("Mint a new track to receive an upload link. Everyone can store one track for now.")
                .sound(Sound.UI_BUTTON_CLICK)
                .slot(TRACK_SLOT)
                .onClick(viewer -> mint(viewer, 0))
                .build();
        }

        if (view.metadata == null) {
            return MenuButton.builder(Material.BARRIER)
                .name("&cTrack: Broken")
                .secondary("Minting")
                .description(view.problem.isBlank() ? "This slot points at a missing track. Clear it and mint again." : view.problem)
                .sound(Sound.UI_BUTTON_CLICK)
                .slot(TRACK_SLOT)
                .requireConfirmation("&cClick again to clear this slot.")
                .onClick(viewer -> clearSlot(viewer, 0))
                .build();
        }

        String title = view.metadata.titleText().orElse("Minted Track");
        return switch (view.metadata.status()) {
            case WAITING_UPLOAD -> waitingUploadButton(view, title);
            case PROCESSING -> processingButton(view, title);
            case READY -> readyButton(view, title);
            case REJECTED -> failedButton(view, title, "This track was rejected. Clear the slot and try again.");
            case FAILED -> failedButton(view, title, "This track failed. Clear the slot and try again.");
        };
    }

    private MenuButton waitingUploadButton(SlotView view, String title) {
        String uploadHint = view.uploadUrl.isBlank()
            ? "The upload token is missing or expired. Clear the slot and mint again."
            : "Click to print your upload link to chat.";

        MenuButton.Builder builder = MenuButton.builder(Material.PAPER)
            .name(Component.text(title, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .secondary("Waiting for upload")
            .description(uploadHint + " Shift right click cancels the mint.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(TRACK_SLOT)
            .onClick(viewer -> printUploadLink(viewer, view))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> cancelMint(viewer, 0));

        if (view.expiresAtEpochSeconds > 0) {
            builder.lore("Expires at " + Instant.ofEpochSecond(view.expiresAtEpochSeconds) + ".");
        }

        return builder.build();
    }

    private MenuButton processingButton(SlotView view, String title) {
        return MenuButton.builder(Material.CLOCK)
            .name(Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .secondary("Processing")
            .description("The worker is transcoding your track. Sit tight; it should be quick, but it does not rush for anyone. Shift right click cancels the mint.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(TRACK_SLOT)
            .onClick(viewer -> viewer.sendMessage(Component.text("Still processing.", NamedTextColor.YELLOW)))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> cancelMint(viewer, 0))
            .build();
    }

    private MenuButton readyButton(SlotView view, String title) {
        String duration = view.metadata == null ? "" : formatDuration(view.metadata.durationSeconds());
        String durationLine = duration.isBlank() ? null : "&7Duration: &f" + duration;

        MenuButton.Builder builder = MenuButton.builder(Material.JUKEBOX)
            .name(Component.text(title, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            .secondary("Playback")
            .description("Click to press a disc. Put it in a jukebox to stream it through voice chat. Shift right click clears the slot.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(TRACK_SLOT)
            .onClick(viewer -> giveDisc(viewer, view))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> clearSlot(viewer, 0));

        if (durationLine != null) {
            builder.lore(durationLine);
        }

        return builder.build();
    }

    private MenuButton failedButton(SlotView view, String title, String message) {
        return MenuButton.builder(Material.BARRIER)
            .name(Component.text(title, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            .secondary("Minting")
            .description(message)
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(TRACK_SLOT)
            .requireConfirmation("&cClick again to clear this slot.")
            .onClick(viewer -> clearSlot(viewer, 0))
            .build();
    }

    private void mint(Player player, int slotIndex) {
        UUID ownerUuid = player.getUniqueId();
        mintService.mint(ownerUuid, slotIndex).thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!result.minted()) {
                player.sendMessage(Component.text(result.message().isBlank() ? "Minting failed." : result.message(), NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Mint started. Upload here:", NamedTextColor.GREEN));
            player.sendMessage(Component.text(result.uploadUrl(), NamedTextColor.AQUA));
            refreshOpenMenu(player);
        }));
    }

    private void cancelMint(Player player, int slotIndex) {
        clearSlot(player, slotIndex);
    }

    private void clearSlot(Player player, int slotIndex) {
        UUID ownerUuid = player.getUniqueId();
        mintService.clearSlot(ownerUuid, slotIndex).thenAccept(cleared -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!cleared) {
                player.sendMessage(Component.text("Could not clear that slot.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Slot cleared.", NamedTextColor.GREEN));
            refreshOpenMenu(player);
        }));
    }

    private void printUploadLink(Player player, SlotView view) {
        if (view.uploadUrl.isBlank()) {
            player.sendMessage(Component.text("No upload link available. Clear the slot and mint again.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Upload link:", NamedTextColor.GREEN));
        player.sendMessage(Component.text(view.uploadUrl, NamedTextColor.AQUA));
    }

    private void giveDisc(Player player, SlotView view) {
        if (view.trackId == null) {
            return;
        }
        String title = Optional.ofNullable(view.metadata)
            .flatMap(JukeboxTrackMetadata::titleText)
            .orElse("Minted Disc");
        ItemStack disc = discService.createDisc(view.trackId, title);
        java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(disc);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("Disc created.", NamedTextColor.GREEN));
    }

    private static String formatDuration(double seconds) {
        if (seconds <= 0D) {
            return "";
        }
        long totalSeconds = Math.round(seconds);
        Duration duration = Duration.ofSeconds(totalSeconds);
        long minutes = duration.toMinutes();
        long remainingSeconds = duration.minusMinutes(minutes).toSeconds();
        if (minutes <= 0) {
            return remainingSeconds + "s";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private record SlotView(
        String trackId,
        JukeboxTrackMetadata metadata,
        String uploadUrl,
        long expiresAtEpochSeconds,
        String problem
    ) {
        static SlotView empty() {
            return new SlotView(null, null, "", 0L, "");
        }

        static SlotView error(String trackId, String message) {
            return new SlotView(trackId, null, "", 0L, message == null ? "" : message);
        }
    }
}

