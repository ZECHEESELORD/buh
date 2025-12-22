Player Core Document:
Each player sits inside the `players` collection. The `meta` chunk tracks first and last joins plus the latest username; `statistics` carries playtime seconds; `inventory` holds the menu item and the stash payload.

Leveling:
Progression XP lives at `progression.xp` as a long. Levels are derived from XP on demand using the server curve; missing values default to zero.

Biome Tracker:
We now record the first moment a player steps into each biome, vanilla or datapack. The path shape is `travel.biomes.<biome key>` with the value as the ISO 8601 instant from `Instant#toString()`. Keys come straight from `Biome#getKey().asString()`, so custom identifiers land untouched.

Behaviors:
1. The join flow records the spawn biome after the metadata pass.
2. World swaps refresh the remembered biome and write it if missing.
3. Later block level biome changes append new keys while leaving the first timestamp intact.

Reading Tips:
`document.get("travel.biomes.minecraft:plains", String.class)` yields the first visit moment; a missing entry means the player has not touched that biome yet.

Staff Flags:
`staff.vanish` mirrors the active vanish toggle; true means the player lands already hidden and keeps their join or leave chatter hushed.
