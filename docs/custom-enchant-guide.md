Custom Enchant Guide:

Overview:
- Enchants are registered in `EnchantRegistry` with ids, display names, descriptions, level curves, stat bonuses, and incompatibilities.
- Canonical data lives in PDC under `item-enchants` as `enchant_id -> level`. Resolver imports vanilla enchants, converts overrides, and hides vanilla lines.
- Effects are driven via stats: combat enchants add modifiers (e.g., Sharpness adds attack_damage); utility enchants can stay vanilla or get custom hooks later.

Define an enchant (code):
- Use `EnchantDefinition`:
  - `id`: namespaced string (`fulcrum:sharpness`).
  - `displayName`: Component for lore.
  - `description`: Component for lore description.
  - `maxLevel`: cap for UI/validation (levels beyond still apply if allowed by curve).
  - `perLevelStats`: map of StatId -> double (baseline per-level value).
  - `levelCurve`: controls scaling per level (default linear). You can supply custom curves.
  - `scaleAttackDamage`: when true and stat is `attack_damage`, bonuses scale off base weapon damage.
  - `incompatibleWith`: set of enchant ids to remove when applying this enchant.
- Register in `ItemEngine.registerDefaultEnchants()` or your own bootstrap.

Sharpness example (already implemented):
- Curve: 1-4 = +5% per level; 5+ grows increments by +5% per tier (5:30%, 6:45%, 7:65%). Scales off base attack damage. Incompatible with Smite/Bane.

Applying enchants:
- Programmatically via `EnchantService.applyEnchant(stack, enchantId, level)`: writes PDC, strips incompatible enchants, preserves glint.
- `/enchant` command (staff) uses the same service and will remove incompatible enchants automatically.
- Anvil/loot ingestion: when resolving, overridden vanilla enchants are converted to PDC and removed from meta; non-overrides stay on meta for vanilla behavior.

Lore rendering:
- ≤4 enchants: each shows name (blue; gold if over max) and wrapped description.
- ≥5 enchants: comma-separated names, wrapped to ~40 chars.
- Glint: if custom enchants exist and no vanilla enchants remain, a dummy hidden enchant is added to keep the glint.

Incompatibilities:
- Enforced on apply: new enchant removes any listed incompatible ids from the PDC map before writing.
- Default set: Sharpness/Smite/Bane are mutually exclusive. Extend by adding ids to `incompatibleWith` in the definition.

Extending effects:
- For non-stat utility (e.g., Flame, Mending), keep vanilla behavior or add custom hooks that read the PDC map.
- To add new stat-based enchants, define a StatId and map per-level values; ensure `computeFinalStats()` consumes them (it already merges enchant bonuses).
