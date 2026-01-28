package com.pocket.rpg.editor.panels.animator;

import com.pocket.rpg.animation.animator.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesEditorContext;
import imgui.extension.imnodes.flag.ImNodesCol;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.*;
import java.util.function.Consumer;

/**
 * Visual node graph editor for AnimatorController using ImNodes.
 * <p>
 * Renders states as draggable nodes and transitions as custom bezier arrows.
 * Supports node selection, context menu-based link creation, and bidirectional arrow separation.
 */
public class AnimatorGraphEditor {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    private static final float NODE_WIDTH = 150f;
    private static final float NODE_HEIGHT = 80f;

    // Transition colors by type (as int packed RGBA)
    private static final int COLOR_INSTANT = packColor(102, 179, 102, 255);           // Green
    private static final int COLOR_WAIT_COMPLETION = packColor(179, 128, 64, 255);    // Orange
    private static final int COLOR_WAIT_LOOP = packColor(102, 128, 179, 255);         // Blue
    private static final int COLOR_PREVIEW = packColor(200, 200, 200, 180);           // Gray for preview
    private static final int COLOR_LINK_SELECTED = packColor(100, 150, 220, 255);     // Blue for selected

    // Arrow dimensions
    private static final float ARROW_LENGTH = 10f;
    private static final float ARROW_WIDTH = 6f;
    private static final float LINK_THICKNESS = 2.5f;
    private static final float LINK_HIT_RADIUS = 8f;

    // Perpendicular offset for bidirectional links (so they don't overlap)
    private static final float BIDIRECTIONAL_OFFSET = 10f;

    // ========================================================================
    // STATE
    // ========================================================================

    private ImNodesEditorContext editorContext;
    private boolean contextCreated = false;
    private final AnimatorIdManager idManager = new AnimatorIdManager();

    // Selection state
    private String selectedStateName = null;
    private int selectedTransitionIndex = -1;

    // Context menu state
    private int contextNodeId = -1;
    private float contextMenuX = 0;
    private float contextMenuY = 0;

    // Transition creation mode
    private String pendingTransitionFrom = null;

    // Hover state
    private int hoveredNodeId = -1;
    private int hoveredTransitionIndex = -1;
    private boolean editorHovered = false;

    // Editor bounds (for manual checks)
    private float editorMinX, editorMinY, editorMaxX, editorMaxY;

    // Node positions (for new nodes and initial layout) - editor space
    private final Map<String, float[]> nodePositions = new HashMap<>();
    // Screen positions from previous frame (for rendering transitions behind nodes)
    private final Map<String, float[]> nodeScreenPositions = new HashMap<>();
    private boolean needsInitialLayout = true;

    // Preview highlighting (pulsing effect)
    private String activeStateName = null;
    private String pendingTransitionTarget = null;

    // Callbacks
    private Consumer<String> onStateSelected;
    private Consumer<Integer> onTransitionSelected;
    private Runnable onSelectionCleared;
    private Runnable onAddState;
    private Consumer<String> onEditState;
    private Consumer<String> onDeleteState;
    private Consumer<String> onSetDefaultState;
    private Consumer<int[]> onCreateTransition; // [fromStateIndex, toStateIndex]
    private Consumer<Integer> onEditTransition;
    private Consumer<Integer> onDeleteTransition;
    private Runnable onAutoLayout;
    private Runnable onCaptureUndo;

    // ========================================================================
    // INITIALIZATION
    // ========================================================================

    public void initialize() {
        ImNodes.createContext();
        editorContext = ImNodes.editorContextCreate();
        contextCreated = true;
    }

    public void destroy() {
        if (contextCreated) {
            if (editorContext != null) {
                ImNodes.editorContextFree(editorContext);
                editorContext = null;
            }
            ImNodes.destroyContext();
            contextCreated = false;
        }
    }

    public void reset() {
        idManager.clear();
        nodePositions.clear();
        nodeScreenPositions.clear();
        selectedStateName = null;
        selectedTransitionIndex = -1;
        pendingTransitionFrom = null;
        needsInitialLayout = true;
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    public void render(AnimatorController controller) {
        if (!contextCreated || controller == null) {
            ImGui.textDisabled("No animator controller loaded");
            return;
        }

        ImNodes.editorContextSet(editorContext);
        applyStyle();

        ImNodes.beginNodeEditor();

        // Render transitions FIRST using previous frame's screen positions
        // This ensures they appear behind all nodes consistently
        renderCustomTransitions(controller);

        // Render state nodes
        for (int i = 0; i < controller.getStateCount(); i++) {
            AnimatorState state = controller.getState(i);
            renderStateNode(state, controller);
        }

        // Update screen positions for next frame (after nodes are positioned)
        updateNodeScreenPositions(controller);

        // Minimap in bottom right
        ImNodes.miniMap(0.15f, ImNodesMiniMapLocation.BottomRight);

        // Capture editor bounds before ending
        editorMinX = ImGui.getWindowPosX();
        editorMinY = ImGui.getWindowPosY();
        editorMaxX = editorMinX + ImGui.getWindowSizeX();
        editorMaxY = editorMinY + ImGui.getWindowSizeY();

        ImNodes.endNodeEditor();

        // Capture hover state AFTER ending the editor
        hoveredNodeId = ImNodes.getHoveredNode();

        // Manual bounds check for editor hover
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        editorHovered = mouseX >= editorMinX && mouseX <= editorMaxX
                     && mouseY >= editorMinY && mouseY <= editorMaxY;

        // Fallback node hit test
        if (hoveredNodeId == -1 && editorHovered) {
            hoveredNodeId = hitTestNodes(controller);
        }

        // Preview line when creating transition
        if (pendingTransitionFrom != null) {
            renderTransitionPreview(controller);
        }

        // Handle transition creation mode
        handleTransitionCreation(controller);

        // Handle deletion
        handleDeletion(controller);

        // Context menus
        handleContextMenus(controller);

        // Handle selection changes
        handleSelection(controller);

        // Apply initial layout if needed
        if (needsInitialLayout && controller.getStateCount() > 0) {
            applyInitialLayout(controller);
            needsInitialLayout = false;
        }
    }

    // ========================================================================
    // STYLING
    // ========================================================================

    private void applyStyle() {
        ImNodes.pushColorStyle(ImNodesCol.NodeBackground, packColor(30, 30, 30, 230));
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundHovered, packColor(45, 45, 45, 230));
        ImNodes.pushColorStyle(ImNodesCol.NodeBackgroundSelected, packColor(50, 60, 80, 230));
        ImNodes.pushColorStyle(ImNodesCol.NodeOutline, packColor(90, 90, 90, 255));

        ImNodes.pushColorStyle(ImNodesCol.TitleBar, packColor(50, 50, 50, 255));
        ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, packColor(60, 60, 60, 255));
        ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, packColor(70, 80, 100, 255));
    }

    private static int packColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    // ========================================================================
    // NODE RENDERING (No pins)
    // ========================================================================

    private void renderStateNode(AnimatorState state, AnimatorController controller) {
        int nodeId = (int) idManager.getNodeId(state.getName());
        boolean isDefault = state.getName().equals(controller.getDefaultState());
        boolean isLinkSource = state.getName().equals(pendingTransitionFrom);
        boolean isActive = state.getName().equals(activeStateName);

        // Set initial position for new nodes
        if (!nodePositions.containsKey(state.getName())) {
            float[] pos = new float[]{100 + nodePositions.size() * 200, 100 + (nodePositions.size() % 3) * 120};
            nodePositions.put(state.getName(), pos);
            ImNodes.setNodeScreenSpacePos(nodeId, pos[0], pos[1]);
        }

        // Push style for active state (pulsing), link source, or default state
        if (isActive) {
            // Pulsing green for active state
            float time = (float) ImGui.getTime();
            float pulse = 0.6f + 0.4f * (float) Math.sin(time * 5.0);
            int pulseGreen = (int) (180 * pulse);
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, packColor(40, pulseGreen, 40, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, packColor(50, pulseGreen + 20, 50, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, packColor(60, pulseGreen + 40, 60, 255));
            ImNodes.pushColorStyle(ImNodesCol.NodeOutline, packColor(100, (int)(255 * pulse), 100, 255));
        } else if (isLinkSource) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, packColor(80, 80, 40, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, packColor(100, 100, 50, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, packColor(120, 120, 60, 255));
            ImNodes.pushColorStyle(ImNodesCol.NodeOutline, packColor(200, 200, 100, 255));
        } else if (isDefault) {
            ImNodes.pushColorStyle(ImNodesCol.TitleBar, packColor(40, 80, 40, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarHovered, packColor(50, 100, 50, 255));
            ImNodes.pushColorStyle(ImNodesCol.TitleBarSelected, packColor(60, 120, 60, 255));
        }

        ImNodes.beginNode(nodeId);

        // Title bar with state name
        ImNodes.beginNodeTitleBar();
        if (isDefault) {
            ImGui.text(MaterialIcons.Star + " " + state.getName());
        } else {
            ImGui.text(state.getName());
        }
        ImNodes.endNodeTitleBar();

        // State type
        ImGui.textDisabled(state.getType().name().toLowerCase());

        // Animation info
        if (state.getType() == StateType.SIMPLE) {
            String animPath = state.getAnimation();
            if (animPath != null && !animPath.isBlank()) {
                String display = truncatePath(animPath, 18);
                ImGui.textDisabled(display);
            } else {
                ImGui.textColored(178, 128, 51, 255, "(no animation)");
            }
        } else if (state.getType() == StateType.DIRECTIONAL) {
            int count = state.countSetDirections();
            ImGui.textDisabled(count + "/4 directions");
        }

        // Add some padding at bottom for consistent height
        ImGui.dummy(NODE_WIDTH - 20, 5);

        ImNodes.endNode();

        if (isActive) {
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
        } else if (isLinkSource) {
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
        } else if (isDefault) {
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
            ImNodes.popColorStyle();
        }

        // Update stored position
        float posX = ImNodes.getNodeEditorSpacePosX(nodeId);
        float posY = ImNodes.getNodeEditorSpacePosY(nodeId);
        nodePositions.put(state.getName(), new float[]{posX, posY});
    }

    private String truncatePath(String path, int maxLen) {
        if (path == null) return "";
        int lastSlash = path.lastIndexOf('/');
        String filename = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            filename = filename.substring(0, dot);
        }
        if (filename.length() > maxLen) {
            return filename.substring(0, maxLen - 3) + "...";
        }
        return filename;
    }

    // ========================================================================
    // CUSTOM TRANSITION RENDERING
    // ========================================================================

    private void renderCustomTransitions(AnimatorController controller) {
        // Use window draw list - rendered before nodes so appears behind them
        ImDrawList drawList = ImGui.getWindowDrawList();
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        // Reset hovered transition
        hoveredTransitionIndex = -1;
        float closestDist = LINK_HIT_RADIUS;

        // Build set of bidirectional pairs for offset calculation
        Set<String> bidirectionalPairs = new HashSet<>();
        for (int i = 0; i < controller.getTransitionCount(); i++) {
            AnimatorTransition t = controller.getTransition(i);
            if (controller.hasTransition(t.getTo(), t.getFrom())) {
                bidirectionalPairs.add(t.getFrom() + "->" + t.getTo());
                bidirectionalPairs.add(t.getTo() + "->" + t.getFrom());
            }
        }

        for (int i = 0; i < controller.getTransitionCount(); i++) {
            AnimatorTransition trans = controller.getTransition(i);
            String fromState = trans.getFrom();
            String toState = trans.getTo();

            if (!idManager.hasNodeId(fromState) || !idManager.hasNodeId(toState)) {
                continue;
            }

            // Get node screen positions from previous frame
            float[] fromPos = nodeScreenPositions.get(fromState);
            float[] toPos = nodeScreenPositions.get(toState);

            // Skip if positions not yet available
            if (fromPos == null || toPos == null) {
                continue;
            }

            float fromX = fromPos[0];
            float fromY = fromPos[1];
            float toX = toPos[0];
            float toY = toPos[1];

            // Calculate node centers
            float fromCenterX = fromX + NODE_WIDTH / 2;
            float fromCenterY = fromY + NODE_HEIGHT / 2;
            float toCenterX = toX + NODE_WIDTH / 2;
            float toCenterY = toY + NODE_HEIGHT / 2;

            // Calculate perpendicular offset for bidirectional transitions
            float offsetX = 0;
            float offsetY = 0;
            String pairKey = fromState + "->" + toState;
            if (bidirectionalPairs.contains(pairKey)) {
                // Use canonical direction (alphabetically first to second) for consistent perpendicular
                float canonicalDx, canonicalDy;
                if (fromState.compareTo(toState) < 0) {
                    canonicalDx = toCenterX - fromCenterX;
                    canonicalDy = toCenterY - fromCenterY;
                } else {
                    canonicalDx = fromCenterX - toCenterX;
                    canonicalDy = fromCenterY - toCenterY;
                }
                float len = (float) Math.sqrt(canonicalDx * canonicalDx + canonicalDy * canonicalDy);
                if (len > 0.001f) {
                    // Perpendicular direction (consistent for both transitions)
                    float perpX = -canonicalDy / len;
                    float perpY = canonicalDx / len;
                    // Apply offset based on state name comparison (opposite signs)
                    float sign = fromState.compareTo(toState) < 0 ? 1 : -1;
                    offsetX = perpX * BIDIRECTIONAL_OFFSET * sign;
                    offsetY = perpY * BIDIRECTIONAL_OFFSET * sign;
                }
            }

            // Apply offset to connection points
            float startX = fromCenterX + offsetX;
            float startY = fromCenterY + offsetY;
            float endX = toCenterX + offsetX;
            float endY = toCenterY + offsetY;

            // Get color
            boolean isSelected = (i == selectedTransitionIndex);
            int color = isSelected ? COLOR_LINK_SELECTED : getTransitionColor(trans.getType());

            // Draw the line with arrow in middle
            drawLineWithMiddleArrow(drawList, startX, startY, endX, endY, color);

            // Hit test for this transition (simple line distance)
            if (editorHovered && pendingTransitionFrom == null) {
                float dist = distanceToLine(mouseX, mouseY, startX, startY, endX, endY);
                if (dist < closestDist) {
                    closestDist = dist;
                    hoveredTransitionIndex = i;
                }
            }
        }
    }

    private void drawLineWithMiddleArrow(ImDrawList drawList, float startX, float startY,
                                          float endX, float endY, int color) {
        // Draw straight line
        drawList.addLine(startX, startY, endX, endY, color, LINK_THICKNESS);

        // Calculate direction
        float dx = endX - startX;
        float dy = endY - startY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;

        // Normalize direction
        float dirX = dx / len;
        float dirY = dy / len;

        // Arrow at middle of line
        float midX = (startX + endX) / 2;
        float midY = (startY + endY) / 2;

        // Arrow tip (slightly ahead of middle in direction of travel)
        float tipX = midX + dirX * ARROW_LENGTH / 2;
        float tipY = midY + dirY * ARROW_LENGTH / 2;

        // Arrow base
        float baseX = midX - dirX * ARROW_LENGTH / 2;
        float baseY = midY - dirY * ARROW_LENGTH / 2;

        // Perpendicular for arrow width
        float perpX = -dirY * ARROW_WIDTH;
        float perpY = dirX * ARROW_WIDTH;

        drawList.addTriangleFilled(
            tipX, tipY,
            baseX + perpX, baseY + perpY,
            baseX - perpX, baseY - perpY,
            color
        );
    }

    private float distanceToLine(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lenSq = dx * dx + dy * dy;

        if (lenSq < 0.001f) {
            // Line is a point
            return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        // Project point onto line, clamped to segment
        float t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));

        // Closest point on segment
        float closestX = x1 + t * dx;
        float closestY = y1 + t * dy;

        return (float) Math.sqrt((px - closestX) * (px - closestX) + (py - closestY) * (py - closestY));
    }

    private void renderTransitionPreview(AnimatorController controller) {
        if (pendingTransitionFrom == null) {
            return;
        }

        // Get position from stored screen positions
        float[] fromPos = nodeScreenPositions.get(pendingTransitionFrom);
        if (fromPos == null) {
            return;
        }

        // Use foreground for preview so it's visible above everything
        ImDrawList drawList = ImGui.getForegroundDrawList();

        // Start from node center
        float startX = fromPos[0] + NODE_WIDTH / 2;
        float startY = fromPos[1] + NODE_HEIGHT / 2;

        // End at mouse position
        float endX = ImGui.getMousePosX();
        float endY = ImGui.getMousePosY();

        // Draw preview line with arrow in middle
        drawLineWithMiddleArrow(drawList, startX, startY, endX, endY, COLOR_PREVIEW);

        // Show hint text
        drawList.addText(endX + 15, endY - 10, COLOR_PREVIEW, "Click target state");
    }

    private int getTransitionColor(TransitionType type) {
        return switch (type) {
            case INSTANT -> COLOR_INSTANT;
            case WAIT_FOR_COMPLETION -> COLOR_WAIT_COMPLETION;
            case WAIT_FOR_LOOP -> COLOR_WAIT_LOOP;
        };
    }

    // ========================================================================
    // TRANSITION CREATION
    // ========================================================================

    private void handleTransitionCreation(AnimatorController controller) {
        if (pendingTransitionFrom == null) {
            return;
        }

        // Cancel on ESC or right-click
        if (ImGui.isKeyPressed(ImGuiKey.Escape) || ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            pendingTransitionFrom = null;
            return;
        }

        // Left click
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && editorHovered) {
            if (hoveredNodeId != -1) {
                // Clicked on a node - complete the transition
                String toState = idManager.getStateName(hoveredNodeId);
                if (toState != null && !toState.equals(pendingTransitionFrom)) {
                    // Check if transition already exists
                    if (!controller.hasTransition(pendingTransitionFrom, toState)) {
                        if (onCaptureUndo != null) {
                            onCaptureUndo.run();
                        }
                        if (onCreateTransition != null) {
                            int fromIndex = controller.getStateIndex(pendingTransitionFrom);
                            int toIndex = controller.getStateIndex(toState);
                            onCreateTransition.accept(new int[]{fromIndex, toIndex});
                        }
                    }
                }
                pendingTransitionFrom = null;
            } else {
                // Clicked on empty space - cancel
                pendingTransitionFrom = null;
            }
        }
    }

    // ========================================================================
    // DELETION
    // ========================================================================

    private void handleDeletion(AnimatorController controller) {
        if (pendingTransitionFrom != null) {
            return; // Don't delete while creating transition
        }

        int numSelectedNodes = ImNodes.numSelectedNodes();

        if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
            // Delete selected transition
            if (selectedTransitionIndex >= 0 && selectedTransitionIndex < controller.getTransitionCount()) {
                if (onCaptureUndo != null) {
                    onCaptureUndo.run();
                }
                if (onDeleteTransition != null) {
                    onDeleteTransition.accept(selectedTransitionIndex);
                }
                selectedTransitionIndex = -1;
                return;
            }

            // Delete selected nodes
            if (numSelectedNodes > 0 && controller.getStateCount() > 1) {
                int[] selectedNodes = new int[numSelectedNodes];
                ImNodes.getSelectedNodes(selectedNodes);
                for (int nodeId : selectedNodes) {
                    String stateName = idManager.getStateName(nodeId);
                    if (stateName != null && onDeleteState != null) {
                        if (onCaptureUndo != null) {
                            onCaptureUndo.run();
                        }
                        onDeleteState.accept(stateName);
                        idManager.removeState(stateName);
                        nodePositions.remove(stateName);
                        break;
                    }
                }
                ImNodes.clearNodeSelection();
            }
        }
    }

    // ========================================================================
    // CONTEXT MENUS
    // ========================================================================

    private void handleContextMenus(AnimatorController controller) {
        if (pendingTransitionFrom != null) {
            return; // No context menus while creating transition
        }

        // Check for right-click
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            if (hoveredNodeId != -1) {
                contextNodeId = hoveredNodeId;
                contextMenuX = ImGui.getMousePosX();
                contextMenuY = ImGui.getMousePosY();
                ImGui.openPopup("node_context_menu");
            } else if (hoveredTransitionIndex >= 0) {
                contextMenuX = ImGui.getMousePosX();
                contextMenuY = ImGui.getMousePosY();
                ImGui.openPopup("transition_context_menu");
            } else if (editorHovered) {
                contextMenuX = ImGui.getMousePosX();
                contextMenuY = ImGui.getMousePosY();
                ImGui.openPopup("canvas_context_menu");
            }
        }

        // Node context menu
        if (ImGui.beginPopup("node_context_menu")) {
            String stateName = idManager.getStateName(contextNodeId);
            if (stateName != null) {
                ImGui.text("State: " + stateName);
                ImGui.separator();

                if (ImGui.menuItem(MaterialIcons.CallMade + " Make Transition")) {
                    pendingTransitionFrom = stateName;
                }

                ImGui.separator();

                if (ImGui.menuItem("Edit State...")) {
                    if (onEditState != null) {
                        onEditState.accept(stateName);
                    }
                }

                boolean isDefault = stateName.equals(controller.getDefaultState());
                if (!isDefault && ImGui.menuItem("Set as Default")) {
                    if (onCaptureUndo != null) {
                        onCaptureUndo.run();
                    }
                    if (onSetDefaultState != null) {
                        onSetDefaultState.accept(stateName);
                    }
                }

                ImGui.separator();

                boolean canDelete = controller.getStateCount() > 1;
                if (!canDelete) ImGui.beginDisabled();
                if (ImGui.menuItem("Delete State")) {
                    if (onCaptureUndo != null) {
                        onCaptureUndo.run();
                    }
                    if (onDeleteState != null) {
                        onDeleteState.accept(stateName);
                    }
                    idManager.removeState(stateName);
                    nodePositions.remove(stateName);
                }
                if (!canDelete) ImGui.endDisabled();
            }
            ImGui.endPopup();
        }

        // Transition context menu
        if (ImGui.beginPopup("transition_context_menu")) {
            if (hoveredTransitionIndex >= 0 && hoveredTransitionIndex < controller.getTransitionCount()) {
                AnimatorTransition trans = controller.getTransition(hoveredTransitionIndex);
                ImGui.text(trans.getFrom() + " " + MaterialIcons.ArrowForward + " " + trans.getTo());
                ImGui.separator();

                if (ImGui.menuItem("Edit Transition...")) {
                    if (onEditTransition != null) {
                        onEditTransition.accept(hoveredTransitionIndex);
                    }
                }

                ImGui.separator();

                if (ImGui.menuItem("Delete Transition")) {
                    if (onCaptureUndo != null) {
                        onCaptureUndo.run();
                    }
                    if (onDeleteTransition != null) {
                        onDeleteTransition.accept(hoveredTransitionIndex);
                    }
                    if (selectedTransitionIndex == hoveredTransitionIndex) {
                        selectedTransitionIndex = -1;
                    }
                }
            }
            ImGui.endPopup();
        }

        // Canvas context menu
        if (ImGui.beginPopup("canvas_context_menu")) {
            if (ImGui.menuItem(MaterialIcons.Add + " Add State...")) {
                if (onAddState != null) {
                    onAddState.run();
                }
            }

            ImGui.separator();

            if (ImGui.menuItem("Auto Layout")) {
                if (onAutoLayout != null) {
                    onAutoLayout.run();
                }
            }

            ImGui.endPopup();
        }
    }

    // ========================================================================
    // SELECTION
    // ========================================================================

    private void handleSelection(AnimatorController controller) {
        if (pendingTransitionFrom != null) {
            return; // Don't change selection while creating transition
        }

        // Check for selected nodes (ImNodes handles this)
        int numSelectedNodes = ImNodes.numSelectedNodes();
        if (numSelectedNodes > 0) {
            int[] selectedNodes = new int[numSelectedNodes];
            ImNodes.getSelectedNodes(selectedNodes);

            if (selectedNodes.length > 0) {
                String stateName = idManager.getStateName(selectedNodes[0]);
                if (stateName != null && !stateName.equals(selectedStateName)) {
                    selectedStateName = stateName;
                    selectedTransitionIndex = -1;
                    if (onStateSelected != null) {
                        onStateSelected.accept(stateName);
                    }
                }
            }
            return;
        }

        // Check for transition click
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && editorHovered) {
            if (hoveredTransitionIndex >= 0) {
                // Clicked on a transition
                if (hoveredTransitionIndex != selectedTransitionIndex) {
                    selectedTransitionIndex = hoveredTransitionIndex;
                    selectedStateName = null;
                    ImNodes.clearNodeSelection();
                    if (onTransitionSelected != null) {
                        onTransitionSelected.accept(selectedTransitionIndex);
                    }
                }
                return;
            }

            // Clicked on empty space - clear selection
            if (hoveredNodeId == -1) {
                if (selectedStateName != null || selectedTransitionIndex >= 0) {
                    selectedStateName = null;
                    selectedTransitionIndex = -1;
                    if (onSelectionCleared != null) {
                        onSelectionCleared.run();
                    }
                }
            }
        }
    }

    // ========================================================================
    // LAYOUT
    // ========================================================================

    private void applyInitialLayout(AnimatorController controller) {
        String defaultState = controller.getDefaultState();
        float startX = 100;
        float startY = 100;
        float xSpacing = 200;
        float ySpacing = 120;

        if (defaultState != null && controller.hasState(defaultState)) {
            int nodeId = (int) idManager.getNodeId(defaultState);
            ImNodes.setNodeEditorSpacePos(nodeId, startX, startY);
            nodePositions.put(defaultState, new float[]{startX, startY});
        }

        int col = 1;
        int row = 0;
        int maxRows = 3;
        for (int i = 0; i < controller.getStateCount(); i++) {
            AnimatorState state = controller.getState(i);
            if (state.getName().equals(defaultState)) continue;

            float x = startX + col * xSpacing;
            float y = startY + row * ySpacing;

            int nodeId = (int) idManager.getNodeId(state.getName());
            ImNodes.setNodeEditorSpacePos(nodeId, x, y);
            nodePositions.put(state.getName(), new float[]{x, y});

            row++;
            if (row >= maxRows) {
                row = 0;
                col++;
            }
        }
    }

    public void requestLayout() {
        needsInitialLayout = true;
    }

    /**
     * Stores current screen positions for use in next frame's transition rendering.
     */
    private void updateNodeScreenPositions(AnimatorController controller) {
        for (int i = 0; i < controller.getStateCount(); i++) {
            AnimatorState state = controller.getState(i);
            String stateName = state.getName();
            if (idManager.hasNodeId(stateName)) {
                int nodeId = (int) idManager.getNodeId(stateName);
                float screenX = ImNodes.getNodeScreenSpacePosX(nodeId);
                float screenY = ImNodes.getNodeScreenSpacePosY(nodeId);
                nodeScreenPositions.put(stateName, new float[]{screenX, screenY});
            }
        }
    }

    // ========================================================================
    // HIT TESTING
    // ========================================================================

    private int hitTestNodes(AnimatorController controller) {
        if (controller == null) return -1;

        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();

        for (int i = 0; i < controller.getStateCount(); i++) {
            AnimatorState state = controller.getState(i);
            String stateName = state.getName();

            if (!idManager.hasNodeId(stateName)) continue;

            int nodeId = (int) idManager.getNodeId(stateName);

            float nodeX = ImNodes.getNodeScreenSpacePosX(nodeId);
            float nodeY = ImNodes.getNodeScreenSpacePosY(nodeId);

            if (mouseX >= nodeX && mouseX <= nodeX + NODE_WIDTH &&
                mouseY >= nodeY && mouseY <= nodeY + NODE_HEIGHT) {
                return nodeId;
            }
        }

        return -1;
    }

    // ========================================================================
    // CALLBACKS
    // ========================================================================

    public void setOnStateSelected(Consumer<String> callback) {
        this.onStateSelected = callback;
    }

    public void setOnTransitionSelected(Consumer<Integer> callback) {
        this.onTransitionSelected = callback;
    }

    public void setOnSelectionCleared(Runnable callback) {
        this.onSelectionCleared = callback;
    }

    public void setOnAddState(Runnable callback) {
        this.onAddState = callback;
    }

    public void setOnEditState(Consumer<String> callback) {
        this.onEditState = callback;
    }

    public void setOnDeleteState(Consumer<String> callback) {
        this.onDeleteState = callback;
    }

    public void setOnSetDefaultState(Consumer<String> callback) {
        this.onSetDefaultState = callback;
    }

    public void setOnCreateTransition(Consumer<int[]> callback) {
        this.onCreateTransition = callback;
    }

    public void setOnEditTransition(Consumer<Integer> callback) {
        this.onEditTransition = callback;
    }

    public void setOnDeleteTransition(Consumer<Integer> callback) {
        this.onDeleteTransition = callback;
    }

    public void setOnAutoLayout(Runnable callback) {
        this.onAutoLayout = callback;
    }

    public void setOnCaptureUndo(Runnable callback) {
        this.onCaptureUndo = callback;
    }

    // ========================================================================
    // ACCESSORS
    // ========================================================================

    public String getSelectedStateName() {
        return selectedStateName;
    }

    public int getSelectedTransitionIndex() {
        return selectedTransitionIndex;
    }

    public void selectState(String stateName) {
        this.selectedStateName = stateName;
        this.selectedTransitionIndex = -1;
        if (stateName != null && idManager.hasNodeId(stateName)) {
            int nodeId = (int) idManager.getNodeId(stateName);
            ImNodes.clearNodeSelection();
            ImNodes.selectNode(nodeId);
        }
    }

    public void selectTransition(int index) {
        this.selectedTransitionIndex = index;
        this.selectedStateName = null;
        ImNodes.clearNodeSelection();
    }

    public void clearSelection() {
        this.selectedStateName = null;
        this.selectedTransitionIndex = -1;
        ImNodes.clearNodeSelection();
    }

    public boolean isCreatingTransition() {
        return pendingTransitionFrom != null;
    }

    public ImVec2 getNewNodePosition() {
        int count = nodePositions.size();
        return new ImVec2(100 + count * 50, 100 + count * 30);
    }

    public void setNodePosition(String stateName, float x, float y) {
        nodePositions.put(stateName, new float[]{x, y});
        if (idManager.hasNodeId(stateName)) {
            int nodeId = (int) idManager.getNodeId(stateName);
            ImNodes.setNodeEditorSpacePos(nodeId, x, y);
        }
    }

    public void renameState(String oldName, String newName) {
        float[] pos = nodePositions.remove(oldName);
        if (pos != null) {
            nodePositions.put(newName, pos);
        }
        idManager.renameState(oldName, newName);
    }

    public float[] getNodePosition(String stateName) {
        return nodePositions.get(stateName);
    }

    // ========================================================================
    // PREVIEW HIGHLIGHTING
    // ========================================================================

    /**
     * Sets the active state for preview highlighting (pulsing effect).
     * @param stateName The state currently playing, or null to clear
     */
    public void setActiveState(String stateName) {
        this.activeStateName = stateName;
    }

    /**
     * Sets the pending transition target for preview highlighting.
     * @param targetStateName The state we're transitioning to, or null to clear
     */
    public void setPendingTransitionTarget(String targetStateName) {
        this.pendingTransitionTarget = targetStateName;
    }

    /**
     * Clears all preview highlighting.
     */
    public void clearPreviewHighlighting() {
        this.activeStateName = null;
        this.pendingTransitionTarget = null;
    }
}
