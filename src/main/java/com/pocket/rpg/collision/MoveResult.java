package com.pocket.rpg.collision;

/**
 * Result of a collision check for movement.
 * <p>
 * Contains:
 * - Whether movement is allowed
 * - Movement modifier (NORMAL, JUMP, SWIM, etc.)
 * - Reason for blocking (if blocked)
 * - Whether entity collision occurred
 */
public record MoveResult(boolean allowed, MovementModifier modifier, String blockedReason, boolean entityBlocked) {
    /**
     * Creates an allowed move result with normal movement.
     */
    public static MoveResult Allowed() {
        return new MoveResult(true, MovementModifier.NORMAL, null, false);
    }

    /**
     * Creates an allowed move result with a modifier.
     */
    public static MoveResult Allowed(MovementModifier modifier) {
        return new MoveResult(true, modifier, null, false);
    }

    /**
     * Creates a blocked result with a reason.
     */
    public static MoveResult Blocked(String reason) {
        return new MoveResult(false, MovementModifier.NORMAL, reason, false);
    }

    /**
     * Creates a blocked result due to entity collision.
     */
    public static MoveResult BlockedByEntity() {
        return new MoveResult(false, MovementModifier.NORMAL, "Entity blocking", true);
    }

    /**
     * Checks if this is a jump move.
     */
    public boolean isJump() {
        return allowed && modifier == MovementModifier.JUMP;
    }

    /**
     * Checks if movement was blocked by terrain.
     */
    public boolean isTerrainBlocked() {
        return !allowed && !entityBlocked;
    }
}