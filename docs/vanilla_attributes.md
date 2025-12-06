# Vanilla Attributes:

Quick reference for the built-in attribute keys and the flavor they add. Values are described in vanilla units so you can line them up with custom stat math without surprises.

## Core health and defense:

* `minecraft:max_health`: Maximum health in half-hearts the entity can be healed to.
* `minecraft:max_absorption`: Maximum absorption hearts the entity can have at once.
* `minecraft:armor`: Armor points that trim most incoming damage.
* `minecraft:armor_toughness`: Armor toughness; keeps armor effective against high damage hits.
* `minecraft:knockback_resistance`: Fraction of generic knockback resisted; 1.0 means immune.
* `minecraft:explosion_knockback_resistance`: Fraction of knockback resisted from explosions only.

## Combat:

* `minecraft:attack_damage`: Base melee damage per hit in half-hearts.
* `minecraft:attack_speed`: Full-strength attacks per second; controls the attack cooldown.
* `minecraft:attack_knockback`: Extra knockback applied when the entity hits something.
* `minecraft:sweeping_damage_ratio`: Fraction of main-hit damage dealt by sweeping attacks.

## Movement and mobility:

* `minecraft:movement_speed`: Base walking or running speed.
* `minecraft:movement_efficiency`: Movement speed through difficult terrain such as cobwebs or some fluids.
* `minecraft:flying_speed`: Flight speed for entities that fly, including elytra and creative-style flight.
* `minecraft:sneaking_speed`: Movement speed while sneaking.
* `minecraft:step_height`: Block height the entity can step over without jumping.
* `minecraft:jump_strength`: Jump height or power, primarily for horses and similar mounts.
* `minecraft:gravity`: Gravity strength pulling the entity down.

## Environment and survivability:

* `minecraft:safe_fall_distance`: Distance the entity can fall without taking fall damage.
* `minecraft:fall_damage_multiplier`: Multiplier applied to fall damage taken.
* `minecraft:burning_time`: How long the entity remains on fire after being ignited.
* `minecraft:oxygen_bonus`: Extra air capacity when underwater before drowning.
* `minecraft:scale`: Visual and hitbox scale of the entity; also affects some derived distances.
* `minecraft:camera_distance`: Third-person camera distance from the entity; scales with `scale`.
* `minecraft:follow_range`: Range at which mobs can detect and pursue targets.
* `minecraft:tempt_range`: Range at which temptable mobs notice tempting items and start following.
* `minecraft:spawn_reinforcements`: Chance for a zombie to spawn an extra zombie when attacked.

## Mining and interaction:

* `minecraft:block_break_speed`: Block breaking speed multiplier.
* `minecraft:mining_efficiency`: General mining efficiency, especially with correct tools.
* `minecraft:submerged_mining_speed`: Mining speed while underwater.
* `minecraft:block_interaction_range`: Reach distance for interacting with blocks: placing, breaking, using.
* `minecraft:entity_interaction_range`: Reach distance for interacting with entities: hitting or using.

## Movement in fluids and waypoints:

* `minecraft:water_movement_efficiency`: Movement speed through water; higher values mean less slowdown.
* `minecraft:waypoint_transmit_range`: Max distance at which this entity transmits itself as a waypoint or locator.
* `minecraft:waypoint_receive_range`: Max distance at which this entity can see other waypoints or locator dots.

## Luck and random rolls:

* `minecraft:luck`: Modifies loot tables, fishing, and other luck-based rolls.
