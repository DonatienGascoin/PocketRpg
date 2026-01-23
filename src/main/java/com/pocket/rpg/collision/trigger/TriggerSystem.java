package com.pocket.rpg.collision.trigger;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.core.GameObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime system for handling trigger activation and dispatch.
 * <p>
 * Integrates with GridMovement to detect tile enter/exit events
 * and dispatches to registered handlers based on trigger type.
 * <p>
 * Usage:
 * <pre>
 * TriggerSystem triggers = new TriggerSystem(triggerDataMap, collisionMap);
 * triggers.registerHandler(WarpTriggerData.class, new WarpHandler());
 * triggers.registerHandler(DoorTriggerData.class, new DoorHandler());
 *
 * // Called by GridMovement when entity enters a tile:
 * triggers.onTileEnter(player, tileX, tileY, elevation);
 * </pre>
 */
public class TriggerSystem {

    private final TriggerDataMap triggerDataMap;
    private final CollisionMap collisionMap;
    private final Map<Class<? extends TriggerData>, TriggerHandler<?>> handlers;

    /**
     * Creates a new trigger system.
     *
     * @param triggerDataMap Map containing trigger configurations
     * @param collisionMap   Map containing collision types
     */
    public TriggerSystem(TriggerDataMap triggerDataMap, CollisionMap collisionMap) {
        this.triggerDataMap = triggerDataMap;
        this.collisionMap = collisionMap;
        this.handlers = new HashMap<>();
    }

    /**
     * Registers a handler for a trigger data type.
     * <p>
     * Only one handler per type is supported. Registering a new handler
     * for an existing type replaces the previous handler.
     *
     * @param type    The trigger data class to handle
     * @param handler The handler implementation
     * @param <T>     The trigger data type
     */
    public <T extends TriggerData> void registerHandler(Class<T> type, TriggerHandler<T> handler) {
        handlers.put(type, handler);
    }

    /**
     * Unregisters a handler for a trigger data type.
     *
     * @param type The trigger data class to unregister
     */
    public void unregisterHandler(Class<? extends TriggerData> type) {
        handlers.remove(type);
    }

    /**
     * Called when an entity enters a tile.
     * <p>
     * Checks if tile is a trigger with ON_ENTER activation and fires if appropriate.
     *
     * @param entity    The entity that entered
     * @param x         Tile X coordinate
     * @param y         Tile Y coordinate
     * @param elevation Tile elevation
     */
    public void onTileEnter(GameObject entity, int x, int y, int elevation) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) {
            // Trigger tile without configuration - this is common during editing
            return;
        }

        if (data.activationMode() != ActivationMode.ON_ENTER) return;
        if (data.playerOnly() && !isPlayer(entity)) return;

        fireTrigger(data, entity, x, y, elevation);
    }

    /**
     * Called when an entity exits a tile.
     * <p>
     * Checks if tile is a trigger with ON_EXIT activation and fires if appropriate.
     *
     * @param entity        The entity that exited
     * @param x             Tile X coordinate
     * @param y             Tile Y coordinate
     * @param elevation     Tile elevation
     * @param exitDirection The direction the entity is moving
     */
    public void onTileExit(GameObject entity, int x, int y, int elevation, Direction exitDirection) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) return;

        if (data.activationMode() != ActivationMode.ON_EXIT) return;
        if (data.playerOnly() && !isPlayer(entity)) return;

        fireTrigger(data, entity, x, y, elevation, exitDirection);
    }

    /**
     * Attempts to interact with a trigger at the entity's position or facing tile.
     * <p>
     * Called when player presses interact button. Checks the tile the entity
     * is standing on first, then the tile they're facing.
     *
     * @param entity    The entity attempting to interact
     * @param x         Entity's current X position
     * @param y         Entity's current Y position
     * @param elevation Entity's current elevation
     * @param facing    Direction the entity is facing
     * @return true if a trigger was activated
     */
    public boolean tryInteract(GameObject entity, int x, int y, int elevation, Direction facing) {
        // Check tile entity is standing on
        if (tryInteractAt(entity, x, y, elevation)) {
            return true;
        }

        // Check tile entity is facing
        int facingX = x + facing.dx;
        int facingY = y + facing.dy;
        return tryInteractAt(entity, facingX, facingY, elevation);
    }

    /**
     * Attempts to interact with a trigger at a specific position.
     */
    private boolean tryInteractAt(GameObject entity, int x, int y, int elevation) {
        CollisionType type = collisionMap.get(x, y, elevation);
        if (!type.isTrigger()) return false;

        TriggerData data = triggerDataMap.get(x, y, elevation);
        if (data == null) return false;
        if (data.activationMode() != ActivationMode.ON_INTERACT) return false;

        fireTrigger(data, entity, x, y, elevation);
        return true;
    }

    /**
     * Dispatches trigger to registered handler (for ON_ENTER and ON_INTERACT).
     */
    private void fireTrigger(TriggerData data, GameObject entity, int x, int y, int elevation) {
        fireTrigger(data, entity, x, y, elevation, null);
    }

    /**
     * Dispatches trigger to registered handler with optional exit direction.
     */
    @SuppressWarnings("unchecked")
    private void fireTrigger(TriggerData data, GameObject entity, int x, int y, int elevation,
                             Direction exitDirection) {
        TriggerHandler<TriggerData> handler =
                (TriggerHandler<TriggerData>) handlers.get(data.getClass());

        if (handler == null) {
            System.err.println("[TriggerSystem] No handler for trigger type: " +
                    data.getClass().getSimpleName());
            return;
        }

        TriggerContext context = new TriggerContext(entity, x, y, elevation, data, exitDirection);
        handler.handle(context);
    }

    /**
     * Checks if an entity is the player.
     * <p>
     * Currently checks for "Player" in the entity name.
     * Override this method if using a different player identification system.
     */
    protected boolean isPlayer(GameObject entity) {
        if (entity == null) return false;
        // Check if entity name contains "Player" (case-insensitive)
        String name = entity.getName();
        return name != null && name.toLowerCase().contains("player");
    }

    /**
     * Returns true if a handler is registered for the given type.
     */
    public boolean hasHandler(Class<? extends TriggerData> type) {
        return handlers.containsKey(type);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
