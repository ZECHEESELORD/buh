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

    public SomeFeature(FulcrumPlugin plugin) {
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
The plugin currently builds `DataApi.using(new JsonDocumentStore(dataPath, executor))`. To switch backends, construct another `DocumentStore` implementation and feed it to `DataApi.using(...)` inside `DataModule`. No feature code changes required.
