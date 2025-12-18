package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;

/**
 * Behavior for solid tiles - blocks all movement.
 */
public class SolidBehavior implements TileBehavior {

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        // Flying entities can pass over solid tiles
        if (context.canFly()) {
            return MoveResult.Allowed();
        }

        return MoveResult.Blocked("Solid wall");
    }

    @Override
    public CollisionType getType() {
        return CollisionType.SOLID;
    }
}