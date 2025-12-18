package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;

/**
 * Behavior for sand tiles - slows movement.
 */
public class SandBehavior implements TileBehavior {

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        // Sand is walkable but slows movement
        return MoveResult.Allowed(MovementModifier.SLOW);
    }

    @Override
    public CollisionType getType() {
        return CollisionType.SAND;
    }
}