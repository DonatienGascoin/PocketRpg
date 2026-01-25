package com.pocket.rpg.editor.gizmos;

/**
 * Interface for components that want to draw gizmos in the scene view.
 * Gizmos drawn via this interface are shown for ALL entities, regardless of selection.
 * <p>
 * For gizmos that should only appear when the entity is selected,
 * implement {@link GizmoDrawableSelected} instead.
 *
 * <h2>Example Usage</h2>
 * <pre>
 * public class MyComponent extends Component implements GizmoDrawable {
 *     {@literal @}Override
 *     public void onDrawGizmos(GizmoContext ctx) {
 *         ctx.setColor(GizmoColors.DEFAULT);
 *         Vector3f pos = getTransform().getWorldPosition();
 *         ctx.drawCircle(pos.x, pos.y, 1.0f);
 *     }
 * }
 * </pre>
 *
 * @see GizmoDrawableSelected
 * @see GizmoContext
 */
public interface GizmoDrawable {

    /**
     * Called every frame to draw gizmos for this component.
     * This is called for ALL entities in the scene, not just selected ones.
     * <p>
     * Use this for visualization that should always be visible, such as:
     * <ul>
     *   <li>Trigger zones boundaries</li>
     *   <li>Waypoint connections</li>
     *   <li>Debug visualization</li>
     * </ul>
     *
     * @param ctx The gizmo drawing context providing drawing primitives
     */
    void onDrawGizmos(GizmoContext ctx);
}
