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
import com.pocket.rpg.editor.undo.commands.SetPropertyCommand;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PropertyDefinition;
import com.pocket.rpg.serialization.ComponentData;
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
    // ENTITY INSPECTOR
    // ========================================================================

    private void renderEntityInspector(EditorEntity entity) {
        String icon;
        if (entity.isScratchEntity()) {
            icon = FontAwesomeIcons.Cube;
        } else if (entity.isPrefabValid()) {
            icon = FontAwesomeIcons.Cubes;
        } else {
            icon = FontAwesomeIcons.ExclamationTriangle;
        }

        // Header
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
                savePrefabPopup.open(entity, savedPrefab -> {
                    System.out.println("Saved prefab: " + savedPrefab.getId());
                });
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Save as Prefab");
            }
            ImGui.sameLine();
        }

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
                // Create command but don't execute (position already changed)
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

        ImGui.separator();

        if (entity.isPrefabInstance()) {
            renderPrefabInstanceInspector(entity);
        } else {
            renderScratchEntityInspector(entity);
        }

        componentBrowserPopup.render();
        savePrefabPopup.render();
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
                    // Use undo command for delete
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

    private void renderPrefabInstanceInspector(EditorEntity entity) {
        Prefab prefab = entity.getPrefab();
        String prefabDisplay = prefab != null ? prefab.getDisplayName() : entity.getPrefabId() + " (missing)";

        ImGui.labelText("Prefab", prefabDisplay);

        if (prefab == null) {
            ImGui.textColored(1f, 0.5f, 0.2f, 1f,
                    FontAwesomeIcons.ExclamationTriangle + " Prefab not found");
            return;
        }

        List<PropertyDefinition> props = prefab.getEditableProperties();
        if (!props.isEmpty()) {
            ImGui.separator();
            ImGui.text("Properties");

            for (PropertyDefinition prop : props) {
                renderPropertyEditor(entity, prop);
            }

            List<String> overridden = entity.getOverriddenProperties();
            if (!overridden.isEmpty()) {
                ImGui.separator();
                ImGui.textDisabled(overridden.size() + " overridden properties");
                ImGui.sameLine();
                if (ImGui.smallButton("Reset All")) {
                    entity.resetAllToDefaults();
                    scene.markDirty();
                }
            }
        }
    }

    private void renderScratchEntityInspector(EditorEntity entity) {
        ImGui.textDisabled("Scratch Entity");

        List<ComponentData> components = entity.getComponents();

        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            ComponentData toRemove = null;

            for (int i = 0; i < components.size(); i++) {
                ComponentData comp = components.get(i);

                ImGui.pushID(i);

                int headerFlags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap;
                boolean open = ImGui.collapsingHeader(comp.getDisplayName(), headerFlags);

                ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                if (ImGui.smallButton(FontAwesomeIcons.Times + "##remove")) {
                    toRemove = comp;
                }
                ImGui.popStyleColor();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Remove component");
                }

                if (open) {
                    ImGui.indent();
                    if (ReflectionFieldEditor.drawComponent(comp, entity)) {
                        scene.markDirty();
                    }
                    ImGui.unindent();
                }

                ImGui.popID();
            }

            // Remove with undo command
            if (toRemove != null) {
                UndoManager.getInstance().execute(
                        new RemoveComponentCommand(entity, toRemove)
                );
                scene.markDirty();
            }
        }

        ImGui.separator();

        if (ImGui.button(FontAwesomeIcons.Plus + " Add Component", -1, 0)) {
            componentBrowserPopup.open(componentData -> {
                // Add with undo command
                UndoManager.getInstance().execute(
                        new AddComponentCommand(entity, componentData)
                );
                scene.markDirty();
            });
        }
    }

    private void renderPropertyEditor(EditorEntity entity, PropertyDefinition prop) {
        Object value = entity.getProperty(prop.name());
        boolean isOverridden = entity.isPropertyOverridden(prop.name());

        ImGui.pushID(prop.name());

        boolean changed = false;
        Object newValue = value;

        if (isOverridden) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);
        }

        ImGui.alignTextToFramePadding();
        ImGui.text(prop.name());

        if (isOverridden) {
            ImGui.popStyleColor();
        }

        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-60);

        switch (prop.type()) {
            case STRING -> {
                String strValue = value != null ? value.toString() : "";
                stringBuffer.set(strValue);
                if (ImGui.inputText("##value", stringBuffer)) {
                    newValue = stringBuffer.get();
                    changed = true;
                }
            }
            case INT -> {
                int intValue = value instanceof Number n ? n.intValue() : 0;
                intBuffer.set(intValue);
                if (ImGui.inputInt("##value", intBuffer)) {
                    newValue = intBuffer.get();
                    changed = true;
                }
            }
            case FLOAT -> {
                float floatValue = value instanceof Number n ? n.floatValue() : 0f;
                floatBuffer[0] = floatValue;
                if (ImGui.dragFloat("##value", floatBuffer, 0.1f)) {
                    newValue = floatBuffer[0];
                    changed = true;
                }
            }
            case BOOLEAN -> {
                boolean boolValue = value instanceof Boolean b && b;
                if (ImGui.checkbox("##value", boolValue)) {
                    newValue = !boolValue;
                    changed = true;
                }
            }
            case VECTOR2 -> {
                float[] vec = value instanceof float[] arr && arr.length >= 2
                        ? new float[]{arr[0], arr[1]}
                        : new float[]{0f, 0f};
                floatBuffer[0] = vec[0];
                floatBuffer[1] = vec[1];
                if (ImGui.dragFloat2("##value", floatBuffer, 0.1f)) {
                    newValue = new float[]{floatBuffer[0], floatBuffer[1]};
                    changed = true;
                }
            }
            case VECTOR3 -> {
                float[] vec = value instanceof float[] arr && arr.length >= 3
                        ? new float[]{arr[0], arr[1], arr[2]}
                        : new float[]{0f, 0f, 0f};
                floatBuffer[0] = vec[0];
                floatBuffer[1] = vec[1];
                floatBuffer[2] = vec[2];
                if (ImGui.dragFloat3("##value", floatBuffer, 0.1f)) {
                    newValue = new float[]{floatBuffer[0], floatBuffer[1], floatBuffer[2]};
                    changed = true;
                }
            }
            case STRING_LIST -> ImGui.textDisabled("(list - not editable yet)");
            case ASSET_REF -> {
                String refValue = value != null ? value.toString() : "";
                stringBuffer.set(refValue);
                if (ImGui.inputText("##value", stringBuffer)) {
                    newValue = stringBuffer.get();
                    changed = true;
                }
            }
            default -> ImGui.text(value != null ? value.toString() : "(null)");
        }

        if (changed) {
            Object oldValue = entity.getProperty(prop.name());
            UndoManager.getInstance().execute(
                    new SetPropertyCommand(entity, prop.name(), oldValue, newValue)
            );
            scene.markDirty();
        }

        if (prop.tooltip() != null && ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(prop.tooltip());
            if (isOverridden) {
                Object defaultVal = entity.getPropertyDefault(prop.name());
                ImGui.textDisabled("Default: " + defaultVal);
            }
            ImGui.endTooltip();
        }

        ImGui.sameLine();
        if (isOverridden) {
            if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset")) {
                entity.resetPropertyToDefault(prop.name());
                scene.markDirty();
            }
            if (ImGui.isItemHovered()) {
                Object defaultVal = entity.getPropertyDefault(prop.name());
                ImGui.setTooltip("Reset to default: " + defaultVal);
            }
        } else {
            ImGui.dummy(20, 0);
        }

        ImGui.popID();
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

    /**
     * Wrapper for commands where execute was already done (drag operations).
     * Stores the original values for proper undo.
     */
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
