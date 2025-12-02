Ability Guide:

Overview:
- Abilities are attached to items via `AbilityComponent` and executed through `AbilityService` when triggers fire.
- Definitions are data-only; execution logic lives in registered executors.
- Lore rendering shows ability name, trigger, and wrapped description from the definition.

Define an ability (code or YAML):
- `AbilityDefinition` fields:
  - `id`: namespaced string (`fulcrum:blink`).
  - `trigger`: `AbilityTrigger` (RIGHT_CLICK, LEFT_CLICK, SHIFT_RIGHT_CLICK, SHIFT_LEFT_CLICK).
  - `displayName`: Component (shown in lore).
  - `description`: List<Component> (wrapped in lore).
  - `cooldown`: Duration (ISO-8601, e.g., `PT5S`).
  - `cooldownKey`: string bucket for shared cooldowns.
- YAML items can declare `abilities:` list with these fields; loader maps them into an `AbilityComponent`.

Registering executors:
- Use `AbilityService.registerExecutor(id, (definition, context) -> { ... })` during bootstrap (e.g., in `ItemEngine.registerSampleAbilities`).
- `AbilityContext` exposes `player()`, `itemInstance()`, `slot()`; perform your logic there.
- Executors should handle cooldowns via `definition.cooldown()`/`cooldownKey()` (service handles cooldown tracking if implemented in the trigger layer).

Trigger flow:
- `AbilityListener` listens to interact events (right/left clicks, with/without sneak) in main hand.
- On trigger match, it fetches the ability from the item’s `AbilityComponent` and calls `AbilityService.trigger(...)`.
- If execution returns true, the event is cancelled.

Lore:
- Abilities render as: `<trigger>: <name>` and wrapped description lines beneath.
- Ensure descriptions are concise; the renderer wraps to ~40 chars per line.

Tips:
- Use unique cooldown keys to share cooldowns across abilities/items when needed.
- Keep ability effects server-side; do not rely on client visuals beyond lore/feedback messages.
- Gate abilities with item traits if needed (extend traits and check in the listener before triggering).
