.Economy Module:
Balances ride through the `economy` module. Grab the `EconomyService` with `BuhPlugin#economyService()` and let it handle persistence and locking for you.

Storage:
Player balances live in the `players` collection at `economy.balance` as a long of whole coins. Reads clamp any negative stray values to zero. Writes flow through the document API so the JSON store keeps using atomic swaps on disk.

API:
1. `BalanceSnapshot balance(UUID playerId)` loads the current view.
2. `MoneyChange deposit(UUID playerId, long amount)` raises the balance and refuses zero or overflow.
3. `MoneyChange withdraw(UUID playerId, long amount)` debits when funds exist; otherwise it returns `MoneyChange.InsufficientFunds`.
4. `TransferResult transfer(UUID sourceId, UUID targetId, long amount)` moves value between accounts with ordered locks and a best effort rollback when the target write fails.

Transaction Guarantees:
Per player locks isolate each mutation; paired locks sort by UUID so a transfer cannot deadlock. Every write uses the document store, which performs atomic file moves for durability. If the second leg of a transfer blows up, the source account rolls back and a severe log marks the incident if the rollback falters.

Example:
```java
plugin.economyService().ifPresent(service -> {
    service.deposit(playerId, 50)
        .thenCompose(result -> service.transfer(playerId, friendId, 25))
        .whenComplete((transfer, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().severe("Economy flow hiccuped: " + throwable.getMessage());
                return;
            }
            switch (transfer) {
                case TransferResult.Success success -> plugin.getLogger().info("Sent coins; now at " + success.source().after().balance());
                case TransferResult.InsufficientFunds insufficient -> plugin.getLogger().info("Need more coins; balance " + insufficient.sourceBalance().balance());
                case TransferResult.Rejected rejected -> plugin.getLogger().warning(rejected.reason());
            }
        });
});
```
