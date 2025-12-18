package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;

/**
 * Behavior for ice tiles - causes sliding.
 * <p>
 * Entities slide across ice until they hit a non-ice tile or obstacle.
 * This is indicated by the SLIDE movement modifier.
 */
public class IceBehavior implements TileBehavior {

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        // Ice is walkable but causes sliding
        return MoveResult.Allowed(MovementModifier.SLIDE);
    }

    @Override
    public CollisionType getType() {
        return CollisionType.ICE;
    }
}