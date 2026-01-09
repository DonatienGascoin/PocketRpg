package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.panels.ComponentBrowserPopup;
import com.pocket.rpg.editor.panels.SavePrefabPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.*;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.serialization.ComponentRegistry;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders single entity inspector.
 */
public class EntityInspector {

    @Setter
    private EditorScene scene;

    private final ComponentFieldEditor fieldEditor = new ComponentFieldEditor();
    private final ComponentBrowserPopup componentBrowserPopup = new ComponentBrowserPopup();
    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    private final ImString stringBuffer = new ImString(256);

    private EditorGameObject pendingDeleteEntity = null;

    public void render(EditorGameObject entity) {
        fieldEditor.setScene(scene);

        String icon = IconUtils.getIconForEntity(entity);

        ImGui.text(icon);
        ImGui.sameLine();
        stringBuffer.set(entity.getName());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 70);
        if (ImGui.inputText("##EntityName", stringBuffer)) {
            entity.setName(stringBuffer.get());
            scene.markDirty();
        }

        ImGui.sameLine();
        if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
            if (ImGui.button(FontAwesomeIcons.Save + "##save")) {
                savePrefabPopup.open(entity, p -> System.out.println("Saved prefab: " + p.getId()));
            }
            if (ImGui.isItemHovered()) ImGui.setTooltip("Save as Prefab");
            ImGui.sameLine();
        }

        ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.3f, 0.3f, 1f);
        if (ImGui.button(FontAwesomeIcons.Trash + "##delete")) {
            pendingDeleteEntity = entity;
            ImGui.openPopup("Delete Entity?");
        }
        ImGui.popStyleColor(2);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Delete Entity");

        ImGui.separator();


        if (entity.isPrefabInstance()) {
            renderPrefabInfo(entity);
        }

        renderComponentList(entity);
        componentBrowserPopup.render();
        savePrefabPopup.render();
    }

    public void renderDeleteConfirmationPopup() {
        if (ImGui.beginPopupModal("Delete Entity?", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            if (pendingDeleteEntity != null) {
                ImGui.text("Delete entity '" + pendingDeleteEntity.getName() + "'?");

                if (pendingDeleteEntity.hasChildren()) {
                    ImGui.textColored(1f, 0.7f, 0.2f, 1f,
                            FontAwesomeIcons.ExclamationTriangle + " This will also delete " +
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
            ImGui.textColored(1f, 0.5f, 0.2f, 1f, FontAwesomeIcons.ExclamationTriangle + " Prefab not found");
        } else {
            int overrideCount = entity.getOverrideCount();
            if (overrideCount > 0) {
                ImGui.textDisabled(overrideCount + " override(s)");
                ImGui.sameLine();
                if (ImGui.smallButton("Reset All")) {
                    entity.resetAllOverrides();
                    scene.markDirty();
                }
            }
        }
    }

    private void renderComponentList(EditorGameObject entity) {
        List<Component> components = entity.getComponents();
        boolean isPrefab = entity.isPrefabInstance();

        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            Component toRemove = null;

            for (int i = 0; i < components.size(); i++) {
                Component comp = components.get(i);
                ImGui.pushID(i);

                String componentType = comp.getClass().getName();
                String label = comp.getClass().getSimpleName();
                if (isPrefab && !entity.getOverriddenFields(componentType).isEmpty()) {
                    label += " *";
                }

                boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap);

                if (!isPrefab) {
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                    if (ImGui.smallButton(FontAwesomeIcons.Times + "##remove")) toRemove = comp;
                    ImGui.popStyleColor();
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Remove component");
                }

                if (open) {
                    if (fieldEditor.renderComponentFields(entity, comp, isPrefab)) {
                        scene.markDirty();
                    }
                }

                ImGui.popID();
            }

            if (toRemove != null) {
                UndoManager.getInstance().execute(new RemoveComponentCommand(entity, toRemove));
                scene.markDirty();
            }
        }

        if (!isPrefab) {
            ImGui.separator();
            if (ImGui.button(FontAwesomeIcons.Plus + " Add Component", -1, 0)) {
                componentBrowserPopup.open(meta -> {
                    Component component = ComponentRegistry.instantiateByClassName(meta.className());
                    if (component != null) {
                        UndoManager.getInstance().execute(new AddComponentCommand(entity, component));
                        scene.markDirty();
                    }
                });
            }
        }
    }
}
