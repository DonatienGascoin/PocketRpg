package com.pocket.rpg.collision;

import lombok.Getter;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Categories for grouping collision types in the editor UI.
 * <p>
 * Types are grouped by category in the CollisionPanel for better organization.
 */
@Getter
public enum CollisionCategory {
    /**
     * Basic movement types (None, Solid)
     */
    MOVEMENT("Movement", 0),

    /**
     * One-way ledge jumps
     */
    LEDGE("Ledges", 1),

    /**
     * Special terrain effects (water, grass, ice, sand)
     */
    TERRAIN("Terrain", 2),

    /**
     * Elevation transitions (stairs up/down)
     */
    ELEVATION("Elevation", 3),

    /**
     * Interactive triggers (warp, door)
     */
    TRIGGER("Triggers", 4);

    private final String displayName;
    private final int order;

    CollisionCategory(String displayName, int order) {
        this.displayName = displayName;
        this.order = order;
    }

    /**
     * Returns all categories sorted by display order.
     */
    public static CollisionCategory[] inOrder() {
        return Arrays.stream(values())
                .sorted(Comparator.comparingInt(CollisionCategory::getOrder))
                .toArray(CollisionCategory[]::new);
    }
}
