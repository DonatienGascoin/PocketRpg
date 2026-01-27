package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Marks a spawn location for the player or other entities.
 * <p>
 * Use multiple SpawnPoints with different IDs for different entry points
 * (e.g., "entrance", "from_cave", "from_town").
 * <p>
 * WarpZone uses the spawnId to determine where to place the player.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Default spawn point
 * GameObject spawn = new GameObject("Spawn_Default");
 * spawn.getTransform().setPosition(5, 10, 0);
 * spawn.addComponent(new SpawnPoint());  // id = "default"
 *
 * // Named spawn point
 * SpawnPoint spawn = new SpawnPoint();
 * spawn.setSpawnId("from_cave");
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class SpawnPoint extends Component implements GizmoDrawable {

    /**
     * Unique identifier for this spawn point.
     * Used by WarpZone to specify destination.
     * Must be set explicitly.
     */
    @Getter
    @Setter
    private String spawnId = "";

    /**
     * Direction the player should face after spawning.
     */
    @Getter
    @Setter
    private Direction facingDirection = Direction.DOWN;

    /**
     * Optional sound to play when an entity arrives at this spawn point.
     */
    @Getter
    @Setter
    private AudioClip arrivalSound;

    /**
     * Gets the world position of this spawn point.
     */
    public Vector3f getSpawnPosition() {
        return getTransform().getPosition();
    }

    /**
     * Gets the tile coordinates of this spawn point.
     */
    public int getTileX() {
        return (int) Math.floor(getTransform().getPosition().x);
    }

    public int getTileY() {
        return (int) Math.floor(getTransform().getPosition().y);
    }

    // ========================================================================
    // GIZMO DRAWING - Always visible (spawn points should be easy to find)
    // ========================================================================

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float x = pos.x;
        float y = pos.y;

        // Calculate tile-aligned position for the area box
        float tileX = (float) Math.floor(x);
        float tileY = (float) Math.floor(y);

        // Draw 1x1 tile area box (semi-transparent fill)
        ctx.setColor(GizmoColors.fromRGBA(0.3f, 0.6f, 1.0f, 0.15f));
        ctx.drawRectFilled(tileX, tileY, 1, 1);

        // Draw area box outline
        ctx.setColor(GizmoColors.fromRGBA(0.3f, 0.6f, 1.0f, 0.6f));
        ctx.setThickness(2.0f);
        ctx.drawRect(tileX, tileY, 1, 1);

        // Draw spawn marker (diamond shape) at tile center
        float centerX = tileX + 0.5f;
        float centerY = tileY + 0.5f;
        float size = ctx.getHandleSize(10);

        // Blue diamond outline
        ctx.setColor(GizmoColors.fromRGBA(0.3f, 0.6f, 1.0f, 0.9f));
        ctx.setThickness(2.0f);
        ctx.drawDiamond(centerX, centerY, size);

        // Draw facing direction arrow
        float arrowSize = size * 0.8f;
        float arrowX = centerX + (facingDirection != null ? facingDirection.dx * size * 1.5f : 0);
        float arrowY = centerY + (facingDirection != null ? facingDirection.dy * size * 1.5f : 0);

        ctx.setColor(GizmoColors.fromRGBA(1.0f, 1.0f, 1.0f, 0.6f));
        ctx.drawArrow(centerX, centerY, arrowX, arrowY, arrowSize * 0.3f);

        // Draw spawn ID label
        ctx.setColor(GizmoColors.WHITE);
        ctx.drawText(centerX, centerY, spawnId, 5, -15);
    }
}
