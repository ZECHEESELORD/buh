Override onPacketSend(PacketSendEvent event):

Get the viewer:

Player viewer = event.getPlayer()

Check fast path:

if NameViewService.getMode(viewer.getUniqueId()) is VANILLA, just return

From here on, you rewrite the packet.

3. Rewriting the tab list entries

PacketEvents exposes player info entries through:

WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event)

EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = packet.getActions()

Only proceed if actions contains any of:

WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER

WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME

Then:

Read the current entries:

List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> dataList = packet.getEntries()

You may mutate PlayerInfo entries in place.

For each PlayerInfo data:

Extract the target profile:

UserProfile profile = data.getGameProfile()

UUID targetId = data.getProfileId()

Resolve alias:

NameViewMode mode = NameViewService.getMode(viewerId)

String alias = LinkedAccountService.getBestAlias(targetId).orElse(null)

If mode == VANILLA or alias == null, keep the original data as is

If you have an alias:

Build a Component for it, for example using Adventure:

Component aliasComponent = Component.text(alias)

Optionally add formatting; do not include the Minecraft name anywhere if you want a pure alias

Set the PacketEvents display name:

data.setDisplayName(aliasComponent)

Then write the list back:

packet.setEntries(dataList)

Result: every time a player appears or their display name is refreshed in the tab list, your viewer sees osu or Discord usernames instead of Minecraft names.

4. Making the toggle actually refresh things

When the viewer flips their setting in your UI, the existing entries will not magically change until something triggers a new PLAYER_INFO packet.

You have two pragmatic options.

Force a re send using hide or show:

For each online target:

Call viewer.hidePlayer(plugin, target)

Then call viewer.showPlayer(plugin, target)

Paper will resend PLAYER_INFO for that viewer, which your PacketEvents listener will rewrite.

Manually send a synthetic PLAYER_INFO_UPDATE packet only to that viewer:

Build a `WrapperPlayServerPlayerInfoUpdate` with action `UPDATE_DISPLAY_NAME`, then send it with:

PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet)

I would pick option two for cleanliness once you are comfortable, and option one as the quick and dirty version while you experiment.

5. Above head name tags:

The client treats the player name tag as part of the player identity; it is derived from the GameProfile name and is not something you can safely replace per viewer without expensive destroy and respawn tricks.

What we do instead:

Hide the vanilla player name tag per viewer:

Send a viewer only scoreboard team update with NameTagVisibility set to NEVER; add the target player's entry name to that team.

Spawn a viewer only label entity:

Spawn a packet only MARKER entity; set CustomName and CustomNameVisible to show the alias.

Attach the label to the target using passengers:

Send SET_PASSENGERS for the target entity id and include the marker entity id in the passenger list; this yields stable motion without teleport spam.

Keep the passenger list merged:

SET_PASSENGERS replaces the whole list; if the server later sends a new passenger list for that vehicle, rewrite it per viewer to append the label passenger again.
