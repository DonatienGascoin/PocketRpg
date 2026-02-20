package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ReparentEntityCommand;
import com.pocket.rpg.editor.utils.IconUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;
import imgui.flag.ImGuiKey;
import lombok.Setter;

import java.util.Set;

/**
 * Handles drag-drop operations for the hierarchy panel.
 * Uses position-aware drop targets with thin line indicators and X-depth detection.
 *
 * X-depth: at any insertion line (ABOVE or BELOW zone), the mouse X position
 * determines the target depth. Moving left outdents (shallower parent),
 * moving right indents (deeper, into the previous entity's subtree).
 */
public class HierarchyDragDropHandler {

    private static final String DRAG_DROP_TYPE = "ENTITY_DND";
    private static final float INDICATOR_THICKNESS = 2f;
    private static final float TRIANGLE_SIZE = 4f;
    private static final int INDICATOR_COLOR = ImGui.colorConvertFloat4ToU32(0.4f, 0.7f, 1.0f, 1.0f);

    @Setter
    private EditorScene scene;

    // Per-frame state — cleared by resetFrame()
    private DropIndicatorState indicatorState;
    private float baseIndentX;

    // Tracks the previous rendered entity for ABOVE zone X-depth.
    // Updated after each handlePositionalDrop call.
    private EditorGameObject lastRenderedEntity;
    private int lastRenderedDepth;

    // Escape cancellation — persists until the drag ends (mouse released)
    private boolean dragCancelled;

    private enum DropZone {
        ABOVE, ON, BELOW
    }

    private record DropIndicatorState(float lineY, float lineStartX, float lineEndX,
                                      EditorGameObject targetParent, int insertIndex) {}

    /**
     * Called once per frame before rendering entities. Clears per-frame state.
     */
    public void resetFrame(float baseIndentX) {
        this.indicatorState = null;
        this.baseIndentX = baseIndentX;
        this.lastRenderedEntity = null;
        this.lastRenderedDepth = -1;
    }

    /**
     * Sets up the drag source for an entity.
     */
    public void handleDragSource(EditorGameObject entity) {
        // Prefab child nodes cannot be dragged (they belong to the prefab structure)
        if (entity.isPrefabChildNode()) return;

        // Check for Escape to cancel drag operation.
        // Workaround: imgui-java doesn't expose ImGui::ClearDragDrop(), so we use a flag-based
        // approach. The drag tooltip is hidden but the user must release the mouse to fully end it.
        // See: https://github.com/SpaiR/imgui-java/issues/365
        // TODO: Once the binding is added, call ImGui.clearDragDrop() here to instantly end the drag.
        if (ImGui.getDragDropPayload() == null) {
            dragCancelled = false;
        } else if (ImGui.isKeyPressed(ImGuiKey.Escape, false)) {
            dragCancelled = true;
        }

        if (dragCancelled) return;

        if (ImGui.beginDragDropSource(ImGuiDragDropFlags.None)) {
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

    /**
     * Core positional drop handler. Called right after rendering each tree node.
     * Computes drop zone from mouse Y position and handles ABOVE/ON/BELOW logic.
     * Both ABOVE and BELOW use mouse X position for depth detection.
     */
    public void handlePositionalDrop(EditorGameObject entity, float nodeMinY, float nodeMaxY,
                                     int depth, boolean isExpandedWithChildren) {
        if (dragCancelled) {
            lastRenderedEntity = entity;
            lastRenderedDepth = depth;
            return;
        }
        if (ImGui.beginDragDropTarget()) {
            float mouseY = ImGui.getMousePosY();
            float itemHeight = nodeMaxY - nodeMinY;

            DropZone zone = computeDropZone(mouseY, nodeMinY, itemHeight, isExpandedWithChildren);

            // Validate: don't allow dropping on self or ancestors
            Set<EditorGameObject> selected = scene.getSelectedEntities();
            boolean isValidTarget = true;
            for (EditorGameObject dragged : selected) {
                if (dragged == entity || dragged.isAncestorOf(entity)) {
                    isValidTarget = false;
                    break;
                }
            }

            if (isValidTarget) {
                float halfGap = ImGui.getStyle().getItemSpacingY() / 2f;
                float rightEdge = ImGui.getContentRegionMaxX() + ImGui.getWindowPosX();

                switch (zone) {
                    case ABOVE -> {
                        var resolved = resolveForAbove(entity, depth);
                        if (resolved != null) {
                            indicatorState = new DropIndicatorState(
                                    nodeMinY - halfGap,
                                    baseIndentX + resolved.depth * getIndentSpacing(),
                                    rightEdge,
                                    resolved.parent, resolved.insertIndex
                            );
                        }
                    }
                    case BELOW -> {
                        var resolved = resolveForBelow(entity, depth);
                        if (resolved != null) {
                            indicatorState = new DropIndicatorState(
                                    nodeMaxY + halfGap,
                                    baseIndentX + resolved.depth * getIndentSpacing(),
                                    rightEdge,
                                    resolved.parent, resolved.insertIndex
                            );
                        }
                    }
                    case ON -> { /* ImGui draws native hover rect */ }
                }
            }

            // For ABOVE/BELOW suppress ImGui's default highlight — we draw our own line.
            // For ON, let ImGui draw its native hover rect.
            int acceptFlags = (zone != DropZone.ON)
                    ? ImGuiDragDropFlags.AcceptNoDrawDefaultRect : ImGuiDragDropFlags.None;
            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE, acceptFlags);
            if (payload != null && isValidTarget) {
                executeDropAction(zone, entity, depth);
            }

            ImGui.endDragDropTarget();
        }

        // Track for ABOVE zone X-depth on the next entity
        lastRenderedEntity = entity;
        lastRenderedDepth = depth;
    }

    /**
     * Handles entity drops on the empty area invisible button (appends as root).
     */
    public void handleEmptyAreaEntityDrop(int rootCount) {
        if (dragCancelled) return;
        if (ImGui.beginDragDropTarget()) {
            byte[] payload = ImGui.acceptDragDropPayload(DRAG_DROP_TYPE);
            if (payload != null) {
                Set<EditorGameObject> selected = scene.getSelectedEntities();
                int offset = 0;
                for (EditorGameObject dragged : selected) {
                    int adjustedIndex = rootCount + offset;
                    if (dragged.getParent() == null && dragged.getOrder() < rootCount) {
                        adjustedIndex = Math.max(0, adjustedIndex - 1);
                    }
                    UndoManager.getInstance().execute(
                            new ReparentEntityCommand(scene, dragged, null, adjustedIndex)
                    );
                    offset++;
                }
            }
            ImGui.endDragDropTarget();
        }
    }

    /**
     * Draws the drop indicator line and triangle. Call once after all entities are rendered.
     */
    public void drawDropIndicator() {
        if (indicatorState == null) return;

        ImDrawList drawList = ImGui.getWindowDrawList();
        float y = indicatorState.lineY;
        float startX = indicatorState.lineStartX;
        float endX = indicatorState.lineEndX;

        // Line
        drawList.addLine(startX, y, endX, y, INDICATOR_COLOR, INDICATOR_THICKNESS);

        // Small right-pointing triangle at the left end
        drawList.addTriangleFilled(
                startX, y - TRIANGLE_SIZE,
                startX, y + TRIANGLE_SIZE,
                startX + TRIANGLE_SIZE * 1.5f, y,
                INDICATOR_COLOR
        );
    }

    // ========================================================================
    // X-DEPTH RESOLUTION
    // ========================================================================

    /**
     * Resolves the drop target for the ABOVE zone with X-depth.
     *
     * The gap above this entity is shared with whatever was rendered previously.
     * Valid depth range: [entity.depth, lastRenderedDepth].
     * - At entity.depth: insert before this entity as its sibling.
     * - At deeper depths: insert after the previous entity (or its ancestor) via resolveBelowTarget.
     */
    private ResolvedTarget resolveForAbove(EditorGameObject entity, int depth) {
        int maxDepth = (lastRenderedDepth >= depth) ? lastRenderedDepth : depth;
        int targetDepth = computeDepthFromMouseX(depth, maxDepth);

        if (targetDepth <= depth) {
            // Insert before this entity at its own level
            return resolveAboveTarget(entity, depth);
        } else {
            // Insert after the previous entity's ancestor at the target depth
            return resolveBelowTarget(lastRenderedEntity, targetDepth);
        }
    }

    /**
     * Resolves the drop target for the BELOW zone with X-depth.
     *
     * Valid depth range: [minDepth, entity.depth].
     * minDepth is limited by the "last child" chain — can only outdent through
     * ancestors that are the last child of their parent.
     */
    private ResolvedTarget resolveForBelow(EditorGameObject entity, int depth) {
        int minDepth = computeMinDepthForBelow(entity, depth);
        int targetDepth = computeDepthFromMouseX(minDepth, depth);
        return resolveBelowTarget(entity, targetDepth);
    }

    /**
     * Computes target depth from mouse X position, clamped to [minDepth, maxDepth].
     */
    private int computeDepthFromMouseX(int minDepth, int maxDepth) {
        float mouseX = ImGui.getMousePosX();
        float indentSpacing = getIndentSpacing();
        if (indentSpacing <= 0) indentSpacing = 21f;

        int mouseDepth = (int) Math.floor((mouseX - baseIndentX) / indentSpacing);
        return Math.max(minDepth, Math.min(mouseDepth, maxDepth));
    }

    /**
     * Determines the minimum depth the BELOW zone can outdent to.
     * Walks up the "last child" chain: outdenting past an ancestor is only valid
     * if that ancestor is the last child of its parent (no siblings below it).
     */
    private int computeMinDepthForBelow(EditorGameObject entity, int entityDepth) {
        int minDepth = entityDepth;
        EditorGameObject current = entity;

        while (current != null) {
            if (!isLastAmongSiblings(current)) break;
            minDepth--;
            current = current.getParent();
        }

        return Math.max(0, minDepth);
    }

    private boolean isLastAmongSiblings(EditorGameObject entity) {
        EditorGameObject parent = entity.getParent();
        var siblings = (parent != null) ? parent.getChildren() : scene.getRootEntities();
        for (EditorGameObject sibling : siblings) {
            if (sibling != entity && sibling.getOrder() > entity.getOrder()) {
                return false;
            }
        }
        return true;
    }

    private float getIndentSpacing() {
        return ImGui.getStyle().getIndentSpacing();
    }

    // ========================================================================
    // TARGET RESOLUTION
    // ========================================================================

    /**
     * For ABOVE zone: insert before this entity at the same level.
     */
    private ResolvedTarget resolveAboveTarget(EditorGameObject entity, int depth) {
        EditorGameObject parent = entity.getParent();
        int insertIndex = entity.getOrder();
        return new ResolvedTarget(parent, insertIndex, depth);
    }

    /**
     * For BELOW zone: walks up ancestors from entity to find the right parent at targetDepth.
     */
    private ResolvedTarget resolveBelowTarget(EditorGameObject entity, int targetDepth) {
        int entityDepth = getDepth(entity);

        // Walk up from entity to find the ancestor at targetDepth
        EditorGameObject ancestor = entity;
        int currentDepth = entityDepth;
        while (currentDepth > targetDepth && ancestor != null) {
            ancestor = ancestor.getParent();
            currentDepth--;
        }

        if (ancestor == null) {
            return new ResolvedTarget(null, scene.getRootEntities().size(), 0);
        }

        EditorGameObject parent = ancestor.getParent();
        int insertIndex = ancestor.getOrder() + 1;
        int resolvedDepth;

        if (parent == null) {
            resolvedDepth = 0;
        } else {
            resolvedDepth = getDepth(parent) + 1;
        }

        return new ResolvedTarget(parent, insertIndex, resolvedDepth);
    }

    private int getDepth(EditorGameObject entity) {
        int depth = 0;
        EditorGameObject current = entity.getParent();
        while (current != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    // ========================================================================
    // DROP EXECUTION
    // ========================================================================

    private void executeDropAction(DropZone zone, EditorGameObject entity, int depth) {
        Set<EditorGameObject> selected = scene.getSelectedEntities();

        switch (zone) {
            case ABOVE -> {
                var resolved = resolveForAbove(entity, depth);
                if (resolved != null) {
                    executeReparent(selected, resolved.parent, resolved.insertIndex);
                }
            }
            case BELOW -> {
                var resolved = resolveForBelow(entity, depth);
                if (resolved != null) {
                    executeReparent(selected, resolved.parent, resolved.insertIndex);
                }
            }
            case ON -> {
                int insertIdx = entity.getChildren().size();
                int offset = 0;
                for (EditorGameObject dragged : selected) {
                    if (dragged != entity && !dragged.isAncestorOf(entity)) {
                        UndoManager.getInstance().execute(
                                new ReparentEntityCommand(scene, dragged, entity, insertIdx + offset)
                        );
                        offset++;
                    }
                }
            }
        }
    }

    private void executeReparent(Set<EditorGameObject> selected, EditorGameObject targetParent,
                                 int insertIndex) {
        int offset = 0;
        for (EditorGameObject dragged : selected) {
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

    // ========================================================================
    // INTERNAL TYPES
    // ========================================================================

    private DropZone computeDropZone(float mouseY, float itemMinY, float itemHeight,
                                     boolean isExpandedWithChildren) {
        float relativeY = mouseY - itemMinY;
        float fraction = relativeY / itemHeight;

        if (isExpandedWithChildren) {
            // Expanded with children: top 25% = ABOVE, bottom 75% = ON (children go inside)
            return fraction < 0.25f ? DropZone.ABOVE : DropZone.ON;
        } else {
            // Leaf or collapsed: top 25% = ABOVE, middle 50% = ON, bottom 25% = BELOW
            if (fraction < 0.25f) return DropZone.ABOVE;
            if (fraction > 0.75f) return DropZone.BELOW;
            return DropZone.ON;
        }
    }

    private record ResolvedTarget(EditorGameObject parent, int insertIndex, int depth) {}
}
