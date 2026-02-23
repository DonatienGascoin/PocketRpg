package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;

/**
 * Behavior for walkable cave tiles - triggers random encounters.
 * <p>
 * Walkable, but may trigger wild Pokémon encounters.
 * GridMovement or encounter system can check the ENCOUNTER modifier.
 */
public class CaveBehavior implements TileBehavior {

    @Override
    public MoveResult checkMove(int fromX, int fromY, int fromZ,
                                int toX, int toY, int toZ,
                                Direction direction,
                                MoveContext context) {
        // Is walkable
        // ENCOUNTER modifier signals that this tile can trigger encounters
        if (context.triggersEncounters()) {
            return MoveResult.Allowed(MovementModifier.ENCOUNTER);
        }

        return MoveResult.Allowed();
    }

    @Override
    public void onEnter(int tileX, int tileY, int tileZ, MoveContext context) {
        // NOTE: Actual encounter check would happen here or in GridMovement
        // For now, just mark the tile as an encounter zone via modifier
    }

    @Override
    public CollisionType getType() {
        return CollisionType.CAVE_PATH;
    }
}