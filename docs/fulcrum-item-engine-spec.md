# Fulcrum Item Engine — Architecture Specification

**Status:** Draft (Converged Design)  
**Target:** Paper 1.21+  
**Dependencies:** ProtocolLib or PacketEvents, Fulcrum Stat Engine, Adventure

---

## 1. Design Goals

The Fulcrum Item Engine provides a unified, data‑driven system for custom and vanilla items:

- Items are first‑class citizens in the **stat engine** and **ability system**.  
- Items are defined as immutable **definitions** and lightweight **instances**.  
- Tooltips are rendered via a **lore component pipeline**, allowing rich, per‑player views.  
- Vanilla items are automatically wrapped so they participate in the same pipeline.  
- The server view of an item remains clean; complex lore is rendered at packet time.

Core principles:

- **Composition over inheritance:** items are composed from components, not subclasses.  
- **“Describe, don’t decide”:** items declare stats, traits, and abilities; systems execute behavior.  
- **Minimalism:** only the responsibilities strictly needed for a scalable network are implemented.

---

## 2. Core Concepts

### 2.1 CustomItem (Definition / Flyweight)

Immutable description of what an item *is*:

- `id: String` — stable, namespaced identifier (`fulcrum:dev_test_item`).  
- `category: ItemCategory` — rigid class (e.g. `SWORD`, `HELMET`, `MATERIAL`).  
- `traits: Set<ItemTrait>` — behavioral tags (e.g. `MELEE_ENCHANTABLE`, `GEM_SLOTTABLE`).  
- `material: Material` — base Bukkit material.  
- `components: Map<ComponentType, ItemComponent>` — pluggable data modules:  
  - `StatsComponent`  
  - `AbilityComponent`  
  - `SocketComponent`  
  - `VisualComponent` config, etc.  
- `loreLayout: List<LoreSectionId>` — ordered sections for tooltip composition.  
- `rarity: ItemRarity` — controls colors and banner framing, if relevant.

Characteristics:

- Immutable, cached in the `ItemRegistry`.  
- Contains only *static* data: anything that varies per instance or per player lives elsewhere.

### 2.2 ItemInstance (Runtime Wrapper)

Lightweight wrapper over a Bukkit `ItemStack` that provides a typed view of its definition and state:

- `definition: CustomItem` (resolved via registry).  
- `stack: ItemStack` (underlying item).  
- `uuid: UUID` (optional stable identity for tracking).  
- Runtime state, stored in PDC, for example:  
  - selected gems / enrichments;  
  - upgrade level;  
  - soulbinding flags;  
  - any other mutable item‑specific data.

Key responsibilities:

- **Identification:** reads the custom ID from PDC.  
  - If present → `CustomItem` from registry.  
  - If absent → use a generated `VanillaWrapper` (see §3).  
- **State accessors:** strongly typed getters/setters for PDC fields.  
- **Stat aggregation:** `computeFinalStats()` that merges:
  - base stats from `StatsComponent`,  
  - gem/socket contributions from PDC,  
  - migrated vanilla enchants.

`ItemInstance` does *not* talk to the stat engine directly; it only returns data structures used by bridges.

---

## 3. Registry and Taxonomy

### 3.1 ItemCategory

Enum describing rigid coarse‑grained type and slot group, for example:

- `SWORD`, `AXE`, `BOW`, `WAND`  
- `HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`  
- `ACCESSORY`, `CONSUMABLE`, `MATERIAL`

Categories determine:

- Default slot (`MAIN_HAND`, `OFF_HAND`, `HELMET`, etc.).  
- Default trait set and potential behaviors.

### 3.2 ItemTrait

Enum for capability flags used by systems instead of ad‑hoc whitelists, for example:

- `MELEE_ENCHANTABLE` — can receive vanilla Sharpness/Smite.  
- `RANGED_ENCHANTABLE` — can receive Power/Infinity.  
- `GEM_SLOTTABLE` — accepts gems/sockets.  
- `SOULBOUNDABLE`, `UNSALVAGEABLE`, etc.

Systems like the Enchantment Service or Gem Service consult traits instead of item IDs.

### 3.3 ItemRegistry

Central service that owns all `CustomItem` definitions:

- `Map<String, CustomItem>` from ID to definition.  
- Load custom definitions from disk on startup (YAML/JSON/HOCON).  
- Apply validation and log configuration issues.  
- Provide lookups by both ID and underlying `Material`/metadata for vanilla wrapping.

Responsibilities:

1. **Vanilla Wrapper Generation**  
   For each Bukkit `Material` that has no explicit custom definition, generate a `VanillaWrapper`:

   - Category derived from `Material` (e.g. diamond sword → `SWORD`).  
   - Traits derived from defaults (e.g. tools get `MELEE_ENCHANTABLE`).  
   - `StatsComponent` may be empty or minimal; actual power can be provided via a migration layer.

   This ensures every item in the game has a valid definition.

2. **MigrationMap**  
   Configurable map from old item IDs to new IDs:

   ```yaml
   migrations:
     fulcrum:old_item_id: fulcrum:new_item_id
   ```

   Used when loading `ItemInstance`s from PDC. If an item’s ID is deprecated, the registry resolves to the new definition transparently.

3. **FallbackItem**  
   A special `CustomItem` used when an ID cannot be resolved even after migration. Visually, this may render as Barrier/Bedrock with a clear error message. This prevents “broken” or undefined items from silently entering circulation.

---

## 4. Components

`ItemComponent` is a marker interface; concrete components represent discrete concerns attached to a `CustomItem`.

### 4.1 StatsComponent

Stores immutable base attributes of the item type, for example:

- `Map<StatId, Double> baseStats`  
  - `ATTACK_DAMAGE: 100`  
  - `ARMOR: 100`  
  - `CRIT_CHANCE: 10`

It does *not* perform any calculations; it is pure data.

### 4.2 SocketComponent (optional)

Defines socket/enrichment capacity and rules, for example:

- `maxSockets` per type (e.g. Oak, Stone, etc.).  
- which gem categories are valid for this item category.

Runtime gem data lives on `ItemInstance` via PDC; this component only describes allowed configuration.

### 4.3 AbilityComponent

Describes abilities attached to the item and how they are triggered. It also doubles as a lore provider.

- `Map<AbilityTrigger, AbilityDefinition>`

`AbilityTrigger` examples:

- `RIGHT_CLICK`  
- `LEFT_CLICK`  
- `SHIFT_RIGHT_SCROLL`  
- `SHIFT_LEFT_SCROLL`

`AbilityDefinition` includes:

- `id: String` — ability identifier, used by the ability system.  
- `displayName: Component` — shown in lore.  
- `description: List<Component>` — ability description text.  
- `cost: AbilityCost` — resource type (mana, health, soulfow) and amount.  
- `cooldownKey: String` — logical cooldown bucket.  
- `cooldownDuration: Duration`.  
- `parameters: Map<String, Object>` — arbitrary effect configuration (damage multiplier, radius, etc.).

The effect logic is implemented by the `AbilityService`, not in the item engine.

### 4.4 VisualComponent

Configuration for extra visual details; at a minimum:

- Rarity banner text and color.  
- Optional flavor text.  
- Layout presets or flags that influence lore rendering (e.g. showing sockets as bullets).

### 4.5 LoreProvider

Interface implemented by components that contribute text to the tooltip:

```java
public interface LoreProvider {
    List<Component> renderLore(LoreContext ctx);
}
```

Examples:

- `StatBlockComponent`  
- `SocketLoreComponent`  
- `AbilityLoreComponent`  
- `FlavorTextComponent`

`LoreContext` contains:

- `CustomItem` definition.  
- `ItemInstance` state.  
- `Player viewer` (optional) for per‑player lore.  
- Snapshot of the viewer’s `StatContainer` if needed.

Each `LoreProvider` is self‑contained and only cares about its own section.

---

## 5. Lore Layout and Rendering

### 5.1 Lore Layout

Each `CustomItem` specifies an ordered list of lore sections:

```yaml
loreLayout:
  - HEADER
  - TAGS
  - PRIMARY_STATS
  - SOCKETS
  - ABILITIES
  - FOOTER
```

`LoreSectionId` is mapped to one or more `LoreProvider` implementations in a registry. This allows precise control of lore structure on a per‑item basis while reusing section implementations.

### 5.2 Rendering Pipeline (Server‑Side Representation)

Lore is *not* stored on the actual `ItemStack`. Instead, it is rendered at packet time and injected into outgoing packets only.

High‑level steps:

1. Build a `LoreContext` (`CustomItem`, `ItemInstance`, `viewer`).  
2. For each `LoreSectionId` in `loreLayout`:
   - Look up the bound `LoreProvider`(s).  
   - Append the rendered components to a `List<Component>` in order.  
3. Apply the lore components to a *cloned* `ItemStack` that lives only in the outgoing packet.

This makes lore per‑player, dynamic, and always up‑to‑date without ItemMeta churn.

---

## 6. Visual Engine (Packet Interceptor)

The visual engine is implemented via ProtocolLib or PacketEvents and never mutates the actual inventory items on the server.

### 6.1 Intercepted Packets

- `WindowItems` — inventory pages.  
- `SetSlot` — single slot updates (cursor, hotbar, armor slots, etc.).

### 6.2 Interception Flow

For each outgoing item inside these packets:

1. Extract `ItemStack`.  
2. Create `ItemInstance` for that stack.  
3. Clone the `ItemStack`.  
4. Build a `LoreContext` for the viewer and instance.  
5. Use `loreLayout` and `LoreProvider`s to generate tooltip lines.  
6. Set display name and lore on the cloned stack, then replace the item in the packet payload.

### 6.3 Caching

To avoid repeated expensive renders on spammy inventory updates, caching is recommended:

- Cache key: `(definitionId, instanceStateHash, viewerId)` or similar.  
- Cached value: prebuilt `List<Component>` for lore and display name.  
- Invalidation: change in instance PDC or relevant player state (e.g. stat‑dependent numbers).

A minimal first implementation can avoid caching; it can be introduced later if profiling indicates need.

### 6.4 Third‑Party Compatibility

Because actual ItemMeta has no lore, plugins that read lore directly will not see the fancy tooltip. There are two options:

- **Strict Mode:** packet‑only lore; other plugins must integrate via the item engine APIs.  
- **Compat Mode:** a minimal static lore subset is baked into NBT (e.g. name + rarity), while the full rich tooltip is still packet‑driven.

The chosen mode should be configurable globally.

---

## 7. Stat Engine Integration

The item engine does not perform combat math. It only produces stat contributions and forwards them into the Fulcrum Stat Engine.

### 7.1 Stat Calculation in ItemInstance

`ItemInstance.computeFinalStats()` returns a `Map<StatId, Double>` representing the item’s total contribution in isolation:

- Start with base stats from `StatsComponent`.  
- Add socket/gem modifiers stored in PDC.  
- Migrate vanilla enchantments to internal stats (e.g. Sharpness → `ATTACK_DAMAGE`, Protection → `ARMOR`).

No knowledge of curves or diminishing returns lives here.

### 7.2 Equipment Listener (Bridge)

A Bukkit listener monitors equipment changes:

- `PlayerItemHeldEvent`  
- Armor equip events (e.g. via a small util or a dedicated library)  
- Join / respawn / death / inventory close as needed

Flow on slot change:

1. Determine affected slot group (main hand, offhand, armor type).  
2. Wrap new item in `ItemInstance` (or create a `VanillaWrapper` instance).  
3. Call `computeFinalStats()` to get the item’s contribution.  
4. Use `StatService` to apply these stats to the player’s `StatContainer` under a stable `StatSourceId`, for example:
   - `item:mainhand`  
   - `item:offhand`  
   - `item:helmet`

5. Before applying new values, clear any existing modifiers for that `StatSourceId` to avoid accumulation.

The stat engine remains the single source of truth for:

- how modifiers are combined;  
- how final stats map to vanilla attributes;  
- how combat formulas work.

Items simply provide “what this item wants to add”.

---

## 8. Ability System Integration

The item engine provides ability descriptors; a dedicated ability subsystem executes them.

### 8.1 Ability Definitions

Defined in `AbilityComponent` and/or separate ability config files:

- `id` — unique identifier.  
- `triggers: Set<AbilityTrigger>` — what actions activate it.  
- `cost: AbilityCost` — resource type and amount.  
- `cooldownKey` and `cooldownDuration`.  
- `effectId` — key used by `AbilityService` to look up effect logic.  
- `parameters` — effect‑specific configuration map.

### 8.2 Lore Rendering for Abilities

`AbilityComponent` implements `LoreProvider` and renders sections like:

- `RIGHT CLICK:` header.  
- Cost line: icon + resource amount.  
- Description line(s).  
- Effect summary (e.g. “Boosts your damage by +20% for 5s.”).

These sections align with the design in the reference screenshot.

### 8.3 AbilityService

A central service responsible for:

- Listening to interaction events:  
  - right/left click;  
  - swapping slots (scroll triggers);  
  - any custom bindings.  
- Resolving the `ItemInstance` in hand and the `AbilityComponent`.  
- For each matching `AbilityTrigger`:
  1. Check cooldown map (`Map<UUID, Map<String, Instant>>`).  
  2. Check and spend cost via resource APIs (`ManaService`, `HealthService`, etc.).  
  3. Dispatch to an `AbilityExecutor` registered under `effectId`.

`AbilityExecutor` implementations may apply temporary stat modifiers, spawn projectiles, deal skill‑based damage, etc.

---

## 9. Item Creation and APIs

Public facing APIs for other Fulcrum modules:

### 9.1 Creating Items

```java
ItemStack createItem(String id, Consumer<ItemInstance> instanceMutator);
```

- Looks up `CustomItem` in the registry.  
- Creates a new `ItemInstance` with default state.  
- Applies the `instanceMutator` to customize runtime fields (gems, upgrades).  
- Serializes PDC onto a fresh `ItemStack`.  
- Returns the physical stack for giving to players, chests, loot tables, etc.

### 9.2 Inspecting Items

Helper methods:

- `boolean isCustomItem(ItemStack stack)`  
- `Optional<ItemInstance> wrap(ItemStack stack)`  
- `CustomItem getDefinition(ItemStack stack)`


These should be the only entry points other plugins use to work with custom items.

---

## 10. Implementation Plan

Recommended implementation order:

1. **Enums and Taxonomy**
   - Implement `ItemCategory` and `ItemTrait`.  
   - Implement `StatId`, `AbilityTrigger`, and core value objects.

2. **Core Registry**
   - Implement `CustomItem` and `ItemRegistry`.  
   - Load definitions from configuration and validate.  
   - Implement `VanillaWrapper` generation, `MigrationMap`, and `FallbackItem`.

3. **ItemInstance & Persistence**
   - Implement `ItemInstance` with PDC read/write helpers.  
   - Add `computeFinalStats()` that merges base stats, gems, and vanilla enchants.

4. **Stat Bridge**
   - Implement `EquipmentListener`.  
   - Use `StatService` to apply modifiers under deterministic `StatSourceId`s.  
   - Verify stat changes by equipping/unequipping test items.

5. **Lore Components and Layout**
   - Implement `LoreContext` and `LoreProvider`.  
   - Implement core providers: header, tag list, `StatBlockComponent`, sockets, flavor/footer.  
   - Wire up `loreLayout` on `CustomItem` and verify server‑side rendering logic.

6. **Packet Visual Engine**
   - Implement ProtocolLib/PacketEvents adapter.  
   - Intercept `WindowItems`/`SetSlot`, wrap stacks in `ItemInstance`, and inject rendered lore into cloned items.  
   - Add simple caching if needed.

7. **Ability System Integration**
   - Implement `AbilityComponent`, `AbilityDefinition`, and `AbilityService`.  
   - Wire interaction events to the ability pipeline.  
   - Add one or two concrete abilities and confirm cost/cooldown logic.

8. **Tooling & Validation**
   - Add configuration validators for item and ability definitions.  
   - Add debug commands for inspecting `ItemInstance`s, computed stats, and rendered lore.

Once these steps are complete, both vanilla and custom items will behave as full citizens in the Fulcrum ecosystem: they will feed into the stat engine, render rich tooltips client‑side, and act as carriers for abilities, sockets, and other modular behaviors.
