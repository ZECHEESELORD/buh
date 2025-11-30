Data API Guide:
Feel free to skim; the API stays tiny yet nimble.

Purpose:
This layer wraps a single document store so features can swap storage without rewriting business code. The plugin wires a JSON-backed store by default, yet any other `DocumentStore` can slide in.

Core types:
1) `DataApi` creates and caches `DocumentCollection` instances; you close it when the plugin shuts down.
2) `DocumentCollection` targets one collection and produces `Document` instances.
3) `Document` reads and writes map-like content using dot paths (`settings.ui.color`).

Getting the API in the plugin:
```java
public final class SomeFeature {
    private final DataApi dataApi;

    public SomeFeature(BuhPlugin plugin) {
        this.dataApi = plugin.dataApi();
    }
}
```

Creating and loading documents:
```java
DataApi data = plugin.dataApi();
DocumentCollection players = data.collection("players");

// Create or overwrite a document
players.create("abcd-1234", Map.of(
    "settings", Map.of("sound", true),
    "inventory", List.of("apple", "torch")
)).toCompletableFuture().join();

// Load an existing document
Document playerDoc = players.load("abcd-1234").toCompletableFuture().join();
```

Reading data:
```java
Optional<Boolean> sound = playerDoc.get("settings.sound", Boolean.class);
Map<String, Object> snapshot = playerDoc.snapshot(); // deep copy
```

Writing data:
```java
// Set or replace any path; creates intermediate maps as needed
playerDoc.set("settings.language", "en_US").toCompletableFuture().join();

// Remove a path
playerDoc.remove("inventory").toCompletableFuture().join();

// Overwrite the entire document
playerDoc.overwrite(Map.of("settings", Map.of("hud", "minimal"))).toCompletableFuture().join();

// Apply multiple changes in one go to avoid extra I/O
playerDoc.update(snapshot -> {
    snapshot.put("meta.lastSeen", Instant.now().toString());
    snapshot.put("statistics.playtimeSeconds", ((Number) snapshot.getOrDefault("statistics.playtimeSeconds", 0L)).longValue() + 120);
    return snapshot;
}).toCompletableFuture().join();

// Or use the built-in patch helper to set/remove paths in one write
playerDoc.patch(
    Map.of(
        "meta.lastSeen", Instant.now().toString(),
        "meta.username", "NewName"
    ),
    List.of("legacy.osu.username", "legacy.osu.userId")
).toCompletableFuture().join();
```

Async usage:
Every mutator returns a `CompletionStage<Void>`; pair it with your executor as needed:
```java
players.load("abcd-1234")
    .thenCompose(doc -> doc.set("stats.kills", 12))
    .thenCompose(ignored -> players.count()) // also async
    .thenAccept(count -> plugin.getLogger().info("Player docs: " + count));
```

Backend swapping:
The plugin currently builds `DataApi.using(new MySqlDocumentStore(...))` by default. To switch backends, construct another `DocumentStore` implementation and feed it to `DataApi.using(...)` inside `DataModule`. No feature code changes required.

Best practices for hot paths:
- Batch writes: prefer `Document.patch(...)`, `Document.update(...)`, or a `DocumentPatch` helper to apply multiple changes in one call instead of chaining `set`/`remove`.
- Coalesce per-player mutations: load once, stage changes in memory, flush once (e.g., join/quit, link flows).
- Bulk operations: use `DocumentCollection.loadAll(...)`/`updateAll(...)` when touching many documents (e.g., mass flush on shutdown).
- Keep writes off the main thread: the API already returns async stages; avoid `.join()` on the server thread unless you know the call is fast or preloaded.
- Watch document size: large JSON blobs slow writes; keep hot data lean or split into dedicated tables if you need heavy querying (e.g., leaderboards).
- Metrics: `DataApi.metrics()` exposes per-operation counts/timing; surface these to your logs/telemetry to spot regressions.
