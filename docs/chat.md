Chat Channels & Formatting:
Players speak through a formatted channel that pulls LuckPerms meta to decorate messages.

Format:
`<prefix> <nameColour>USERNAME<gray>: <chatColour>message`
- `prefix`: LuckPerms prefix (legacy ampersand codes supported). Empty if none.
- `nameColour`: LuckPerms meta key `nameColour`, falls back to white.
- `chatColour`: LuckPerms meta key `chatColour`, falls back to white.

Behaviour:
- We listen to `AsyncChatEvent` (Paper) and set a custom renderer.
- If LuckPerms is missing, we fall back to white names and messages with no prefix.
- Missing meta keys simply revert to white; no errors are thrown.

Supported colour inputs:
- Hex strings (`#RRGGBB` or `RRGGBB`).
- Named colours matching `NamedTextColor` (e.g., `red`, `gold`, `aqua`).

Extending:
- Want multiple channels? Add a new listener with your own permission gates and reuse `ChatFormatService` (feed it the `LuckPerms` instance from `BuhPlugin` → `LuckPermsModule`).
- To tweak defaults (e.g., different meta keys), adjust `ChatFormatService` in `plugin/chat/ChatFormatService.java`.

Username Formatting API:
Need the standalone `<prefix> <nameColour>USERNAME` component for scoreboards or menus? Reach for the async `FormattedUsernameService`.
* Pull it from `LuckPermsModule#formattedUsernameService()` or `BuhPlugin#formattedUsernameService()`.
* It reads the same LuckPerms prefix and `nameColour` meta that chat uses, returning a ready-to-use display name component.
* No LuckPerms present? The service still answers with a plain white name so callers can keep rendering without extra guards.
