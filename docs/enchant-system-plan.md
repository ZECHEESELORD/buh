Enchant System Plan:

Scope:
- Canonical custom enchants persisted on items via PDC.
- Mirror to vanilla enchants (as needed) for PvP/compat, while keeping the custom stat pipeline for PvE.
- Packet-side lore rendering that shows custom enchants; optionally hide vanilla enchant lines.

Data model (PDC, canonical):
- PDC key: `item-enchants` (namespaced). Value: serialized map of `enchant_id -> level`.
- Optional PDC key: `item-enchant-version` for migrations.
- Represent enchants as stable IDs (`fulcrum:sharpness`, `fulcrum:smite`, etc.) with levels as ints.

Engine integration:
- ItemPdc handles get/set/clear for enchant data to avoid ad-hoc PDC writes.
- On item load (ItemInstance), parse PDC enchants into a runtime map.
- Stat aggregation: convert custom enchants into stat contributions (e.g., Sharpness -> flat `attack_damage`, Protection -> `armor`). Apply via the stat bridge as modifiers.
- Vanilla mirror (optional):
  - For PvP/compat, map selected custom enchants to vanilla enchants and write to ItemMeta when creating/refreshing stacks (server-side stacks), and/or ensure packet lore hides vanilla lines.
  - Keep custom PDC as the source of truth; mirrors are derived.

Lore rendering:
- Add an EnchantLore section: list custom enchants with levels using the canonical IDs/display names.
- Hide vanilla enchant lines in packet lore (HIDE_ENCHANTS flag) while keeping glint; render custom lines instead.
- Vanilla-named enchants get custom effects: treat Sharpness/Smite/Protection/etc. as entries in the registry that map to explicit stat bonuses (e.g., Sharpness -> flat `attack_damage`, Protection -> `armor`/reduction). PvE uses these custom mappings; vanilla enchant behavior is not relied on for effects, only mirrored for PvP/compat visibility if enabled.

Application paths:
- Enchant Table/Anvil analogue writes to PDC via ItemPdc, updates ItemMeta vanilla enchants if mirroring is enabled.
- Stat bridge picks up new PDC data when items are equipped/changed and re-applies modifiers.

Combat behavior:
- PvE uses custom stats (enchants -> stats).
- PvP can optionally rely on vanilla mirrors; if mirrors are disabled, PvP stays vanilla with no enchant effect unless we later hook PvP too.

Key steps to implement:
1) Define EnchantDefinition registry (id, max level, display name, stat effects).
2) Extend ItemInstance to parse/hold enchant map from PDC.
3) Extend stat computation to apply enchant stat effects.
4) Extend lore renderer to show custom enchants and hide vanilla enchant text.
5) Add mirror utility to apply selected enchants to vanilla ItemMeta when needed.
6) Add APIs to add/remove enchants programmatically; UI/command layer later.
