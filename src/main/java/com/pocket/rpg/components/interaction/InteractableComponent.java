package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.components.Tooltip;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Abstract base class for interactable components.
 * <p>
 * Provides common infrastructure so subclasses only need to implement
 * {@link #interact(GameObject)} and {@link #getInteractionPrompt()}.
 * <p>
 * Handles:
 * <ul>
 *   <li>TileEntityMap registration/unregistration for InteractionController detection</li>
 *   <li>Automatic TriggerZone dependency (via {@link RequiredComponent})</li>
 *   <li>Shared gizmo icon drawing centered on the TriggerZone area</li>
 * </ul>
 *
 * <h2>Gizmo Icon System</h2>
 * Each subclass sets {@link #gizmoColor} and {@link #gizmoShape} in its constructor
 * to get a unique, recognizable icon in the editor. The TriggerZone draws the yellow
 * tile area; this class draws the icon on top.
 *
 * <h2>Usage</h2>
 * <pre>
 * {@literal @}ComponentMeta(category = "Interaction")
 * public class Chest extends InteractableComponent {
 *     public Chest() {
 *         gizmoShape = GizmoShape.SQUARE;
 *         gizmoColor = GizmoColors.fromRGBA(1.0f, 0.8f, 0.2f, 0.9f);
 *     }
 *
 *     {@literal @}Override
 *     public void interact(GameObject player) { ... }
 *
 *     {@literal @}Override
 *     public String getInteractionPrompt() { return "Open"; }
 * }
 * </pre>
 */
@RequiredComponent(TriggerZone.class)
public abstract class InteractableComponent extends Component implements Interactable, GizmoDrawable {

    /**
     * Shapes available for gizmo icons.
     */
    public enum GizmoShape {
        DIAMOND,
        CIRCLE,
        SQUARE,
        CROSS
    }

    // ========================================================================
    // GIZMO CONFIGURATION — set in subclass constructor
    // ========================================================================

    /**
     * The gizmo icon color. Set in subclass constructor.
     */
    protected transient int gizmoColor = GizmoColors.fromRGBA(0.4f, 0.9f, 1.0f, 0.9f);

    /**
     * The gizmo icon shape. Set in subclass constructor.
     */
    protected transient GizmoShape gizmoShape = GizmoShape.DIAMOND;

    // ========================================================================
    // INTERACTION CONFIGURATION — serialized, editable in inspector
    // ========================================================================

    /**
     * The direction the player must approach from to interact.
     * For example, DOWN means the player must be below the object.
     * Set to null to allow interaction from any direction.
     */
    @Getter
    @Setter
    @Tooltip("Direction the player must be relative to this object to interact. E.g. DOWN = player must be below.")
    private Direction interactFrom = Direction.DOWN;

    // ========================================================================
    // RUNTIME STATE — not serialized
    // ========================================================================

    @ComponentRef
    private TriggerZone triggerZone;

    private transient TileEntityMap tileEntityMap;
    private transient TileCoord registeredTile;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        tileEntityMap = fetchTileEntityMap();
        registerWithMap();
        onInteractableStart();
    }

    @Override
    protected void onDestroy() {
        onInteractableDestroy();
        unregisterFromMap();
    }

    /**
     * Called after the interactable has been registered with the tile map.
     * Override for subclass-specific initialization.
     */
    protected void onInteractableStart() {
        // Default: nothing
    }

    /**
     * Called before the interactable is unregistered from the tile map.
     * Override for subclass-specific cleanup.
     */
    protected void onInteractableDestroy() {
        // Default: nothing
    }

    // ========================================================================
    // INTERACTABLE DEFAULTS — override as needed
    // ========================================================================

    @Override
    public boolean canInteract(GameObject player) {
        if (interactFrom == null) {
            return true;
        }

        Vector3f objectPos = getTransform().getPosition();
        int objectX = (int) Math.floor(objectPos.x);
        int objectY = (int) Math.floor(objectPos.y);

        Vector3f playerPos = player.getTransform().getPosition();
        int playerX = (int) Math.floor(playerPos.x);
        int playerY = (int) Math.floor(playerPos.y);

        // Player must be on the tile the object faces toward
        int expectedX = objectX + interactFrom.dx;
        int expectedY = objectY + interactFrom.dy;
        return playerX == expectedX && playerY == expectedY;
    }

    @Override
    public int getInteractionPriority() {
        return 0;
    }

    // ========================================================================
    // GIZMO DRAWING
    // ========================================================================

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float baseX = (float) Math.floor(pos.x);
        float baseY = (float) Math.floor(pos.y);

        // Find TriggerZone via owner (works in both editor and runtime)
        TriggerZone tz = triggerZone != null ? triggerZone : getComponent(TriggerZone.class);

        // Compute center of the TriggerZone area
        float centerX;
        float centerY;
        if (tz != null) {
            centerX = baseX + tz.getOffsetX() + tz.getWidth() / 2.0f;
            centerY = baseY + tz.getOffsetY() + tz.getHeight() / 2.0f;
        } else {
            centerX = baseX + 0.5f;
            centerY = baseY + 0.5f;
        }

        float size = 0.25f; // Fixed world-space size (quarter tile)

        ctx.setColor(gizmoColor);
        ctx.setThickness(2.0f);

        switch (gizmoShape) {
            case DIAMOND -> ctx.drawDiamond(centerX, centerY, size);
            case CIRCLE -> ctx.drawCircle(centerX, centerY, size);
            case SQUARE -> ctx.drawRectCentered(centerX, centerY, size * 2, size * 2);
            case CROSS -> ctx.drawCrossHair(centerX, centerY, size);
        }

    }

    // ========================================================================
    // TILE ENTITY MAP REGISTRATION
    // ========================================================================

    private TileCoord getTileCoord() {
        Vector3f pos = getTransform().getPosition();
        int x = (int) Math.floor(pos.x);
        int y = (int) Math.floor(pos.y);
        int elevation = triggerZone != null ? triggerZone.getElevation() : 0;
        return new TileCoord(x, y, elevation);
    }

    private void registerWithMap() {
        if (tileEntityMap == null) return;

        TileCoord tile = getTileCoord();
        tileEntityMap.register(this, tile);
        registeredTile = tile;
    }

    private void unregisterFromMap() {
        if (tileEntityMap == null || registeredTile == null) return;

        tileEntityMap.unregister(this, registeredTile);
        registeredTile = null;
    }

    private TileEntityMap fetchTileEntityMap() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem().getTileEntityMap();
    }
}
