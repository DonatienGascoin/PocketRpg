package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Non-blocking trigger that fires when entities enter its tile(s).
 * <p>
 * Does NOT block movement - entities can walk through freely.
 * The trigger notifies all TriggerListener components on the same GameObject.
 * <p>
 * Uses a box-based approach similar to Unity's BoxCollider2D:
 * - offset: Position offset from entity (in tiles)
 * - size: Width x Height of the trigger area (in tiles)
 * <p>
 * Use cases:
 * - WarpZone (teleport on enter)
 * - CutsceneTrigger (start cutscene)
 * - DamageZone (hurt player)
 * - EventTrigger (custom scripting)
 *
 * <h2>Usage</h2>
 * <pre>
 * // Create warp zone (single tile)
 * GameObject warp = new GameObject("Warp_To_Town");
 * warp.getTransform().setPosition(5, 10, 0);
 * warp.addComponent(new TriggerZone());
 * warp.addComponent(new WarpZone("town", "spawn_entrance"));
 *
 * // Create large trigger area (3x2)
 * TriggerZone trigger = new TriggerZone();
 * trigger.setWidth(3);
 * trigger.setHeight(2);
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class TriggerZone extends Component implements GizmoDrawable {

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
     * Width of the trigger area (in tiles). Minimum 1.
     */
    @Getter
    private int width = 1;

    /**
     * Height of the trigger area (in tiles). Minimum 1.
     */
    @Getter
    private int height = 1;

    /**
     * If true, trigger fires only once then deactivates.
     */
    @Getter
    @Setter
    private boolean oneShot = false;

    /**
     * If true, only triggers for entities tagged as "Player".
     */
    @Getter
    @Setter
    private boolean playerOnly = true;

    /**
     * Elevation level for trigger detection (default 0 = ground level).
     */
    @Getter
    @Setter
    private int elevation = 0;

    public void setWidth(int width) {
        this.width = Math.max(1, width);
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
    }

    // Runtime state - not serialized
    private transient boolean triggered = false;
    private transient TileEntityMap tileEntityMap;
    private transient List<TileCoord> registeredTiles;

    // Callbacks for enter/exit events
    private transient BiConsumer<GameObject, TriggerZone> onEnterCallback;
    private transient BiConsumer<GameObject, TriggerZone> onExitCallback;

    @Override
    protected void onStart() {
        tileEntityMap = getTileEntityMap();
        registeredTiles = new ArrayList<>();
        registerTiles();
    }

    @Override
    protected void onDestroy() {
        unregisterTiles();
    }

    /**
     * Registers a callback to be invoked when an entity enters the trigger zone.
     *
     * @param callback Callback receiving (entity, triggerZone)
     */
    public void setOnEnterCallback(BiConsumer<GameObject, TriggerZone> callback) {
        this.onEnterCallback = callback;
    }

    /**
     * Registers a callback to be invoked when an entity exits the trigger zone.
     *
     * @param callback Callback receiving (entity, triggerZone)
     */
    public void setOnExitCallback(BiConsumer<GameObject, TriggerZone> callback) {
        this.onExitCallback = callback;
    }

    /**
     * Called by GridMovement when an entity enters this trigger's tiles.
     * Invokes the registered callback and notifies TriggerListener components.
     *
     * @param entity The entity that entered
     */
    public void onEntityEnter(GameObject entity) {
        if (oneShot && triggered) return;
        if (playerOnly && !isPlayer(entity)) return;

        triggered = true;

        // Invoke callback if registered
        if (onEnterCallback != null) {
            onEnterCallback.accept(entity, this);
        }

        // Also notify TriggerListener components for backward compatibility
        if (gameObject != null) {
            for (Component c : gameObject.getAllComponents()) {
                if (c instanceof TriggerListener listener) {
                    listener.onTriggerEnter(entity, this);
                }
            }
        }
    }

    /**
     * Called by GridMovement when an entity exits this trigger's tiles.
     * Invokes the registered callback and notifies TriggerListener components.
     *
     * @param entity The entity that exited
     */
    public void onEntityExit(GameObject entity) {
        if (playerOnly && !isPlayer(entity)) return;

        // Invoke callback if registered
        if (onExitCallback != null) {
            onExitCallback.accept(entity, this);
        }

        // Also notify TriggerListener components for backward compatibility
        if (gameObject != null) {
            for (Component c : gameObject.getAllComponents()) {
                if (c instanceof TriggerListener listener) {
                    listener.onTriggerExit(entity, this);
                }
            }
        }
    }

    /**
     * Resets the trigger (for one-shot triggers that need to fire again).
     */
    public void reset() {
        triggered = false;
    }

    /**
     * Gets trigger tiles in world coordinates.
     * Calculates from entity position, offset, and size.
     */
    public List<TileCoord> getAbsoluteTiles() {
        Vector3f pos = getTransform().getPosition();
        int baseX = (int) Math.floor(pos.x) + offsetX;
        int baseY = (int) Math.floor(pos.y) + offsetY;

        List<TileCoord> absolute = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                absolute.add(new TileCoord(baseX + dx, baseY + dy, elevation));
            }
        }
        return absolute;
    }

    /**
     * Checks if a tile is within this trigger zone.
     */
    public boolean contains(int x, int y, int elev) {
        if (elev != elevation) return false;

        Vector3f pos = getTransform().getPosition();
        int baseX = (int) Math.floor(pos.x) + offsetX;
        int baseY = (int) Math.floor(pos.y) + offsetY;

        return x >= baseX && x < baseX + width
            && y >= baseY && y < baseY + height;
    }

    private void registerTiles() {
        if (tileEntityMap == null) return;

        for (TileCoord tile : getAbsoluteTiles()) {
            tileEntityMap.register(this, tile);
            registeredTiles.add(tile);
        }
    }

    private void unregisterTiles() {
        if (tileEntityMap == null || registeredTiles == null) return;

        for (TileCoord tile : registeredTiles) {
            tileEntityMap.unregister(this, tile);
        }
        registeredTiles.clear();
    }

    private boolean isPlayer(GameObject entity) {
        // Check if entity has "Player" tag
        String name = entity.getName();
        return name != null && (name.equals("Player") || name.contains("Player"));
    }

    private TileEntityMap getTileEntityMap() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem().getTileEntityMap();
    }

    // ========================================================================
    // GIZMO DRAWING - Always visible
    // ========================================================================

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float baseX = (float) Math.floor(pos.x) + offsetX;
        float baseY = (float) Math.floor(pos.y) + offsetY;

        // Draw filled area (semi-transparent yellow)
        ctx.setColor(GizmoColors.TRIGGER);
        ctx.drawRectFilled(baseX, baseY, width, height);

        // Draw border
        ctx.setColor(GizmoColors.TRIGGER_BORDER);
        ctx.setThickness(2.0f);
        ctx.drawRect(baseX, baseY, width, height);
    }
}
