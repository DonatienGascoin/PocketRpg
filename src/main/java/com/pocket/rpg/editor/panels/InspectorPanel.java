package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.panels.inspector.*;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.utils.ReflectionFieldEditor;
import imgui.ImGui;
import lombok.Setter;

import java.util.Set;

/**
 * Context-sensitive inspector panel - orchestrates specialized inspectors.
 */
public class InspectorPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private HierarchyPanel hierarchyPanel;

    private final CameraInspector cameraInspector = new CameraInspector();
    private final TilemapLayersInspector tilemapInspector = new TilemapLayersInspector();
    private final CollisionMapInspector collisionInspector = new CollisionMapInspector();
    private final EntityInspector entityInspector = new EntityInspector();
    private final MultiSelectionInspector multiSelectionInspector = new MultiSelectionInspector();

    public void render() {
        if (ImGui.begin("Inspector")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            // Update scene references
            cameraInspector.setScene(scene);
            tilemapInspector.setScene(scene);
            collisionInspector.setScene(scene);
            entityInspector.setScene(scene);
            multiSelectionInspector.setScene(scene);

            if (hierarchyPanel != null && hierarchyPanel.isCameraSelected()) {
                cameraInspector.render();
            } else if (hierarchyPanel != null && hierarchyPanel.isTilemapLayersSelected()) {
                tilemapInspector.render();
            } else if (hierarchyPanel != null && hierarchyPanel.isCollisionMapSelected()) {
                collisionInspector.render();
            } else {
                Set<EditorGameObject> selected = scene.getSelectedEntities();
                if (selected.size() > 1) {
                    multiSelectionInspector.render(selected);
                } else if (selected.size() == 1) {
                    entityInspector.render(selected.iterator().next());
                } else {
                    ImGui.textDisabled("Select an item to inspect");
                }
            }
        }
        ReflectionFieldEditor.renderAssetPicker();
        entityInspector.renderDeleteConfirmationPopup();
        ImGui.end();

    }
}
