package com.pocket.rpg.collision;

import com.pocket.rpg.editor.core.MaterialIcons;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * Collision types for tile-based collision system.
 * <p>
 * Each type references a TileBehavior that defines its collision logic.
 * Types are grouped by {@link CollisionCategory} for UI organization.
 * <p>
 * Categories:
 * - MOVEMENT: Basic walkability (None, Solid)
 * - LEDGE: One-way jumps (Pokémon-style)
 * - TERRAIN: Special effects (Water, Grass, Ice, Sand)
 * - ELEVATION: Floor transitions (Stairs)
 * <p>
 * Note: WARP, DOOR, SPAWN_POINT are now entity-based components
 * (WarpZone, Door, SpawnPoint) instead of collision types.
 */
@Getter
public enum CollisionType {
    // === MOVEMENT ===
    NONE(0, "None", CollisionCategory.MOVEMENT,
            "No collision - fully walkable",
            new float[]{0.0f, 0.0f, 0.0f, 0.0f}, null, null, 0),

    SOLID(1, "Solid", CollisionCategory.MOVEMENT,
            "Solid wall - blocks all movement",
            new float[]{0.8f, 0.2f, 0.2f, 0.6f}, null, MaterialIcons.Block, 0),

    // === LEDGES ===
    LEDGE_DOWN(2, "Ledge " + MaterialIcons.ArrowDownward, CollisionCategory.LEDGE,
            "Ledge - can jump down (south)",
            new float[]{1.0f, 0.5f, 0.0f, 0.6f}, Direction.DOWN, MaterialIcons.ArrowDownward, 0),

    LEDGE_UP(3, "Ledge " + MaterialIcons.ArrowUpward, CollisionCategory.LEDGE,
            "Ledge - can jump up (north)",
            new float[]{1.0f, 0.7f, 0.0f, 0.6f}, Direction.UP, MaterialIcons.ArrowUpward, 0),

    LEDGE_LEFT(4, "Ledge " + MaterialIcons.ArrowBack, CollisionCategory.LEDGE,
            "Ledge - can jump left (west)",
            new float[]{1.0f, 0.6f, 0.0f, 0.6f}, Direction.LEFT, MaterialIcons.ArrowBack, 0),

    LEDGE_RIGHT(5, "Ledge " + MaterialIcons.ArrowForward, CollisionCategory.LEDGE,
            "Ledge - can jump right (east)",
            new float[]{1.0f, 0.65f, 0.0f, 0.6f}, Direction.RIGHT, MaterialIcons.ArrowForward, 0),

    // === ELEVATED LEDGES (ledge + elevation change) ===
    // Purple-ish tones to distinguish from regular orange ledges
    LEDGE_DOWN_ELEV(20, "Ledge " + MaterialIcons.ArrowDownward + " -1", CollisionCategory.LEDGE,
            "Elevated ledge - jump down (south) and descend one floor",
            new float[]{0.7f, 0.3f, 0.9f, 0.7f}, Direction.DOWN, MaterialIcons.ArrowDownward, -1),

    LEDGE_UP_ELEV(21, "Ledge " + MaterialIcons.ArrowUpward + " +1", CollisionCategory.LEDGE,
            "Elevated ledge - jump up (north) and ascend one floor",
            new float[]{0.5f, 0.3f, 0.9f, 0.7f}, Direction.UP, MaterialIcons.ArrowUpward, 1),

    LEDGE_LEFT_ELEV(22, "Ledge " + MaterialIcons.ArrowBack + " -1", CollisionCategory.LEDGE,
            "Elevated ledge - jump left (west) and descend one floor",
            new float[]{0.6f, 0.3f, 0.9f, 0.7f}, Direction.LEFT, MaterialIcons.ArrowBack, -1),

    LEDGE_RIGHT_ELEV(23, "Ledge " + MaterialIcons.ArrowForward + " +1", CollisionCategory.LEDGE,
            "Elevated ledge - jump right (east) and ascend one floor",
            new float[]{0.55f, 0.3f, 0.9f, 0.7f}, Direction.RIGHT, MaterialIcons.ArrowForward, 1),

    // === TERRAIN ===
    WATER(6, "Water", CollisionCategory.TERRAIN,
            "Water - requires swimming ability",
            new float[]{0.2f, 0.5f, 0.9f, 0.6f}, null, MaterialIcons.Water, 0),

    TALL_GRASS(7, "Tall Grass", CollisionCategory.TERRAIN,
            "Tall grass - triggers wild encounters",
            new float[]{0.3f, 0.8f, 0.3f, 0.6f}, null, MaterialIcons.Grass, 0),

    ICE(8, "Ice", CollisionCategory.TERRAIN,
            "Ice - causes sliding movement",
            new float[]{0.7f, 0.9f, 1.0f, 0.6f}, null, MaterialIcons.AcUnit, 0),

    SAND(9, "Sand", CollisionCategory.TERRAIN,
            "Sand - slows movement",
            new float[]{0.9f, 0.85f, 0.6f, 0.6f}, null, MaterialIcons.Terrain, 0),

    // === ELEVATION ===
    STAIRS(13, "Stairs", CollisionCategory.ELEVATION,
            "Bidirectional stairs - elevation based on exit direction",
            new float[]{0.5f, 0.7f, 0.9f, 0.6f}, null, MaterialIcons.Stairs, 0);

    /**
     * Numeric ID for serialization
     */
    private final int id;

    /**
     * Display name for UI
     */
    private final String displayName;

    /**
     * Category for UI grouping
     */
    private final CollisionCategory category;

    /**
     * Description for tooltips
     */
    private final String description;

    /**
     * RGBA color for editor overlay
     */
    private final float[] overlayColor;

    /**
     * Ledge direction (null if not a ledge)
     */
    private final Direction ledgeDirection;

    /**
     * Material icon for scene view (null if none)
     */
    private final String icon;

    /**
     * Elevation change when entering this tile (for elevated ledges).
     * 0 = no change, positive = go up, negative = go down.
     */
    private final int elevationChange;

    CollisionType(int id, String displayName, CollisionCategory category, String description,
                  float[] overlayColor, Direction ledgeDirection, String icon, int elevationChange) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
        this.overlayColor = overlayColor;
        this.ledgeDirection = ledgeDirection;
        this.icon = icon;
        this.elevationChange = elevationChange;
    }

    /**
     * Checks if this type changes elevation when entered.
     */
    public boolean hasElevationChange() {
        return elevationChange != 0;
    }

    /**
     * Gets CollisionType by ID.
     *
     * @param id Numeric ID
     * @return CollisionType, or NONE if invalid
     */
    public static CollisionType fromId(int id) {
        for (CollisionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * Gets all types in a specific category.
     */
    public static List<CollisionType> getByCategory(CollisionCategory category) {
        return Arrays.stream(values())
                .filter(t -> t.category == category)
                .toList();
    }

    /**
     * Checks if this is a ledge type.
     */
    public boolean isLedge() {
        return ledgeDirection != null;
    }

    /**
     * Checks if this type has an icon for scene view rendering.
     */
    public boolean hasIcon() {
        return icon != null;
    }

    /**
     * Returns true if this type requires trigger metadata configuration.
     */
    public boolean requiresMetadata() {
        return this == STAIRS;
    }

    /**
     * Returns true if this type is a trigger (fires events when stepped on).
     */
    public boolean isTrigger() {
        return category == CollisionCategory.TRIGGER
                || category == CollisionCategory.ELEVATION;
    }

    /**
     * Checks if this type requires special handling (water, grass, ice, etc.)
     */
    public boolean isSpecialTerrain() {
        return this == WATER || this == TALL_GRASS || this == ICE || this == SAND;
    }

    /**
     * Checks if this type triggers an interaction.
     * Note: WARP and DOOR are now entity-based (WarpZone, Door components).
     */
    public boolean isInteractionTrigger() {
        return false;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
