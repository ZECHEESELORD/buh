Stash Goals:
Store overflow so players never lose gifts; keep every transfer atomic to shut the door on dupes.

Access:
`BuhPlugin#stashService()` yields the `StashService`. It lives inside the `stash` module and persists inside the core player document under `inventory.stash` (collection `players`).

Depositing after failures:
1. Detect the failure or overflow condition in your feature logic.
2. Collect the `ItemStack`s that failed to fit.
3. Call `stashService.giveOrStash(player, items)` and chain logging as needed. The service already nudges the player with: `One or more items didn't fit in your inventory and were added to your item stash! Click here to pick them up!`

Example:
```java
plugin.stashService().ifPresent(stash -> {
    stash.giveOrStash(player, List.of(rewardItem))
        .whenComplete((result, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().severe("Stash deposit fizzled for " + player.getUniqueId());
                return;
            }
        });
});
```

Viewing and pickup for debugging:
Use `/viewstash` to print the stored stacks and `/pickupstash` to pull as many as the inventory allows. Both commands run via the stash service and are safe for concurrency testing.

Reminders:
If items linger in a stash, the player sees a centered two line prompt every minute with a clickable pickup call so nobody forgets their loot.

Notes:
The service clones incoming stacks before persisting. Inventory insertion happens on the main thread and is guarded with per player locks so concurrent deposits or pickups resolve without dupes. Keep long or blocking work off the main thread and let the completion stages tell you when disk writes finished. The persisted layout is the player document with `inventory.stash` containing a list of serialized `ItemStack` maps.***
