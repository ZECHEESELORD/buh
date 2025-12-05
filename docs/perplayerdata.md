Override onPacketSending(PacketEvent event):

Get the viewer:

Player viewer = event.getPlayer()

Check fast path:

if NameViewService.getMode(viewer.getUniqueId()) is VANILLA, just return

From here on, you rewrite the packet.

3. Rewriting the tab list entries

Modern ProtocolLib exposes player info entries through:

PacketContainer packet = event.getPacket()

EnumSet<EnumWrappers.PlayerInfoAction> actions = packet.getPlayerInfoActions().read(0)

Only proceed if actions contains any of:

EnumWrappers.PlayerInfoAction.ADD_PLAYER

EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME

Then:

Read the current entries:

List<PlayerInfoData> dataList = packet.getPlayerInfoDataLists().read(0)

You must not mutate PlayerInfoData in place; construct new ones.

For each PlayerInfoData data:

Extract the target profile:

WrappedGameProfile profile = data.getProfile()

UUID targetId = profile.getUUID()

Resolve alias:

NameViewMode mode = NameViewService.getMode(viewerId)

String alias = LinkedAccountService.getBestAlias(targetId).orElse(null)

If mode == VANILLA or alias == null, keep the original data as is

If you have an alias:

Build a Component for it, for example using Adventure:

Component aliasComponent = Component.text(alias)

Optionally add formatting; do not include the Minecraft name anywhere if you want a pure alias

Convert to a ProtocolLib chat component:

String json = GsonComponentSerializer.gson().serialize(aliasComponent)

WrappedChatComponent wrapped = WrappedChatComponent.fromJson(json)

Create a new PlayerInfoData instance with the same non name fields:

Latency from data.getLatency()

Game mode from data.getGameMode()

Listed flag from data.isListed()

Profile from data.getProfile()

Profile key data from data.getProfileKeyData()

and inject your wrapped as the display name.

Build a new List<PlayerInfoData> with these replacements, then:

packet.getPlayerInfoDataLists().write(0, modifiedList)

Result: every time a player appears or their display name is refreshed in the tab list, your viewer sees osu or Discord usernames instead of Minecraft names.

4. Making the toggle actually refresh things

When the viewer flips their setting in your UI, the existing entries will not magically change until something triggers a new PLAYER_INFO packet.

You have two pragmatic options.

Force a re send using hide or show:

For each online target:

Call viewer.hidePlayer(plugin, target)

Then call viewer.showPlayer(plugin, target)

Paper will resend PLAYER_INFO for that viewer, which your ProtocolLib listener will rewrite.

Manually send a synthetic PLAYER_INFO packet only to that viewer:

Use ProtocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)

Populate:

actions set containing EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME

playerInfoDataLists with entries for all online players, each already rewritten with your alias logic

Send it:

protocolManager.sendServerPacket(viewer, packet)

I would pick option two for cleanliness once you are comfortable, and option one as the quick and dirty version while you experiment.

5. Above head name tags (optional and trickier)

Tab list will already feel good, but if you also want the alias above players’ heads, you need to override the name tag.

You have two main patterns.

A. Entity metadata approach

For each viewer, intercept:

PacketType.Play.Server.ENTITY_METADATA in another PacketAdapter

In onPacketSending:

Resolve entityId to a Player instance using your own map of entity id to player, updated on:

PacketType.Play.Server.NAMED_ENTITY_SPAWN

player join or quit

When you know the target is a player:

Look at the watcher entries inside the metadata packet:

Use packet.getWatchableCollectionModifier().read(0) which gives metadata items

Inject or replace the CustomName metadata for that player:

Use a WrappedDataWatcher.WrappedDataWatcherObject for the correct index

Set value to a WrappedChatComponent built from your alias component

Also ensure CustomNameVisible is set to true

This yields a custom name tag separate from the actual username. It does not affect tab list and can be per viewer because you only send modified metadata to the one viewer.

Drawback: this behaves like a custom mob name; styling may differ slightly from vanilla player name tags, but it is robust and per viewer.

B. Scoreboard team spoofing (only if you really want it)

If you want something closer to vanilla player tags:

Send virtual scoreboard team packets only to the viewer:

Use PacketType.Play.Server.SCOREBOARD_TEAM

Create a team per target player for that viewer, with:

Team name equal to some internal identifier like alias_<uuid>

Name tag visibility set to ALWAYS

Prefix or suffix carrying part of the alias

Since scoreboard teams only add prefix and suffix to the real name, you cannot neatly replace the entire name without other hacks. For a pure alias this is less ideal; it shines for rank tags.

My strong suggestion here: use entity metadata for pure alias name above head, and scoreboard teams only if you later need rank brackets as well.