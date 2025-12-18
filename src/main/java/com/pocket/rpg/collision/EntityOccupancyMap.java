package com.pocket.rpg.collision;

import java.util.*;

/**
 * Tracks which entities occupy which tiles.
 * <p>
 * Used for entity-to-entity collision checks:
 * - Player cannot walk through NPCs
 * - NPCs cannot walk through each other
 * - Multiple entities can occupy the same tile if allowed (flying over ground entity)
 * <p>
 * Thread-safe for concurrent access.
 */
public class EntityOccupancyMap {

    // Map of (x,y,z) -> set of entities at that position
    private final Map<Long, Set<Object>> occupancy = new HashMap<>();

    /**
     * Registers an entity at a tile position.
     *
     * @param entity Entity to register
     * @param tileX  Tile X coordinate
     * @param tileY  Tile Y coordinate
     * @param z      Z-level
     */
    public synchronized void register(Object entity, int tileX, int tileY, int z) {
        long key = key(tileX, tileY, z);
        occupancy.computeIfAbsent(key, k -> new HashSet<>()).add(entity);
    }

    /**
     * Unregisters an entity from a tile position.
     *
     * @param entity Entity to unregister
     * @param tileX  Tile X coordinate
     * @param tileY  Tile Y coordinate
     * @param z      Z-level
     */
    public synchronized void unregister(Object entity, int tileX, int tileY, int z) {
        long key = key(tileX, tileY, z);
        Set<Object> entities = occupancy.get(key);
        if (entities != null) {
            entities.remove(entity);
            if (entities.isEmpty()) {
                occupancy.remove(key);
            }
        }
    }

    /**
     * Moves an entity from one tile to another.
     *
     * @param entity Entity to move
     * @param fromX  Old tile X
     * @param fromY  Old tile Y
     * @param fromZ  Old Z-level
     * @param toX    New tile X
     * @param toY    New tile Y
     * @param toZ    New Z-level
     */
    public synchronized void move(Object entity, int fromX, int fromY, int fromZ,
                                  int toX, int toY, int toZ) {
        unregister(entity, fromX, fromY, fromZ);
        register(entity, toX, toY, toZ);
    }

    /**
     * Checks if a tile is occupied by any entity (excluding the given entity).
     *
     * @param tileX  Tile X coordinate
     * @param tileY  Tile Y coordinate
     * @param z      Z-level
     * @param entity Entity to exclude from check (usually the one trying to move)
     * @return true if tile is occupied by another entity
     */
    public synchronized boolean isOccupied(int tileX, int tileY, int z, Object entity) {
        long key = key(tileX, tileY, z);
        Set<Object> entities = occupancy.get(key);
        if (entities == null || entities.isEmpty()) {
            return false;
        }

        // Check if any other entity is present
        if (entity == null) {
            return true;
        }

        return entities.size() > 1 || !entities.contains(entity);
    }

    /**
     * Gets all entities at a tile position.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @param z     Z-level
     * @return Set of entities (empty if none)
     */
    public synchronized Set<Object> getEntities(int tileX, int tileY, int z) {
        long key = key(tileX, tileY, z);
        Set<Object> entities = occupancy.get(key);
        return entities != null ? new HashSet<>(entities) : Collections.emptySet();
    }

    /**
     * Clears all entity registrations.
     */
    public synchronized void clear() {
        occupancy.clear();
    }

    /**
     * Gets the total number of registered entities across all tiles.
     */
    public synchronized int getEntityCount() {
        int count = 0;
        for (Set<Object> entities : occupancy.values()) {
            count += entities.size();
        }
        return count;
    }

    /**
     * Encodes tile coordinates into a 64-bit key.
     * Uses (x, y, z) where z is stored in the upper bits.
     */
    private static long key(int x, int y, int z) {
        // Pack: z (16 bits) | x (24 bits) | y (24 bits)
        return ((long) z << 48) | ((long) (x & 0xFFFFFF) << 24) | (y & 0xFFFFFF);
    }

    @Override
    public String toString() {
        return String.format("EntityOccupancyMap[occupiedTiles=%d, totalEntities=%d]",
                occupancy.size(), getEntityCount());
    }
}