package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;

/**
 * Behavior for water tiles - requires swimming ability.
 */
public class WaterBehavior implements TileBehavior {

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        // Flying entities can pass over water
        if (context.canFly()) {
            return MoveResult.Allowed();
        }

        // Swimming entities can enter water
        if (context.canSwim()) {
            return MoveResult.Allowed(MovementModifier.SWIM);
        }

        // Ground-based entities cannot enter water
        return MoveResult.Blocked("Cannot swim");
    }

    @Override
    public CollisionType getType() {
        return CollisionType.WATER;
    }
}