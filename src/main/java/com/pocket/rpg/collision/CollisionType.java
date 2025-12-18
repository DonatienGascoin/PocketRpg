package com.pocket.rpg.collision;

import lombok.Getter;

/**
 * Collision types for tile-based collision system.
 * <p>
 * Each type references a TileBehavior that defines its collision logic.
 * <p>
 * Categories:
 * - NONE: Walkable, no collision
 * - SOLID: Blocks all movement
 * - LEDGE_*: One-way jumps (Pokémon-style)
 * - Special terrain: WATER, TALL_GRASS, ICE, SAND
 * - Interaction zones: WARP, DOOR, SCRIPT_TRIGGER
 */
@Getter
public enum CollisionType {
    /**
     * No collision - fully walkable
     */
    NONE(0, "None", new float[]{0.0f, 0.0f, 0.0f, 0.0f}, null),

    /**
     * Solid wall - blocks all movement
     */
    SOLID(1, "Solid", new float[]{0.8f, 0.2f, 0.2f, 0.6f}, null),

    /**
     * Ledge - can jump down (from top to bottom)
     */
    LEDGE_DOWN(2, "Ledge ↓", new float[]{1.0f, 0.5f, 0.0f, 0.6f}, Direction.DOWN),

    /**
     * Ledge - can jump up (from bottom to top)
     */
    LEDGE_UP(3, "Ledge ↑", new float[]{1.0f, 0.7f, 0.0f, 0.6f}, Direction.UP),

    /**
     * Ledge - can jump left (from right to left)
     */
    LEDGE_LEFT(4, "Ledge ←", new float[]{1.0f, 0.6f, 0.0f, 0.6f}, Direction.LEFT),

    /**
     * Ledge - can jump right (from left to right)
     */
    LEDGE_RIGHT(5, "Ledge →", new float[]{1.0f, 0.65f, 0.0f, 0.6f}, Direction.RIGHT),

    /**
     * Water - triggers swimming state
     */
    WATER(6, "Water", new float[]{0.2f, 0.5f, 0.9f, 0.6f}, null),

    /**
     * Tall grass - triggers encounter checks
     */
    TALL_GRASS(7, "Tall Grass", new float[]{0.3f, 0.8f, 0.3f, 0.6f}, null),

    /**
     * Ice - sliding movement
     */
    ICE(8, "Ice", new float[]{0.7f, 0.9f, 1.0f, 0.6f}, null),

    /**
     * Sand - slower movement
     */
    SAND(9, "Sand", new float[]{0.9f, 0.85f, 0.6f, 0.6f}, null),

    /**
     * Warp zone - triggers scene transition
     */
    WARP(10, "Warp", new float[]{0.8f, 0.3f, 0.8f, 0.6f}, null),

    /**
     * Door - triggers door interaction
     */
    DOOR(11, "Door", new float[]{0.6f, 0.4f, 0.2f, 0.6f}, null),

    /**
     * Script trigger - executes script on step
     */
    SCRIPT_TRIGGER(12, "Script", new float[]{0.9f, 0.9f, 0.3f, 0.6f}, null);

    /**
     * Numeric ID for serialization
     */
    private final int id;

    /**
     * Display name for UI
     */
    private final String displayName;

    /**
     * RGBA color for editor overlay
     */
    private final float[] overlayColor;

    /**
     * Ledge direction (null if not a ledge)
     */
    private final Direction ledgeDirection;

    CollisionType(int id, String displayName, float[] overlayColor, Direction ledgeDirection) {
        this.id = id;
        this.displayName = displayName;
        this.overlayColor = overlayColor;
        this.ledgeDirection = ledgeDirection;
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
     * Checks if this is a ledge type.
     */
    public boolean isLedge() {
        return ledgeDirection != null;
    }

    /**
     * Checks if this type requires special handling (water, grass, ice, etc.)
     */
    public boolean isSpecialTerrain() {
        return this == WATER || this == TALL_GRASS || this == ICE || this == SAND;
    }

    /**
     * Checks if this type triggers an interaction (warp, door, script)
     */
    public boolean isInteractionTrigger() {
        return this == WARP || this == DOOR || this == SCRIPT_TRIGGER;
    }

    @Override
    public String toString() {
        return displayName;
    }
}