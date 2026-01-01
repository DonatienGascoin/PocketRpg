package com.pocket.rpg.editor.panels;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.EditorModeManager;
import com.pocket.rpg.editor.assets.HierarchyDropTarget;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.tools.EditorTool;
import com.pocket.rpg.editor.tools.ToolManager;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntitiesCommand;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.undo.commands.ReparentEntityCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.*;
import imgui.type.ImString;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.*;

/**
 * Unified hierarchy panel with drag-drop reordering and multi-selection.
 */
public class HierarchyPanel {

    private static final String DRAG_DROP_TYPE = "ENTITY_DND";
    private static final float INDENT_WIDTH = 2f;
    private static final float DROP_ZONE_HEIGHT = 4f;

    @Setter
    private EditorScene scene;

    @Setter
    private EditorModeManager modeManager;

    @Setter
    private ToolManager toolManager;

    @Setter
    private EditorTool selectionTool;

    @Setter
    private EditorTool brushTool;

    @Setter
    private UIEntityFactory uiFactory;

    // Selection state
    @Getter
    private boolean cameraSelected = false;

    @Getter
    private boolean tilemapLayersSelected = false;

    @Getter
    private boolean collisionMapSelected = false;

    // Rename state
    private Object renamingItem = null;
    private final ImString renameBuffer = new ImString(64);

    // Multi-selection
    private EditorEntity lastClickedEntity = null;

    // Drag-drop state
    private EditorEntity draggedEntity = null;
    private DropTarget currentDropTarget = null;

    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    private enum DropPosition {
        BEFORE, ON, AFTER
    }

    private record DropTarget(EditorEntity entity, DropPosition position) {
    }

    public void init() {
        if (modeManager != null) {
            modeManager.onModeChanged(this::onModeChanged);
        }
    }

    private void onModeChanged(EditorModeManager.Mode mode) {
        switch (mode) {
            case TILEMAP -> {
                cameraSelected = false;
                tilemapLayersSelected = true;
                collisionMapSelected = false;
                if (scene != null) scene.clearSelection();
            }
            case COLLISION -> {
                cameraSelected = false;
                tilemapLayersSelected = false;
                collisionMapSelected = true;
                if (scene != null) scene.clearSelection();
            }
            case ENTITY -> {
                tilemapLayersSelected = false;
                collisionMapSelected = false;
            }
        }
    }

    public void render() {
        if (ImGui.begin("Hierarchy")) {
            if (scene == null) {
                ImGui.textDisabled("No scene loaded");
                ImGui.end();
                return;
            }

            ImGui.text(IconUtils.getSceneIcon() + " " + scene.getName());
            ImGui.sameLine(ImGui.getContentRegionMaxX() - 15);
            if (ImGui.smallButton(FontAwesomeIcons.Plus)) {
                ImGui.openPopup("CreateEntity_Popup");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Add...");
            }

            ImGui.separator();

            renderCameraItem();
            renderTilemapLayersItem();
            renderCollisionMapItem();

            ImGui.separator();

            renderEntitiesSection();
            renderEntityCreationMenu();
        }
        ImGui.end();

        // Reset drop target each frame
        currentDropTarget = null;
    }

    // ========================================================================
    // CAMERA
    // ========================================================================

    private void renderCameraItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (cameraSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##camera", flags, IconUtils.getCameraIcon() + " Scene Camera");

        if (ImGui.isItemClicked()) {
            selectCamera();
        }
    }

    public void selectCamera() {
        cameraSelected = true;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        if (scene != null) {
            scene.clearSelection();
            scene.setActiveLayer(-1);
        }
    }

    // ========================================================================
    // TILEMAP LAYERS
    // ========================================================================

    private void renderTilemapLayersItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (tilemapLayersSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        int layerCount = scene != null ? scene.getLayerCount() : 0;
        ImGui.treeNodeEx("##tilemapLayers", flags, IconUtils.getLayersIcon() + " Tilemap Layers (" + layerCount + ")");

        if (ImGui.isItemClicked()) {
            selectTilemapLayers();
        }
    }

    public void selectTilemapLayers() {
        cameraSelected = false;
        if (modeManager != null) {
            modeManager.switchToTilemap();
        }
        if (toolManager != null && brushTool != null) {
            toolManager.setActiveTool(brushTool);
        }
    }

    // ========================================================================
    // COLLISION MAP
    // ========================================================================

    private void renderCollisionMapItem() {
        int flags = ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen | ImGuiTreeNodeFlags.SpanAvailWidth;
        if (collisionMapSelected) {
            flags |= ImGuiTreeNodeFlags.Selected;
        }

        ImGui.treeNodeEx("##collisionMap", flags, IconUtils.getCollisionsIcon() + " Collision Map");

        if (ImGui.isItemClicked()) {
            selectCollisionMap();
        }
    }

    public void selectCollisionMap() {
        cameraSelected = false;
        if (modeManager != null) {
            modeManager.switchToCollision();
        }
    }

    // ========================================================================
    // ENTITIES SECTION
    // ========================================================================

    private void renderEntitiesSection() {
        List<EditorEntity> rootEntities = scene.getRootEntities();

        // Sort by order
        rootEntities.sort(Comparator.comparingInt(EditorEntity::getOrder));

        if (rootEntities.isEmpty()) {
            ImGui.textDisabled("No entities");
            // Drop zone for empty list
            renderDropZone(null, 0, null);
        } else {
            for (int i = 0; i < rootEntities.size(); i++) {
                EditorEntity entity = rootEntities.get(i);

                // Drop zone before this entity
                renderDropZone(null, i, entity);

                renderEntityTree(entity);
            }

            // Drop zone after last root entity
            renderDropZone(null, rootEntities.size(), null);
        }

        // Multi-selection context menu
        renderMultiSelectionContextMenu();


        HierarchyDropTarget.handleEmptyAreaDrop(scene);
    }

    private void renderEntityCreationMenu() {
        if (ImGui.beginPopup("CreateEntity_Popup")) {

            if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Entity")) {
                createEmptyEntity();
            }

            if (ImGui.beginMenu(FontAwesomeIcons.WindowMaximize + " Create UI")) {
                if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                    createUIElement("Canvas");
                }
                ImGui.separator();
                if (ImGui.menuItem(IconUtils.getUIPanelIcon() + " Panel")) {
                    createUIElement("Panel");
                }
                if (ImGui.menuItem(IconUtils.getUIImageIcon() + " Image")) {
                    createUIElement("Image");
                }
                if (ImGui.menuItem(IconUtils.getUIButtonIcon() + " Button")) {
                    createUIElement("Button");
                }
                if (ImGui.menuItem(IconUtils.getUITextIcon() + " Text")) {
                    createUIElement("Text");
                }
                ImGui.endMenu();
            }

            ImGui.endPopup();
        }
    }

    private void renderEntityTree(EditorEntity entity) {
        boolean isSelected = scene.isSelected(entity);
        boolean isRenaming = entity == renamingItem;
        boolean hasChildren = entity.hasChildren();

        ImGui.pushID(entity.getId());

        // Tree node flags
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        if (isSelected) flags |= ImGuiTreeNodeFlags.Selected;
        if (!hasChildren) flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        else flags |= ImGuiTreeNodeFlags.DefaultOpen;

        // Highlight if drop target
        boolean isDropTarget = currentDropTarget != null &&
                currentDropTarget.entity == entity &&
                currentDropTarget.position == DropPosition.ON;
        if (isDropTarget) {
            ImGui.pushStyleColor(ImGuiCol.Header, 0.3f, 0.5f, 0.8f, 0.5f);
        }

        // Render node: either rename field or label
        if (isRenaming) {
            renderRenameField(entity);
        } else {
            String label = IconUtils.getIconForEntity(entity) + " " + entity.getName();
            boolean nodeOpen = ImGui.treeNodeEx("##entity", flags, label);

            // Selection & interaction
            handleEntityInteraction(entity, 0);

            // Drag-drop
            handleDragDrop(entity);

            // Render children recursively
            if (hasChildren && nodeOpen) {
                List<EditorEntity> children = new ArrayList<>(entity.getChildren());
                children.sort(Comparator.comparingInt(EditorEntity::getOrder));

                for (int i = 0; i < children.size(); i++) {
                    EditorEntity child = children.get(i);

                    // Drop zone before child
                    renderDropZone(entity, i, child);

                    // Recursive call
                    renderEntityTree(child);
                }

                // Drop zone after last child
                renderDropZone(entity, children.size(), null);

                ImGui.treePop();
            }

            // Context menu and tooltip
            renderEntityContextMenu(entity);
            renderEntityTooltip(entity);
        }

        if (isDropTarget) {
            ImGui.popStyleColor();
        }

        ImGui.popID();

        // External drop handling
        HierarchyDropTarget.handleEntityDrop(scene, entity);
    }


    private void renderRenameField(EditorEntity entity) {
        ImGui.setNextItemWidth(150);
        ImGui.setKeyboardFocusHere();

        int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll;
        if (ImGui.inputText("##rename", renameBuffer, inputFlags)) {
            String newName = renameBuffer.get().trim();
            if (!newName.isEmpty()) {
                entity.setName(newName);
                scene.markDirty();
            }
            renamingItem = null;
        }

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renamingItem = null;
        }
    }

    private void handleEntityInteraction(EditorEntity entity, int depth) {
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            boolean ctrlHeld = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
            boolean shiftHeld = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

            if (ctrlHeld) {
                // Toggle selection
                scene.toggleSelection(entity);
            } else if (shiftHeld && lastClickedEntity != null) {
                // Range selection
                selectRange(lastClickedEntity, entity);
            } else {
                // Single selection
                selectEntity(entity);
            }

            lastClickedEntity = entity;
        }

        // Double-click to rename
        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            renamingItem = entity;
            renameBuffer.set(entity.getName());
        }
    }

    private void handleDragDrop(EditorEntity entity) {
        // Drag source
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            draggedEntity = entity;

            // If dragging a non-selected entity, select only it
            if (!scene.isSelected(entity)) {
                scene.setSelection(Set.of(entity));
            }

            ImGui.setDragDropPayload(DRAG_DROP_TYPE, entity.getId().getBytes());

            int selectedCount = scene.getSelectedEntities().size();
            if (selectedCount > 1) {
                ImGui.text(IconUtils.getMultipleEntitiesIcon() + " " + selectedCount + " entities");
            } else {
                ImGui.text(IconUtils.getScratchEntityIcon() + " " + entity.getName());
            }

            ImGui.endDragDropSource();
        }

        // Drop target (ON entity = make child at end)
        if (ImGui.beginDragDropTarget()) {
            // Show visual feedback that we're over an entity
            currentDropTarget = new DropTarget(entity, DropPosition.ON);

            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE);
            if (payload != null) {
                Set<EditorEntity> selected = scene.getSelectedEntities();
                int insertIdx = entity.getChildren().size();
                for (EditorEntity dragged : selected) {
                    if (dragged != entity && !dragged.isAncestorOf(entity)) {
                        UndoManager.getInstance().execute(
                                new ReparentEntityCommand(scene, dragged, entity, insertIdx)
                        );
                        insertIdx++;
                    }
                }
            }

            ImGui.endDragDropTarget();
        }
    }

    /**
     * Renders an invisible drop zone for reordering.
     *
     * @param targetParent Parent to insert under (null for root)
     * @param insertIndex  Index to insert at among siblings
     * @param nextEntity   Entity that will be after this position (for ID uniqueness)
     */
    private void renderDropZone(EditorEntity targetParent, int insertIndex, EditorEntity nextEntity) {
        String zoneId = "##dropzone_" +
                (targetParent != null ? targetParent.getId() : "root") + "_" +
                insertIndex + "_" +
                (nextEntity != null ? nextEntity.getId() : "end");

        float width = ImGui.getContentRegionAvailX();
        if (width < 1.0f) width = 1.0f;  // Prevent zero-size assertion

        ImGui.invisibleButton(zoneId, width, DROP_ZONE_HEIGHT);

        if (ImGui.beginDragDropTarget()) {
            // Visual indicator - draw line
            ImVec2 min = new ImVec2();
            ImVec2 max = new ImVec2();
            ImGui.getItemRectMin(min);
            ImGui.getItemRectMax(max);
            ImGui.getWindowDrawList().addLine(
                    min.x, min.y + DROP_ZONE_HEIGHT / 2,
                    max.x, min.y + DROP_ZONE_HEIGHT / 2,
                    ImGui.colorConvertFloat4ToU32(0.4f, 0.7f, 1.0f, 1.0f), 2.0f);

            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE);
            if (payload != null) {
                Set<EditorEntity> selected = scene.getSelectedEntities();
                int offset = 0;
                for (EditorEntity dragged : selected) {
                    // Skip if trying to drop onto itself or its descendants
                    if (dragged == targetParent || (targetParent != null && dragged.isAncestorOf(targetParent))) {
                        continue;
                    }

                    // Adjust insert index if moving within same parent and from earlier position
                    int adjustedIndex = insertIndex + offset;
                    if (dragged.getParent() == targetParent && dragged.getOrder() < insertIndex) {
                        adjustedIndex = Math.max(0, adjustedIndex - 1);
                    }

                    UndoManager.getInstance().execute(
                            new ReparentEntityCommand(scene, dragged, targetParent, adjustedIndex)
                    );
                    offset++;
                }
            }

            ImGui.endDragDropTarget();
        }
    }

    private int getNextChildOrder(EditorEntity parent) {
        if (parent == null) {
            return scene.getRootEntities().size();
        }
        return parent.getChildren().size();
    }

    private void renderEntityContextMenu(EditorEntity entity) {
        if (ImGui.beginPopupContextItem("entity_ctx_" + entity.getId())) {
            Set<EditorEntity> selected = scene.getSelectedEntities();
            boolean multiSelect = selected.size() > 1;

            if (multiSelect) {
                ImGui.text(selected.size() + " entities selected");
                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete All")) {
                    UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
                }

                if (ImGui.menuItem(FontAwesomeIcons.LevelUpAlt + " Unparent All")) {
                    for (EditorEntity e : selected) {
                        if (e.getParent() != null) {
                            UndoManager.getInstance().execute(
                                    new ReparentEntityCommand(scene, e, null, getNextChildOrder(null))
                            );
                        }
                    }
                }
            } else {
                if (ImGui.menuItem(FontAwesomeIcons.Edit + " Rename")) {
                    renamingItem = entity;
                    renameBuffer.set(entity.getName());
                }

                if (ImGui.menuItem(FontAwesomeIcons.Copy + " Duplicate")) {
                    duplicateEntity(entity);
                }

                if (entity.getParent() != null) {
                    if (ImGui.menuItem(FontAwesomeIcons.LevelUpAlt + " Unparent")) {
                        UndoManager.getInstance().execute(
                                new ReparentEntityCommand(scene, entity, null, getNextChildOrder(null))
                        );
                    }
                }

                if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
                    if (ImGui.menuItem(FontAwesomeIcons.Save + " Save as Prefab...")) {
                        savePrefabPopup.open(entity, savedPrefab -> {
                            System.out.println("Saved prefab: " + savedPrefab.getId());
                        });
                    }
                }

                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete")) {
                    scene.removeEntity(entity);
                }
            }

            ImGui.endPopup();
        }

        savePrefabPopup.render();
    }

    private void renderEntityTooltip(EditorEntity entity) {
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            if (entity.isScratchEntity()) {
                ImGui.text("Scratch Entity");
                int compCount = entity.getComponents().size();
                ImGui.textDisabled(compCount + " component" + (compCount != 1 ? "s" : ""));
            } else {
                Prefab prefab = entity.getPrefab();
                if (prefab != null) {
                    ImGui.text(prefab.getDisplayName());
                } else {
                    ImGui.textColored(1f, 0.5f, 0.2f, 1f, "Missing prefab: " + entity.getPrefabId());
                }
            }
            Vector3f pos = entity.getPosition();
            ImGui.textDisabled(String.format("Position: (%.1f, %.1f)", pos.x, pos.y));

            if (entity.hasChildren()) {
                ImGui.textDisabled(entity.getChildren().size() + " children");
            }

            ImGui.endTooltip();
        }
    }

    private void renderMultiSelectionContextMenu() {
        // Global context menu when right-clicking empty space
        if (ImGui.beginPopupContextWindow("hierarchy_ctx", ImGuiMouseButton.Right)) {
            Set<EditorEntity> selected = scene.getSelectedEntities();

            if (!selected.isEmpty()) {
                ImGui.text(selected.size() + " selected");
                ImGui.separator();

                if (ImGui.menuItem(FontAwesomeIcons.Trash + " Delete Selected")) {
                    UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
                }

                if (ImGui.menuItem(FontAwesomeIcons.TimesCircle + " Clear Selection")) {
                    scene.clearSelection();
                }
            } else {
                if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Entity")) {
                    createEmptyEntity();
                }

                if (ImGui.beginMenu(FontAwesomeIcons.WindowMaximize + " Create UI")) {
                    if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                        createUIElement("Canvas");
                    }
                    ImGui.separator();
                    if (ImGui.menuItem(IconUtils.getUIPanelIcon() + " Panel")) {
                        createUIElement("Panel");
                    }
                    if (ImGui.menuItem(IconUtils.getUIImageIcon() + " Image")) {
                        createUIElement("Image");
                    }
                    if (ImGui.menuItem(IconUtils.getUIButtonIcon() + " Button")) {
                        createUIElement("Button");
                    }
                    if (ImGui.menuItem(IconUtils.getUITextIcon() + " Text")) {
                        createUIElement("Text");
                    }
                    ImGui.endMenu();
                }
            }

            ImGui.endPopup();
        }
    }

    private void selectRange(EditorEntity from, EditorEntity to) {
        // Flatten hierarchy for range selection
        List<EditorEntity> flat = new ArrayList<>();
        flattenEntities(scene.getRootEntities(), flat);

        int fromIdx = flat.indexOf(from);
        int toIdx = flat.indexOf(to);

        if (fromIdx == -1 || toIdx == -1) return;

        int start = Math.min(fromIdx, toIdx);
        int end = Math.max(fromIdx, toIdx);

        Set<EditorEntity> rangeSet = new HashSet<>();
        for (int i = start; i <= end; i++) {
            rangeSet.add(flat.get(i));
        }

        scene.setSelection(rangeSet);
        switchToEntityMode();
    }

    private void flattenEntities(List<EditorEntity> entities, List<EditorEntity> result) {
        for (EditorEntity entity : entities) {
            result.add(entity);
            if (entity.hasChildren()) {
                List<EditorEntity> children = new ArrayList<>(entity.getChildren());
                children.sort(Comparator.comparingInt(EditorEntity::getOrder));
                flattenEntities(children, result);
            }
        }
    }

    private void createEmptyEntity() {
        Vector3f position = new Vector3f(0, 0, 0);
        int count = scene.getEntities().size();
        String name = "Entity_" + (count + 1);

        EditorEntity entity = new EditorEntity(name, position, false);
        entity.setOrder(getNextChildOrder(null));
        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
        selectEntity(entity);
        scene.markDirty();
    }

    private void selectEntity(EditorEntity entity) {
        cameraSelected = false;
        tilemapLayersSelected = false;
        collisionMapSelected = false;
        scene.setSelection(Set.of(entity));
        switchToEntityMode();
    }

    private void switchToEntityMode() {
        if (modeManager != null) {
            modeManager.switchToEntity();
        }
        if (toolManager != null && selectionTool != null) {
            toolManager.setActiveTool(selectionTool);
        }
    }

    private void duplicateEntity(EditorEntity original) {
        Vector3f newPos = new Vector3f(original.getPosition()).add(1, 0, 0);
        EditorEntity copy;

        if (original.isScratchEntity()) {
            copy = new EditorEntity(original.getName() + "_copy", newPos, false);
            for (ComponentData comp : original.getComponents()) {
                ComponentData compCopy = new ComponentData(comp.getType());
                compCopy.getFields().putAll(comp.getFields());
                copy.addComponent(compCopy);
            }
        } else {
            copy = new EditorEntity(original.getPrefabId(), newPos);
            copy.setName(original.getName() + "_copy");
            for (ComponentData comp : original.getComponents()) {
                String componentType = comp.getType();
                for (String fieldName : original.getOverriddenFields(componentType)) {
                    Object value = original.getFieldValue(componentType, fieldName);
                    copy.setFieldValue(componentType, fieldName, value);
                }
            }
        }

        // Same parent, next order
        copy.setParent(original.getParent());
        copy.setOrder(original.getOrder() + 1);

        scene.addEntity(copy);
        selectEntity(copy);
        scene.markDirty();
    }

    // ========================================================================
    // UI ENTITY CREATION
    // ========================================================================

    private void createUIElement(String uiType) {
        if (scene == null || uiFactory == null) return;
        EditorEntity entity = uiFactory.create(uiType, null);
        if (entity == null) return;

        EditorEntity parent = null;
        EditorEntity selected = scene.getSelectedEntity();

        if (selected != null) {
            if (selected.hasComponent(UITransform.class) || selected.hasComponent(UICanvas.class)) {
                // Selected is UI element: create as direct child
                parent = selected;
            } else {
                // Selected is non-UI: create canvas as child, then element under it
                if (uiType.equals("Canvas")) {
                    parent = selected;
                } else {
                    EditorEntity canvas = findOrCreateCanvasUnder(selected);
                    parent = canvas;
                }
            }
        } else if (!uiType.equals("Canvas")) {
            // No selection, non-canvas: parent to root canvas
            parent = findOrCreateCanvas();
        }
        // No selection + Canvas: stays at root (parent = null)
        List<EditorEntity> entitiesToAdd = new ArrayList<>();

        if (parent != null) {
            entity.setParent(parent);
            entity.setOrder(parent.getChildren().size());
            entitiesToAdd.add(parent);
        } else {
            entity.setOrder(getNextChildOrder(null));
        }

        entitiesToAdd.add(entity);
        UndoManager.getInstance().execute(new AddEntitiesCommand(scene, entitiesToAdd));
        selectEntity(entity);
        scene.markDirty();
    }

    /**
     * Find or create a canvas under a specified entity: Warning, will not add it to the scene !
     *
     * @param parent Parent entity
     * @return create EditorEntity with UICanvas component
     */
    private EditorEntity findOrCreateCanvasUnder(EditorEntity parent) {
        // Look for existing canvas child
        for (EditorEntity child : parent.getChildren()) {
            if (child.hasComponent("UICanvas")) {
                return child;
            }
        }

        // Create new canvas as child
        EditorEntity canvas = uiFactory.create("Canvas", "UI Canvas");
        canvas.setParent(parent);
        canvas.setOrder(parent.getChildren().size());
        return canvas;
    }

    /**
     * Find or create a canvas: Warning, will not add it to the scene !
     *
     * @return create EditorEntity with UICanvas component
     */
    private EditorEntity findOrCreateCanvas() {
        // Look for existing canvas
        for (EditorEntity entity : scene.getEntities()) {
            if (entity.hasComponent("UICanvas")) {
                return entity;
            }
        }

        // Create new canvas
        EditorEntity canvas = uiFactory.create("Canvas", "UI Canvas");
        canvas.setOrder(getNextChildOrder(null));
        return canvas;
    }
}