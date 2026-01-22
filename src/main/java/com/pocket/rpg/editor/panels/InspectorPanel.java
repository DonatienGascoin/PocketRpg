package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.panels.inspector.*;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.ReflectionFieldEditor;
import imgui.ImGui;
import lombok.Setter;

import java.util.Set;

/**
 * Context-sensitive inspector panel - orchestrates specialized inspectors.
 * Shows the current selection regardless of editor mode.
 */
public class InspectorPanel extends EditorPanel {

    private static final String PANEL_ID = "inspector";

    @Setter
    private EditorScene scene;

    @Setter
    private EditorSelectionManager selectionManager;

    private final CameraInspector cameraInspector = new CameraInspector();
    private final TilemapLayersInspector tilemapInspector = new TilemapLayersInspector();
    private final CollisionMapInspector collisionInspector = new CollisionMapInspector();
    private final EntityInspector entityInspector = new EntityInspector();
    private final MultiSelectionInspector multiSelectionInspector = new MultiSelectionInspector();

    public InspectorPanel() {
        super(PANEL_ID, true); // Default open - core panel
    }

    @Override
    public void render() {
        if (!isOpen()) return;
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

            if (selectionManager == null) {
                ImGui.textDisabled("Select an item to inspect");
            } else if (selectionManager.isCameraSelected()) {
                cameraInspector.render();
            } else if (selectionManager.isTilemapLayerSelected()) {
                tilemapInspector.render();
            } else if (selectionManager.isCollisionLayerSelected()) {
                collisionInspector.render();
            } else if (selectionManager.hasEntitySelection()) {
                Set<EditorGameObject> selected = selectionManager.getSelectedEntities();
                if (selected.size() > 1) {
                    multiSelectionInspector.render(selected);
                } else if (selected.size() == 1) {
                    entityInspector.render(selected.iterator().next());
                }
            } else {
                ImGui.textDisabled("Select an item to inspect");
            }
        }
        ReflectionFieldEditor.renderAssetPicker();
        entityInspector.renderDeleteConfirmationPopup();
        ImGui.end();

    }
}
