package sh.harold.fulcrum.plugin.stash;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StashService implements AutoCloseable {

    private static final Duration SYNC_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> SUPPORTED_ITEM_KEYS = Set.of(
        "id",
        "count",
        "components",
        "DataVersion",
        "schema_version",
        "=="
    );

    private final JavaPlugin plugin;
    private final DocumentCollection players;
    private final ExecutorService executor;
    private final Map<UUID, Lock> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private final Logger logger;

    StashService(JavaPlugin plugin, DataApi dataApi) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(dataApi, "dataApi").collection("players");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.logger = plugin.getLogger();
    }

    public CompletionStage<StashView> view(UUID playerId) {
        return runLocked(playerId, () -> {
            Document document = loadDocument(playerId);
            List<ItemStack> items = readItems(document);
            return new StashView(items);
        });
    }

    public CompletionStage<PickupResult> pickup(Player player) {
        UUID playerId = player.getUniqueId();
        return runLocked(playerId, () -> {
            Document document = loadDocument(playerId);
            List<ItemStack> stash = readItems(document);
            if (stash.isEmpty()) {
                return new PickupResult(0, 0, 0);
            }

            List<ItemStack> remaining = new ArrayList<>();
            int movedStacks = 0;
            int movedItems = 0;

            for (ItemStack stack : stash) {
                OfferOutcome outcome = offerToInventory(player, stack);
                if (outcome.insertedAmount() > 0) {
                    movedStacks++;
                    movedItems += outcome.insertedAmount();
                }
                if (outcome.leftover() != null && outcome.leftover().getAmount() > 0) {
                    remaining.add(outcome.leftover());
                }
            }

            persist(document, remaining);
            return new PickupResult(movedStacks, movedItems, remaining.size());
        });
    }

    public CompletionStage<StashDepositResult> giveOrStash(Player player, Collection<ItemStack> items) {
        UUID playerId = player.getUniqueId();
        List<ItemStack> sanitized = sanitize(items);
        if (sanitized.isEmpty()) {
            return CompletableFuture.completedFuture(new StashDepositResult(0, 0));
        }

        return runLocked(playerId, () -> {
            Document document = loadDocument(playerId);
            List<ItemStack> stash = readItems(document);
            int stashedStacks = 0;
            int stashedItems = 0;

            for (ItemStack stack : sanitized) {
                OfferOutcome outcome = offerToInventory(player, stack);
                ItemStack leftover = outcome.leftover();
                if (leftover != null && leftover.getAmount() > 0) {
                    stash.add(leftover);
                    stashedStacks++;
                    stashedItems += leftover.getAmount();
                }
            }

            if (stashedStacks > 0) {
                persist(document, stash);
            }
            return new StashDepositResult(stashedStacks, stashedItems);
        });
    }

    public CompletionStage<StashDepositResult> stash(Player player, Collection<ItemStack> items) {
        UUID playerId = player.getUniqueId();
        List<ItemStack> sanitized = sanitize(items);
        if (sanitized.isEmpty()) {
            return CompletableFuture.completedFuture(new StashDepositResult(0, 0));
        }

        return runLocked(playerId, () -> {
            Document document = loadDocument(playerId);
            List<ItemStack> stash = readItems(document);

            int stashedStacks = 0;
            int stashedItems = 0;

            for (ItemStack stack : sanitized) {
                stash.add(stack);
                stashedStacks++;
                stashedItems += stack.getAmount();
            }

            persist(document, stash);
            return new StashDepositResult(stashedStacks, stashedItems);
        });
    }

    @Override
    public void close() {
        executor.close();
    }

    private Document loadDocument(UUID playerId) {
        return players.load(playerId.toString()).toCompletableFuture().join();
    }

    private List<ItemStack> readItems(Document document) {
        List<ItemStack> items = new ArrayList<>();
        List<?> raw = document.get("inventory.stash", List.class).orElse(List.of());
        for (Object entry : raw) {
            ItemStack item = deserialize(entry);
            if (item != null && item.getAmount() > 0) {
                items.add(item);
            }
        }
        return items;
    }

    private void persist(Document document, List<ItemStack> items) {
        List<Map<String, Object>> serialized = items.stream()
            .map(this::serialize)
            .toList();
        document.set("inventory.stash", serialized).toCompletableFuture().join();
    }

    private <T> CompletionStage<T> runLocked(UUID playerId, java.util.function.Supplier<T> task) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(task, "task");
        Lock lock = locks.computeIfAbsent(playerId, ignored -> new ReentrantLock());
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                return task.get();
            } finally {
                lock.unlock();
            }
        }, executor);
    }

    private OfferOutcome offerToInventory(Player player, ItemStack original) {
        ItemStack toOffer = Objects.requireNonNull(original, "original").clone();
        int offeredAmount = toOffer.getAmount();

        Map<Integer, ItemStack> leftover = callSync(() -> player.getInventory().addItem(toOffer));
        int leftoverAmount = leftover.values().stream()
            .mapToInt(ItemStack::getAmount)
            .sum();

        ItemStack remaining = null;
        if (leftoverAmount > 0) {
            remaining = leftover.values().stream()
                .findFirst()
                .map(ItemStack::clone)
                .orElseGet(original::clone);
            remaining.setAmount(leftoverAmount);
        }

        int inserted = Math.max(offeredAmount - leftoverAmount, 0);
        return new OfferOutcome(inserted, remaining);
    }

    private Map<String, Object> serialize(ItemStack item) {
        return new java.util.LinkedHashMap<>(item.serialize());
    }

    private ItemStack deserialize(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        Object amount = copy.remove("amount");
        if (!copy.containsKey("count")) {
            switch (amount) {
                case Number number -> copy.put("count", number.intValue());
                case String value -> {
                    try {
                        copy.put("count", Integer.parseInt(value));
                    } catch (NumberFormatException ignored) {
                        // leave count absent when parsing fails
                    }
                }
                default -> {
                }
            }
        }
        Map<String, Object> sanitized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : copy.entrySet()) {
            if (SUPPORTED_ITEM_KEYS.contains(entry.getKey())) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        try {
            return ItemStack.deserialize(sanitized);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logger.log(Level.WARNING, "Failed to deserialize stashed item", exception);
            return null;
        }
    }

    private List<ItemStack> sanitize(Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ItemStack> sanitized = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            sanitized.add(item.clone());
        }
        return sanitized;
    }

    private <T> T callSync(java.util.concurrent.Callable<T> callable) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));

        try {
            return future.orTimeout(SYNC_TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Failed to complete synchronous stash operation", cause);
        }
    }

    private record OfferOutcome(int insertedAmount, ItemStack leftover) {
    }
}
