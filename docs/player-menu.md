Player Menu:
The join routine now tucks a marked trigger into slot nine; if something already rests there, the stash service catches it before the gleaming menu star settles in. The item always reads `&aPlayer Menu &7(Right Click)` with lore `&e&lCLICK &eto open!`, and a persistent tag keeps us from looping the stash step on every login.

Storage Path:
`inventory.menuItem.material`

Flow:
1. The player data pass seeds the material when the document is missing it, falling back to a nether star.
2. The menu distributor loads that material, builds the tagged item, and applies it on the main thread.
3. Any displaced stack is pushed through `StashService#stash`; if that fails, the stack drops back into the inventory and the logger complains.
