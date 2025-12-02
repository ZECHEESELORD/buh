Custom Item Guide:

Overview:
- Custom items are defined as immutable `CustomItem` definitions with components (stats, abilities, visuals) and optional PDC state.
- Runtime stacks are resolved via `ItemResolver` and sanitized; lore is rendered packet-side via `ItemLoreRenderer`.
- Stats flow into the stat container through `ItemStatBridge` using stable source ids per slot (`item:main_hand`, etc.).

How to define an item (YAML):
- Place a YAML file under `plugins/buh/config/items/` (loaded by `YamlItemDefinitionLoader`).
- Required fields:
  - `id`: namespaced string, e.g., `fulcrum:my_sword`.
  - `material`: Bukkit material, e.g., `IRON_SWORD`.
- Optional fields:
  - `category`: overrides auto category (SWORD, AXE, HELMET, etc.).
  - `traits`: list of `ItemTrait` values (e.g., `MELEE_ENCHANTABLE`).
  - `stats`: map of stat ids to doubles (e.g., `attack_damage: 12`).
  - `abilities`: list of maps with `id`, `trigger`, `name`, `description`, `cooldown`, `cooldown_key`.
  - `flavor`: list of strings (flavor text).
  - `lore_layout`: optional list of `LoreSection` values to reorder sections.

Example YAML:
```
id: fulcrum:ember_blade
material: DIAMOND_SWORD
category: SWORD
traits:
  - MELEE_ENCHANTABLE
stats:
  attack_damage: 14.0
  crit_damage: 0.25
abilities:
  - id: fulcrum:fire_dash
    trigger: RIGHT_CLICK
    name: Fire Dash
    description:
      - Dash forward and ignite foes.
    cooldown: PT5S
display_name: Ember Blade
flavor:
  - Hot to the touch.
lore_layout:
  - HEADER
  - RARITY
  - TAGS
  - ENCHANTS
  - PRIMARY_STATS
  - ABILITIES
  - FOOTER
```

How to create stacks programmatically:
- Use `ItemEngine.createItem(id)` or `ItemEngine.resolver().initializeItem(definition)` to get a sanitized ItemStack with id and PDC stats.
- Do not write PDC directly; use `ItemResolver`/`ItemPdc` helpers.

Lore and visuals:
- Lore is packet-rendered; server stacks keep attributes/enchants hidden. Enchant and stat sections are auto-generated.
- Vanilla items are wrapped automatically with a visual and stat table; you can override by defining your own id for that material.

Slots and stats:
- On equip/pickup/click/drag/etc., `ItemStatBridge.refreshPlayer` clears and re-adds modifiers per slot source id.
- Keep stat ids aligned with `StatIds` (e.g., `attack_damage`, `attack_speed`, `armor`, `max_health`, `crit_damage`).

Tips:
- Use traits to gate enchanting/abilities instead of hardcoding ids.
- Keep stats in PDC; avoid ItemMeta attributes for logic (they’re only mirrored for client feel).
