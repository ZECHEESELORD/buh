package sh.harold.fulcrum.common.loader;

/**
 * High-level grouping for Fulcrum modules so configs stay organized as the list grows.
 */
public enum ModuleCategory {
    API,
    ECONOMY,
    GAMEPLAY,
    UTILITY,
    HUD,
    SOCIAL,
    STAFF,
    PLAYER,
    ADMIN,
    WORLD,
    MISC;

    public String configKey() {
        return name().toLowerCase();
    }
}
