package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.utils.ReflectionFieldEditor;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddComponentCommand;
import com.pocket.rpg.editor.undo.commands.MoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RemoveComponentCommand;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.serialization.ComponentMeta;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.FieldMeta;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Context-sensitive inspector panel with full undo support.
 * <p>
 * Uses a unified component-based approach for both prefab instances
 * and scratch entities.
 */
public class InspectorPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private HierarchyPanel hierarchyPanel;

    private final ImString stringBuffer = new ImString(256);
    private final float[] floatBuffer = new float[4];
    private final ImInt intBuffer = new ImInt();

    private final ComponentBrowserPopup componentBrowserPopup = new ComponentBrowserPopup();
    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    // Delete confirmation
    private EditorEntity pendingDeleteEntity = null;

    // Position drag tracking for undo
    private EditorEntity draggingEntity = null;
    private Vector3f dragStartPosition = null;

    public void render() {
        if (ImGui.begin("Inspector")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            if (hierarchyPanel != null && hierarchyPanel.isCameraSelected()) {
                renderCameraInspector();
            } else if (scene.getSelectedEntity() != null) {
                renderEntityInspector(scene.getSelectedEntity());
            } else if (scene.getActiveLayerIndex() >= 0) {
                TilemapLayer layer = scene.getActiveLayer();
                if (layer != null) {
                    renderLayerInspector(layer);
                }
            } else {
                ImGui.textDisabled("Select an item to inspect");
            }
        }
        ImGui.end();

        ReflectionFieldEditor.renderAssetPicker();
        renderDeleteConfirmationPopup();
    }

    // ========================================================================
    // CAMERA INSPECTOR
    // ========================================================================

    private void renderCameraInspector() {
        ImGui.text(FontAwesomeIcons.Camera + " Scene Camera");
        ImGui.separator();

        SceneCameraSettings cam = scene.getCameraSettings();

        floatBuffer[0] = cam.getPosition().x;
        floatBuffer[1] = cam.getPosition().y;
        if (ImGui.dragFloat2("Start Position", floatBuffer, 0.5f)) {
            cam.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        floatBuffer[0] = cam.getOrthographicSize();
        if (ImGui.dragFloat("Ortho Size", floatBuffer, 0.5f, 1f, 50f)) {
            cam.setOrthographicSize(floatBuffer[0]);
            scene.markDirty();
        }

        ImGui.separator();
        ImGui.text("Follow Target");

        boolean followPlayer = cam.isFollowPlayer();
        if (ImGui.checkbox("Follow Player", followPlayer)) {
            cam.setFollowPlayer(!followPlayer);
            scene.markDirty();
        }

        if (cam.isFollowPlayer()) {
            stringBuffer.set(cam.getFollowTargetName());
            if (ImGui.inputText("Target Name", stringBuffer)) {
                cam.setFollowTargetName(stringBuffer.get());
                scene.markDirty();
            }
        }

        ImGui.separator();
        ImGui.text("Camera Bounds");

        boolean useBounds = cam.isUseBounds();
        if (ImGui.checkbox("Use Bounds", useBounds)) {
            cam.setUseBounds(!useBounds);
            scene.markDirty();
        }

        if (cam.isUseBounds()) {
            floatBuffer[0] = cam.getBounds().x;
            floatBuffer[1] = cam.getBounds().y;
            ImGui.text("Min (X, Y)");
            if (ImGui.dragFloat2("##boundsMin", floatBuffer, 0.5f)) {
                cam.setBounds(floatBuffer[0], floatBuffer[1], cam.getBounds().z, cam.getBounds().w);
                scene.markDirty();
            }

            floatBuffer[0] = cam.getBounds().z;
            floatBuffer[1] = cam.getBounds().w;
            ImGui.text("Max (X, Y)");
            if (ImGui.dragFloat2("##boundsMax", floatBuffer, 0.5f)) {
                cam.setBounds(cam.getBounds().x, cam.getBounds().y, floatBuffer[0], floatBuffer[1]);
                scene.markDirty();
            }
        }
    }

    // ========================================================================
    // ENTITY INSPECTOR (UNIFIED)
    // ========================================================================

    private void renderEntityInspector(EditorEntity entity) {
        // Determine icon based on entity type
        String icon;
        if (entity.isScratchEntity()) {
            icon = FontAwesomeIcons.Cube;
        } else if (entity.isPrefabValid()) {
            icon = FontAwesomeIcons.Cubes;
        } else {
            icon = FontAwesomeIcons.ExclamationTriangle;
        }

        // Header with name
        ImGui.text(icon);
        ImGui.sameLine();
        stringBuffer.set(entity.getName());
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 70);
        if (ImGui.inputText("##EntityName", stringBuffer)) {
            entity.setName(stringBuffer.get());
            scene.markDirty();
        }

        // Save as Prefab button (for scratch entities with components)
        ImGui.sameLine();
        if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
            if (ImGui.button(FontAwesomeIcons.Save + "##save")) {
                savePrefabPopup.open(entity, savedPrefab -> {
                    System.out.println("Saved prefab: " + savedPrefab.getId());
                });
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save as Prefab");
            }
            ImGui.sameLine();
        }

        // Delete button
        ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.3f, 0.3f, 1f);
        if (ImGui.button(FontAwesomeIcons.Trash + "##delete")) {
            pendingDeleteEntity = entity;
            ImGui.openPopup("Delete Entity?");
        }
        ImGui.popStyleColor(2);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Delete Entity");
        }

        ImGui.separator();

        // Position with undo support
        renderPositionEditor(entity);

        ImGui.separator();

        // Prefab info (if applicable)
        if (entity.isPrefabInstance()) {
            renderPrefabInfo(entity);
            ImGui.separator();
        } else {
            ImGui.textDisabled("Scratch Entity");
        }

        // Components (unified for both prefab instances and scratch entities)
        renderComponentList(entity);

        // Popups
        componentBrowserPopup.render();
        savePrefabPopup.render();
    }

    private void renderPositionEditor(EditorEntity entity) {
        Vector3f pos = entity.getPosition();
        floatBuffer[0] = pos.x;
        floatBuffer[1] = pos.y;

        // Track drag start
        if (ImGui.isItemActive() && draggingEntity != entity) {
            draggingEntity = entity;
            dragStartPosition = new Vector3f(pos);
        }

        if (ImGui.dragFloat2("Position", floatBuffer, 0.1f)) {
            entity.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        // Commit move command when drag ends
        if (draggingEntity == entity && !ImGui.isItemActive()) {
            Vector3f newPos = entity.getPosition();
            if (!newPos.equals(dragStartPosition)) {
                MoveEntityCommand cmd = new MoveEntityCommand(entity, dragStartPosition, newPos);
                UndoManager.getInstance().execute(new NoOpWrapperCommand(cmd, entity, dragStartPosition));
            }
            draggingEntity = null;
            dragStartPosition = null;
        }

        ImGui.sameLine();
        if (ImGui.smallButton("Snap")) {
            Vector3f oldPos = new Vector3f(entity.getPosition());
            entity.setPosition(
                    Math.round(pos.x * 2) / 2f,
                    Math.round(pos.y)
            );
            Vector3f newPos = entity.getPosition();
            if (!newPos.equals(oldPos)) {
                UndoManager.getInstance().execute(
                        new MoveEntityCommand(entity, oldPos, newPos) {
                            @Override
                            public void execute() {
                                // Already done
                            }
                        }
                );
            }
            scene.markDirty();
        }
    }

    private void renderPrefabInfo(EditorEntity entity) {
        Prefab prefab = entity.getPrefab();
        String prefabDisplay = prefab != null ? prefab.getDisplayName() : entity.getPrefabId() + " (missing)";

        ImGui.labelText("Prefab", prefabDisplay);

        if (prefab == null) {
            ImGui.textColored(1f, 0.5f, 0.2f, 1f,
                    FontAwesomeIcons.ExclamationTriangle + " Prefab not found");
        } else {
            int overrideCount = entity.getOverrideCount();
            if (overrideCount > 0) {
                ImGui.textDisabled(overrideCount + " field override(s)");
                ImGui.sameLine();
                if (ImGui.smallButton("Reset All")) {
                    entity.resetAllOverrides();
                    scene.markDirty();
                }
            }
        }
    }

    private void renderComponentList(EditorEntity entity) {
        List<ComponentData> components = entity.getComponents();
        boolean isPrefabInstance = entity.isPrefabInstance();

        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            ComponentData toRemove = null;

            for (int i = 0; i < components.size(); i++) {
                ComponentData comp = components.get(i);

                ImGui.pushID(i);

                // Component header
                int headerFlags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap;
                
                // Show override indicator for prefab instances
                String headerLabel = comp.getDisplayName();
                if (isPrefabInstance) {
                    List<String> overridden = entity.getOverriddenFields(comp.getType());
                    if (!overridden.isEmpty()) {
                        headerLabel += " *";
                    }
                }

                boolean open = ImGui.collapsingHeader(headerLabel, headerFlags);

                // Remove button (only for scratch entities)
                if (!isPrefabInstance) {
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                    if (ImGui.smallButton(FontAwesomeIcons.Times + "##remove")) {
                        toRemove = comp;
                    }
                    ImGui.popStyleColor();
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Remove component");
                    }
                }

                if (open) {
                    ImGui.indent();

                    if (isPrefabInstance) {
                        // For prefab instances, render with override tracking
                        renderComponentFieldsWithOverrides(entity, comp);
                    } else {
                        // For scratch entities, use standard editor
                        if (ReflectionFieldEditor.drawComponent(comp, entity)) {
                            scene.markDirty();
                        }
                    }

                    ImGui.unindent();
                }

                ImGui.popID();
            }

            // Remove with undo command (scratch entities only)
            if (toRemove != null) {
                UndoManager.getInstance().execute(
                        new RemoveComponentCommand(entity, toRemove)
                );
                scene.markDirty();
            }
        }

        // Add Component button (only for scratch entities)
        if (!isPrefabInstance) {
            ImGui.separator();
            if (ImGui.button(FontAwesomeIcons.Plus + " Add Component", -1, 0)) {
                componentBrowserPopup.open(componentData -> {
                    UndoManager.getInstance().execute(
                            new AddComponentCommand(entity, componentData)
                    );
                    scene.markDirty();
                });
            }
        }
    }

    /**
     * Renders component fields for prefab instances with override tracking.
     */
    private void renderComponentFieldsWithOverrides(EditorEntity entity, ComponentData comp) {
        ComponentMeta meta = ComponentRegistry.getByClassName(comp.getType());
        if (meta == null) {
            ImGui.textDisabled("Unknown component type");
            return;
        }

        for (FieldMeta fieldMeta : meta.fields()) {
            String fieldName = fieldMeta.name();
            String componentType = comp.getType();
            
            boolean isOverridden = entity.isFieldOverridden(componentType, fieldName);
            Object value = entity.getFieldValue(componentType, fieldName);

            ImGui.pushID(fieldName);

            // Show override indicator
            if (isOverridden) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);
            }

            // Draw the field using reflection editor
            boolean changed = ReflectionFieldEditor.drawField(comp, fieldMeta);

            if (isOverridden) {
                ImGui.popStyleColor();
            }

            // Reset button for overridden fields
            if (isOverridden) {
                ImGui.sameLine();
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset")) {
                    entity.resetFieldToDefault(componentType, fieldName);
                    
                    // Update ComponentData to reflect the reset
                    Object defaultValue = entity.getFieldDefault(componentType, fieldName);
                    comp.getFields().put(fieldName, defaultValue);
                    
                    scene.markDirty();
                }
                if (ImGui.isItemHovered()) {
                    Object defaultVal = entity.getFieldDefault(componentType, fieldName);
                    ImGui.setTooltip("Reset to default: " + defaultVal);
                }
            }

            // Track changes for prefab overrides
            if (changed) {
                Object newValue = comp.getFields().get(fieldName);
                entity.setFieldValue(componentType, fieldName, newValue);
                scene.markDirty();
            }

            ImGui.popID();
        }

        // Show component references (read-only)
        ReflectionFieldEditor.drawComponentReferences(meta.references(), entity);
    }

    private void renderDeleteConfirmationPopup() {
        if (ImGui.beginPopupModal("Delete Entity?", imgui.flag.ImGuiWindowFlags.AlwaysAutoResize)) {
            if (pendingDeleteEntity != null) {
                ImGui.text("Delete entity '" + pendingDeleteEntity.getName() + "'?");
                ImGui.spacing();
                ImGui.textDisabled("This can be undone with Ctrl+Z");
                ImGui.spacing();
                ImGui.separator();

                ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1f);
                if (ImGui.button("Delete", 120, 0)) {
                    UndoManager.getInstance().execute(
                            new RemoveEntityCommand(scene, pendingDeleteEntity)
                    );
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

    // ========================================================================
    // LAYER INSPECTOR
    // ========================================================================

    private void renderLayerInspector(TilemapLayer layer) {
        ImGui.text(FontAwesomeIcons.LayerGroup + " Layer");
        ImGui.separator();

        stringBuffer.set(layer.getName());
        if (ImGui.inputText("Name", stringBuffer)) {
            String newName = stringBuffer.get().trim();
            if (!newName.isEmpty()) {
                layer.setName(newName);
                layer.getGameObject().setName(newName);
                scene.markDirty();
            }
        }

        intBuffer.set(layer.getZIndex());
        if (ImGui.inputInt("Z-Index", intBuffer)) {
            layer.setZIndex(intBuffer.get());
            scene.markDirty();
        }

        ImGui.sameLine();
        ImGui.textDisabled("(higher = front)");

        boolean visible = layer.isVisible();
        if (ImGui.checkbox("Visible", visible)) {
            layer.setVisible(!visible);
            scene.markDirty();
        }

        boolean locked = layer.isLocked();
        if (ImGui.checkbox("Locked", locked)) {
            layer.setLocked(!locked);
            scene.markDirty();
        }

        ImGui.separator();

        if (ImGui.button(FontAwesomeIcons.Trash + " Delete Layer")) {
            int index = scene.getActiveLayerIndex();
            if (index >= 0) {
                scene.removeLayer(index);
            }
        }
    }

    // ========================================================================
    // HELPER COMMAND
    // ========================================================================

    private static class NoOpWrapperCommand extends MoveEntityCommand {
        public NoOpWrapperCommand(MoveEntityCommand original, EditorEntity entity, Vector3f oldPos) {
            super(entity, oldPos, entity.getPosition());
        }

        @Override
        public void execute() {
            // No-op: position already changed during drag
        }
    }
}
