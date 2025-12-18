package com.pocket.rpg.collision;

import com.pocket.rpg.collision.behavior.CollisionBehaviorRegistry;
import com.pocket.rpg.collision.behavior.TileBehavior;
import lombok.Getter;

/**
 * Query API for collision checking during gameplay.
 * <p>
 * Uses:
 * - CollisionMap for tile collision data
 * - CollisionBehaviorRegistry for tile behavior logic
 * - EntityOccupancyMap for entity-to-entity collision
 * <p>
 * This provides the high-level API that GridMovement and other game code uses.
 */
public class CollisionSystem {

    @Getter
    private final CollisionMap collisionMap;

    @Getter
    private final EntityOccupancyMap entityOccupancyMap;

    private final CollisionBehaviorRegistry behaviorRegistry;

    /**
     * Default Z-level for queries (ground level).
     */
    @Getter
    private int defaultZLevel = 0;

    public CollisionSystem(CollisionMap collisionMap) {
        this(collisionMap, new EntityOccupancyMap());
    }

    public CollisionSystem(CollisionMap collisionMap, EntityOccupancyMap entityOccupancyMap) {
        this.collisionMap = collisionMap;
        this.entityOccupancyMap = entityOccupancyMap;
        this.behaviorRegistry = CollisionBehaviorRegistry.getInstance();
    }

    /**
     * Sets the default Z-level for queries that don't specify a level.
     */
    public void setDefaultZLevel(int zLevel) {
        this.defaultZLevel = zLevel;
    }

    // ========================================================================
    // MAIN QUERY API
    // ========================================================================

    /**
     * Checks if movement from one tile to another is allowed.
     * Uses default Z-level and default move context.
     *
     * @param fromX     Starting tile X
     * @param fromY     Starting tile Y
     * @param toX       Target tile X
     * @param toY       Target tile Y
     * @param direction Direction of movement
     * @return MoveResult indicating if move is allowed, blocked, or special
     */
    public MoveResult canMove(int fromX, int fromY, int toX, int toY, Direction direction) {
        return canMove(fromX, fromY, defaultZLevel, toX, toY, defaultZLevel, direction, null);
    }

    /**
     * Checks if movement is allowed with full parameters.
     *
     * @param fromX     Starting tile X
     * @param fromY     Starting tile Y
     * @param fromZ     Starting Z-level
     * @param toX       Target tile X
     * @param toY       Target tile Y
     * @param toZ       Target Z-level
     * @param direction Direction of movement
     * @param entity    Moving entity (for entity collision checks)
     * @return MoveResult with allowed flag, modifier, and reason
     */
    public MoveResult canMove(int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ,
                              Direction direction,
                              Object entity) {
        // Check entity collision first
        if (entityOccupancyMap.isOccupied(toX, toY, toZ, entity)) {
            return MoveResult.BlockedByEntity();
        }

        // Get target tile collision type
        CollisionType targetType = collisionMap.get(toX, toY, toZ);

        // Get behavior for target tile
        TileBehavior behavior = behaviorRegistry.getBehavior(targetType);

        // Create default context
        TileBehavior.MoveContext context = TileBehavior.MoveContext.defaultContext(entity);

        // Check collision using behavior
        return behavior.checkMove(fromX, fromY, fromZ, toX, toY, toZ, direction, context);
    }

    /**
     * Checks movement with custom move context (for swim, fly, etc.)
     */
    public MoveResult canMove(int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ,
                              Direction direction,
                              Object entity,
                              TileBehavior.MoveContext context) {
        // Check entity collision first
        if (entityOccupancyMap.isOccupied(toX, toY, toZ, entity)) {
            return MoveResult.BlockedByEntity();
        }

        // Get target tile collision type
        CollisionType targetType = collisionMap.get(toX, toY, toZ);

        // Get behavior for target tile
        TileBehavior behavior = behaviorRegistry.getBehavior(targetType);

        // Check collision using behavior
        return behavior.checkMove(fromX, fromY, fromZ, toX, toY, toZ, direction, context);
    }

    // ========================================================================
    // SIMPLE QUERIES
    // ========================================================================

    /**
     * Gets the collision type at a tile position (default Z-level).
     */
    public CollisionType getCollisionAt(int tileX, int tileY) {
        return collisionMap.get(tileX, tileY, defaultZLevel);
    }

    /**
     * Gets the collision type at a tile position (specific Z-level).
     */
    public CollisionType getCollisionAt(int tileX, int tileY, int z) {
        return collisionMap.get(tileX, tileY, z);
    }

    /**
     * Checks if a tile is walkable (not blocked by terrain or entities).
     */
    public boolean isWalkable(int tileX, int tileY, Object entity) {
        return isWalkable(tileX, tileY, defaultZLevel, entity);
    }

    /**
     * Checks if a tile is walkable (specific Z-level).
     */
    public boolean isWalkable(int tileX, int tileY, int z, Object entity) {
        // Check entity collision
        if (entityOccupancyMap.isOccupied(tileX, tileY, z, entity)) {
            return false;
        }

        // Check terrain - walkable if any direction allows movement in
        CollisionType type = collisionMap.get(tileX, tileY, z);
        TileBehavior behavior = behaviorRegistry.getBehavior(type);
        TileBehavior.MoveContext context = TileBehavior.MoveContext.defaultContext(entity);

        // Try all four directions - if any allows entry, tile is walkable
        for (Direction dir : Direction.values()) {
            MoveResult result = behavior.checkMove(
                    tileX - dir.dx, tileY - dir.dy, z,
                    tileX, tileY, z,
                    dir, context
            );
            if (result.allowed()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a tile is a ledge.
     */
    public boolean isLedge(int tileX, int tileY) {
        return collisionMap.get(tileX, tileY, defaultZLevel).isLedge();
    }

    /**
     * Checks if a tile has special terrain.
     */
    public boolean isSpecialTerrain(int tileX, int tileY) {
        return collisionMap.get(tileX, tileY, defaultZLevel).isSpecialTerrain();
    }

    /**
     * Checks if a tile is an interaction trigger.
     */
    public boolean isInteractionTrigger(int tileX, int tileY) {
        return collisionMap.get(tileX, tileY, defaultZLevel).isInteractionTrigger();
    }

    // ========================================================================
    // ENTITY MANAGEMENT (with Z-level support)
    // ========================================================================

    /**
     * Registers an entity at a tile position (default Z-level).
     */
    public void registerEntity(Object entity, int tileX, int tileY) {
        entityOccupancyMap.register(entity, tileX, tileY, defaultZLevel);
    }

    /**
     * Registers an entity at a tile position (specific Z-level).
     */
    public void registerEntity(Object entity, int tileX, int tileY, int z) {
        entityOccupancyMap.register(entity, tileX, tileY, z);
    }

    /**
     * Unregisters an entity from a tile position (default Z-level).
     */
    public void unregisterEntity(Object entity, int tileX, int tileY) {
        entityOccupancyMap.unregister(entity, tileX, tileY, defaultZLevel);
    }

    /**
     * Unregisters an entity from a tile position (specific Z-level).
     */
    public void unregisterEntity(Object entity, int tileX, int tileY, int z) {
        entityOccupancyMap.unregister(entity, tileX, tileY, z);
    }

    /**
     * Moves an entity registration from one tile to another (default Z-level).
     */
    public void moveEntity(Object entity, int fromX, int fromY, int toX, int toY) {
        entityOccupancyMap.move(entity, fromX, fromY, defaultZLevel, toX, toY, defaultZLevel);
    }

    /**
     * Moves an entity registration from one tile to another (with Z-level).
     */
    public void moveEntity(Object entity, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        entityOccupancyMap.move(entity, fromX, fromY, fromZ, toX, toY, toZ);
    }

    // ========================================================================
    // TILE BEHAVIOR TRIGGERS
    // ========================================================================

    /**
     * Triggers tile behavior onEnter event.
     */
    public void triggerEnter(int tileX, int tileY, int z, Object entity) {
        CollisionType type = collisionMap.get(tileX, tileY, z);
        TileBehavior behavior = behaviorRegistry.getBehavior(type);
        TileBehavior.MoveContext context = TileBehavior.MoveContext.defaultContext(entity);
        behavior.onEnter(tileX, tileY, z, context);
    }

    /**
     * Triggers tile behavior onExit event.
     */
    public void triggerExit(int tileX, int tileY, int z, Object entity) {
        CollisionType type = collisionMap.get(tileX, tileY, z);
        TileBehavior behavior = behaviorRegistry.getBehavior(type);
        TileBehavior.MoveContext context = TileBehavior.MoveContext.defaultContext(entity);
        behavior.onExit(tileX, tileY, z, context);
    }

    @Override
    public String toString() {
        return String.format("CollisionSystem[map=%s, entities=%s]",
                collisionMap, entityOccupancyMap);
    }
}