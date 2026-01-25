package com.pocket.rpg.editor.gizmos;

/**
 * Interface for components that want to draw gizmos only when the entity is selected.
 * <p>
 * For gizmos that should always be visible regardless of selection,
 * implement {@link GizmoDrawable} instead.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * public class SpriteRenderer extends Component implements GizmoDrawableSelected {
 *     {@literal @}Override
 *     public void onDrawGizmosSelected(GizmoContext ctx) {
 *         // Draw pivot point
 *         ctx.setColor(GizmoColors.PIVOT);
 *         Vector3f pos = getTransform().getWorldPosition();
 *         ctx.drawCrossHair(pos.x, pos.y, 0.2f);
 *
 *         // Draw bounds
 *         ctx.setColor(GizmoColors.BOUNDS);
 *         ctx.drawRect(pos.x - 0.5f, pos.y - 0.5f, 1f, 1f);
 *     }
 * }
 * </pre>
 *
 * @see GizmoDrawable
 * @see GizmoContext
 */
public interface GizmoDrawableSelected {

    /**
     * Called every frame to draw gizmos for this component when its entity is selected.
     * <p>
     * Use this for visualization that should only appear when editing, such as:
     * <ul>
     *   <li>Pivot points</li>
     *   <li>Bounding boxes</li>
     *   <li>Collision shapes</li>
     *   <li>Audio zone radii</li>
     * </ul>
     *
     * @param ctx The gizmo drawing context providing drawing primitives
     */
    void onDrawGizmosSelected(GizmoContext ctx);
}
