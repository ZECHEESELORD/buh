Formatted Username Guide:
Serve the `<prefix> <nameColour>USERNAME` combo anywhere you render names: scoreboards, menus, chat snippets.

What it does:
1) Pulls LuckPerms prefix (legacy ampersand codes are respected) and the meta key `nameColour`.
2) Builds a prefix component and a coloured name component, plus a convenience `displayName()` that joins them with a space.
3) Falls back to a plain white name when LuckPerms is not present or the meta keys are missing.

Getting the service:
```java
public final class Nameplates {
    private final FormattedUsernameService usernames;

    public Nameplates(BuhPlugin plugin) {
        this.usernames = plugin.formattedUsernameService()
            .orElseThrow(() -> new IllegalStateException("Username formatter unavailable"));
    }
}
```

Rendering a player’s label:
```java
usernames.username(player)
    .thenAccept(format -> {
        Component name = format.displayName(); // <prefix> <nameColour>USERNAME
        player.sendActionBar(name);
    });
```

Rendering from UUID + cached username:
```java
UUID playerId = ...;
String cachedName = "...";
usernames.username(playerId, cachedName)
    .thenAccept(format -> scoreboardLine.setText(format.displayName()));
```

Meta keys read:
1) `prefix`: LuckPerms prefix (may be empty).
2) `nameColour`: Hex `#RRGGBB` or a `NamedTextColor` name.

Fallbacks and behaviour:
1) Missing LuckPerms: prefix becomes empty; name colour becomes white.
2) Bad colour strings: ignored in favour of white.
3) The call is asynchronous; keep continuations off the main thread if you add heavy work, and never block the server thread.
