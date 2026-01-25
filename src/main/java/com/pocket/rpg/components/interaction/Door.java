package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.BlockingComponent;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawableSelected;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Interactive door that can be opened/closed by the player.
 * <p>
 * When closed, the door blocks movement. When open, entities can pass through.
 * Supports locked doors that require a specific key item to unlock.
 * <p>
 * The door registers with TileEntityMap for both blocking (when closed)
 * and interaction detection.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Simple door
 * GameObject door = new GameObject("Door");
 * door.addComponent(new SpriteRenderer());
 * door.addComponent(new Door());
 *
 * // Locked door requiring a key
 * Door lockedDoor = new Door();
 * lockedDoor.setLocked(true);
 * lockedDoor.setRequiredKeyId("basement_key");
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class Door extends Component implements Interactable, BlockingComponent, GizmoDrawableSelected {

    /**
     * Whether the door is currently open.
     */
    @Getter
    @Setter
    private boolean open = false;

    /**
     * Whether the door is locked (requires key to open).
     */
    @Getter
    @Setter
    private boolean locked = false;

    /**
     * The item ID required to unlock this door.
     * Only used if {@link #locked} is true.
     */
    @Getter
    @Setter
    private String requiredKeyId = "";

    /**
     * If true, the key is consumed when used to unlock the door.
     */
    @Getter
    @Setter
    private boolean consumeKey = false;

    /**
     * If true, the door stays open after being opened (one-way).
     * If false, player can close the door again.
     */
    @Getter
    @Setter
    private boolean stayOpen = false;

    /**
     * Horizontal offset from entity position (in tiles).
     */
    @Getter
    @Setter
    private int offsetX = 0;

    /**
     * Vertical offset from entity position (in tiles).
     */
    @Getter
    @Setter
    private int offsetY = 0;

    /**
     * Width of the door (in tiles). Minimum 1.
     */
    @Getter
    private int width = 1;

    /**
     * Height of the door (in tiles). Minimum 1.
     */
    @Getter
    private int height = 1;

    /**
     * Elevation level for collision (default 0 = ground level).
     */
    @Getter
    @Setter
    private int elevation = 0;

    // Runtime state - not serialized
    private transient TileEntityMap tileEntityMap;
    private transient TileCoord registeredTile;

    public void setWidth(int width) {
        this.width = Math.max(1, width);
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
    }

    @Override
    protected void onStart() {
        tileEntityMap = getTileEntityMap();
        registerWithMap();
    }

    @Override
    protected void onDestroy() {
        unregisterFromMap();
    }

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean canInteract(GameObject player) {
        // Can always interact (to open, close, or see "locked" message)
        return true;
    }

    @Override
    public void interact(GameObject player) {
        if (open) {
            // Try to close
            if (!stayOpen) {
                close();
            }
        } else {
            // Try to open
            if (locked) {
                if (playerHasKey(player)) {
                    unlock(player);
                    open();
                } else {
                    onLockedInteract(player);
                }
            } else {
                open();
            }
        }
    }

    @Override
    public String getInteractionPrompt() {
        if (open) {
            return stayOpen ? null : "Close";
        } else if (locked) {
            return "Unlock";
        } else {
            return "Open";
        }
    }

    @Override
    public int getInteractionPriority() {
        return 5; // Higher than signs, lower than NPCs
    }

    // ========================================================================
    // DOOR OPERATIONS
    // ========================================================================

    /**
     * Opens the door, allowing passage.
     */
    public void open() {
        if (!open) {
            open = true;
            onDoorOpened();
        }
    }

    /**
     * Closes the door, blocking passage.
     */
    public void close() {
        if (open && !stayOpen) {
            open = false;
            onDoorClosed();
        }
    }

    /**
     * Toggles the door state.
     */
    public void toggle() {
        if (open) {
            close();
        } else if (!locked) {
            open();
        }
    }

    /**
     * Unlocks the door.
     */
    public void unlock(GameObject player) {
        if (locked) {
            locked = false;
            if (consumeKey) {
                consumeKeyFromPlayer(player);
            }
            onDoorUnlocked(player);
        }
    }

    /**
     * Locks the door.
     */
    public void lock() {
        if (!locked && !open) {
            locked = true;
        }
    }

    // ========================================================================
    // BLOCKING COMPONENT - only blocks when closed
    // ========================================================================

    @Override
    public boolean isBlocking() {
        return !open; // Only block when closed
    }

    /**
     * Gets the tiles this door occupies (for collision).
     * Only blocks when door is closed.
     */
    public TileCoord getBlockingTile() {
        Vector3f pos = getTransform().getPosition();
        int x = (int) Math.floor(pos.x) + offsetX;
        int y = (int) Math.floor(pos.y) + offsetY;
        return new TileCoord(x, y, elevation);
    }

    /**
     * Checks if the door blocks the given tile.
     * Returns false if door is open.
     */
    public boolean blocksAt(int x, int y, int elev) {
        if (open) return false; // Open doors don't block
        if (elev != elevation) return false;

        Vector3f pos = getTransform().getPosition();
        int baseX = (int) Math.floor(pos.x) + offsetX;
        int baseY = (int) Math.floor(pos.y) + offsetY;

        return x >= baseX && x < baseX + width
            && y >= baseY && y < baseY + height;
    }

    // ========================================================================
    // CALLBACKS - Override in subclasses for custom behavior
    // ========================================================================

    /**
     * Called when the door is opened.
     * Override for custom animations, sounds, etc.
     */
    protected void onDoorOpened() {
        // TODO: Play open animation/sound
        System.out.println("[Door] Opened: " + gameObject.getName());
    }

    /**
     * Called when the door is closed.
     * Override for custom animations, sounds, etc.
     */
    protected void onDoorClosed() {
        // TODO: Play close animation/sound
        System.out.println("[Door] Closed: " + gameObject.getName());
    }

    /**
     * Called when the door is unlocked.
     * Override for custom effects.
     */
    protected void onDoorUnlocked(GameObject player) {
        // TODO: Play unlock sound
        System.out.println("[Door] Unlocked: " + gameObject.getName());
    }

    /**
     * Called when player interacts with a locked door without the key.
     * Override to show dialogue or play sound.
     */
    protected void onLockedInteract(GameObject player) {
        // TODO: Show "It's locked" message or play locked sound
        System.out.println("[Door] Locked! Requires: " + requiredKeyId);
    }

    // ========================================================================
    // KEY/INVENTORY INTEGRATION
    // ========================================================================

    /**
     * Checks if the player has the required key.
     * Override to integrate with your inventory system.
     */
    protected boolean playerHasKey(GameObject player) {
        if (requiredKeyId == null || requiredKeyId.isEmpty()) {
            return true; // No key required
        }
        // TODO: Check player inventory for key
        // For now, return false - implement when inventory system exists
        // Example: return player.getComponent(Inventory.class).hasItem(requiredKeyId);
        return false;
    }

    /**
     * Consumes the key from player's inventory.
     * Override to integrate with your inventory system.
     */
    protected void consumeKeyFromPlayer(GameObject player) {
        if (requiredKeyId == null || requiredKeyId.isEmpty()) {
            return;
        }
        // TODO: Remove key from player inventory
        // Example: player.getComponent(Inventory.class).removeItem(requiredKeyId, 1);
        System.out.println("[Door] Key consumed: " + requiredKeyId);
    }

    // ========================================================================
    // TILE ENTITY MAP REGISTRATION
    // ========================================================================

    private void registerWithMap() {
        if (tileEntityMap == null) return;

        // Register for interaction detection (always)
        TileCoord tile = getBlockingTile();
        tileEntityMap.register(this, tile);
        registeredTile = tile;
    }

    private void unregisterFromMap() {
        if (tileEntityMap == null || registeredTile == null) return;

        tileEntityMap.unregister(this, registeredTile);
        registeredTile = null;
    }

    private TileEntityMap getTileEntityMap() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem().getTileEntityMap();
    }

    // ========================================================================
    // GIZMO DRAWING
    // ========================================================================

    @Override
    public void onDrawGizmosSelected(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float baseX = (float) Math.floor(pos.x) + offsetX;
        float baseY = (float) Math.floor(pos.y) + offsetY;

        // Choose color based on state
        int fillColor;
        int borderColor;
        if (open) {
            // Open = green (passable)
            fillColor = GizmoColors.fromRGBA(0.2f, 0.8f, 0.2f, 0.3f);
            borderColor = GizmoColors.fromRGBA(0.2f, 0.8f, 0.2f, 0.8f);
        } else if (locked) {
            // Locked = red (blocked, needs key)
            fillColor = GizmoColors.fromRGBA(0.9f, 0.2f, 0.2f, 0.4f);
            borderColor = GizmoColors.fromRGBA(0.9f, 0.2f, 0.2f, 0.9f);
        } else {
            // Closed = orange (blocked but openable)
            fillColor = GizmoColors.fromRGBA(1.0f, 0.6f, 0.2f, 0.4f);
            borderColor = GizmoColors.fromRGBA(1.0f, 0.6f, 0.2f, 0.9f);
        }

        // Draw filled area
        ctx.setColor(fillColor);
        ctx.drawRectFilled(baseX, baseY, width, height);

        // Draw border
        ctx.setColor(borderColor);
        ctx.setThickness(2.0f);
        ctx.drawRect(baseX, baseY, width, height);
    }
}
