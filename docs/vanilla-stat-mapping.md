Damage & Stat Mapping (Short Spec)

Goal:
Implement the mapping layer between our custom RPG stats and vanilla values for damage, keeping 1:1 health mapping and giving armor more influence via a custom damage pipeline. Consumers (combat, regions, items, buffs) already use the stat system; this spec is only for the mapping and pipeline.

1) Health mapping (1:1 with vanilla)

- Our stats:
    - max_health: final maximum HP from the stat system.
- Vanilla:
    - minecraft:max_health attribute
    - Entity#getHealth / #setHealth

Rules:
- Whenever the max_health stat changes for an entity:
    - Update the vanilla minecraft:max_health attribute to exactly the same value.
- Current health:
    - We treat vanilla health as the current HP source of truth.
    - Damage and healing always use vanilla health values, but the amounts are computed by our custom pipeline.

Result:
- 1 health in our system = 1 health in vanilla.
- All UI that reads vanilla HP (hearts, bossbar, etc.) stays correct.

2) Armor & toughness mapping (used only by our pipeline)

- Our stats:
    - armor: the main physical mitigation stat. Exponential falloff (approaching 99% damage reduction)
    - we do not use armor_toughness in our calculations
- Vanilla armor attributes:
    - We do NOT rely on minecraft:armor or minecraft:armor_toughness for reduction.
    - They are optional: can be used for UI flavor or kept at neutral values (e.g. 0–1).

Mapping rule:
- When armor / armor_toughness stats change:
    - Optionally mirror them into the corresponding vanilla attributes for visuals.
    - Combat logic must NOT use vanilla reduction; it always queries the stats directly.

3) Damage hook and pipeline (single stage from the mapping layer)

Entry point:
- We hook into damage events (e.g. EntityDamageByEntityEvent and EntityDamageEvent).
- For each incoming damage event:
    1) Identify attacker (if any) and defender, and get their StatContainers.
    2) Read:
        - From attacker (optional): attack_power, crit stats, skill modifiers, etc.
        - From defender: armor, and any other relevant defensive stats.
    3) Compute finalDamage in HP using our custom math (see below).
    4) Set event.setDamage(finalDamage) and neutralize any vanilla modifiers so only our value applies.

Armor Stage (Exponential Defense, No Toughness)

Goal:
Use a single “armor” stat that gives exponentially increasing damage reduction which asymptotically approaches (but never reaches) 100% damage negation. No armor toughness is used.

Inputs:
- D = damage after attacker-side bonuses, before defenses (in HP).
- A = armor stat (non-negative; can scale beyond vanilla values, e.g. 0–100+).

Config knobs:
- defenseScale > 0
    - Controls how quickly armor ramps up.
    - Larger defenseScale = slower ramp (you need more armor to reach high reduction).
    - Smaller defenseScale = faster ramp (armor feels stronger sooner).
- maxReduction in (0, 1)
    - Absolute upper bound for reduction from armor alone.
    - Example: 0.90 means armor can at most reduce damage by 90%.

Formula:

1) Compute the exponential “saturation” term:

   raw = 1 - exp(-A / defenseScale)

   Properties:
    - A = 0   → raw = 0       (no armor, no reduction)
    - A → ∞   → raw → 1       (very high armor, near full reduction)
    - The curve is smooth and strictly increasing.

2) Convert to actual reduction percent (capped):

   reductionPercent = maxReduction * raw

    - At very high A, reductionPercent approaches maxReduction but never exceeds it.
    - Example with defenseScale = 25 and maxReduction = 0.90:
        - A = 10   → ~29.7% reduction
        - A = 25   → ~56.9% reduction
        - A = 50   → ~77.8% reduction
        - A = 100  → ~88.4% reduction

3) Apply to the damage:

   D_afterArmor = D * (1 - reductionPercent)

Behavior summary:
- Low A: armor gives modest protection; small but noticeable reduction.
- Mid A: armor becomes a strong lever; each point matters.
- High A: armor still improves reduction, but with diminishing returns as it approaches maxReduction.
- No toughness; the damage reduction is independent of D (big and small hits are reduced by the same percentage for a given A).

Implementation notes:
- Use double precision for the math (`exp`).
- Clamp A to be ≥ 0 before applying the formula.
- Keep defenseScale and maxReduction in a config file so you can tune armor “feel” without changing code.

Everything else (crit, lifesteal, elemental resists, etc.) is layered in the combat consumer code, but they always:
- Use the stat system for all stat values.
- Use this mapping layer to:
    - Keep max health synced with minecraft:max_health.
    - Route damage through a single custom calculation that ignores vanilla armor logic and writes the final HP loss back as event damage.

Config and defaults:
1) File location: plugins/buh/config/stats/config.yml.
2) Keys: damage.defense_scale (default 16.0), damage.max_reduction (default 0.90), visuals.mirror_armor_attributes (default true for UI flavor only).
3) Mirror flag: when true we copy armor into vanilla armor attributes for display; the damage pipeline still ignores vanilla armor math.

Consumer integration (items, mobs, combat systems):
1) Acquire services: StatsModule from BuhPlugin, then StatService via statsModule().statService(), StatRegistry via statsModule().statRegistry().
2) Resolve entity: EntityKey.fromUuid(entity.getUniqueId()), then StatContainer container = statService.getContainer(key).
3) Item engines: on equip add StatModifier with a stable StatSourceId per slot (example: new StatSourceId("item:mainhand")); choose ModifierOp.FLAT or percentages; on unequip call container.removeModifier(statId, sourceId) or container.clearSource(sourceId).
4) Mob engines: after spawn set base lines with container.setBase(StatIds.MAX_HEALTH, value) and container.setBase(StatIds.ATTACK_DAMAGE, value); add modifiers for buffs or AI states with a distinct source id; on despawn call statService.removeContainer(key).
5) Combat consumers: read attack_damage and armor directly from the container; the mapping layer already sets attack_damage base from the incoming event, but you can override by setting base or modifiers before damage runs.
6) Health behavior: max_health changes instantly set vanilla max health and clamp current health down to the new max so downstream UI stays honest.

Sample usage (item equip):
```java
EntityKey key = EntityKey.fromUuid(player.getUniqueId());
StatContainer container = statService.getContainer(key);
StatSourceId mainhandSource = new StatSourceId("item:mainhand");

// Add a flat damage bonus and some armor on equip
container.addModifier(new StatModifier(StatIds.ATTACK_DAMAGE, mainhandSource, ModifierOp.FLAT, 3.0));
container.addModifier(new StatModifier(StatIds.ARMOR, mainhandSource, ModifierOp.FLAT, 5.0));

// On unequip
container.removeModifier(StatIds.ATTACK_DAMAGE, mainhandSource);
container.removeModifier(StatIds.ARMOR, mainhandSource);
```
