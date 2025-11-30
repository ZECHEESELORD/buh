# Stat Mapping & Integration Guide

Fulcrum’s stat engine powers every RPG layer: items feed modifiers, mobs expose container values, and combat runs through a custom damage curve before touching vanilla health. This guide shows how to wire consumers and reminds you which knobs live in config.

## Quick recap: what the engine exposes

- Stat ids (vanilla-aligned): `max_health`, `attack_damage`, `attack_speed`, `movement_speed`, `armor`.
- Core services: `StatService` (entity -> `StatContainer`), `StatRegistry` (definitions), `StatBindingManager` (fan-out to platform bindings).
- Default mappings already registered in the plugin:
  - `max_health` mirrors to vanilla `Attribute.MAX_HEALTH` and clamps current health when max drops.
  - Armor mirrors to vanilla attributes for visuals when enabled (config flag), but damage reduction always uses the custom armor stat; armor toughness is ignored and zeroed for vanilla display parity.
  - Damage listener overwrites incoming event damage with the custom curve and zeroes vanilla modifiers.

## Config knobs (plugins/buh/config/stats/config.yml)

- Module toggle lives in `modules.yml` at `modules.gameplay.rpg-stats`.
- `damage.defense_scale` (default 16.0): larger means slower armor ramp.
- `damage.max_reduction` (default 0.90): cap on armor’s percent reduction.
- `visuals.mirror_armor_attributes` (default true): copy armor into vanilla attributes for UI flavor only.

## Consumer playbook

### 1) Resolve containers
```java
StatService statService = plugin.statsModule().statService();
StatRegistry statRegistry = plugin.statsModule().statRegistry();
EntityKey key = EntityKey.fromUuid(entity.getUniqueId());
StatContainer container = statService.getContainer(key);
```

### 2) Items: add/remove modifiers with stable sources
```java
StatSourceId mainhand = new StatSourceId("item:mainhand");
container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, mainhand, ModifierOp.FLAT, 3.0));
container.addModifier(new StatModifier(StatIds.ARMOR, mainhand, ModifierOp.FLAT, 5.0));
// On unequip
container.removeModifier(StatIds.ATTACK_DAMAGE, mainhand);
container.removeModifier(StatIds.ARMOR, mainhand);
```
Use deterministic source ids per slot/buff so you can clear them precisely.

Custom items:
* Let your item system be the single source of armor and other stats; equip and unequip should add or remove `StatModifier`s with stable `StatSourceId`s such as `item:mainhand` or `item:helmet`.
* If you want the vanilla armor bar to reflect custom values, keep `mirror_armor_attributes` enabled. It now only mirrors outward; it no longer pulls vanilla values back into the stat container.
* Attack damage is still set from the event’s raw damage each swing. If you want item-defined damage as the true base, either treat the event damage as just another modifier or set the vanilla attack attribute to your desired base so the event aligns with the stat.
* On spawn, the stats module seeds bases from vanilla attributes. For PDC-driven mobs with “no armor,” set your intended bases right after spawn or before the entity joins the world so the seed is replaced with your values.

### 3) Mobs: seed base stats on spawn
```java
container.setBase(StatIds.MAX_HEALTH, 40.0);
container.setBase(StatIds.ATTACK_DAMAGE, 6.0);
container.setBase(StatIds.ARMOR, 12.0);
// On despawn
statService.removeContainer(key);
```
The mapping layer clamps current health if max drops, keeping vanilla HP honest.

### 4) Combat: read/write around the damage hook
- The damage listener reads attacker `attack_damage` from the stat container and applies the armor curve on the defender. Keep attacker stats up to date via your item/power systems.
- To override, set base or add modifiers before the event fires (e.g., custom skills), using a distinct `StatSourceId`.
- Vanilla armor math is neutralized; only the custom pipeline applies.
- Armor is no longer read back from vanilla attributes during damage resolution—what you set in the container (bases, modifiers) is what the curve uses. If you still want the vanilla armor bar for flavor, leave `mirror_armor_attributes` on so the binding pushes stat values outward without pulling them back in.
- PvP stays vanilla: if both attacker and defender are players, the custom damage hook bails out. Player-vs-mob and mob-vs-player still use the custom stat pipeline.

### 5) Visuals / UI
- `max_health` is mirrored 1:1 to vanilla so hearts and boss bars stay correct.
- If `mirror_armor_attributes` is true, vanilla armor values reflect the stat for display; they do not affect damage.
- Access `StatDefinition.visual()` for icon/color if you need to render HUD elements.

## Lifecycle notes
- The RPG stats module bootstraps containers for all living entities and cleans them up on removal.
- Health clamps immediately when max health shrinks.
- All calculations use doubles; armor is clamped to >= 0 before applying the exponential curve.

## When to extend
- Need more bindings (e.g., movement speed)? Implement `StatBinding`, register with `StatBindingManager`, and subscribe it to `StatService`.
- Want different armor feel? Tune `defense_scale` and `max_reduction` in config without code changes.
