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
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
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

    public void renderEntityTree(EditorGameObject entity) {
        boolean isSelected = scene.isSelected(entity);
        boolean isRenaming = entity == renamingItem;
        boolean hasChildren = entity.hasChildren();

        ImGui.pushID(entity.getId());

        int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
        if (isSelected) flags |= ImGuiTreeNodeFlags.Selected;
        if (!hasChildren) flags |= ImGuiTreeNodeFlags.Leaf | ImGuiTreeNodeFlags.NoTreePushOnOpen;
        else flags |= ImGuiTreeNodeFlags.DefaultOpen;

        boolean isDropTarget = dragDropHandler.isDropTarget(entity);
        if (isDropTarget) {
            ImGui.pushStyleColor(ImGuiCol.Header, 0.3f, 0.5f, 0.8f, 0.5f);
        }

        if (isRenaming) {
            renderRenameField(entity);
        } else {
            String label = IconUtils.getIconForEntity(entity) + " " + entity.getName();
            boolean nodeOpen = ImGui.treeNodeEx("##entity_" + entity.getId(), flags, label);

            handleEntityInteraction(entity);
            dragDropHandler.handleDragSource(entity);
            dragDropHandler.handleDropTarget(entity);

            if (hasChildren && nodeOpen) {
                List<EditorGameObject> children = new ArrayList<>(entity.getChildren());
                children.sort(Comparator.comparingInt(EditorGameObject::getOrder));

                for (int i = 0; i < children.size(); i++) {
                    EditorGameObject child = children.get(i);
                    dragDropHandler.renderDropZone(entity, i, child);
                    renderEntityTree(child);
                }

                dragDropHandler.renderDropZone(entity, children.size(), null);
                ImGui.treePop();
            }

            renderEntityContextMenu(entity);
            renderEntityTooltip(entity);
        }

        if (isDropTarget) {
            ImGui.popStyleColor();
        }

        ImGui.popID();

        HierarchyDropTarget.handleEntityDrop(scene, entity);
    }

    private void renderRenameField(EditorGameObject entity) {
        ImGui.setNextItemWidth(150);
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

        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            renamingItem = null;
            nameBeforeRename = null;
        }
    }

    private void handleEntityInteraction(EditorGameObject entity) {
        if (ImGui.isItemClicked(ImGuiMouseButton.Left)) {
            selectionHandler.handleEntityClick(entity);
        }

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

    private void renderEntityTooltip(EditorGameObject entity) {
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

    private int getNextChildOrder(EditorGameObject parent) {
        if (parent == null) {
            return scene.getRootEntities().size();
        }
        return parent.getChildren().size();
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
