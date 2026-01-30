package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.RequiredComponent;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
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
        return true;
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

        // Compute center of the TriggerZone area
        float centerX;
        float centerY;
        if (triggerZone != null) {
            centerX = baseX + triggerZone.getOffsetX() + triggerZone.getWidth() / 2.0f;
            centerY = baseY + triggerZone.getOffsetY() + triggerZone.getHeight() / 2.0f;
        } else {
            centerX = baseX + 0.5f;
            centerY = baseY + 0.5f;
        }

        float size = ctx.getHandleSize(12);

        ctx.setColor(gizmoColor);
        ctx.setThickness(2.0f);

        switch (gizmoShape) {
            case DIAMOND -> ctx.drawDiamond(centerX, centerY, size);
            case CIRCLE -> ctx.drawCircle(centerX, centerY, size);
            case SQUARE -> ctx.drawRectCentered(centerX, centerY, size * 2, size * 2);
            case CROSS -> ctx.drawCrossHair(centerX, centerY, size);
        }

        // Draw component name label below the icon
        ctx.setColor(GizmoColors.fromRGBA(1.0f, 1.0f, 1.0f, 0.8f));
        ctx.drawText(centerX, centerY, getClass().getSimpleName(), 0, 12);
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
