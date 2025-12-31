package com.pocket.rpg.editor.assets;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import imgui.ImGui;
import org.joml.Vector3f;

/**
 * Handles asset drop operations in the HierarchyPanel.
 * <p>
 * Provides methods for handling drops on:
 * - Empty area (creates entity at origin)
 * - Existing entity (creates as child - pending parenting system)
 * - Section headers (creates at origin)
 * <p>
 * Integration example:
 * <pre>
 * // After rendering entities section header
 * if (HierarchyDropTarget.handleSectionDrop(scene, "Entities")) {
 *     // Entity was added
 * }
 *
 * // After rendering each entity item
 * if (HierarchyDropTarget.handleEntityDrop(scene, parentEntity)) {
 *     // Child entity was added (pending parenting)
 * }
 * </pre>
 */
public class HierarchyDropTarget {

    private static final Vector3f ORIGIN = new Vector3f(0, 0, 0);

    /**
     * Handles drop on empty area or section header.
     * Creates entity at world origin.
     *
     * @param scene The editor scene
     * @param targetId Unique ID for the drop target (e.g., "Entities", "empty_area")
     * @return true if an entity was created
     */
    public static boolean handleSectionDrop(EditorScene scene, String targetId) {
        if (scene == null) {
            return false;
        }

        if (ImGui.beginDragDropTarget()) {
            byte[] payloadData = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);

            if (payloadData != null && payloadData.length > 0) {
                AssetDragPayload payload = AssetDragPayload.deserialize(payloadData);

                if (payload != null && AssetDropHandler.canInstantiate(payload)) {
                    EditorEntity entity = AssetDropHandler.handleDrop(payload, new Vector3f(ORIGIN));

                    if (entity != null) {
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
     * Handles drop on an existing entity.
     * Creates entity as child of the target entity.
     * <p>
     * Note: Parenting is pending implementation. Currently creates
     * entity at same position as parent.
     *
     * @param scene The editor scene
     * @param parentEntity The entity being dropped onto
     * @return true if an entity was created
     */
    public static boolean handleEntityDrop(EditorScene scene, EditorEntity parentEntity) {
        if (scene == null || parentEntity == null) {
            return false;
        }

        if (ImGui.beginDragDropTarget()) {
            byte[] payloadData = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);

            if (payloadData != null && payloadData.length > 0) {
                AssetDragPayload payload = AssetDragPayload.deserialize(payloadData);

                if (payload != null && AssetDropHandler.canInstantiate(payload)) {
                    // Position at parent's location (will be relative when parenting is implemented)
                    Vector3f position = parentEntity.getPosition();

                    EditorEntity entity = AssetDropHandler.handleDrop(payload, position);

                    if (entity != null) {
                        // TODO: Set parent when parenting system is implemented
                        // entity.setParent(parentEntity);

                        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
                        scene.setSelectedEntity(entity);
                        scene.markDirty();

                        System.out.println("Created entity as child of " + parentEntity.getName() +
                                " (parenting pending implementation)");

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
     * Renders a drop target indicator for the entire entities section.
     * Call after rendering all entity items.
     *
     * @param scene The editor scene
     * @return true if an entity was created from drop
     */
    public static boolean handleEmptyAreaDrop(EditorScene scene) {
        if (scene == null) {
            return false;
        }

        // Create an invisible button to catch drops on empty area
        float availableHeight = ImGui.getContentRegionAvailY();
        if (availableHeight > 20) {
            ImGui.invisibleButton("##empty_drop_area", ImGui.getContentRegionAvailX(), availableHeight - 10);

            if (ImGui.beginDragDropTarget()) {
                byte[] payloadData = ImGui.acceptDragDropPayload(AssetDragPayload.DRAG_TYPE);

                if (payloadData != null && payloadData.length > 0) {
                    AssetDragPayload payload = AssetDragPayload.deserialize(payloadData);

                    if (payload != null && AssetDropHandler.canInstantiate(payload)) {
                        EditorEntity entity = AssetDropHandler.handleDrop(payload, new Vector3f(ORIGIN));

                        if (entity != null) {
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
        }

        return false;
    }

    /**
     * Checks if a drag is currently in progress with an instantiable asset.
     */
    public static boolean isDraggingInstantiableAsset() {
        // Check if we're in a drag operation with our payload type
        return ImGui.getDragDropPayload() != null;
    }
}
