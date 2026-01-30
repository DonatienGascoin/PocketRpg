package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import com.pocket.rpg.serialization.Required;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Defines a rectangular camera bounds region.
 * <p>
 * Each zone has a {@code boundsId} that can be referenced by {@link SpawnPoint#getCameraBoundsId()}
 * or by the scene's {@code initialBoundsId} to activate camera clamping within this zone's rectangle.
 * <p>
 * When activated, the game camera is clamped so its visible area stays within
 * {@code (minX, minY)} to {@code (maxX, maxY)}.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Create a bounds zone for the outdoor area
 * GameObject boundsObj = new GameObject("CameraBounds_Outdoor");
 * CameraBoundsZone zone = new CameraBoundsZone();
 * zone.setBoundsId("outdoor");
 * zone.setMinX(-20); zone.setMinY(-15);
 * zone.setMaxX(15);  zone.setMaxY(13);
 * boundsObj.addComponent(zone);
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class CameraBoundsZone extends Component implements GizmoDrawable {

    /**
     * Unique identifier for this bounds zone.
     * Referenced by SpawnPoint.cameraBoundsId and SceneData.initialBoundsId.
     */
    @Required
    @Getter
    @Setter
    private String boundsId = "";

    @Getter
    @Setter
    private float minX = 0f;

    @Getter
    @Setter
    private float minY = 0f;

    @Getter
    @Setter
    private float maxX = 20f;

    @Getter
    @Setter
    private float maxY = 15f;

    /**
     * Applies this zone's bounds to the given camera.
     */
    public void applyBounds(GameCamera camera) {
        camera.setBounds(minX, minY, maxX, maxY);
    }

    /**
     * Clears bounds from the given camera.
     */
    public void clearBounds(GameCamera camera) {
        camera.clearBounds();
    }

    // ========================================================================
    // GIZMO DRAWING - Always visible
    // ========================================================================

    private static final int ZONE_COLOR = GizmoColors.fromRGBA(1.0f, 0.15f, 0.15f, 1.0f);
    private static final int ZONE_LABEL_COLOR = GizmoColors.fromRGBA(1.0f, 0.15f, 0.15f, 1.0f);
    private static final float DASH_LENGTH = 0.4f;
    private static final float GAP_LENGTH = 0.2f;

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        // Draw bounds rectangle as dashed lines (matching previous camera bounds style)
        ctx.setColor(ZONE_COLOR);
        ctx.setThickness(2.0f);
        ctx.drawDashedLine(minX, minY, maxX, minY, DASH_LENGTH, GAP_LENGTH); // bottom
        ctx.drawDashedLine(maxX, minY, maxX, maxY, DASH_LENGTH, GAP_LENGTH); // right
        ctx.drawDashedLine(maxX, maxY, minX, maxY, DASH_LENGTH, GAP_LENGTH); // top
        ctx.drawDashedLine(minX, maxY, minX, minY, DASH_LENGTH, GAP_LENGTH); // left

        // Draw boundsId label at top-left corner
        ctx.setColor(ZONE_LABEL_COLOR);
        ctx.drawText(minX, maxY, boundsId.isEmpty() ? "(no id)" : boundsId, 4, -16);

        // Draw center diamond marker
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float size = ctx.getHandleSize(8);
        ctx.setColor(ZONE_COLOR);
        ctx.drawDiamond(centerX, centerY, size);
    }
}
