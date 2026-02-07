package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.IGameObject;
import com.pocket.rpg.editor.core.EditorFonts;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.hierarchy.HierarchyItem;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.panels.SavePrefabPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldUndoTracker;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.*;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.RequestPrefabEditEvent;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import lombok.Setter;

import java.util.List;

/**
 * Renders single entity inspector.
 */
public class EntityInspector {

    @Setter
    private EditorScene scene;

    private final ComponentFieldEditor fieldEditor = new ComponentFieldEditor();
    private final ComponentBrowserPopup componentBrowserPopup = new ComponentBrowserPopup();
    private final ComponentListRenderer componentListRenderer;
    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    public EntityInspector() {
        componentListRenderer = new ComponentListRenderer(fieldEditor, componentBrowserPopup);
    }

    private final ImString stringBuffer = new ImString(256);

    private EditorGameObject pendingDeleteEntity = null;

    // Undo support for rename
    private String nameBeforeEdit = null;

    // Selection-change detection for clearing stale undo state
    private String lastSelectedEntityId = null;

    public void render(EditorGameObject entity) {
        // Clear stale undo state when selection changes
        String currentId = entity.getId();
        if (!currentId.equals(lastSelectedEntityId)) {
            FieldUndoTracker.clear();
            lastSelectedEntityId = currentId;
        }

        fieldEditor.setScene(scene);

        // Enabled checkbox
        boolean ownEnabled = entity.isOwnEnabled();
        ImBoolean enabledRef = new ImBoolean(ownEnabled);
        if (ImGui.checkbox("##EntityEnabled", enabledRef)) {
            UndoManager.getInstance().execute(
                    new ToggleEntityEnabledCommand(entity, enabledRef.get()));
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(ownEnabled ? "Disable entity" : "Enable entity");
        }
        ImGui.sameLine();

        String icon = IconUtils.getIconForEntity(entity);
        ImGui.pushFont(EditorFonts.getIconFont(20)); // Larger icon size
        float cursorY = ImGui.getCursorPosY();
        ImGui.setCursorPosY(cursorY - 3);
        ImGui.text(icon);
        ImGui.popFont();
        ImGui.setCursorPosY(cursorY);
        ImGui.sameLine();
        stringBuffer.set(entity.getName());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 70);
        if (ImGui.inputText("##EntityName", stringBuffer)) {
            entity.setName(stringBuffer.get());
            scene.markDirty();
        }

        // Capture old name when field is activated
        if (ImGui.isItemActivated()) {
            nameBeforeEdit = entity.getName();
        }

        // Push undo command when field is deactivated
        if (ImGui.isItemDeactivatedAfterEdit() && nameBeforeEdit != null) {
            String newName = entity.getName();
            if (!nameBeforeEdit.equals(newName)) {
                UndoManager.getInstance().push(new RenameEntityCommand(entity, nameBeforeEdit, newName));
            }
            nameBeforeEdit = null;
        }

        ImGui.sameLine();
        if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
            if (ImGui.button(MaterialIcons.Save + "##save")) {
                savePrefabPopup.open(entity, p -> System.out.println("Saved prefab: " + p.getId()));
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Save as Prefab");
            ImGui.sameLine();
        }

        // Edit Prefab button (icon only) for prefab instances
        if (entity.isPrefabInstance()) {
            Prefab prefab = entity.getPrefab();
            if (prefab instanceof JsonPrefab jsonPrefab) {
                if (ImGui.button(MaterialIcons.Edit + "##editPrefab")) {
                    EditorEventBus.get().publish(new RequestPrefabEditEvent(jsonPrefab));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Edit Prefab");
                }
                ImGui.sameLine();
            }
        }

        ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.3f, 0.3f, 1f);
        if (ImGui.button(MaterialIcons.Delete + "##delete")) {
            pendingDeleteEntity = entity;
            ImGui.openPopup("Delete Entity");
        }
        ImGui.popStyleColor(2);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Delete Entity");

        ImGui.separator();


        if (entity.isPrefabInstance()) {
            renderPrefabInfo(entity);
        }

        componentListRenderer.render(entity, entity.isPrefabInstance(),
                !entity.isPrefabInstance(), scene);
        componentListRenderer.renderPopup();
        savePrefabPopup.render();
    }

    /**
     * Renders inspector for a runtime game object during play mode.
     * Read-only header, editable component fields (changes are temporary).
     */
    public void renderRuntime(HierarchyItem gameObject) {
        // Header (read-only)
        ImGui.text(MaterialIcons.ViewInAr);
        ImGui.sameLine();
        ImGui.text(gameObject.getName());
        ImGui.sameLine();
        if (gameObject.isEnabled()) {
            ImGui.textColored(0.4f, 0.8f, 0.4f, 1f, "(enabled)");
        } else {
            ImGui.textColored(0.8f, 0.4f, 0.4f, 1f, "(disabled)");
        }

        ImGui.separator();

        // Render components
        List<Component> components = gameObject.getAllComponents();
        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                ImGui.pushID(i);
                String label = comp.getClass().getSimpleName();
                boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen);
                if (open) {
                    fieldEditor.renderRuntimeComponentFields(comp, gameObject);
                }
                ImGui.popID();
            }
        }

        ImGui.separator();
        ImGui.textDisabled("Changes reset when play mode stops");
    }

    public void renderDeleteConfirmationPopup() {
        if (ImGui.beginPopupModal("Delete Entity", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            if (pendingDeleteEntity != null) {
                ImGui.text("Delete entity '" + pendingDeleteEntity.getName() + "'?");

                if (pendingDeleteEntity.hasChildren()) {
                    ImGui.textColored(1f, 0.7f, 0.2f, 1f,
                            MaterialIcons.Warning + " This will also delete " +
                                    pendingDeleteEntity.getChildren().size() + " children!");
                }

                ImGui.spacing();
                ImGui.textDisabled("This can be undone with Ctrl+Z");
                ImGui.spacing();
                ImGui.separator();

                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1f);
                if (ImGui.button("Delete", 120, 0)) {
                    UndoManager.getInstance().execute(new RemoveEntityCommand(scene, pendingDeleteEntity));
                    scene.markDirty();
                    pendingDeleteEntity = null;
                    ImGui.closeCurrentPopup();
                }
                ImGui.popStyleColor();

                ImGui.sameLine();
                if (ImGui.button("Cancel", 120, 0)) {
                    pendingDeleteEntity = null;
                    ImGui.closeCurrentPopup();
                }
                if (ImGui.isKeyPressed(imgui.flag.ImGuiKey.Escape)) {
                    pendingDeleteEntity = null;
                    ImGui.closeCurrentPopup();
                }
            }
            ImGui.endPopup();
        }
    }



    private static final float LABEL_WIDTH = 90f;

    private void inspectorRow(String label, Runnable field) {
        ImGui.text(label);
        ImGui.sameLine(LABEL_WIDTH);
        ImGui.setNextItemWidth(-1);
        field.run();
    }

    private void renderPrefabInfo(EditorGameObject entity) {
        Prefab prefab = entity.getPrefab();
        ImGui.labelText("Prefab", prefab != null ? prefab.getDisplayName() : entity.getPrefabId() + " (missing)");

        if (prefab == null) {
            ImGui.textColored(1f, 0.5f, 0.2f, 1f, MaterialIcons.Warning + " Prefab not found");
        } else {
            int overrideCount = entity.getOverrideCount();
            if (overrideCount > 0) {
                ImGui.textDisabled(overrideCount + " override(s)");
                ImGui.sameLine();
                if (ImGui.smallButton("Reset All")) {
                    UndoManager.getInstance().execute(new ResetAllOverridesCommand(entity));
                    scene.markDirty();
                }
            }
        }
    }

}
