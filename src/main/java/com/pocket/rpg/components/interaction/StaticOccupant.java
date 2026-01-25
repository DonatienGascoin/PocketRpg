package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.BlockingComponent;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawableSelected;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers static objects with TileEntityMap so they block movement.
 * <p>
 * Use this for non-moving objects like chests, pots, signs, statues.
 * For moving entities, use GridMovement instead.
 * <p>
 * Key principle: Collision map is for terrain (walls, water).
 * All objects (static or moving) use TileEntityMap.
 * <p>
 * Uses a box-based approach similar to Unity's BoxCollider2D:
 * - offset: Position offset from entity (in tiles)
 * - size: Width x Height of the blocking area (in tiles)
 *
 * <h2>Usage</h2>
 * <pre>
 * // Single-tile object (pot, sign) - default 1x1
 * GameObject pot = new GameObject("Pot");
 * pot.addComponent(new SpriteRenderer());
 * pot.addComponent(new StaticOccupant());
 *
 * // Multi-tile object (2x1 table)
 * StaticOccupant occupant = new StaticOccupant();
 * occupant.setWidth(2);
 * occupant.setHeight(1);
 * table.addComponent(occupant);
 *
 * // Large statue with offset (2x2, centered)
 * StaticOccupant occupant = new StaticOccupant();
 * occupant.setOffsetX(-1);  // Shift left
 * occupant.setWidth(2);
 * occupant.setHeight(2);
 * statue.addComponent(occupant);
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class StaticOccupant extends Component implements BlockingComponent, GizmoDrawableSelected {

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
     * Width of the blocking area (in tiles). Minimum 1.
     */
    @Getter
    private int width = 1;

    /**
     * Height of the blocking area (in tiles). Minimum 1.
     */
    @Getter
    private int height = 1;

    /**
     * Elevation level for collision (default 0 = ground level).
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
    private transient TileEntityMap tileEntityMap;
    private transient List<TileCoord> registeredTiles;

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
     * Gets occupied tiles in world coordinates.
     * <p>
     * Calculates absolute tile positions from entity position, offset, and size.
     * All tiles in the box share the same elevation level.
     *
     * @return List of absolute TileCoord positions this component blocks
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
     * Checks if this occupant blocks the given tile.
     */
    public boolean occupies(int x, int y, int elev) {
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

        // Draw filled area (semi-transparent)
        ctx.setColor(GizmoColors.COLLISION);
        ctx.drawRectFilled(baseX, baseY, width, height);

        // Draw border
        ctx.setColor(GizmoColors.COLLISION_BORDER);
        ctx.setThickness(2.0f);
        ctx.drawRect(baseX, baseY, width, height);
    }
}
