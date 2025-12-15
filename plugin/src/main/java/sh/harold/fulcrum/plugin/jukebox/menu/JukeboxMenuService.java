package sh.harold.fulcrum.plugin.jukebox.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.fulcrum.api.menu.Menu;
import sh.harold.fulcrum.api.menu.MenuService;
import sh.harold.fulcrum.api.menu.component.MenuButton;
import sh.harold.fulcrum.api.menu.component.MenuItem;
import sh.harold.fulcrum.api.menu.impl.DefaultListMenu;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class JukeboxMenuService {

    private static final int MENU_ROWS = 6;
    private static final int CONTENT_END_SLOT = 44;
    private static final long POLL_PERIOD_TICKS = 40L;
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
            .thenApply(this::slotViews)
            .thenCompose(views -> openOnMainThread(player, views))
            .exceptionally(throwable -> {
                plugin.getLogger().warning("Failed to open jukebox menu for " + ownerUuid);
                return null;
            });
    }

    private CompletionStage<Void> openOnMainThread(Player player, List<SlotView> views) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> openWithViews(player, views).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                future.completeExceptionally(throwable);
            } else {
                future.complete(null);
            }
        }));
        return future;
    }

    private CompletionStage<Void> openWithViews(Player player, List<SlotView> views) {
        List<MenuItem> buttons = slotButtons(views);

        MenuButton closeButton = MenuButton.builder(Material.BARRIER)
            .name("&cClose")
            .secondary("Jukebox")
            .description("Close this menu and get back to the world.")
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(CLOSE_SLOT)
            .onClick(Player::closeInventory)
            .build();

        MenuButton refreshButton = MenuButton.builder(Material.SUNFLOWER)
            .name("&bRefresh")
            .secondary("Jukebox")
            .description("Pull the latest status from the worker.")
            .sound(Sound.UI_BUTTON_CLICK)
            .slot(REFRESH_SLOT)
            .onClick(viewer -> refreshOpenMenu(viewer))
            .build();

        return menuService.createListMenu()
            .title(Component.text("Jukebox"))
            .rows(MENU_ROWS)
            .contentSlots(0, CONTENT_END_SLOT)
            .fillEmpty(Material.BLACK_STAINED_GLASS_PANE, " ")
            .addButton(closeButton)
            .addButton(refreshButton)
            .addItems(buttons)
            .buildAsync(player)
            .thenCompose(menu -> {
                attachPolling(menu, player.getUniqueId());
                return CompletableFuture.completedFuture(null);
            });
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
            .thenApply(this::slotViews)
            .thenAccept(views -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!menu.isOpen()) {
                    return;
                }
                Player viewer = menu.getViewer().orElse(null);
                if (viewer == null) {
                    return;
                }
                if (!(menu instanceof DefaultListMenu listMenu)) {
                    return;
                }
                listMenu.clearContentItems();
                listMenu.addContentItems(slotButtons(views));
                menu.update();
            }));
    }

    private List<SlotView> slotViews(JukeboxPlayerSlots slots) {
        List<SlotView> views = new ArrayList<>(slots.slots().size());
        long now = Instant.now().getEpochSecond();
        for (int index = 0; index < slots.slots().size(); index++) {
            String trackId = slots.slots().get(index);
            if (trackId == null || trackId.isBlank()) {
                views.add(SlotView.empty(index));
                continue;
            }

            JukeboxTrackFiles trackFiles = JukeboxTrackFiles.forTrack(config.tracksDirectory(), trackId);
            JukeboxTrackMetadata metadata;
            try {
                metadata = trackReader.read(trackFiles.jsonPath()).orElse(null);
            } catch (IOException exception) {
                views.add(SlotView.error(index, trackId, "Track metadata unreadable."));
                continue;
            }
            if (metadata == null) {
                views.add(SlotView.error(index, trackId, "Track metadata missing."));
                continue;
            }
            if (!slots.ownerUuid().equals(metadata.ownerUuid())) {
                views.add(SlotView.error(index, trackId, "Track ownership mismatch."));
                continue;
            }
            if (!trackId.equals(metadata.trackId())) {
                views.add(SlotView.error(index, trackId, "Track ID mismatch."));
                continue;
            }

            if (metadata.status() == JukeboxTrackStatus.READY) {
                JukeboxTrackValidation validation = JukeboxTrackValidator.validateReady(metadata, config, trackFiles.pcmPath());
                if (!validation.valid()) {
                    views.add(SlotView.error(index, trackId, validation.message().isBlank() ? "Track is not playable." : validation.message()));
                    continue;
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

            views.add(new SlotView(index, trackId, metadata, uploadUrl, expiresAt, ""));
        }
        return views;
    }

    private List<MenuItem> slotButtons(List<SlotView> views) {
        List<MenuItem> items = new ArrayList<>(views.size());
        for (SlotView view : views) {
            items.add(slotButton(view));
        }
        return items;
    }

    private MenuItem slotButton(SlotView view) {
        if (view.trackId == null) {
            return MenuButton.builder(Material.MUSIC_DISC_13)
                .name("&aEmpty Slot #" + (view.slotIndex + 1))
                .secondary("Minting")
                .description("Mint a new track for this slot. You will get an upload link, then the worker will transcode it for voice chat.")
                .sound(Sound.UI_BUTTON_CLICK)
                .onClick(viewer -> mint(viewer, view.slotIndex))
                .build();
        }

        if (view.metadata == null) {
            return MenuButton.builder(Material.BARRIER)
                .name("&cSlot #" + (view.slotIndex + 1) + ": Broken")
                .secondary("Minting")
                .description(view.problem.isBlank() ? "This slot points at a missing track. Clear it and mint again." : view.problem)
                .sound(Sound.UI_BUTTON_CLICK)
                .requireConfirmation("&cClick again to clear this slot.")
                .onClick(viewer -> clearSlot(viewer, view.slotIndex))
                .build();
        }

        String title = view.metadata.titleText().orElse("Slot #" + (view.slotIndex + 1));
        return switch (view.metadata.status()) {
            case WAITING_UPLOAD -> waitingUploadButton(view, title);
            case PROCESSING -> processingButton(view, title);
            case READY -> readyButton(view, title);
            case REJECTED -> failedButton(view, title, "This track was rejected. Clear the slot and try again.");
            case FAILED -> failedButton(view, title, "This track failed. Clear the slot and try again.");
        };
    }

    private MenuItem waitingUploadButton(SlotView view, String title) {
        String uploadHint = view.uploadUrl.isBlank()
            ? "The upload token is missing or expired. Clear the slot and mint again."
            : "Click to print your upload link to chat.";

        MenuButton.Builder builder = MenuButton.builder(Material.PAPER)
            .name(Component.text(title, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .secondary("Waiting for upload")
            .description(uploadHint + " Shift right click cancels the mint.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> printUploadLink(viewer, view))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> cancelMint(viewer, view.slotIndex));

        if (view.expiresAtEpochSeconds > 0) {
            builder.lore("Expires at " + Instant.ofEpochSecond(view.expiresAtEpochSeconds) + ".");
        }

        return builder.build();
    }

    private MenuItem processingButton(SlotView view, String title) {
        return MenuButton.builder(Material.CLOCK)
            .name(Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .secondary("Processing")
            .description("The worker is transcoding your track. Sit tight; it should be quick, but it does not rush for anyone. Shift right click cancels the mint.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> viewer.sendMessage(Component.text("Still processing.", NamedTextColor.YELLOW)))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> cancelMint(viewer, view.slotIndex))
            .build();
    }

    private MenuItem readyButton(SlotView view, String title) {
        String duration = view.metadata == null ? "" : formatDuration(view.metadata.durationSeconds());
        String durationLine = duration.isBlank() ? null : "&7Duration: &f" + duration;

        MenuButton.Builder builder = MenuButton.builder(Material.JUKEBOX)
            .name(Component.text(title, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            .secondary("Playback")
            .description("Click to press a disc. Put it in a jukebox to stream it through voice chat. Shift right click clears the slot.")
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .onClick(viewer -> giveDisc(viewer, view))
            .onClick(org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, viewer -> clearSlot(viewer, view.slotIndex));

        if (durationLine != null) {
            builder.lore(durationLine);
        }

        return builder.build();
    }

    private MenuItem failedButton(SlotView view, String title, String message) {
        return MenuButton.builder(Material.BARRIER)
            .name(Component.text(title, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
            .secondary("Minting")
            .description(message)
            .lore("&7Track ID: &f" + view.trackId)
            .sound(Sound.UI_BUTTON_CLICK)
            .requireConfirmation("&cClick again to clear this slot.")
            .onClick(viewer -> clearSlot(viewer, view.slotIndex))
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
        int slotIndex,
        String trackId,
        JukeboxTrackMetadata metadata,
        String uploadUrl,
        long expiresAtEpochSeconds,
        String problem
    ) {
        static SlotView empty(int slotIndex) {
            return new SlotView(slotIndex, null, null, "", 0L, "");
        }

        static SlotView error(int slotIndex, String trackId, String message) {
            return new SlotView(slotIndex, trackId, null, "", 0L, message == null ? "" : message);
        }
    }

}
