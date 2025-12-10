package sh.harold.fulcrum.plugin.unlockable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.economy.EconomyService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CosmeticPriceDropRefund {

    private static final String REFUND_FLAG_PATH = "progression.refunds.cosmetic-price-drop";
    private static final String UNLOCKABLES_ROOT = "progression.unlockables";
    private static final Map<UnlockableId, Long> REFUNDS = Map.of(
        UnlockableCatalog.SIT_ACTION, 419L,
        UnlockableCatalog.CRAWL_ACTION, 462L,
        UnlockableCatalog.RIDE_ACTION, 450L
    );

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final EconomyService economyService;
    private final UnlockableRegistry registry;
    private final Logger logger;
    private final AtomicInteger refundedPlayers = new AtomicInteger();
    private final AtomicLong refundedShards = new AtomicLong();

    CosmeticPriceDropRefund(DataApi dataApi, EconomyService economyService, UnlockableRegistry registry, JavaPlugin plugin, Logger logger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    CompletionStage<Void> run() { //TODO: REMOVE after price-drop refunds finish.
        logger.info("[refund:cosmetics] Starting cosmetic price-drop refund sweep...");
        return players.all()
            .thenCompose(documents -> {
                List<CompletableFuture<Void>> refunds = documents.stream()
                    .map(this::refundPlayer)
                    .map(CompletionStage::toCompletableFuture)
                    .toList();
                return CompletableFuture.allOf(refunds.toArray(CompletableFuture[]::new));
            })
            .whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    logger.log(Level.SEVERE, "[refund:cosmetics] Refund sweep failed", throwable);
                    return;
                }
                logger.info(() -> "[refund:cosmetics] Refunded " + refundedShards.get() + " shards to " + refundedPlayers.get() + " players.");
            });
    }

    private CompletionStage<Void> refundPlayer(Document document) {
        if (document.get(REFUND_FLAG_PATH, Boolean.class).orElse(false)) {
            return CompletableFuture.completedFuture(null);
        }
        UUID playerId;
        try {
            playerId = UUID.fromString(document.key().id());
        } catch (IllegalArgumentException exception) {
            logger.log(Level.FINE, "[refund:cosmetics] Skipping document with non-UUID id: " + document.key().id(), exception);
            return CompletableFuture.completedFuture(null);
        }
        RefundDetail detail = refundDetail(document);
        if (detail.total() <= 0L) {
            return document.set(REFUND_FLAG_PATH, true);
        }
        return economyService.deposit(playerId, detail.total())
            .handle((change, throwable) -> {
                if (throwable != null) {
                    throw new CompletionException(throwable);
                }
                return change;
            })
            .thenCompose(ignored -> document.set(REFUND_FLAG_PATH, true))
            .thenRun(() -> {
                refundedPlayers.incrementAndGet();
                refundedShards.addAndGet(detail.total());
                notifyPlayer(playerId, detail);
            })
            .exceptionally(throwable -> {
                logger.log(Level.WARNING, "[refund:cosmetics] Failed to refund " + detail.total() + " shards to " + playerId, throwable);
                return null;
            });
    }

    private RefundDetail refundDetail(Document document) {
        long total = 0L;
        List<String> names = new ArrayList<>();
        for (Map.Entry<UnlockableId, Long> entry : REFUNDS.entrySet()) {
            if (unlocked(document, entry.getKey())) {
                total += entry.getValue();
                names.add(registry.definition(entry.getKey())
                    .map(UnlockableDefinition::name)
                    .orElse(entry.getKey().value()));
            }
        }
        return new RefundDetail(total, List.copyOf(names));
    }

    private boolean unlocked(Document document, UnlockableId unlockableId) {
        String basePath = UNLOCKABLES_ROOT + ".cosmetics." + unlockableId.value() + ".tier";
        return document.get(basePath, Number.class)
            .or(() -> document.get(UNLOCKABLES_ROOT + "." + unlockableId.value() + ".tier", Number.class))
            .map(number -> number.intValue() > 0)
            .orElse(false);
    }

    private void notifyPlayer(UUID playerId, RefundDetail detail) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(playerId);
            if (player == null) {
                return;
            }
            Component message = Component.text()
                .append(Component.text("REFUND! ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("You have received a refund for ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(detail.total() + " shards", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false))
                .append(Component.text(" from ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(String.join(", ", detail.names()), NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false))
                .append(Component.text(".", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .build();
            player.sendMessage(message);
        });
    }

    private record RefundDetail(long total, List<String> names) {
    }
}
