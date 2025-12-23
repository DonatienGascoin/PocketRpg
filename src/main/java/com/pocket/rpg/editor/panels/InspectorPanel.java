package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.components.ReflectionFieldEditor;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.serialization.ComponentData;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetPropertyCommand;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PropertyDefinition;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Context-sensitive inspector panel.
 * <p>
 * Shows different content based on selection:
 * - Camera: Position, ortho size, follow settings, bounds
 * - Entity: Name, prefab, position, custom properties
 * - Layer: Name, z-index, visibility
 */
public class InspectorPanel {

    @Setter
    private EditorScene scene;

    @Setter
    private HierarchyPanel hierarchyPanel;

    // Buffers for input fields
    private final ImString stringBuffer = new ImString(256);
    private final float[] floatBuffer = new float[4];
    // private final int[] intBuffer = new int[1];
    private final ImInt intBuffer = new ImInt();

    // Component browser popup
    private final ComponentBrowserPopup componentBrowserPopup = new ComponentBrowserPopup();

    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    /**
     * Renders the inspector panel.
     */
    public void render() {
        if (ImGui.begin("Inspector")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            // Determine what to inspect
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
        // Render asset picker if open
        ReflectionFieldEditor.renderAssetPicker();
    }

    // ========================================================================
    // CAMERA INSPECTOR
    // ========================================================================

    private void renderCameraInspector() {
        ImGui.text(FontAwesomeIcons.Camera + " Scene Camera");
        ImGui.separator();

        SceneCameraSettings cam = scene.getCameraSettings();

        // Position
        floatBuffer[0] = cam.getPosition().x;
        floatBuffer[1] = cam.getPosition().y;
        if (ImGui.dragFloat2("Start Position", floatBuffer, 0.5f)) {
            cam.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        // Orthographic size
        floatBuffer[0] = cam.getOrthographicSize();
        if (ImGui.dragFloat("Ortho Size", floatBuffer, 0.5f, 1f, 50f)) {
            cam.setOrthographicSize(floatBuffer[0]);
            scene.markDirty();
        }

        ImGui.separator();
        ImGui.text("Follow Target");

        // Follow player toggle
        boolean followPlayer = cam.isFollowPlayer();
        if (ImGui.checkbox("Follow Player", followPlayer)) {
            cam.setFollowPlayer(!followPlayer);
            scene.markDirty();
        }

        // Target name (only if following)
        if (cam.isFollowPlayer()) {
            stringBuffer.set(cam.getFollowTargetName());
            if (ImGui.inputText("Target Name", stringBuffer)) {
                cam.setFollowTargetName(stringBuffer.get());
                scene.markDirty();
            }
        }

        ImGui.separator();
        ImGui.text("Camera Bounds");

        // Use bounds toggle
        boolean useBounds = cam.isUseBounds();
        if (ImGui.checkbox("Use Bounds", useBounds)) {
            cam.setUseBounds(!useBounds);
            scene.markDirty();
        }

        // Bounds editor (only if using bounds)
        if (cam.isUseBounds()) {
            floatBuffer[0] = cam.getBounds().x;
            floatBuffer[1] = cam.getBounds().y;
            floatBuffer[2] = cam.getBounds().z;
            floatBuffer[3] = cam.getBounds().w;

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
            // Scratch entity - use cube icon
            icon = FontAwesomeIcons.Cube;
        } else if (entity.isPrefabValid()) {
            // Valid prefab instance
            icon = FontAwesomeIcons.Cubes;
        } else {
            // Missing prefab - warning icon
            icon = FontAwesomeIcons.ExclamationTriangle;
        }
        ImGui.text(icon);
        ImGui.sameLine();
        stringBuffer.set(entity.getName());
        if (ImGui.inputText("##EntityName", stringBuffer)) {
            entity.setName(stringBuffer.get());
            scene.markDirty();
        }
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getContentRegionMaxX() - 60);
        if (entity.isScratchEntity() &&
                !entity.getComponents().isEmpty() &&
                ImGui.button(FontAwesomeIcons.Save)) {
            savePrefabPopup.open(entity, savedPrefab -> {
                // Optionally convert entity to prefab instance
                System.out.println("Saved prefab: " + savedPrefab.getId());
            });
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Save as Prefab");
        }
        ImGui.sameLine();
        if (ImGui.button(FontAwesomeIcons.Trash)) {
            scene.removeEntity(entity);
        }
        if (ImGui.isItemHovered()) { // TODO: Add confirmation modal !
            ImGui.setTooltip(" Delete Entity");
        }
        ImGui.separator();

        // Position
        Vector3f pos = entity.getPosition();
        floatBuffer[0] = pos.x;
        floatBuffer[1] = pos.y;

        if (ImGui.dragFloat2("Position", floatBuffer, 0.1f)) {
            entity.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        ImGui.sameLine();
        if (ImGui.smallButton("Snap")) {
            entity.setPosition(
                    Math.round(pos.x * 2) / 2f,
                    Math.round(pos.y)
            );
            scene.markDirty();
        }

        ImGui.separator();

        // ================================================================
        // PREFAB INSTANCE
        // ================================================================
        if (entity.isPrefabInstance()) {
            renderPrefabInstanceInspector(entity);
        }
        // ================================================================
        // SCRATCH ENTITY
        // ================================================================
        else {
            renderScratchEntityInspector(entity);
        }

        // Render component browser popup (must be called every frame)
        componentBrowserPopup.render();
    }

    /**
     * Renders inspector for prefab-based entities (existing behavior).
     */
    private void renderPrefabInstanceInspector(EditorEntity entity) {
        Prefab prefab = entity.getPrefab();
        String prefabDisplay = prefab != null ? prefab.getDisplayName() : entity.getPrefabId() + " (missing)";

        ImGui.labelText("Prefab", prefabDisplay);

        if (prefab == null) {
            ImGui.textColored(1f, 0.5f, 0.2f, 1f,
                    FontAwesomeIcons.ExclamationTriangle + " Prefab not found");
            return;
        }

        // Editable properties from prefab
        List<PropertyDefinition> props = prefab.getEditableProperties();
        if (!props.isEmpty()) {
            ImGui.separator();
            ImGui.text("Properties");

            for (PropertyDefinition prop : props) {
                // TODO: How to render the propertyDefinition with ReflectionFieldEditor ?
                //  Also, now that prefabs are JSON based, should this be removed totally ?
                //  I think it would be better, and replace this with prefab override
                renderPropertyEditor(entity, prop);
            }

            // Reset all overrides button
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

    /**
     * Renders inspector for scratch entities (new behavior).
     */
    private void renderScratchEntityInspector(EditorEntity entity) {
        ImGui.textDisabled("Scratch Entity");

        List<ComponentData> components = entity.getComponents();

        if (components.isEmpty()) {
            ImGui.textDisabled("No components");
        } else {
            // Track component to remove (can't modify list while iterating)
            ComponentData toRemove = null;

            for (int i = 0; i < components.size(); i++) {
                ComponentData comp = components.get(i);

                ImGui.pushID(i);

                // Component header with collapse and remove button
                int headerFlags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowItemOverlap;
                boolean open = ImGui.collapsingHeader(comp.getDisplayName(), headerFlags);

                // Remove button aligned to the right
                ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                if (ImGui.smallButton(FontAwesomeIcons.Times)) {
                    toRemove = comp;
                }
                ImGui.popStyleColor();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Remove component");
                }

                // Component fields
                if (open) {
                    ImGui.indent();
                    if (ReflectionFieldEditor.drawComponent(comp)) {
                        scene.markDirty();
                    }
                    ImGui.unindent();
                }

                ImGui.popID();
            }

            // Remove marked component
            if (toRemove != null) {
                entity.removeComponent(toRemove);
                scene.markDirty();
            }
        }

        ImGui.separator();

        // Add Component button
        if (ImGui.button(FontAwesomeIcons.Plus + " Add Component", -1, 0)) {
            componentBrowserPopup.open(componentData -> {
                entity.addComponent(componentData);
                scene.markDirty();
            });
        }

        // Render popup
        savePrefabPopup.render();
    }

    /**
     * Renders an editor for a single property.
     */
    /**
     * Renders an editor for a single property with override indication.
     */
    private void renderPropertyEditor(EditorEntity entity, PropertyDefinition prop) {
        Object value = entity.getProperty(prop.name());
        boolean isOverridden = entity.isPropertyOverridden(prop.name());

        ImGui.pushID(prop.name());

        boolean changed = false;
        Object newValue = value;

        // Override indicator: bold + colored label
        if (isOverridden) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);  // Cyan
        }

        ImGui.alignTextToFramePadding();
        String labelText = prop.name();
        ImGui.text(labelText);

        if (isOverridden) {
            ImGui.popStyleColor();
        }

        ImGui.sameLine(130);  // Fixed label width
        ImGui.setNextItemWidth(-60);  // Room for reset button

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

            case STRING_LIST -> {
                ImGui.textDisabled("(list - not editable yet)");
            }

            case ASSET_REF -> {
                String refValue = value != null ? value.toString() : "";
                stringBuffer.set(refValue);
                if (ImGui.inputText("##value", stringBuffer)) {
                    newValue = stringBuffer.get();
                    changed = true;
                }
            }

            default -> {
                ImGui.text(value != null ? value.toString() : "(null)");
            }
        }

        // Apply change
        if (changed) {
            Object oldValue = entity.getProperty(prop.name());
            UndoManager.getInstance().execute(
                    new SetPropertyCommand(entity, prop.name(), oldValue, newValue)
            );
            scene.markDirty();
        }

        // Tooltip with property info
        if (prop.tooltip() != null && ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.text(prop.tooltip());
            if (isOverridden) {
                Object defaultVal = entity.getPropertyDefault(prop.name());
                ImGui.textDisabled("Default: " + defaultVal);
            }
            ImGui.endTooltip();
        }

        // Reset button (only show if overridden)
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
            // Placeholder to keep alignment
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

        // Name
        stringBuffer.set(layer.getName());
        if (ImGui.inputText("Name", stringBuffer)) {
            String newName = stringBuffer.get().trim();
            if (!newName.isEmpty()) {
                layer.setName(newName);
                layer.getGameObject().setName(newName);
                scene.markDirty();
            }
        }

        // Z-Index
        intBuffer.set(layer.getZIndex());
        if (ImGui.inputInt("Z-Index", intBuffer)) {
            layer.setZIndex(intBuffer.get());
            scene.markDirty();
        }

        ImGui.sameLine();
        ImGui.textDisabled("(higher = front)");

        // Visibility
        boolean visible = layer.isVisible();
        if (ImGui.checkbox("Visible", visible)) {
            layer.setVisible(!visible);
            scene.markDirty();
        }

        // Locked
        boolean locked = layer.isLocked();
        if (ImGui.checkbox("Locked", locked)) {
            layer.setLocked(!locked);
            scene.markDirty();
        }

        ImGui.separator();

        // Layer info
        // TODO: ImGui.textDisabled("Tile count: " + layer.getTilemap().getTileCount());

        // Actions
        ImGui.separator();
        if (ImGui.button(FontAwesomeIcons.Trash + " Delete Layer")) {
            int index = scene.getActiveLayerIndex();
            if (index >= 0) {
                scene.removeLayer(index);
            }
        }
    }
}
