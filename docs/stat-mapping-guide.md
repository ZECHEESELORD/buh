# Stat Mapping & Integration Guide

Fulcrum’s stat engine powers every RPG layer: items feed modifiers, mobs expose container values, and combat runs through a custom damage curve before touching vanilla health. This guide shows how to wire consumers and reminds you which knobs live in config.

## Quick recap: what the engine exposes

- Stat ids (vanilla-aligned): `max_health`, `attack_damage`, `attack_speed`, `movement_speed`, `armor` (toughness unused).
- Core services: `StatService` (entity -> `StatContainer`), `StatRegistry` (definitions), `StatBindingManager` (fan-out to platform bindings).
- Default mappings already registered in the plugin:
  - `max_health` mirrors to vanilla `Attribute.MAX_HEALTH` and clamps current health when max drops.
  - Armor mirrors to vanilla attributes for visuals when enabled (config flag), but damage reduction always uses the custom armor stat.
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
- The damage listener sets attacker `attack_damage` base from the event’s raw damage and applies the armor curve on the defender.
- To override, set base or add modifiers before the event fires (e.g., custom skills), using a distinct `StatSourceId`.
- Vanilla armor math is neutralized; only the custom pipeline applies.

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
