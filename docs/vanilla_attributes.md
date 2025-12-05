# Core health / defense

minecraft:max_health                  - Maximum health (in half-hearts) the entity can be healed to.
minecraft:max_absorption              - Maximum absorption hearts the entity can have at once.
minecraft:armor                       - Armor points; reduces most incoming damage.
minecraft:armor_toughness             - Armor toughness; makes armor scale better against high damage hits.
minecraft:knockback_resistance        - Fraction of generic knockback resisted (1.0 = no knockback).
minecraft:explosion_knockback_resistance - Fraction of knockback resisted from explosions only.

# Combat

minecraft:attack_damage               - Base melee damage dealt per hit (in half-hearts).
minecraft:attack_speed                - Full-strength attacks per second; controls attack cooldown.
minecraft:attack_knockback            - Extra knockback applied when the entity hits something.
minecraft:sweeping_damage_ratio       - Fraction of main-hit damage dealt by sweeping (AoE) attacks.

# Movement and mobility

minecraft:movement_speed              - Base walking/running speed.
minecraft:movement_efficiency         - Movement speed through “difficult” terrain (e.g. cobwebs, some fluids).
minecraft:flying_speed                - Flight speed for entities that fly (elytra, creative-style flight, some mobs).
minecraft:sneaking_speed              - Movement speed while sneaking.
minecraft:water_movement_efficiency   - Movement speed through water; higher = less slowdown.
minecraft:step_height                 - Block height the entity can step over without jumping.
minecraft:jump_strength               - Jump height / power (primarily affects horses and similar mounts).
minecraft:gravity                     - Strength of gravity pulling the entity down (affects fall curve).

# Environment / survivability / visuals

minecraft:safe_fall_distance          - Distance the entity can fall without taking fall damage.
minecraft:fall_damage_multiplier      - Multiplier on fall damage taken.
minecraft:burning_time                - How long the entity remains on fire after being ignited.
minecraft:oxygen_bonus                - Extra “air” capacity when underwater before drowning.
minecraft:scale                       - Visual and hitbox scale of the entity (also affects some derived distances).
minecraft:camera_distance             - Third-person camera distance from the entity; scaled by `scale`.
minecraft:follow_range                - Range at which mobs can detect and pursue targets.
minecraft:tempt_range                 - Range at which temptable mobs notice tempting items and start following.
minecraft:spawn_reinforcements        - Chance for a zombie to spawn an extra zombie when attacked.

# Mining / interaction

minecraft:block_break_speed           - Block breaking speed multiplier.
minecraft:mining_efficiency           - General mining efficiency, especially with correct tools.
minecraft:submerged_mining_speed      - Mining speed while underwater.
minecraft:block_interaction_range     - Reach distance for interacting with blocks (placing, breaking, using).
minecraft:entity_interaction_range    - Reach distance for interacting with entities (hitting, using).

# Movement in fluids / waypoint system

minecraft:water_movement_efficiency   - Movement speed through water (listed above; included here for grouping).
minecraft:waypoint_transmit_range     - Max distance at which this entity transmits itself as a waypoint / locator.
minecraft:waypoint_receive_range      - Max distance at which this entity can see other waypoints / locator dots.

# Luck / RNG

minecraft:luck                        - Modifies loot tables, fishing, and other luck-based rolls.
