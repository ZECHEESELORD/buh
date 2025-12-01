Enchant System Plan:

Scope:
- Canonical custom enchants persisted on items via PDC.
- Entire combat flow (PvE and PvP) reads custom stats that include enchant contributions; vanilla behavior is ignored.
- Packet-side lore rendering shows custom enchants; vanilla enchant lines stay hidden.

Data model (PDC, canonical):
- PDC key: `item-enchants` (namespaced). Value: serialized map of `enchant_id -> level`.
- Optional PDC key: `item-enchant-version` for migrations.
- Represent enchants as stable IDs (`fulcrum:sharpness`, `fulcrum:smite`, etc.) with levels as ints.

Engine integration:
- ItemPdc handles get/set/clear for enchant data; no ad hoc PDC writes elsewhere.
- On item load (ItemInstance), parse PDC enchants into a runtime map.
- Stat aggregation: convert custom enchants into stat contributions (for example Sharpness -> flat `attack_damage`, Protection -> `armor`). Apply via the stat bridge as modifiers atop the item’s PDC stat map.
- Vanilla mirrors are optional and visual only; custom PDC remains the source of truth even in PvP.

Lore rendering:
- Add an EnchantLore section: list custom enchants with levels using canonical IDs or display names.
- Keep `HIDE_ENCHANTS` on ItemMeta; render custom lines in packet lore instead.
- Vanilla-named enchants still map to explicit stat bonuses; vanilla enchant behavior is never used for effects.

Application paths:
- Enchant Table/Anvil analogue writes to PDC via ItemPdc, refreshes ItemMeta only if we choose to mirror visuals.
- Stat bridge picks up PDC enchant data when items are equipped or changed and re-applies modifiers.

Combat behavior:
- Both PvE and PvP use the custom stats pipeline; enchant effects are always derived from our registry mappings.

Key steps to implement:
1) Define EnchantDefinition registry (id, max level, display name, stat effects).
2) Extend ItemInstance to parse and hold the enchant map from PDC.
3) Extend stat computation to apply enchant stat effects on top of the item’s base stat map.
4) Extend lore renderer to show custom enchants and keep vanilla enchant text hidden.
5) Optional: mirror selected enchants to vanilla ItemMeta purely for visibility.
6) Add APIs to add/remove enchants programmatically; UI/command layer later.
