package com.pocket.rpg.collision;

/**
 * Cardinal directions for movement and collision checking.
 * <p>
 * Used by:
 * - GridMovement for player movement
 * - CollisionSystem for directional checks
 * - Ledge behaviors for one-way jumping
 */
public enum Direction {
    UP(0, 1),
    DOWN(0, -1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * Gets the opposite direction.
     */
    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    /**
     * Checks if this is a vertical direction.
     */
    public boolean isVertical() {
        return this == UP || this == DOWN;
    }

    /**
     * Checks if this is a horizontal direction.
     */
    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }
}