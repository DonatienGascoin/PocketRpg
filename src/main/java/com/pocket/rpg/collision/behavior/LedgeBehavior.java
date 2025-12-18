package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;

/**
 * Behavior for ledge tiles - one-way jumps.
 * <p>
 * Ledges work like this:
 * - You CAN jump in the ledge's direction (triggers JUMP)
 * - You CANNOT enter from the opposite direction (BLOCKED)
 * - You CAN walk onto a ledge from perpendicular directions (ALLOWED)
 * <p>
 * Example: LEDGE_DOWN (↓)
 * - Moving DOWN onto ledge: JUMP (allowed)
 * - Moving UP onto ledge: BLOCKED (can't climb ledge from below)
 * - Moving LEFT/RIGHT onto ledge: ALLOWED (can walk alongside ledge)
 */
public class LedgeBehavior implements TileBehavior {

    private final CollisionType ledgeType;

    public LedgeBehavior(CollisionType ledgeType) {
        if (!ledgeType.isLedge()) {
            throw new IllegalArgumentException("CollisionType must be a ledge: " + ledgeType);
        }
        this.ledgeType = ledgeType;
    }

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        Direction ledgeDirection = ledgeType.getLedgeDirection();

        // Moving IN the ledge direction = JUMP
        if (direction == ledgeDirection) {
            return MoveResult.Allowed(MovementModifier.JUMP);
        }

        // Moving OPPOSITE the ledge direction = BLOCKED
        if (direction == ledgeDirection.opposite()) {
            return MoveResult.Blocked("Cannot climb ledge from this side");
        }

        // Moving perpendicular = ALLOWED (walking alongside ledge)
        return MoveResult.Allowed();
    }

    @Override
    public CollisionType getType() {
        return ledgeType;
    }
}