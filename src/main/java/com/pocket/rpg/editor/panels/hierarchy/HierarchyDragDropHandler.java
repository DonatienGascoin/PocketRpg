package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ReparentEntityCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiDragDropFlags;
import lombok.Setter;

import java.util.Set;

/**
 * Handles drag-drop operations for the hierarchy panel.
 */
public class HierarchyDragDropHandler {

    private static final String DRAG_DROP_TYPE = "ENTITY_DND";
    private static final float DROP_ZONE_HEIGHT = 4f;

    @Setter
    private EditorScene scene;

    private EditorEntity draggedEntity = null;
    private DropTarget currentDropTarget = null;

    private enum DropPosition {
        BEFORE, ON, AFTER
    }

    private record DropTarget(EditorEntity entity, DropPosition position) {
    }

    public void resetDropTarget() {
        currentDropTarget = null;
    }

    public boolean isDropTarget(EditorEntity entity) {
        return currentDropTarget != null &&
                currentDropTarget.entity == entity &&
                currentDropTarget.position == DropPosition.ON;
    }

    public void handleDragSource(EditorEntity entity) {
        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
            draggedEntity = entity;

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
    }

    public void handleDropTarget(EditorEntity entity) {
        if (ImGui.beginDragDropTarget()) {
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

    public void renderDropZone(EditorEntity targetParent, int insertIndex, EditorEntity nextEntity) {
        String zoneId = "##dropzone_" +
                (targetParent != null ? targetParent.getId() : "root") + "_" +
                insertIndex + "_" +
                (nextEntity != null ? nextEntity.getId() : "end");

        float width = ImGui.getContentRegionAvailX();
        if (width < 1.0f) width = 1.0f;

        ImGui.invisibleButton(zoneId, width, DROP_ZONE_HEIGHT);

        if (ImGui.beginDragDropTarget()) {
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
                    if (dragged == targetParent || (targetParent != null && dragged.isAncestorOf(targetParent))) {
                        continue;
                    }

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
}
