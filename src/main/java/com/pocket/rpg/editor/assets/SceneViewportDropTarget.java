package com.pocket.rpg.editor.assets;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Vector3f;

/**
 * Handles asset drop operations in the SceneViewport.
 * <p>
 * Add this to SceneViewport by calling {@link #handleDropTarget} after rendering the viewport image.
 * <p>
 * Integration example in SceneViewport.renderContent():
 * <pre>
 * // After ImGui.image(...)
 * if (SceneViewportDropTarget.handleDropTarget(camera, scene, viewportX, viewportY)) {
 *     // Entity was added
 * }
 * </pre>
 */
public class SceneViewportDropTarget {

    /**
     * Handles drop target for the scene viewport.
     * Call this after rendering the viewport image (ImGui.image).
     *
     * @param camera    Editor camera for coordinate conversion
     * @param scene     Editor scene to add entities to
     * @param viewportX Viewport X position in screen coords
     * @param viewportY Viewport Y position in screen coords
     * @return true if an entity was created from a drop
     */
    public static boolean handleDropTarget(EditorCamera camera, EditorScene scene,
                                           float viewportX, float viewportY) {
        if (scene == null || camera == null) {
            return false;
        }

        // Don't show drop target when drag is cancelled
        if (AssetDragPayload.isDragCancelled()) {
            return false;
        }

        if (ImGui.beginDragDropTarget()) {
            byte[] payloadData = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);

            if (payloadData != null && payloadData.length > 0) {

                AssetDragPayload payload = AssetDragPayload.deserialize(payloadData);

                if (payload != null && AssetDropHandler.canInstantiate(payload)) {
                    // Get mouse position and convert to world coords
                    ImVec2 mousePos = ImGui.getMousePos();
                    float localX = mousePos.x - viewportX;
                    float localY = mousePos.y - viewportY;

                    Vector3f worldPos = camera.screenToWorld(localX, localY);

                    // Create entity
                    EditorGameObject entity = AssetDropHandler.handleDrop(payload, worldPos);

                    if (entity != null) {
                        // Add to scene with undo support
                        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
                        scene.setSelectedEntity(entity);
                        scene.markDirty();

                        ImGui.endDragDropTarget();
                        return true;
                    }
                }
            }

            ImGui.endDragDropTarget();
        }

        return false;
    }

    /**
     * Renders a visual indicator when dragging over the viewport.
     * Call this during renderToolOverlay() if a drag is in progress.
     */
    public static void renderDragOverlay(EditorCamera camera, float viewportX, float viewportY,
                                         float viewportWidth, float viewportHeight) {
        // Check if we're dragging an asset payload (and not cancelled)
        if (ImGui.getDragDropPayload() == null || AssetDragPayload.isDragCancelled()) {
            return;
        }

        // Only show overlay if mouse is within viewport bounds
        ImVec2 mousePos = ImGui.getMousePos();
        if (mousePos.x < viewportX || mousePos.x > viewportX + viewportWidth ||
                mousePos.y < viewportY || mousePos.y > viewportY + viewportHeight) {
            return;
        }

        // Draw a subtle highlight around viewport
        var drawList = ImGui.getWindowDrawList();
        int highlightColor = ImGui.colorConvertFloat4ToU32(0.3f, 0.7f, 1.0f, 0.3f);

        drawList.addRectFilled(
                viewportX, viewportY,
                viewportX + viewportWidth, viewportY + viewportHeight,
                highlightColor
        );

        // Draw crosshair at mouse position
        int crosshairColor = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.8f);
        float cx = mousePos.x;
        float cy = mousePos.y;
        float size = 20f;

        drawList.addLine(cx - size, cy, cx + size, cy, crosshairColor, 2f);
        drawList.addLine(cx, cy - size, cx, cy + size, crosshairColor, 2f);
    }
}