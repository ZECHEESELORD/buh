package sh.harold.fulcrum.plugin.economy;

import sh.harold.fulcrum.common.data.DataApi;
import sh.harold.fulcrum.common.data.Document;
import sh.harold.fulcrum.common.data.DocumentCollection;
import sh.harold.fulcrum.common.data.ledger.LedgerEntry;
import sh.harold.fulcrum.common.data.ledger.LedgerRepository;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EconomyService implements AutoCloseable {

    private static final String BALANCE_PATH = "bank.shards";

    private final DocumentCollection players;
    private final LedgerRepository ledger;
    private final ExecutorService executor;
    private final Map<UUID, Lock> accountLocks = new ConcurrentHashMap<>();
    private final Logger logger;

    EconomyService(DataApi dataApi, Logger logger) {
        Objects.requireNonNull(dataApi, "dataApi");
        this.players = dataApi.collection("players");
        this.ledger = dataApi.ledger().orElse(null);
        this.logger = Objects.requireNonNull(logger, "logger");
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public CompletionStage<BalanceSnapshot> balance(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return runExclusive(playerId, () -> {
            Document document = loadAccount(playerId);
            long balance = readBalance(document);
            return new BalanceSnapshot(playerId, balance);
        });
    }

    public CompletionStage<MoneyChange> deposit(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        validateAmount(amount);
        return runExclusive(playerId, () -> {
            Document document = loadAccount(playerId);
            long currentBalance = readBalance(document);
            long updatedBalance = safeAdd(currentBalance, amount);
            persistBalance(document, updatedBalance);
            BalanceSnapshot before = new BalanceSnapshot(playerId, currentBalance);
            BalanceSnapshot after = new BalanceSnapshot(playerId, updatedBalance);
            appendLedger(playerId, LedgerEntry.LedgerType.DEPOSIT, amount, updatedBalance, "manual");
            return new MoneyChange.Success(new BalanceChange(before, after));
        });
    }

    public CompletionStage<MoneyChange> withdraw(UUID playerId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        validateAmount(amount);
        return runExclusive(playerId, () -> {
            Document document = loadAccount(playerId);
            long currentBalance = readBalance(document);
            if (currentBalance < amount) {
                return new MoneyChange.InsufficientFunds(new BalanceSnapshot(playerId, currentBalance));
            }
            long updatedBalance = currentBalance - amount;
            persistBalance(document, updatedBalance);
            BalanceSnapshot before = new BalanceSnapshot(playerId, currentBalance);
            BalanceSnapshot after = new BalanceSnapshot(playerId, updatedBalance);
            appendLedger(playerId, LedgerEntry.LedgerType.WITHDRAWAL, amount, updatedBalance, "manual");
            return new MoneyChange.Success(new BalanceChange(before, after));
        });
    }

    public CompletionStage<TransferResult> transfer(UUID sourcePlayerId, UUID targetPlayerId, long amount) {
        Objects.requireNonNull(sourcePlayerId, "sourcePlayerId");
        Objects.requireNonNull(targetPlayerId, "targetPlayerId");
        validateAmount(amount);
        if (sourcePlayerId.equals(targetPlayerId)) {
            return CompletableFuture.completedFuture(new TransferResult.Rejected("Source and target must differ"));
        }

        return runExclusive(sourcePlayerId, targetPlayerId, () -> {
            Document source = loadAccount(sourcePlayerId);
            Document target = loadAccount(targetPlayerId);

            long sourceBalance = readBalance(source);
            if (sourceBalance < amount) {
                return new TransferResult.InsufficientFunds(new BalanceSnapshot(sourcePlayerId, sourceBalance));
            }

            long targetBalance = readBalance(target);
            long updatedSource = sourceBalance - amount;
            long updatedTarget = safeAdd(targetBalance, amount);

            persistBalance(source, updatedSource);
            try {
                persistBalance(target, updatedTarget);
            } catch (RuntimeException exception) {
                rollback(source, sourceBalance, sourcePlayerId);
                throw exception;
            }
            appendLedger(sourcePlayerId, LedgerEntry.LedgerType.TRANSFER_OUT, amount, updatedSource, "to:" + targetPlayerId);
            appendLedger(targetPlayerId, LedgerEntry.LedgerType.TRANSFER_IN, amount, updatedTarget, "from:" + sourcePlayerId);

            BalanceChange withdrawn = new BalanceChange(
                new BalanceSnapshot(sourcePlayerId, sourceBalance),
                new BalanceSnapshot(sourcePlayerId, updatedSource)
            );
            BalanceChange deposited = new BalanceChange(
                new BalanceSnapshot(targetPlayerId, targetBalance),
                new BalanceSnapshot(targetPlayerId, updatedTarget)
            );
            return new TransferResult.Success(withdrawn, deposited);
        });
    }

    @Override
    public void close() {
        executor.close();
    }

    private Document loadAccount(UUID playerId) {
        return players.load(playerId.toString()).toCompletableFuture().join();
    }

    private long readBalance(Document document) {
        long raw = document.get(BALANCE_PATH, Number.class)
            .map(Number::longValue)
            .orElse(0L);
        return Math.max(raw, 0L);
    }

    private void persistBalance(Document document, long balance) {
        document.set(BALANCE_PATH, balance).toCompletableFuture().join();
    }

    private long safeAdd(long value, long delta) {
        try {
            long updated = Math.addExact(value, delta);
            if (updated < 0) {
                throw new IllegalArgumentException("Balance cannot be negative");
            }
            return updated;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Balance overflow for economy operation", exception);
        }
    }

    private void rollback(Document document, long previousBalance, UUID playerId) {
        try {
            persistBalance(document, previousBalance);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to roll back balance for " + playerId, exception);
        }
    }

    private void appendLedger(UUID playerId, LedgerEntry.LedgerType type, long amount, long balance, String source) {
        if (ledger == null) {
            return;
        }
        LedgerEntry entry = new LedgerEntry(playerId, type, amount, balance, source, java.time.Instant.now());
        ledger.append(entry).exceptionally(throwable -> {
            logger.log(Level.WARNING, "Failed to append ledger entry for " + playerId, throwable);
            return null;
        });
    }

    private Lock lockFor(UUID playerId) {
        return accountLocks.computeIfAbsent(playerId, ignored -> new ReentrantLock());
    }

    private <T> CompletionStage<T> runExclusive(UUID playerId, Supplier<T> task) {
        Lock lock = lockFor(playerId);
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                return task.get();
            } finally {
                lock.unlock();
            }
        }, executor);
    }

    private <T> CompletionStage<T> runExclusive(UUID firstPlayerId, UUID secondPlayerId, Supplier<T> task) {
        UUID primary = firstPlayerId.compareTo(secondPlayerId) <= 0 ? firstPlayerId : secondPlayerId;
        UUID secondary = primary.equals(firstPlayerId) ? secondPlayerId : firstPlayerId;
        Lock firstLock = lockFor(primary);
        Lock secondLock = lockFor(secondary);

        return CompletableFuture.supplyAsync(() -> {
            firstLock.lock();
            try {
                secondLock.lock();
                try {
                    return task.get();
                } finally {
                    secondLock.unlock();
                }
            } finally {
                firstLock.unlock();
            }
        }, executor);
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
