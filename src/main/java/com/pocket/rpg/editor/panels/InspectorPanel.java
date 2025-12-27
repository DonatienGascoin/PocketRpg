package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.utils.ReflectionFieldEditor;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.LayerVisibilityMode;
import com.pocket.rpg.editor.scene.SceneCameraSettings;
import com.pocket.rpg.editor.scene.TilemapLayer;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddComponentCommand;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.undo.commands.BulkMoveCommand;
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
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Context-sensitive inspector panel with multi-selection support.
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

    private EditorEntity pendingDeleteEntity = null;
    private EditorEntity draggingEntity = null;
    private Vector3f dragStartPosition = null;

    private int renamingLayerIndex = -1;
    private final ImString layerRenameBuffer = new ImString(64);

    private static final int ENTITY_Z_LEVEL = 0;

    public void render() {
        if (ImGui.begin("Inspector")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            if (hierarchyPanel != null && hierarchyPanel.isCameraSelected()) {
                renderCameraInspector();
            } else if (hierarchyPanel != null && hierarchyPanel.isTilemapLayersSelected()) {
                renderTilemapLayersInspector();
            } else if (hierarchyPanel != null && hierarchyPanel.isCollisionMapSelected()) {
                renderCollisionMapInspector();
            } else {
                Set<EditorEntity> selected = scene.getSelectedEntities();
                if (selected.size() > 1) {
                    renderMultiSelectionInspector(selected);
                } else if (selected.size() == 1) {
                    renderEntityInspector(selected.iterator().next());
                } else {
                    ImGui.textDisabled("Select an item to inspect");
                }
            }
        }
        ImGui.end();

        ReflectionFieldEditor.renderAssetPicker();
        renderDeleteConfirmationPopup();
    }

    // ========================================================================
    // MULTI-SELECTION INSPECTOR
    // ========================================================================

    private void renderMultiSelectionInspector(Set<EditorEntity> selected) {
        ImGui.text(FontAwesomeIcons.ObjectGroup + " " + selected.size() + " entities selected");
        ImGui.separator();

        // Bulk position offset
        ImGui.text("Move Offset");
        floatBuffer[0] = 0;
        floatBuffer[1] = 0;
        if (ImGui.dragFloat2("##offset", floatBuffer, 0.1f)) {
            if (floatBuffer[0] != 0 || floatBuffer[1] != 0) {
                Vector3f offset = new Vector3f(floatBuffer[0], floatBuffer[1], 0);
                UndoManager.getInstance().execute(new BulkMoveCommand(scene, selected, offset));
                floatBuffer[0] = 0;
                floatBuffer[1] = 0;
            }
        }

        ImGui.sameLine();
        if (ImGui.smallButton("Snap All")) {
            for (EditorEntity entity : selected) {
                Vector3f pos = entity.getPosition();
                entity.setPosition(Math.round(pos.x * 2) / 2f, Math.round(pos.y));
            }
            scene.markDirty();
        }

        ImGui.separator();

        // List selected entities
        ImGui.text("Selected:");
        ImGui.beginChild("##selectedList", 0, 100, true);
        for (EditorEntity entity : selected) {
            String icon = entity.isScratchEntity() ? FontAwesomeIcons.Cube : FontAwesomeIcons.Cubes;
            ImGui.text(icon + " " + entity.getName());
        }
        ImGui.endChild();

        ImGui.separator();

        // Bulk actions
        ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.6f, 0.3f, 0.3f, 1f);
        if (ImGui.button(FontAwesomeIcons.Trash + " Delete All", -1, 0)) {
            UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
        }
        ImGui.popStyleColor(2);

        if (ImGui.button(FontAwesomeIcons.TimesCircle + " Clear Selection", -1, 0)) {
            scene.clearSelection();
        }

        ImGui.separator();
        ImGui.textDisabled("Component editing disabled for multi-selection");
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
    // TILEMAP LAYERS INSPECTOR
    // ========================================================================

    private void renderTilemapLayersInspector() {
        ImGui.text(FontAwesomeIcons.LayerGroup + " Tilemap Layers");
        ImGui.separator();

        renderLayerControls();
        ImGui.separator();
        renderLayerList();

        TilemapLayer activeLayer = scene.getActiveLayer();
        if (activeLayer != null) {
            ImGui.separator();
            renderActiveLayerDetails(activeLayer);
        }
    }

    private void renderLayerControls() {
        if (ImGui.button(FontAwesomeIcons.Plus + " Add Layer")) {
            scene.addLayer("Layer " + scene.getLayerCount());
        }

        ImGui.sameLine();

        boolean canRemove = scene.getActiveLayer() != null;
        if (!canRemove) ImGui.beginDisabled();
        if (ImGui.button(FontAwesomeIcons.Trash + " Remove")) {
            int activeIndex = scene.getActiveLayerIndex();
            if (activeIndex >= 0) scene.removeLayer(activeIndex);
        }
        if (!canRemove) ImGui.endDisabled();

        ImGui.spacing();
        ImGui.text("Visibility Mode:");

        LayerVisibilityMode current = scene.getVisibilityMode();
        int mode = current.ordinal();

        if (ImGui.radioButton("All", mode == LayerVisibilityMode.ALL.ordinal())) {
            mode = LayerVisibilityMode.ALL.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("All layers visible");
        ImGui.sameLine();

        if (ImGui.radioButton("Selected", mode == LayerVisibilityMode.SELECTED_ONLY.ordinal())) {
            mode = LayerVisibilityMode.SELECTED_ONLY.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Selected layer only");
        ImGui.sameLine();

        if (ImGui.radioButton("Dimmed", mode == LayerVisibilityMode.SELECTED_DIMMED.ordinal())) {
            mode = LayerVisibilityMode.SELECTED_DIMMED.ordinal();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Non-selected layers dimmed");

        LayerVisibilityMode newMode = LayerVisibilityMode.values()[mode];
        if (newMode != current) scene.setVisibilityMode(newMode);

        if (scene.getVisibilityMode() == LayerVisibilityMode.SELECTED_DIMMED) {
            ImGui.setNextItemWidth(100);
            floatBuffer[0] = scene.getDimmedOpacity();
            if (ImGui.sliderFloat("Opacity", floatBuffer, 0.1f, 1f)) {
                scene.setDimmedOpacity(floatBuffer[0]);
            }
        }
    }

    private void renderLayerList() {
        List<TilemapLayer> layers = scene.getLayers();
        if (layers.isEmpty()) {
            ImGui.textDisabled("No layers. Click 'Add Layer' to create one.");
            return;
        }

        List<LayerEntry> sortedLayers = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            sortedLayers.add(new LayerEntry(i, layers.get(i)));
        }
        sortedLayers.sort(Comparator.comparingInt((LayerEntry e) -> e.layer.getZIndex()).reversed());

        boolean entitySeparatorRendered = false;
        int activeIndex = scene.getActiveLayerIndex();

        for (LayerEntry entry : sortedLayers) {
            if (!entitySeparatorRendered && entry.layer.getZIndex() <= ENTITY_Z_LEVEL) {
                renderEntitySeparator();
                entitySeparatorRendered = true;
            }
            renderLayerItem(entry.originalIndex, entry.layer, activeIndex);
        }

        if (!entitySeparatorRendered) renderEntitySeparator();
    }

    private void renderEntitySeparator() {
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.8f, 0.6f, 1.0f);
        String text = FontAwesomeIcons.User + " -- Entities (z: 0) --";
        float indent = (ImGui.getContentRegionAvailX() - ImGui.calcTextSize(text).x) / 2;
        if (indent > 0) ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        ImGui.text(text);
        ImGui.popStyleColor();
        if (ImGui.isItemHovered()) ImGui.setTooltip("Entities render at Z-index 0");
        ImGui.spacing();
    }

    private void renderLayerItem(int index, TilemapLayer layer, int activeIndex) {
        boolean isActive = index == activeIndex;
        boolean isRenaming = index == renamingLayerIndex;
        int zIndex = layer.getZIndex();
        boolean atEntityLevel = zIndex == ENTITY_Z_LEVEL;

        ImGui.pushID(index);

        boolean visible = layer.isVisible();
        if (ImGui.smallButton((visible ? FontAwesomeIcons.Eye : FontAwesomeIcons.EyeSlash) + "##vis")) {
            layer.setVisible(!visible);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(visible ? "Hide" : "Show");

        ImGui.sameLine();

        boolean locked = layer.isLocked();
        if (ImGui.smallButton((locked ? FontAwesomeIcons.Lock : FontAwesomeIcons.LockOpen) + "##lock")) {
            layer.setLocked(!locked);
            scene.markDirty();
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(locked ? "Unlock" : "Lock");

        ImGui.sameLine();

        String zDisplay = atEntityLevel ? "(z: 0 " + FontAwesomeIcons.ExclamationCircle + ")" : "(z: " + zIndex + ")";

        if (isRenaming) {
            ImGui.setNextItemWidth(100);
            ImGui.setKeyboardFocusHere();
            if (ImGui.inputText("##rename", layerRenameBuffer, ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll)) {
                String newName = layerRenameBuffer.get().trim();
                if (!newName.isEmpty()) scene.renameLayer(index, newName);
                renamingLayerIndex = -1;
            }
            if (ImGui.isKeyPressed(ImGuiKey.Escape) || (!ImGui.isItemActive() && ImGui.isMouseClicked(0))) {
                renamingLayerIndex = -1;
            }
            ImGui.sameLine();
            ImGui.textDisabled(zDisplay);
        } else {
            if (atEntityLevel) ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.8f, 0.2f, 1.0f);

            float availWidth = ImGui.getContentRegionAvailX() - 55;
            if (ImGui.selectable(layer.getName() + " " + zDisplay, isActive, ImGuiSelectableFlags.AllowDoubleClick | ImGuiSelectableFlags.AllowItemOverlap, availWidth, 0f)) {
                scene.setActiveLayer(index);
                if (ImGui.isMouseDoubleClicked(0)) {
                    renamingLayerIndex = index;
                    layerRenameBuffer.set(layer.getName());
                }
            }

            if (atEntityLevel) ImGui.popStyleColor();
            if (atEntityLevel && ImGui.isItemHovered()) ImGui.setTooltip("Same Z-index as entities!");
        }

        if (ImGui.beginPopupContextItem("layer_context_" + index)) {
            if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                renamingLayerIndex = index;
                layerRenameBuffer.set(layer.getName());
            }
            ImGui.separator();
            if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) scene.removeLayer(index);
            ImGui.endPopup();
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 45);

        if (ImGui.smallButton(FontAwesomeIcons.ChevronUp + "##up")) {
            layer.setZIndex(zIndex + 1);
            scene.markDirty();
        }
        ImGui.sameLine();
        if (ImGui.smallButton(FontAwesomeIcons.ChevronDown + "##down")) {
            layer.setZIndex(zIndex - 1);
            scene.markDirty();
        }

        ImGui.popID();
    }

    private void renderActiveLayerDetails(TilemapLayer layer) {
        ImGui.text(FontAwesomeIcons.Edit + " Active Layer");

        stringBuffer.set(layer.getName());
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputText("##layerName", stringBuffer)) {
            String newName = stringBuffer.get().trim();
            if (!newName.isEmpty()) {
                layer.setName(newName);
                if (layer.getGameObject() != null) layer.getGameObject().setName(newName);
                scene.markDirty();
            }
        }

        intBuffer.set(layer.getZIndex());
        ImGui.setNextItemWidth(80);
        if (ImGui.inputInt("Z-Index", intBuffer)) {
            layer.setZIndex(intBuffer.get());
            scene.markDirty();
        }
        ImGui.sameLine();
        ImGui.textDisabled("(higher = front)");
    }

    private record LayerEntry(int originalIndex, TilemapLayer layer) {}

    // ========================================================================
    // COLLISION MAP INSPECTOR
    // ========================================================================

    private void renderCollisionMapInspector() {
        ImGui.text(FontAwesomeIcons.BorderAll + " Collision Map");
        ImGui.separator();

        boolean collisionVisible = scene.isCollisionVisible();
        if (ImGui.checkbox("Show Collision Overlay", collisionVisible)) {
            scene.setCollisionVisible(!collisionVisible);
        }

        ImGui.separator();
        ImGui.text("Tools");
        ImGui.bulletText("Brush - Paint collision");
        ImGui.bulletText("Eraser - Remove collision");
        ImGui.bulletText("Fill - Fill area");
        ImGui.bulletText("Rectangle - Draw rectangle");

        ImGui.separator();
        ImGui.textDisabled("Collision data stored in scene file");
    }

    // ========================================================================
    // SINGLE ENTITY INSPECTOR
    // ========================================================================

    private void renderEntityInspector(EditorEntity entity) {
        String icon = entity.isScratchEntity() ? FontAwesomeIcons.Cube
                : entity.isPrefabValid() ? FontAwesomeIcons.Cubes
                : FontAwesomeIcons.ExclamationTriangle;

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
        renderPositionEditor(entity);

        // Hierarchy info
        if (entity.getParent() != null) {
            ImGui.textDisabled("Parent: " + entity.getParent().getName());
        }
        if (entity.hasChildren()) {
            ImGui.textDisabled("Children: " + entity.getChildren().size());
        }

        ImGui.separator();

        if (entity.isPrefabInstance()) {
            renderPrefabInfo(entity);
            ImGui.separator();
        } else {
            ImGui.textDisabled("Scratch Entity");
        }

        renderComponentList(entity);
        componentBrowserPopup.render();
        savePrefabPopup.render();
    }

    private void renderPositionEditor(EditorEntity entity) {
        Vector3f pos = entity.getPosition();
        floatBuffer[0] = pos.x;
        floatBuffer[1] = pos.y;

        if (ImGui.isItemActive() && draggingEntity != entity) {
            draggingEntity = entity;
            dragStartPosition = new Vector3f(pos);
        }

        if (ImGui.dragFloat2("Position", floatBuffer, 0.1f)) {
            entity.setPosition(floatBuffer[0], floatBuffer[1]);
            scene.markDirty();
        }

        if (draggingEntity == entity && !ImGui.isItemActive()) {
            Vector3f newPos = entity.getPosition();
            if (!newPos.equals(dragStartPosition)) {
                UndoManager.getInstance().execute(new NoOpWrapperCommand(null, entity, dragStartPosition));
            }
            draggingEntity = null;
            dragStartPosition = null;
        }

        ImGui.sameLine();
        if (ImGui.smallButton("Snap")) {
            Vector3f oldPos = new Vector3f(entity.getPosition());
            entity.setPosition(Math.round(pos.x * 2) / 2f, Math.round(pos.y));
            Vector3f newPos = entity.getPosition();
            if (!newPos.equals(oldPos)) {
                UndoManager.getInstance().execute(new MoveEntityCommand(entity, oldPos, newPos) {
                    @Override public void execute() {}
                });
            }
            scene.markDirty();
        }
    }

    private void renderPrefabInfo(EditorEntity entity) {
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

    private void renderComponentList(EditorEntity entity) {
        List<ComponentData> components = entity.getComponents();
        boolean isPrefab = entity.isPrefabInstance();

        if (components.isEmpty()) {
            ImGui.spacing();
            ImGui.textDisabled("No components");
            ImGui.spacing();
        } else {
            ComponentData toRemove = null;

            for (int i = 0; i < components.size(); i++) {
                ComponentData comp = components.get(i);
                ImGui.pushID(i);

                String label = comp.getDisplayName();
                if (isPrefab && !entity.getOverriddenFields(comp.getType()).isEmpty()) label += " *";

                boolean open = ImGui.collapsingHeader(label, ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowOverlap);

                if (!isPrefab) {
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.5f, 0.2f, 0.2f, 1f);
                    if (ImGui.smallButton(FontAwesomeIcons.Times + "##remove")) toRemove = comp;
                    ImGui.popStyleColor();
                    if (ImGui.isItemHovered()) ImGui.setTooltip("Remove component");
                }

                if (open) {
                    ImGui.indent();
                    if (isPrefab) {
                        renderComponentFieldsWithOverrides(entity, comp);
                    } else {
                        if (ReflectionFieldEditor.drawComponent(comp, entity)) scene.markDirty();
                    }
                    ImGui.unindent();
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
                componentBrowserPopup.open(componentData -> {
                    UndoManager.getInstance().execute(new AddComponentCommand(entity, componentData));
                    scene.markDirty();
                });
            }
        }
    }

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

            ImGui.pushID(fieldName);
            if (isOverridden) ImGui.pushStyleColor(ImGuiCol.Text, 0.4f, 0.8f, 1.0f, 1.0f);

            boolean changed = ReflectionFieldEditor.drawField(comp, fieldMeta);

            if (isOverridden) ImGui.popStyleColor();

            if (isOverridden) {
                ImGui.sameLine();
                if (ImGui.smallButton(FontAwesomeIcons.Undo + "##reset")) {
                    entity.resetFieldToDefault(componentType, fieldName);
                    comp.getFields().put(fieldName, entity.getFieldDefault(componentType, fieldName));
                    scene.markDirty();
                }
                if (ImGui.isItemHovered()) ImGui.setTooltip("Reset to default: " + entity.getFieldDefault(componentType, fieldName));
            }

            if (changed) {
                entity.setFieldValue(componentType, fieldName, comp.getFields().get(fieldName));
                scene.markDirty();
            }

            ImGui.popID();
        }

        ReflectionFieldEditor.drawComponentReferences(meta.references(), entity);
    }

    private void renderDeleteConfirmationPopup() {
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

    private static class NoOpWrapperCommand extends MoveEntityCommand {
        public NoOpWrapperCommand(MoveEntityCommand original, EditorEntity entity, Vector3f oldPos) {
            super(entity, oldPos, entity.getPosition());
        }
        @Override public void execute() {}
    }
}