package com.pocket.rpg.collision.behavior;

import com.pocket.rpg.collision.CollisionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for TileBehavior implementations.
 * <p>
 * Maps CollisionType to behavior instances.
 * Allows behaviors to be swapped or extended without modifying core collision code.
 */
public class CollisionBehaviorRegistry {

    private static final CollisionBehaviorRegistry INSTANCE = new CollisionBehaviorRegistry();

    private final Map<CollisionType, TileBehavior> behaviors = new HashMap<>();

    private CollisionBehaviorRegistry() {
        registerDefaultBehaviors();
    }

    /**
     * Gets the singleton instance.
     */
    public static CollisionBehaviorRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers default behaviors for all collision types.
     */
    private void registerDefaultBehaviors() {
        // Solid
        register(new SolidBehavior());

        // Ledges
        register(new LedgeBehavior(CollisionType.LEDGE_DOWN));
        register(new LedgeBehavior(CollisionType.LEDGE_UP));
        register(new LedgeBehavior(CollisionType.LEDGE_LEFT));
        register(new LedgeBehavior(CollisionType.LEDGE_RIGHT));

        // Special terrain
        register(new WaterBehavior());
        register(new IceBehavior());
        register(new SandBehavior());
        register(new TallGrassBehavior());

        // NONE, WARP, DOOR, SCRIPT_TRIGGER use default behavior (allow movement)
    }

    /**
     * Registers a behavior for its collision type.
     */
    public void register(TileBehavior behavior) {
        behaviors.put(behavior.getType(), behavior);
    }

    /**
     * Gets the behavior for a collision type.
     * Returns default behavior if none registered.
     */
    public TileBehavior getBehavior(CollisionType type) {
        return behaviors.getOrDefault(type, DEFAULT_BEHAVIOR);
    }

    /**
     * Checks if a behavior is registered for a type.
     */
    public boolean hasBehavior(CollisionType type) {
        return behaviors.containsKey(type);
    }

    /**
     * Default behavior - allows all movement with no modifiers.
     * Used for NONE and interaction triggers.
     */
    private static final TileBehavior DEFAULT_BEHAVIOR = new TileBehavior() {
        @Override
        public com.pocket.rpg.collision.MoveResult checkMove(
                int fromX, int fromY, int fromZ,
                int toX, int toY, int toZ,
                com.pocket.rpg.collision.Direction direction,
                MoveContext context) {
            return com.pocket.rpg.collision.MoveResult.Allowed();
        }

        @Override
        public CollisionType getType() {
            return CollisionType.NONE;
        }
    };
}