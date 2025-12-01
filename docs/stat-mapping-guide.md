# Stat Mapping and Integration Guide

Fulcrum’s stat engine now owns all combat. Items publish stat maps into PDC, the bridge feeds containers, and both PvE and PvP run through the same custom curve before vanilla health drops. These notes keep consumers aligned.

Stat surface:
- Stat ids: `max_health`, `attack_damage`, `attack_speed`, `movement_speed`, `armor`.
- Services: `StatService` (entity to `StatContainer`), `StatRegistry` (definitions), `StatBindingManager` (fan out to platform bindings).
- Defaults: registry baselines only; we no longer seed from live attributes.

Config (plugins/buh/config/stats/config.yml):
- Module toggle: `modules.yml` at `modules.gameplay.rpg-stats`.
- `damage.defense_scale` (default 16.0) controls how quickly armor ramps.
- `damage.max_reduction` (default 0.90) caps reduction.
- `visuals.mirror_armor_attributes` (default true) pushes custom armor to vanilla attributes for display flavor; the push never feeds the stat container.

Consumer playbook:
1) Resolve containers
```java
StatService statService = plugin.statsModule().statService();
EntityKey key = EntityKey.fromUuid(entity.getUniqueId());
StatContainer container = statService.getContainer(key);
```

2) Items and modifiers
- ItemResolver writes authoritative stat maps into PDC (`item-stats`) on creation/wrap. ItemStatBridge reads those maps and applies flat modifiers per slot source id (`item:main_hand`, etc.).
- If you add temporary buffs, use stable `StatSourceId`s so you can clear them cleanly.
- Do not rely on vanilla ItemMeta attributes; they are cleared and hidden at creation time.

3) Mobs and bases
- Set bases directly on spawn if a mob needs non-default stats:
```java
container.setBase(StatIds.MAX_HEALTH, 40.0);
container.setBase(StatIds.ATTACK_DAMAGE, 6.0);
container.setBase(StatIds.ARMOR, 12.0);
```
- Remove the container on despawn with `statService.removeContainer(key)`.

4) Combat path
- Damage listener always reads attacker `attack_damage` from the stat container and applies the armor curve on the defender; vanilla modifiers are zeroed. This applies in PvE and PvP.
- Armor and other combat stats come only from the container (bases plus modifiers). Vanilla armor math is ignored.
- Bindings for health, attack speed, attack damage, and armor stay active for all entities; they keep vanilla attributes in sync for client visuals without feeding back into the stat math.

5) Visuals
- `max_health` mirrors to vanilla attributes so hearts and boss bars stay honest.
- If `mirror_armor_attributes` is true, armor is mirrored outward for UI flavor only.
- Lore rendering reads the stat map directly; ItemMeta attributes are not consulted.

When to extend:
- Add new bindings (for example movement speed) by implementing `StatBinding`, registering it with `StatBindingManager`, and subscribing it to `StatService`.
- Tune the armor curve with `defense_scale` and `max_reduction` without code changes.
