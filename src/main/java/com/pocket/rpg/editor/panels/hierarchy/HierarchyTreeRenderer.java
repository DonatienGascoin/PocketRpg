package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.PlayModeSelectionManager;
import com.pocket.rpg.editor.assets.HierarchyDropTarget;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.SavePrefabPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RenameEntityCommand;
import com.pocket.rpg.editor.undo.commands.ReparentEntityCommand;
import com.pocket.rpg.editor.undo.commands.ToggleEntityEnabledCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.prefab.Prefab;
import com.pocket.rpg.prefab.PrefabHierarchyHelper;
import com.pocket.rpg.editor.core.EditorColors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders the entity tree with rename, context menus, and tooltips.
 */
public class HierarchyTreeRenderer {

    @Setter
    private EditorScene scene;

    @Setter
    private HierarchySelectionHandler selectionHandler;

    @Setter
    private HierarchyDragDropHandler dragDropHandler;

    @Setter
    private EntityCreationService creationService;

    private Object renamingItem = null;
    private boolean renameJustStarted = false;
    private final ImString renameBuffer = new ImString(64);
    private String nameBeforeRename = null;  // For undo support
    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    // Scroll-to-selection support
    private Set<String> entitiesToForceOpen = new HashSet<>();
    private String scrollToEntityId = null;

    // Cache entity rects from previous frame for right-click pre-selection
    private final java.util.Map<String, float[]> entityRectCache = new java.util.HashMap<>();

    // Tracks which entity tree nodes are currently expanded (updated each frame during render)
    private final Set<String> expandedEntityIds = new HashSet<>();

    // Programmatic expand/collapse: entity ID → desired open state (consumed during render)
    private final java.util.Map<String, Boolean> pendingOpenState = new java.util.HashMap<>();

    public Set<String> getExpandedEntityIds() {
        return expandedEntityIds;
    }


    /**
     * Programmatically expand or collapse a tree node on the next frame.
     */
    public void setEntityOpen(String entityId, boolean open) {
        pendingOpenState.put(entityId, open);
    }

    public boolean isRenaming() {
        return renamingItem != null;
    }

    /**
     * Starts inline rename for the given entity.
     * Called by double-click, context menu, and F2 shortcut.
     */
    /**
     * Cancels any active rename without applying changes.
     */
    public void cancelRename() {
        renamingItem = null;
        nameBeforeRename = null;
    }

    public void startRename(EditorGameObject entity) {
        if (entity == null || entity.isPrefabChildNode()) return;
        renamingItem = entity;
        nameBeforeRename = entity.getName();
        renameBuffer.set(nameBeforeRename);
        renameJustStarted = true;
        // Clear any pending narrow-select to prevent re-selection on mouse release
        if (selectionHandler != null) {
            selectionHandler.clearPendingNarrowSelect();
        }
    }

    /**
     * Requests the tree to expand all ancestor nodes and scroll to reveal the given entity.
     * One-shot: clears after the entity is rendered.
     */
    public void requestScrollToEntity(EditorGameObject entity) {
        scrollToEntityId = entity.getId();
        entitiesToForceOpen.clear();
        GameObject current = entity.getParent();
        while (current != null) {
            entitiesToForceOpen.add(current.getId());
            current = current.getParent();
        }
    }

    public void renderEntityTree(EditorGameObject entity, int depth) {
        boolean isRenaming = entity == renamingItem;
        boolean hasChildren = entity.hasChildren();

        ImGui.pushID(entity.getId());

        // Check if this entity will be right-clicked this frame
        // We need to select it BEFORE rendering so the Selected flag is correct
        boolean willBeRightClicked = !isRenaming
                && ImGui.isMouseClicked(ImGuiMouseButton.Right)
                && isMouseOverEntityArea(entity);

        if (willBeRightClicked && !scene.isSelected(entity)) {
            selectionHandler.selectEntity(entity);
        }

        boolean isSelected = scene.isSelected(entity);

        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        if (isSelected) flags |= ImGuiTreeNodeFlags.Selected;
        if (isRenaming) flags |= ImGuiTreeNodeFlags.AllowOverlap;
        if (!hasChildren) flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;

        // Determine open state: programmatic overrides > force-open for scroll > tracked state (default closed)
        if (hasChildren) {
            if (pendingOpenState.containsKey(entity.getId())) {
                ImGui.setNextItemOpen(pendingOpenState.remove(entity.getId()));
            } else if (entitiesToForceOpen.contains(entity.getId())) {
                ImGui.setNextItemOpen(true);
            } else if (expandedEntityIds.contains(entity.getId())) {
                ImGui.setNextItemOpen(true);
            }
        }

        // Determine the label - empty when renaming (we'll draw inline input after)
        boolean hierarchicallyDisabled = !entity.isActiveInHierarchy();
        String label;
        if (isRenaming) {
            label = IconUtils.getIconForEntity(entity);
        } else {
            label = IconUtils.getIconForEntity(entity) + " " + entity.getName();
        }

        // Gray out entities that are disabled (own or via parent)
        if (hierarchicallyDisabled) {
            float[] disabledWithAlpha = EditorColors.withAlpha(EditorColors.DISABLED_TEXT, 0.6f);
            ImGui.pushStyleColor(ImGuiCol.Text, disabledWithAlpha[0], disabledWithAlpha[1], disabledWithAlpha[2], disabledWithAlpha[3]);
        }

        boolean nodeOpen = ImGui.treeNodeEx("##entity_" + entity.getId(), flags, label);

        // Track expanded state for visible-only shift-click range selection
        if (hasChildren) {
            if (nodeOpen) expandedEntityIds.add(entity.getId());
            else expandedEntityIds.remove(entity.getId());
        }

        if (hierarchicallyDisabled) {
            ImGui.popStyleColor();
        }

        // Scroll to this entity if it's the target
        if (entity.getId().equals(scrollToEntityId)) {
            // Only scroll if the item is not already visible in the panel
            float itemMinY = ImGui.getItemRectMinY();
            float itemMaxY = ImGui.getItemRectMaxY();
            float itemHeight = itemMaxY - itemMinY;
            float margin = itemHeight * 2; // ~2 rows of padding from the edge
            float windowMinY = ImGui.getWindowPosY();
            float windowMaxY = windowMinY + ImGui.getWindowHeight();
            if (itemMinY < windowMinY + margin || itemMaxY > windowMaxY - margin) {
                if (itemMinY < windowMinY + margin) {
                    ImGui.setScrollHereY(0.15f); // Item above — show near top with margin
                } else {
                    ImGui.setScrollHereY(0.85f); // Item below — show near bottom with margin
                }
            }
            scrollToEntityId = null;
            entitiesToForceOpen.clear();
        }

        // Render inline rename field on same line as tree node
        if (isRenaming) {
            ImGui.sameLine(0, 0);
            renderRenameField(entity);
        }

        // Cache this entity's rect for next frame's right-click pre-selection
        float nodeMinY = ImGui.getItemRectMinY();
        float nodeMaxY = ImGui.getItemRectMaxY();
        entityRectCache.put(entity.getId(), new float[]{
                ImGui.getItemRectMinX(), nodeMinY,
                ImGui.getItemRectMaxX(), nodeMaxY
        });

        if (!isRenaming) {
            handleEntityInteraction(entity, hasChildren);
            dragDropHandler.handleDragSource(entity);
            // Positional drop target (tree node is last item for beginDragDropTarget)
            boolean dropped = dragDropHandler.handlePositionalDrop(entity, nodeMinY, nodeMaxY,
                    depth, hasChildren && nodeOpen);
            if (dropped && !scene.getSelectedEntities().isEmpty()) {
                requestScrollToEntity(scene.getSelectedEntities().iterator().next());
            }
            // Asset drops on entity (also targets the tree node)
            HierarchyDropTarget.handleEntityDrop(scene, entity);
            renderEntityContextMenu(entity);
        }

        // Always render children if node is open
        if (hasChildren && nodeOpen) {
            List<EditorGameObject> children = entity.getChildren().stream()
                    .map(go -> (EditorGameObject) go)
                    .collect(Collectors.toCollection(ArrayList::new));
            children.sort(Comparator.comparingInt(EditorGameObject::getOrder));

            for (EditorGameObject child : children) {
                renderEntityTree(child, depth + 1);
            }

            ImGui.treePop();
        }

        ImGui.popID();
    }

    private void renderRenameField(EditorGameObject entity) {
        // Use remaining width for the input field
        ImGui.setNextItemWidth(-1);
        if (renameJustStarted) {
            ImGui.setKeyboardFocusHere();
            renameJustStarted = false;
        }

        // Keep horizontal padding inside the field for text spacing, zero vertical to match tree line height
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, ImGui.getStyle().getFramePaddingX(), 0);

        int inputFlags = ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll;
        if (ImGui.inputText("##rename", renameBuffer, inputFlags)) {
            String newName = renameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(nameBeforeRename)) {
                entity.setName(newName);
                scene.markDirty();
                UndoManager.getInstance().push(new RenameEntityCommand(entity, nameBeforeRename, newName));
            }
            renamingItem = null;
            nameBeforeRename = null;
        }
        ImGui.popStyleVar();

        // Cancel on escape
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renamingItem = null;
            nameBeforeRename = null;
        }

        if ((ImGui.isMouseClicked(ImGuiMouseButton.Left) || ImGui.isMouseClicked(ImGuiMouseButton.Middle)
            || ImGui.isMouseClicked(ImGuiMouseButton.Right)) && !ImGui.isItemHovered()) {
            // If clicking outside the input, cancel rename
                renamingItem = null;
                nameBeforeRename = null;
        }
    }

    private void handleEntityInteraction(EditorGameObject entity, boolean hasChildren) {
        // Skip selection and rename when clicking the expand/collapse arrow (parent nodes only)
        boolean clickedOnArrow = hasChildren
                && ImGui.getMousePosX() < ImGui.getItemRectMinX() + ImGui.getTreeNodeToLabelSpacing();

        if (ImGui.isItemClicked(ImGuiMouseButton.Left) && !clickedOnArrow) {
            selectionHandler.handleEntityClick(entity);
        }

        // Note: Right-click selection is handled earlier via pre-selection
        // to ensure the entity appears selected before the context menu opens

        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)
                && !entity.isPrefabChildNode() && !clickedOnArrow) {
            startRename(entity);
        }
    }

    private void renderEntityContextMenu(EditorGameObject entity) {
        if (ImGui.beginPopupContextItem("entity_ctx_" + entity.getId())) {
            Set<EditorGameObject> selected = scene.getSelectedEntities();
            boolean multiSelect = selected.size() > 1;

            if (multiSelect) {
                ImGui.text(selected.size() + " entities selected");
                ImGui.separator();

                // Toggle enabled for all selected entities
                boolean anyEnabled = selected.stream().anyMatch(EditorGameObject::isEnabled);
                String toggleLabel = anyEnabled
                        ? MaterialIcons.VisibilityOff + " Disable All"
                        : MaterialIcons.Visibility + " Enable All";
                if (ImGui.menuItem(toggleLabel)) {
                    boolean newState = !anyEnabled;
                    List<EditorCommand> commands = new ArrayList<>();
                    for (EditorGameObject e : selected) {
                        if (e.isEnabled() != newState) {
                            commands.add(new ToggleEntityEnabledCommand(e, newState));
                        }
                    }
                    if (!commands.isEmpty()) {
                        UndoManager.getInstance().execute(
                                new CompoundCommand(newState ? "Enable All" : "Disable All", commands));
                        scene.markDirty();
                    }
                }

                // Filter out prefab children for destructive operations
                Set<EditorGameObject> nonPrefabChildren = new HashSet<>();
                int prefabChildCount = 0;
                for (EditorGameObject e : selected) {
                    if (e.isPrefabChildNode()) {
                        prefabChildCount++;
                    } else {
                        nonPrefabChildren.add(e);
                    }
                }

                if (!nonPrefabChildren.isEmpty()) {
                    if (ImGui.menuItem(MaterialIcons.Delete + " Delete All")) {
                        UndoManager.getInstance().execute(new BulkDeleteCommand(scene, nonPrefabChildren));
                    }

                    if (ImGui.menuItem(MaterialIcons.CallMade + " Unparent All")) {
                        for (EditorGameObject e : nonPrefabChildren) {
                            if (e.getParent() != null) {
                                UndoManager.getInstance().execute(
                                        new ReparentEntityCommand(scene, e, null, getNextChildOrder(null))
                                );
                            }
                        }
                    }
                }

                if (prefabChildCount > 0) {
                    ImGui.textDisabled(prefabChildCount + " prefab children excluded");
                }
            } else if (entity.isPrefabChildNode()) {
                // Restricted context menu for prefab child nodes
                ImGui.textDisabled(MaterialIcons.AccountTree + " Prefab Child");
                ImGui.separator();

                // Toggle enabled
                boolean ownEnabled = entity.isEnabled();
                String enableLabel = ownEnabled
                        ? MaterialIcons.VisibilityOff + " Disable"
                        : MaterialIcons.Visibility + " Enable";
                if (ImGui.menuItem(enableLabel)) {
                    UndoManager.getInstance().execute(
                            new ToggleEntityEnabledCommand(entity, !ownEnabled));
                    scene.markDirty();
                }

                // Edit Prefab shortcut
                Prefab prefab = entity.getPrefab();
                if (prefab instanceof com.pocket.rpg.prefab.JsonPrefab jsonPrefab) {
                    if (ImGui.menuItem(MaterialIcons.Edit + " Edit Prefab")) {
                        com.pocket.rpg.editor.events.EditorEventBus.get().publish(
                                new com.pocket.rpg.editor.events.RequestPrefabEditEvent(jsonPrefab));
                    }
                }
            } else {
                renderCreateMenuItems();
                ImGui.separator();

                if (ImGui.menuItem(MaterialIcons.Edit + " Rename")) {
                    startRename(entity);
                }

                // Toggle enabled using isEnabled (raw field, not hierarchical)
                boolean ownEnabled = entity.isEnabled();
                String enableLabel = ownEnabled
                        ? MaterialIcons.VisibilityOff + " Disable"
                        : MaterialIcons.Visibility + " Enable";
                if (ImGui.menuItem(enableLabel)) {
                    UndoManager.getInstance().execute(
                            new ToggleEntityEnabledCommand(entity, !ownEnabled));
                    scene.markDirty();
                }

                if (ImGui.menuItem(MaterialIcons.ContentCopy + " Duplicate")) {
                    creationService.duplicateEntity(entity);
                }

                if (entity.getParent() != null) {
                    if (ImGui.menuItem(MaterialIcons.CallMade + " Unparent")) {
                        UndoManager.getInstance().execute(
                                new ReparentEntityCommand(scene, entity, null, getNextChildOrder(null))
                        );
                    }
                }

                if (entity.isScratchEntity() && !entity.getComponents().isEmpty()) {
                    if (ImGui.menuItem(MaterialIcons.Save + " Save as Prefab...")) {
                        savePrefabPopup.open(entity, savedPrefab -> {
                            EditorGameObject newRoot = PrefabHierarchyHelper.replaceScratchWithPrefabInstance(
                                    scene, entity, savedPrefab, null);
                            selectionHandler.handleEntityClick(newRoot);
                        });
                    }
                }

                ImGui.separator();

                if (ImGui.menuItem(MaterialIcons.Delete + " Delete")) {
                    UndoManager.getInstance().execute(new RemoveEntityCommand(scene, entity));
                }
            }

            ImGui.endPopup();
        }

        savePrefabPopup.render();
    }

    /**
     * Renders the shared create entity menu items.
     * Creates as child of the currently selected entity.
     */
    private void renderCreateMenuItems() {
        if (ImGui.menuItem(IconUtils.getScratchEntityIcon() + " New Child Entity")) {
            creationService.createEmptyEntity();
        }

        if (ImGui.beginMenu(MaterialIcons.Widgets + " Create UI Child")) {
            // Canvas
            if (ImGui.menuItem(IconUtils.getUICanvasIcon() + " Canvas")) {
                creationService.createUIElement("Canvas");
            }
            ImGui.separator();

            // Basic elements
            if (ImGui.menuItem(IconUtils.getUIPanelIcon() + " Panel")) {
                creationService.createUIElement("Panel");
            }
            if (ImGui.menuItem(IconUtils.getUIImageIcon() + " Image")) {
                creationService.createUIElement("Image");
            }
            if (ImGui.menuItem(IconUtils.getUIButtonIcon() + " Button")) {
                creationService.createUIElement("Button");
            }
            if (ImGui.menuItem(IconUtils.getUITextIcon() + " Text")) {
                creationService.createUIElement("Text");
            }
            ImGui.separator();

            // Scroll View
            if (ImGui.menuItem(IconUtils.getUIScrollViewIcon() + " Scroll View")) {
                creationService.createUIElement("ScrollView");
            }
            ImGui.separator();

            // Layout submenu
            if (ImGui.beginMenu(MaterialIcons.ViewModule + " Layout")) {
                if (ImGui.menuItem(IconUtils.getUIHorizontalLayoutIcon() + " Horizontal Layout")) {
                    creationService.createUIElement("HorizontalLayout");
                }
                if (ImGui.menuItem(IconUtils.getUIVerticalLayoutIcon() + " Vertical Layout")) {
                    creationService.createUIElement("VerticalLayout");
                }
                if (ImGui.menuItem(IconUtils.getUIGridLayoutIcon() + " Grid Layout")) {
                    creationService.createUIElement("GridLayout");
                }
                ImGui.endMenu();
            }

            ImGui.endMenu();
        }
    }

    private int getNextChildOrder(EditorGameObject parent) {
        if (parent == null) {
            return scene.getRootEntities().size();
        }
        return parent.getChildren().size();
    }

    /**
     * Checks if the mouse is over the entity's cached rect from the previous frame.
     * Used for right-click pre-selection so the entity appears selected immediately.
     */
    private boolean isMouseOverEntityArea(EditorGameObject entity) {
        float[] rect = entityRectCache.get(entity.getId());
        if (rect == null) return false;

        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        return mouseX >= rect[0] && mouseX <= rect[2]
                && mouseY >= rect[1] && mouseY <= rect[3];
    }

    // ========================================================================
    // PLAY MODE HIERARCHY
    // ========================================================================

    /**
     * Renders a HierarchyItem tree for play mode display.
     * Read-only — no drag-drop, no context menu, no rename.
     */
    public void renderHierarchyItemTree(HierarchyItem item, PlayModeSelectionManager selMgr) {
        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;

        boolean hasChildren = item.hasHierarchyChildren();
        if (!hasChildren) {
            flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        }

        // Selection highlight
        if (item instanceof RuntimeGameObjectAdapter adapter && selMgr != null) {
            if (selMgr.isSelected(adapter.getGameObject())) {
                flags |= ImGuiTreeNodeFlags.Selected;
            }
        }

        String icon = item.isEnabled()
                ? IconUtils.getScratchEntityIcon()
                : MaterialIcons.VisibilityOff;
        boolean open = ImGui.treeNodeEx(item.getId(), flags, icon + " " + item.getName());

        // Handle click — select in PlayModeSelectionManager
        if (ImGui.isItemClicked() && item instanceof RuntimeGameObjectAdapter adapter && selMgr != null) {
            boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
            if (ctrl) {
                selMgr.toggleSelection(adapter.getGameObject());
            } else {
                selMgr.select(adapter.getGameObject());
            }
        }

        if (open && hasChildren) {
            for (HierarchyItem child : item.getHierarchyChildren()) {
                renderHierarchyItemTree(child, selMgr);
            }
            ImGui.treePop();
        }
    }
}
