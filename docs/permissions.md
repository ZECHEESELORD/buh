LuckPerms Integration Guide:
Here is how to tap into staff checks while staying decoupled.

What you get:
The LuckPerms module discovers LuckPerms through the service manager. When present, it builds a `StaffService` that returns true for anyone inheriting one of the default staff groups (owner, admin, manager, staff, moderator, mod, helper) or holding the permission `fulcrum.staff`. If LuckPerms is absent, the service falls back to always false.

Obtaining the service:
```java
public final class SomeFeature {
    private final StaffService staff;

    public SomeFeature(BuhPlugin plugin) {
        this.staff = plugin.staffService()
            .orElseThrow(() -> new IllegalStateException("Staff service unavailable"));
    }
}
```

Checking staff status:
```java
staff.isStaff(player.getUniqueId())
    .thenAccept(isStaff -> {
        if (isStaff) {
            // grant access
        }
    });
```

Notes:
1) The call is asynchronous; never block the server thread waiting for it. Chain continuations instead.
2) The defaults are hardcoded for now. If you need a different group set or permission node, we can surface configuration on the module.
3) The plugin declares a soft-depend on LuckPerms, so the module initializes after it when available.
