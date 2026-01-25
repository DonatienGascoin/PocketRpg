package com.pocket.rpg.collision;

import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;

import java.util.*;

/**
 * Unified system tracking all entity components per tile.
 * <p>
 * Used for:
 * - Blocking queries (before movement) - checks StaticOccupant, GridMovement
 * - Trigger queries (after movement) - finds TriggerZone components
 * - Future: damage zones, cutscene triggers, etc.
 * <p>
 * Thread-safe for concurrent access.
 */
public class TileEntityMap {

    private final Map<Long, Set<Component>> entities = new HashMap<>();

    // ========================================================================
    // REGISTRATION
    // ========================================================================

    /**
     * Registers a component at a tile position.
     */
    public synchronized void register(Component comp, TileCoord tile) {
        long key = tile.pack();
        entities.computeIfAbsent(key, k -> new HashSet<>()).add(comp);
    }

    /**
     * Registers a component at a tile position (x, y, z).
     */
    public synchronized void register(Component comp, int x, int y, int z) {
        register(comp, new TileCoord(x, y, z));
    }

    /**
     * Unregisters a component from a tile position.
     */
    public synchronized void unregister(Component comp, TileCoord tile) {
        long key = tile.pack();
        Set<Component> set = entities.get(key);
        if (set != null) {
            set.remove(comp);
            if (set.isEmpty()) {
                entities.remove(key);
            }
        }
    }

    /**
     * Unregisters a component from a tile position (x, y, z).
     */
    public synchronized void unregister(Component comp, int x, int y, int z) {
        unregister(comp, new TileCoord(x, y, z));
    }

    /**
     * Moves a component from one tile to another.
     */
    public synchronized void move(Component comp, TileCoord from, TileCoord to) {
        unregister(comp, from);
        register(comp, to);
    }

    /**
     * Moves a component from one tile to another (x, y, z).
     */
    public synchronized void move(Component comp, int fromX, int fromY, int fromZ,
                                  int toX, int toY, int toZ) {
        move(comp, new TileCoord(fromX, fromY, fromZ), new TileCoord(toX, toY, toZ));
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Gets all components at a tile.
     */
    public synchronized Set<Component> getAll(TileCoord tile) {
        Set<Component> set = entities.get(tile.pack());
        return set != null ? new HashSet<>(set) : Collections.emptySet();
    }

    /**
     * Gets all components at a tile (x, y, z).
     */
    public synchronized Set<Component> getAll(int x, int y, int z) {
        return getAll(new TileCoord(x, y, z));
    }

    /**
     * Gets components of a specific type at a tile.
     */
    public synchronized <T> List<T> get(TileCoord tile, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Component c : getAll(tile)) {
            if (type.isInstance(c)) {
                result.add(type.cast(c));
            }
        }
        return result;
    }

    /**
     * Gets components of a specific type at a tile (x, y, z).
     */
    public synchronized <T> List<T> get(int x, int y, int z, Class<T> type) {
        return get(new TileCoord(x, y, z), type);
    }

    /**
     * Checks if tile has any component of the given type.
     */
    public synchronized <T> boolean hasType(TileCoord tile, Class<T> type) {
        for (Component c : getAll(tile)) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // BLOCKING QUERIES
    // ========================================================================

    /**
     * Checks if tile is blocked by a blocking component (excluding the mover).
     * A component blocks if it implements {@link BlockingComponent} and
     * {@link BlockingComponent#isBlocking()} returns true.
     *
     * @param tile  Tile to check
     * @param mover The entity trying to move (excluded from check), can be null
     * @return true if blocked by another entity
     */
    public synchronized boolean isBlocked(TileCoord tile, Object mover) {
        for (Component c : getAll(tile)) {
            if (c == mover) continue;
            if (c instanceof BlockingComponent bc && bc.isBlocking()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if tile is blocked (x, y, z variant).
     */
    public synchronized boolean isBlocked(int x, int y, int z, Object mover) {
        return isBlocked(new TileCoord(x, y, z), mover);
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Clears all registrations.
     */
    public synchronized void clear() {
        entities.clear();
    }

    /**
     * Gets the number of tiles with registered components.
     */
    public synchronized int getTileCount() {
        return entities.size();
    }

    /**
     * Gets the total number of registered components.
     */
    public synchronized int getComponentCount() {
        int count = 0;
        for (Set<Component> set : entities.values()) {
            count += set.size();
        }
        return count;
    }

    @Override
    public String toString() {
        return String.format("TileEntityMap[tiles=%d, components=%d]",
                getTileCount(), getComponentCount());
    }
}
