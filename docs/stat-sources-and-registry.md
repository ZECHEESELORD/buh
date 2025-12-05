Stat Registry and Bonuses:

Add a stat to the registry:
- Open `common/src/main/java/sh/harold/fulcrum/stats/core/StatRegistry.java`.
- Register a new `StatDefinition` with:
  - `StatId` (e.g., `new StatId("lifesteal")`)
  - Base/min/max
  - Stacking model (usually `StackingModel.DEFAULT`)
  - Optional `StatVisual` (icon + hex color) to render in menus/items.
- Keep `StatIds` in sync if you want a constant for the id (see `StatIds` class).

Add bonuses properly:
- Define where the modifier comes from (item base stats, enchant, perk, buff, etc.).
- Use the appropriate bridge/helper to add modifiers:
  - Items: add stats to the item definition or enchant definition so they flow through `ItemResolver` → `ItemStatBridge`.
  - Other sources: when adding a `StatModifier` to `StatService`’s container, also register a `StatSourceContext` with:
    - Name/secondary/description (e.g., “Perk: Hot Potato” / “Perk” / “Stats from hot potato upgrade.”)
    - Display item/icon (optional but recommended for menus)
    - Category (`SourceCategory.BASE/ENCHANT/PERK/BONUS/DEFAULT/UNKNOWN`)
    - Slot tag if it’s tied to a specific slot (e.g., `MAIN_HAND`, `HELMET`).
  - For items/enchants, `ItemStatBridge` already registers context; for new sources, use `StatSourceContextRegistry.put(EntityKey, StatSourceId, StatSourceContext)` when you add modifiers.
- Keep values in raw form; display scaling (e.g., movement speed ×1000, crit ×100) is handled by renderers.
