package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Transform;
import com.pocket.rpg.editor.PrefabEditController;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetPrefabMetadataCommand;
import com.pocket.rpg.prefab.JsonPrefab;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImString;
import org.joml.Vector3f;

/**
 * Inspector panel content for prefab edit mode.
 * Shows prefab metadata, component list, and save/exit buttons.
 */
public class PrefabInspector {

    private final ComponentFieldEditor fieldEditor;
    private final ComponentListRenderer componentListRenderer;

    private final ImString displayNameBuffer = new ImString(256);
    private final ImString categoryBuffer = new ImString(256);

    // Undo support for metadata fields
    private String displayNameBeforeEdit = null;
    private String categoryBeforeEdit = null;

    public PrefabInspector(ComponentFieldEditor fieldEditor, ComponentBrowserPopup componentBrowserPopup) {
        this.fieldEditor = fieldEditor;
        this.componentListRenderer = new ComponentListRenderer(fieldEditor, componentBrowserPopup);
    }

    public void render(PrefabEditController controller) {
        if (!controller.isActive()) return;

        JsonPrefab prefab = controller.getTargetPrefab();
        EditorGameObject workingEntity = controller.getWorkingEntity();
        if (prefab == null || workingEntity == null) return;

        fieldEditor.setContext(controller::markDirty, null);

        // Metadata section
        renderMetadata(controller, prefab);
        ImGui.separator();

        // Components section
        renderComponents(controller, workingEntity);

        componentListRenderer.renderPopup();
    }

    private void renderMetadata(PrefabEditController controller, JsonPrefab prefab) {
        ImGui.spacing();

        // Display Name
        ImGui.text("Display Name");
        ImGui.sameLine(100);
        displayNameBuffer.set(prefab.getDisplayName() != null ? prefab.getDisplayName() : "");
        ImGui.setNextItemWidth(-1);
        if (ImGui.isItemActivated()) {
            displayNameBeforeEdit = prefab.getDisplayName();
        }
        if (ImGui.inputText("##displayName", displayNameBuffer)) {
            prefab.setDisplayName(displayNameBuffer.get());
            controller.markDirty();
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newValue = displayNameBuffer.get();
            if (displayNameBeforeEdit != null && !displayNameBeforeEdit.equals(newValue)) {
                UndoManager.getInstance().push(new SetPrefabMetadataCommand(
                        prefab, "displayName", displayNameBeforeEdit, newValue,
                        JsonPrefab::setDisplayName, controller::markDirty
                ));
            }
            displayNameBeforeEdit = null;
        }

        // Category
        ImGui.text("Category");
        ImGui.sameLine(100);
        categoryBuffer.set(prefab.getCategory() != null ? prefab.getCategory() : "");
        ImGui.setNextItemWidth(-1);
        if (ImGui.isItemActivated()) {
            categoryBeforeEdit = prefab.getCategory();
        }
        if (ImGui.inputText("##category", categoryBuffer)) {
            prefab.setCategory(categoryBuffer.get());
            controller.markDirty();
        }
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newValue = categoryBuffer.get();
            if (categoryBeforeEdit != null && !categoryBeforeEdit.equals(newValue)) {
                UndoManager.getInstance().push(new SetPrefabMetadataCommand(
                        prefab, "category", categoryBeforeEdit, newValue,
                        JsonPrefab::setCategory, controller::markDirty
                ));
            }
            categoryBeforeEdit = null;
        }

        ImGui.spacing();
    }

    private void renderComponents(PrefabEditController controller, EditorGameObject workingEntity) {
        // Render components via shared ComponentListRenderer
        // isPrefabInstance=false: working entity is scratch, no override display
        // allowStructuralChanges=true: can add/remove components
        componentListRenderer.render(workingEntity, false, true, controller::markDirty);

        // Transform warning
        Transform transform = workingEntity.getTransform();
        if (transform != null) {
            Vector3f pos = transform.getPosition();
            Vector3f rot = transform.getRotation();
            Vector3f scale = transform.getScale();
            boolean nonOrigin = (pos.x != 0 || pos.y != 0 || pos.z != 0)
                    || (rot.x != 0 || rot.y != 0 || rot.z != 0)
                    || (scale.x != 1 || scale.y != 1 || scale.z != 1);
            if (nonOrigin) {
                ImGui.spacing();
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f);
                ImGui.textWrapped(MaterialIcons.Warning + " Non-origin default transform values will affect all instances without overrides");
                ImGui.popStyleColor();
            }
        }
    }

}
