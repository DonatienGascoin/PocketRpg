package com.pocket.rpg.collision;

/**
 * Named elevation levels for intuitive display.
 * Maps integer z-levels to human-readable names.
 */
public enum ElevationLevel {
    GROUND(0, "Ground"),
    BRIDGE(1, "Bridge"),
    FLOOR_2(2, "Floor 2"),
    ROOF(3, "Roof");

    private final int level;
    private final String displayName;

    ElevationLevel(int level, String displayName) {
        this.level = level;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the ElevationLevel for a given integer level.
     * Returns null for levels outside the defined range.
     */
    public static ElevationLevel fromLevel(int level) {
        for (ElevationLevel el : values()) {
            if (el.level == level) {
                return el;
            }
        }
        return null;
    }

    /**
     * Gets the display name for a given integer level.
     * Falls back to "Level X" for undefined levels.
     */
    public static String getDisplayName(int level) {
        ElevationLevel el = fromLevel(level);
        return el != null ? el.displayName : "Level " + level;
    }

    /**
     * Formats an elevation change as "Source → Destination".
     * Example: fromLevel=0, change=+1 returns "Ground → Bridge"
     */
    public static String formatChange(int fromLevel, int elevationChange) {
        int toLevel = fromLevel + elevationChange;
        return getDisplayName(fromLevel) + " → " + getDisplayName(toLevel);
    }

    /**
     * Formats an elevation change with direction indicator.
     * Example: change=+1 returns "↑ to Bridge" (assuming destination is Bridge)
     */
    public static String formatChangeShort(int fromLevel, int elevationChange) {
        int toLevel = fromLevel + elevationChange;
        String arrow = elevationChange > 0 ? "↑" : "↓";
        return arrow + " " + getDisplayName(toLevel);
    }

    /**
     * Gets all defined elevation levels.
     */
    public static ElevationLevel[] getAll() {
        return values();
    }

    /**
     * Gets the maximum defined level.
     */
    public static int getMaxLevel() {
        return ROOF.level;
    }
}
