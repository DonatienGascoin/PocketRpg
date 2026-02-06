package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.PlayModeSelectionManager;
import com.pocket.rpg.editor.assets.HierarchyDropTarget;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.SavePrefabPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.BulkDeleteCommand;
import com.pocket.rpg.editor.undo.commands.RemoveEntityCommand;
import com.pocket.rpg.editor.undo.commands.RenameEntityCommand;
import com.pocket.rpg.editor.undo.commands.ReparentEntityCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.prefab.Prefab;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final ImString renameBuffer = new ImString(64);
    private String nameBeforeRename = null;  // For undo support
    private final SavePrefabPopup savePrefabPopup = new SavePrefabPopup();

    // Scroll-to-selection support
    private Set<String> entitiesToForceOpen = new HashSet<>();
    private String scrollToEntityId = null;

    // Cache entity rects from previous frame for right-click pre-selection
    private final java.util.Map<String, float[]> entityRectCache = new java.util.HashMap<>();

    /**
     * Requests the tree to expand all ancestor nodes and scroll to reveal the given entity.
     * One-shot: clears after the entity is rendered.
     */
    public void requestScrollToEntity(EditorGameObject entity) {
        scrollToEntityId = entity.getId();
        entitiesToForceOpen.clear();
        EditorGameObject current = entity.getParent();
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
            selectionHandler.handleEntityClick(entity);
        }

        boolean isSelected = scene.isSelected(entity);

        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        if (isSelected) flags |= ImGuiTreeNodeFlags.Selected;
        if (!hasChildren) flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        else flags |= ImGuiTreeNodeFlags.DefaultOpen;

        // Force-open ancestor nodes to reveal scroll target
        if (entitiesToForceOpen.contains(entity.getId())) {
            ImGui.setNextItemOpen(true);
        }

        // Determine the label - empty when renaming (we'll draw inline input after)
        String label;
        if (isRenaming) {
            // Just show icon, name will be replaced by input field
            label = IconUtils.getIconForEntity(entity) + " ";
        } else {
            label = IconUtils.getIconForEntity(entity) + " " + entity.getName();
        }

        boolean nodeOpen = ImGui.treeNodeEx("##entity_" + entity.getId(), flags, label);

        // Scroll to this entity if it's the target
        if (entity.getId().equals(scrollToEntityId)) {
            ImGui.setScrollHereY(0.5f);
            scrollToEntityId = null;
            entitiesToForceOpen.clear();
        }

        // Render inline rename field on same line as tree node
        if (isRenaming) {
            ImGui.sameLine();
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
            handleEntityInteraction(entity);
            dragDropHandler.handleDragSource(entity);
            // Positional drop target (tree node is last item for beginDragDropTarget)
            dragDropHandler.handlePositionalDrop(entity, nodeMinY, nodeMaxY,
                    depth, hasChildren && nodeOpen);
            // Asset drops on entity (also targets the tree node)
            HierarchyDropTarget.handleEntityDrop(scene, entity);
            renderEntityContextMenu(entity);
        }

        // Always render children if node is open
        if (hasChildren && nodeOpen) {
            List<EditorGameObject> children = new ArrayList<>(entity.getChildren());
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
        ImGui.setKeyboardFocusHere();

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

        // Cancel on escape
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renamingItem = null;
            nameBeforeRename = null;
        }
    }

    private void handleEntityInteraction(EditorGameObject entity) {
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            selectionHandler.handleEntityClick(entity);
        }

        // Note: Right-click selection is handled earlier via pre-selection
        // to ensure the entity appears selected before the context menu opens

        if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
            renamingItem = entity;
            nameBeforeRename = entity.getName();
            renameBuffer.set(nameBeforeRename);
        }
    }

    private void renderEntityContextMenu(EditorGameObject entity) {
        if (ImGui.beginPopupContextItem("entity_ctx_" + entity.getId())) {
            Set<EditorGameObject> selected = scene.getSelectedEntities();
            boolean multiSelect = selected.size() > 1;

            // Create options (entity is already selected via right-click)
            renderCreateMenuItems();
            ImGui.separator();

            if (multiSelect) {
                ImGui.text(selected.size() + " entities selected");
                ImGui.separator();

                if (ImGui.menuItem(MaterialIcons.Delete + " Delete All")) {
                    UndoManager.getInstance().execute(new BulkDeleteCommand(scene, selected));
                }

                if (ImGui.menuItem(MaterialIcons.CallMade + " Unparent All")) {
                    for (EditorGameObject e : selected) {
                        if (e.getParent() != null) {
                            UndoManager.getInstance().execute(
                                    new ReparentEntityCommand(scene, e, null, getNextChildOrder(null))
                            );
                        }
                    }
                }
            } else {
                if (ImGui.menuItem(MaterialIcons.Edit + " Rename")) {
                    renamingItem = entity;
                    nameBeforeRename = entity.getName();
                    renameBuffer.set(nameBeforeRename);
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
                            System.out.println("Saved prefab: " + savedPrefab.getId());
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
