package com.pocket.rpg.editor.gizmos;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import imgui.ImDrawList;
import imgui.ImGui;
import lombok.Getter;
import lombok.Setter;

/**
 * Renders gizmos for components in the scene view.
 * <p>
 * Components can implement {@link GizmoDrawable} to draw gizmos for all entities,
 * or {@link GizmoDrawableSelected} to draw gizmos only when their entity is selected.
 *
 * <h2>Integration</h2>
 * Call {@link #render} from the scene viewport's overlay rendering:
 * <pre>
 * // In SceneViewport.renderToolOverlay():
 * gizmoRenderer.render(scene, camera, viewportX, viewportY, viewportWidth, viewportHeight);
 * </pre>
 *
 * @see GizmoDrawable
 * @see GizmoDrawableSelected
 * @see GizmoContext
 */
public class GizmoRenderer {

    @Getter
    @Setter
    private boolean enabled = true;

    @Getter
    @Setter
    private boolean drawAlwaysGizmos = true;

    @Getter
    @Setter
    private boolean drawSelectedGizmos = true;

    /**
     * Renders gizmos for all entities in the scene.
     *
     * @param scene          The editor scene containing entities
     * @param camera         The editor camera for coordinate conversion
     * @param viewportX      Viewport X position in screen space
     * @param viewportY      Viewport Y position in screen space
     * @param viewportWidth  Viewport width in pixels
     * @param viewportHeight Viewport height in pixels
     */
    public void render(EditorScene scene, EditorCamera camera,
                       float viewportX, float viewportY,
                       float viewportWidth, float viewportHeight) {
        if (!enabled || scene == null) {
            return;
        }

        ImDrawList drawList = ImGui.getWindowDrawList();

        // Clip to viewport bounds
        drawList.pushClipRect(viewportX, viewportY,
                viewportX + viewportWidth,
                viewportY + viewportHeight, true);

        try {
            GizmoContext ctx = new GizmoContext(drawList, camera, viewportX, viewportY);

            // Phase 1: Draw "always" gizmos for ALL entities
            if (drawAlwaysGizmos) {
                renderAlwaysGizmos(scene, ctx);
            }

            // Phase 2: Draw "selected" gizmos for SELECTED entities only
            if (drawSelectedGizmos) {
                renderSelectedGizmos(scene, ctx);
            }

        } finally {
            drawList.popClipRect();
        }
    }

    /**
     * Renders gizmos that should always be visible (GizmoDrawable).
     */
    private void renderAlwaysGizmos(EditorScene scene, GizmoContext ctx) {
        for (EditorGameObject entity : scene.getEntities()) {
            if (!entity.isEnabled()) continue; // Skip disabled entities

            Transform transform = entity.getComponent(Transform.class);
            ctx.setTransform(transform);

            for (Component component : entity.getComponents()) {
                if (component instanceof GizmoDrawable gizmoDrawable && component.isOwnEnabled()) {
                    // Reset style for each component
                    ctx.setColor(GizmoColors.DEFAULT);
                    ctx.setThickness(2.0f);

                    gizmoDrawable.onDrawGizmos(ctx);
                }
            }

            // Recursively draw gizmos for children
            renderAlwaysGizmosRecursive(entity, ctx);
        }
    }

    /**
     * Recursively renders always-visible gizmos for child entities.
     */
    private void renderAlwaysGizmosRecursive(EditorGameObject parent, GizmoContext ctx) {
        for (EditorGameObject child : parent.getChildren()) {
            if (!child.isEnabled()) continue; // Skip disabled children

            Transform transform = child.getComponent(Transform.class);
            ctx.setTransform(transform);

            for (Component component : child.getComponents()) {
                if (component instanceof GizmoDrawable gizmoDrawable && component.isOwnEnabled()) {
                    ctx.setColor(GizmoColors.DEFAULT);
                    ctx.setThickness(2.0f);
                    gizmoDrawable.onDrawGizmos(ctx);
                }
            }

            renderAlwaysGizmosRecursive(child, ctx);
        }
    }

    /**
     * Renders gizmos for selected entities (GizmoDrawableSelected).
     */
    private void renderSelectedGizmos(EditorScene scene, GizmoContext ctx) {
        for (EditorGameObject entity : scene.getSelectedEntities()) {
            if (!entity.isEnabled()) continue; // Skip disabled entities

            Transform transform = entity.getComponent(Transform.class);
            ctx.setTransform(transform);

            for (Component component : entity.getComponents()) {
                if (component instanceof GizmoDrawableSelected gizmoDrawableSelected && component.isOwnEnabled()) {
                    // Reset style for each component
                    ctx.setColor(GizmoColors.DEFAULT);
                    ctx.setThickness(2.0f);

                    gizmoDrawableSelected.onDrawGizmosSelected(ctx);
                }
            }
        }
    }
}
