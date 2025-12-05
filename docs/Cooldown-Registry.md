# Cooldown Toolkit

So you want to add cooldowns. Great! Fulcrum provides a cooldown registry, so you can reference or share timers for certain cooldowns. For example, you want to link the cooldown between NPC interactions, and clicking menu buttons- this allows you to do that!

## When To Reach For The Registry
- You need to rate-limit an action across multiple services.
- Multiple player surfaces should share the same timer (menu button + slash command).
- You want async friendly throttling without rolling your own schedulers or hash maps.

## Quick Cooldown Toolkit Walkthrough

### Step 0: Know The Building Blocks

| Type                  | Purpose                                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------------------|
| `CooldownKey`         | Java 21 record describing namespace, name, subject UUID, and optional context UUID.                   |
| `CooldownSpec`        | Duration + policy. `rejecting` blocks while active, `extending` keeps pushing the expiry forward.     |
| `CooldownRegistry`    | Service interface (provided by `common-api`) that stores, links, and reaps timers.                    |
| `CooldownAcquisition` | Sealed hierarchy returned by `acquire`: `Accepted` holds a ticket, `Rejected` reports remaining time. |

Once the registry is injected into your feature container, every runtime surface can share it.

### Step 1: Compose A Key

Keys answer *who* and *what* you are throttling. Helpers in `CooldownKeys` keep namespaces consistent.

```java
UUID playerId = player.getUniqueId();
CooldownKey key = CooldownKeys.of(
        "command",          // namespace
        "party.invite",     // logical action
        playerId,           // subject
        null                // optional context (NPC instance, menu template…)
);
```

### Step 2: Pick A Policy

```java
CooldownSpec spec = CooldownSpec.rejecting(Duration.ofSeconds(5));
// or CooldownSpec.extending(Duration.ofSeconds(5)) for debounce-style flows
```

`rejecting` is ideal when you want to show “wait X seconds”; `extending` is good when repeated clicks should stretch the window rather than error immediately.

### Step 3: Acquire And React (Async-First)

```java
CompletionStage<CooldownAcquisition> attempt = registry.acquire(key, spec);

attempt.thenAccept(acquisition -> {
    if (acquisition instanceof CooldownAcquisition.Accepted accepted) {
        inviteService.sendInvite(playerId, targetId);
        log.debug("Cooldown expires at {}", accepted.ticket().expiresAt());
        return;
    }

    Duration remaining = ((CooldownAcquisition.Rejected) acquisition).remaining();
    Message.error("invite.cooldown", remaining.toSeconds()).send(player);
});
```

The registry returns a `CompletionStage`, so you can stay off the main thread. Tests or legacy handlers can still block via `toCompletableFuture().join()` if needed.

### Step 4: Wrap The Pattern In Your Own Guard

You could encapsulate the boilerplate once so call sites stay tiny.

```java
public record CooldownGuard(CooldownRegistry registry) {
    public boolean withTicket(CooldownKey key,
                              Duration duration,
                              Runnable onAccepted,
                              Consumer<Duration> onRejected) {
        CooldownAcquisition result = registry.acquire(key, CooldownSpec.rejecting(duration))
                .toCompletableFuture()
                .join();

        if (result instanceof CooldownAcquisition.Accepted) {
            onAccepted.run();
            return true;
        }
        onRejected.accept(((CooldownAcquisition.Rejected) result).remaining());
        return false;
    }
}
```

Could be helpful if you need to invoke cooldown registry related logic from multiple places; wrapping it keeps handlers lean and makes policy tweaks simple.

### Step 5: Share Timers Across Paths

Linking keys makes alternate entry points respect the same window.

```java
CooldownKey npcKey = CooldownKeys.npcInteraction(playerId, npcInstanceId);
CooldownKey tutorialKey = CooldownKeys.of("conversation", "tutorial", playerId, null);

registry.link(npcKey, tutorialKey);
```

Link keys when both identities exist (usually the first time a player touches the flow). Once linked, either path respects the shared timer until the tickets expire.

## Full API Reference

**`CooldownRegistry`**
- `CompletionStage<CooldownAcquisition> acquire(CooldownKey, CooldownSpec)`: reserve a slot.
- `CompletionStage<Void> release(CooldownTicket)`: optional manual release; tickets also expire automatically.
- `void link(CooldownKey primary, CooldownKey secondary)`: share timers across related actions.
- `int trackedCount()`: current entry count.
- `void pauseReaper()`, `void resumeReaper()`, `int drainOnce(int batchSize)`: deterministic test controls.

**`CooldownKey`**
- Record fields: `namespace`, `name`, `subject`, `context`.
- Factory helpers: `CooldownKeys.of`, `CooldownKeys.npcInteraction`, `CooldownKeys.menuSlot`, etc., to keep naming consistent.

**`CooldownSpec`**
- `static CooldownSpec rejecting(Duration window)`: rejects while active.
- `static CooldownSpec extending(Duration window)`: accepts but pushes the expiry forward.

**`CooldownAcquisition`**
- `Accepted` -> `CooldownTicket ticket()` exposing `Instant issuedAt()` / `expiresAt()`.
- `Rejected` -> `Duration remaining()` for UX messaging or logging.