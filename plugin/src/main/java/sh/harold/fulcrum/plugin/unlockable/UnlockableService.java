package sh.harold.fulcrum.plugin.unlockable;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.plugin.economy.EconomyService;
import sh.harold.fulcrum.plugin.economy.MoneyChange;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class UnlockableService {

    private static final String UNLOCKABLES_ROOT = "progression.unlockables";
    private static final String LEGACY_PERKS_ROOT = "progression.perks";

    private final UnlockableRegistry registry;
    private final DocumentCollection players;
    private final Map<UUID, PlayerUnlockableState> stateCache = new ConcurrentHashMap<>();
    private final Supplier<Optional<EconomyService>> economySupplier;
    private final Logger logger;

    public UnlockableService(DataApi dataApi, UnlockableRegistry registry, Supplier<Optional<EconomyService>> economySupplier, Logger logger) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.economySupplier = Objects.requireNonNull(economySupplier, "economySupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.players = dataApi.collection("players");
    }

    public CompletionStage<PlayerUnlockableState> loadState(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PlayerUnlockableState cached = stateCache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return players.load(playerId.toString())
            .thenApply(this::buildState)
            .thenApply(state -> {
                stateCache.put(playerId, state);
                return state;
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to load unlockables for " + playerId, throwable);
            });
    }

    public Optional<PlayerUnlockableState> cachedState(UUID playerId) {
        return Optional.ofNullable(stateCache.get(Objects.requireNonNull(playerId, "playerId")));
    }

    public void evict(UUID playerId) {
        stateCache.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public CompletionStage<Boolean> isUnlocked(UUID playerId, UnlockableId unlockableId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(unlockableId, "unlockableId");
        return loadState(playerId)
            .thenApply(state -> state.unlockable(unlockableId).map(PlayerUnlockable::unlocked).orElse(false));
    }

    public CompletionStage<Boolean> isEnabled(UUID playerId, UnlockableId unlockableId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(unlockableId, "unlockableId");
        return loadState(playerId)
            .thenApply(state -> state.unlockable(unlockableId).map(PlayerUnlockable::enabled).orElse(false));
    }

    public CompletionStage<PlayerUnlockable> unlockNextTier(UUID playerId, UnlockableId unlockableId) {
        Objects.requireNonNull(playerId, "playerId");
        UnlockableDefinition definition = requireDefinition(unlockableId);
        return players.load(playerId.toString())
            .thenCompose(document -> {
                PlayerUnlockable current = resolveUnlockable(document, definition);
                int nextTier = Math.min(current.tier() + 1, definition.maxTier());
                if (nextTier == current.tier()) {
                    return CompletableFuture.completedFuture(current);
                }
                boolean enabled = current.enabled() || current.tier() == 0;
                return unlockAndCharge(playerId, document, definition, current.tier(), nextTier, enabled);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to unlock " + unlockableId + " for " + playerId, throwable);
            });
    }

    public CompletionStage<PlayerUnlockable> unlockToTier(UUID playerId, UnlockableId unlockableId, int targetTier) {
        Objects.requireNonNull(playerId, "playerId");
        if (targetTier < 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Target tier must be at least 1"));
        }
        UnlockableDefinition definition = requireDefinition(unlockableId);
        int clampedTier = Math.min(targetTier, definition.maxTier());
        return players.load(playerId.toString())
            .thenCompose(document -> {
                PlayerUnlockable current = resolveUnlockable(document, definition);
                int nextTier = Math.max(current.tier(), clampedTier);
                if (nextTier == current.tier()) {
                    return CompletableFuture.completedFuture(current);
                }
                boolean enabled = current.enabled() || current.tier() == 0;
                return unlockAndCharge(playerId, document, definition, current.tier(), nextTier, enabled);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to unlock tier " + targetTier + " for " + unlockableId + " (" + playerId + ")", throwable);
            });
    }

    public CompletionStage<PlayerUnlockable> toggle(UUID playerId, UnlockableId unlockableId) {
        Objects.requireNonNull(playerId, "playerId");
        UnlockableDefinition definition = requireDefinition(unlockableId);
        return players.load(playerId.toString())
            .thenCompose(document -> {
                PlayerUnlockable current = resolveUnlockable(document, definition);
                if (!current.unlocked()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Unlockable is locked: " + unlockableId));
                }
                if (!definition.toggleable()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Unlockable cannot be toggled: " + unlockableId));
                }
                boolean enabled = !current.enabled();
                return persistUnlockable(document, playerId, definition, current.tier(), enabled);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to toggle unlockable " + unlockableId + " for " + playerId, throwable);
            });
    }

    public CompletionStage<PlayerUnlockable> setEnabled(UUID playerId, UnlockableId unlockableId, boolean enabled) {
        Objects.requireNonNull(playerId, "playerId");
        UnlockableDefinition definition = requireDefinition(unlockableId);
        return players.load(playerId.toString())
            .thenCompose(document -> {
                PlayerUnlockable current = resolveUnlockable(document, definition);
                if (!current.unlocked()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Unlockable is locked: " + unlockableId));
                }
                if (!definition.toggleable() && !enabled) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Unlockable cannot be disabled: " + unlockableId));
                }
                if (current.enabled() == enabled) {
                    return CompletableFuture.completedFuture(current);
                }
                return persistUnlockable(document, playerId, definition, current.tier(), enabled);
            })
            .exceptionally(throwable -> {
                throw new CompletionException("Failed to set unlockable state for " + unlockableId + " (" + playerId + ")", throwable);
            });
    }

    private UnlockableDefinition requireDefinition(UnlockableId unlockableId) {
        return registry.definition(unlockableId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown unlockable: " + unlockableId));
    }

    private CompletionStage<PlayerUnlockable> unlockAndCharge(
        UUID playerId,
        Document document,
        UnlockableDefinition definition,
        int currentTier,
        int nextTier,
        boolean enabled
    ) {
        long cost = costBetween(definition, currentTier, nextTier);
        return charge(playerId, cost).thenCompose(ignored ->
            persistUnlockable(document, playerId, definition, nextTier, enabled)
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return refundAndFail(playerId, cost, throwable);
                })
                .thenCompose(future -> future)
        );
    }

    private long costBetween(UnlockableDefinition definition, int currentTier, int targetTier) {
        if (targetTier <= currentTier) {
            return 0L;
        }
        long total = 0L;
        for (int tier = currentTier + 1; tier <= targetTier; tier++) {
            long tierCost = definition.tier(tier)
                .map(UnlockableTier::costInShards)
                .orElse(0L);
            total = Math.addExact(total, tierCost);
        }
        return total;
    }

    private CompletionStage<Void> charge(UUID playerId, long cost) {
        if (cost <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        Optional<EconomyService> economy = economySupplier.get();
        if (economy.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Shard economy unavailable for unlock cost of " + cost));
        }
        return economy.get().withdraw(playerId, cost).thenCompose(result -> switch (result) {
            case MoneyChange.Success ignored -> CompletableFuture.completedFuture(null);
            case MoneyChange.InsufficientFunds insufficient -> CompletableFuture.failedFuture(new IllegalStateException(
                "Need " + cost + " shards, have " + insufficient.balance().balance()
            ));
        });
    }

    private CompletionStage<PlayerUnlockable> persistUnlockable(
        Document document,
        UUID playerId,
        UnlockableDefinition definition,
        int tier,
        boolean enabled
    ) {
        PlayerUnlockable updated = new PlayerUnlockable(definition, tier, enabled);
        String basePath = unlockablePath(definition.id());
        CompletableFuture<Void> tierUpdate = document.set(basePath + ".tier", updated.tier()).toCompletableFuture();
        CompletableFuture<Void> enabledUpdate = document.set(basePath + ".enabled", updated.enabled()).toCompletableFuture();
        CompletableFuture<Void> legacyTierUpdate = document.set(legacyPerkPath(definition.id()) + ".tier", updated.tier()).toCompletableFuture();
        CompletableFuture<Void> legacyEnabledUpdate = document.set(legacyPerkPath(definition.id()) + ".enabled", updated.enabled()).toCompletableFuture();
        return CompletableFuture.allOf(tierUpdate, enabledUpdate, legacyTierUpdate, legacyEnabledUpdate)
            .thenApply(ignored -> cacheState(playerId, document).unlockable(definition.id()).orElse(updated));
    }

    private PlayerUnlockableState buildState(Document document) {
        Map<UnlockableId, PlayerUnlockable> unlockables = new LinkedHashMap<>();
        Map<?, ?> raw = document.get(UNLOCKABLES_ROOT, Map.class).orElse(Map.of());
        Map<?, ?> legacyRaw = document.get(LEGACY_PERKS_ROOT, Map.class).orElse(Map.of());

        for (UnlockableDefinition definition : registry.definitions()) {
            String basePath = unlockablePath(definition.id());
            PlayerUnlockable unlockable = parseUnlockable(raw, basePath, document, definition);
            if (!unlockable.unlocked()) {
                unlockable = parseUnlockable(legacyRaw, legacyPerkPath(definition.id()), document, definition);
            }
            unlockables.put(definition.id(), unlockable);
        }
        logUnknown(raw);
        logUnknown(legacyRaw);
        return new PlayerUnlockableState(unlockables);
    }

    private PlayerUnlockable parseUnlockable(Map<?, ?> raw, String basePath, Document document, UnlockableDefinition definition) {
        int tier = 0;
        boolean enabled = false;
        Object rawEntry = raw.get(definition.id().value());
        if (rawEntry instanceof Map<?, ?> map) {
            Object tierObject = map.get("tier");
            if (tierObject instanceof Number number) {
                tier = number.intValue();
            }
            Object enabledObject = map.get("enabled");
            if (enabledObject instanceof Boolean flag) {
                enabled = flag;
            } else if (tier > 0) {
                enabled = true;
            }
        } else {
            tier = document.get(basePath + ".tier", Number.class).map(Number::intValue).orElse(0);
            enabled = document.get(basePath + ".enabled", Boolean.class).orElse(tier > 0);
        }
        return new PlayerUnlockable(definition, tier, enabled);
    }

    private PlayerUnlockable resolveUnlockable(Document document, UnlockableDefinition definition) {
        String basePath = unlockablePath(definition.id());
        int tier = document.get(basePath + ".tier", Number.class)
            .map(Number::intValue)
            .orElse(0);
        boolean enabled = document.get(basePath + ".enabled", Boolean.class)
            .orElse(tier > 0);
        return new PlayerUnlockable(definition, tier, enabled);
    }

    private PlayerUnlockableState cacheState(UUID playerId, Document document) {
        PlayerUnlockableState state = buildState(document);
        stateCache.put(playerId, state);
        return state;
    }

    private void logUnknown(Map<?, ?> raw) {
        if (raw.isEmpty()) {
            return;
        }
        for (Object key : raw.keySet()) {
            if (key instanceof String unlockableKey) {
                try {
                    UnlockableId unlockableId = new UnlockableId(unlockableKey);
                    if (registry.definition(unlockableId).isEmpty()) {
                        logger.fine(() -> "Ignoring unknown unlockable entry in data: " + unlockableKey);
                    }
                } catch (IllegalArgumentException ignored) {
                    logger.fine(() -> "Ignoring malformed unlockable entry in data: " + unlockableKey);
                }
            }
        }
    }

    private String unlockablePath(UnlockableId unlockableId) {
        return UNLOCKABLES_ROOT + "." + unlockableId.value();
    }

    private String legacyPerkPath(UnlockableId unlockableId) {
        return LEGACY_PERKS_ROOT + "." + unlockableId.value();
    }

    private CompletionStage<PlayerUnlockable> refundAndFail(UUID playerId, long cost, Throwable cause) {
        if (cost <= 0) {
            return CompletableFuture.failedFuture(cause);
        }
        Optional<EconomyService> economy = economySupplier.get();
        if (economy.isEmpty()) {
            return CompletableFuture.failedFuture(cause);
        }
        return economy.get().deposit(playerId, cost)
            .handle((ignored, refundThrowable) -> {
                if (refundThrowable != null) {
                    logger.warning("Failed to refund " + cost + " shards to " + playerId + " after unlock failure: " + refundThrowable.getMessage());
                }
                throw cause instanceof CompletionException completionException ? completionException : new CompletionException(cause);
            });
    }
}
