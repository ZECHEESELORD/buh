Custom Stat Pipeline Refactor:

Goal:
* One unified combat pipeline for PvE and PvP that relies only on our custom stat system; no vanilla attribute or vanilla enchant fallthrough.

Principles:
* Registry defaults define baselines; no live attribute snapshots.
* Every item stack carries an authoritative stat map in PDC; ItemMeta attributes are cleared on creation/wrap.
* Damage, defense, and combat speed always read from the stat container; no event damage passthrough.

Stat storage:
* PDC key: `item-stats` (namespaced). Value: serialized map `stat_id -> double`.
* Populate on item creation or first wrap; read from PDC on resolve. If PDC missing, derive from definition or vanilla lookup, then write back.
* Sources feeding the stat map:
  * Custom items: `StatsComponent` from the definition.
  * Vanilla items: material lookup table (see below).
  * Future hooks: enchant-derived modifiers or temporary effects can merge before write.

Vanilla mapping (authoritative stat packs):
* Sword family:
  * Wooden: ATTACK_DAMAGE +4
  * Stone: ATTACK_DAMAGE +5
  * Iron: ATTACK_DAMAGE +6
  * Diamond: ATTACK_DAMAGE +7
  * Netherite: ATTACK_DAMAGE +8
* Axe family:
  * Wooden: ATTACK_DAMAGE +7
  * Stone: ATTACK_DAMAGE +9
  * Iron: ATTACK_DAMAGE +9
  * Diamond: ATTACK_DAMAGE +9
  * Netherite: ATTACK_DAMAGE +10
* Armor (vanilla armor points):
  * Leather: Helmet +1, Chest +3, Legs +2, Boots +1
  * Iron: Helmet +2, Chest +6, Legs +5, Boots +2
  * Diamond: Helmet +3, Chest +8, Legs +6, Boots +3
  * Netherite: Helmet +3, Chest +8, Legs +6, Boots +3 (toughness ignored in current model)
* Attack speed:
  * Swords: ATTACK_SPEED +1.6
  * Axes: ATTACK_SPEED +1.0
  * Hoes: ATTACK_SPEED +4.0 (tier-agnostic for now)
* Shields, bows, tridents: no direct combat stat in current model; can extend if needed.

Item creation and wrapping:
* When generating any stack (custom or vanilla), clear ItemMeta attribute modifiers and enchants; add HIDE_ATTRIBUTES and HIDE_ENCHANTS.
* Build the stat map from definition or vanilla lookup, write `item-stats` PDC, and set our item id PDC.
* Optionally mirror selected stats back into ItemMeta attributes only for client feel if we need them rendered in third party viewers, but the server logic must ignore those.

Runtime pipeline:
* ItemResolver reads stat map from PDC; if absent, derive from definition/vanilla table, then persist.
* ItemStatBridge feeds stat modifiers from the resolved stat map into the container for all slots.
* StatDamageListener always uses container stats for base damage and armor; remove the PvP bypass.
* Stat bindings (attack damage, attack speed, armor, health) remain active for all entities so attributes reflect the container consistently.

Lore rendering:
* Use the resolved stat map directly; do not read ItemMeta attributes.
* Show all non-zero stats we track (ATTACK_DAMAGE, ARMOR, ATTACK_SPEED, MOVEMENT_SPEED, etc.).
* Ensure HIDE_ENCHANTS is set; future enchant lore section will render custom lines via packets.

Migration steps:
* Implement PDC stat codec and integrate into ItemResolver.
* Add vanilla material stat table and inject during wrap.
* Update sanitizer to zero ItemMeta attributes/enchants on creation and add hide flags.
* Adjust StatEntityListener to skip live attribute bootstrapping; rely on registry defaults.
* Update StatDamageListener to always use container stats and drop the PvP skip.
* Update lore renderer to consume stat map, list all stats, and set HIDE_ENCHANTS.

Open items:
* Confirm the stat ids we want persisted for launch (attack_damage, armor, attack_speed, movement_speed, max_health?).
* Decide on serialization format for `item-stats` (JSON string vs. custom binary). JSON is simplest to inspect; binary is smaller.
