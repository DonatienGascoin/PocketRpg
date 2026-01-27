# ImNodes/Node Editor Integration

## Overview

The project already has access to two node graph libraries through imgui-java (version 1.90.0):

1. **ImNodes** - Simple, lightweight node editor
2. **imgui-node-editor** - More feature-rich, polished node editor

Both are included in the existing `imgui-java-binding` dependency - no additional dependencies needed.

---

## Part 1: Library Comparison

| Feature | ImNodes | imgui-node-editor |
|---------|---------|-------------------|
| **Complexity** | Simple | More complex |
| **Visual Style** | Basic, functional | Polished, Blueprint-like |
| **Built-in Minimap** | Yes | No |
| **Link Styling** | Basic | Bezier curves, colors |
| **Context Menus** | Manual handling | Built-in helpers |
| **Node Selection** | Manual | Built-in multi-select |
| **Zoom/Pan** | Manual | Built-in |
| **Learning Curve** | Low | Medium |

**Recommendation:** Start with **imgui-node-editor** for the Animator Editor due to its polished appearance and better built-in features for a production editor.

---

## Part 2: ImNodes Example (Simpler Option)

### Basic Structure

```java
import imgui.extension.imnodes.ImNodes;
import imgui.extension.imnodes.ImNodesContext;
import imgui.extension.imnodes.ImNodesEditorContext;
import imgui.extension.imnodes.flag.ImNodesPinShape;
import imgui.extension.imnodes.flag.ImNodesMiniMapLocation;

public class AnimatorGraphImNodes {

    private ImNodesEditorContext editorContext;
    private final ImInt linkStartAttr = new ImInt();
    private final ImInt linkEndAttr = new ImInt();

    // ID generation
    private int nextNodeId = 1;
    private int nextPinId = 1000;
    private int nextLinkId = 5000;

    public void initialize() {
        ImNodes.createContext();
        editorContext = ImNodes.editorContextCreate();
    }

    public void render(AnimatorController controller) {
        ImNodes.editorContextSet(editorContext);
        ImNodes.beginNodeEditor();

        // Render all state nodes
        for (AnimatorState state : controller.getStates()) {
            renderStateNode(state, controller);
        }

        // Render all transition links
        int linkId = nextLinkId;
        for (AnimatorTransition trans : controller.getTransitions()) {
            int outputPin = getOutputPinId(trans.getFrom());
            int inputPin = getInputPinId(trans.getTo());
            if (outputPin != -1 && inputPin != -1) {
                ImNodes.link(linkId++, outputPin, inputPin);
            }
        }

        // Minimap in corner
        ImNodes.miniMap(0.2f, ImNodesMiniMapLocation.BottomRight);

        ImNodes.endNodeEditor();

        // Handle new link creation
        if (ImNodes.isLinkCreated(linkStartAttr, linkEndAttr)) {
            handleLinkCreated(controller, linkStartAttr.get(), linkEndAttr.get());
        }

        // Handle context menus
        handleContextMenus(controller);
    }

    private void renderStateNode(AnimatorState state, AnimatorController controller) {
        int nodeId = getNodeId(state.getName());
        NodeLayout layout = getLayout(state.getName());

        ImNodes.beginNode(nodeId);

        // Title bar
        ImNodes.beginNodeTitleBar();
        if (state.getName().equals(controller.getDefaultState())) {
            ImGui.text("* " + state.getName());  // Mark default
        } else {
            ImGui.text(state.getName());
        }
        ImNodes.endNodeTitleBar();

        // Input pin (for incoming transitions)
        ImNodes.beginInputAttribute(getInputPinId(state.getName()), ImNodesPinShape.CircleFilled);
        ImGui.text("In");
        ImNodes.endInputAttribute();

        // State type indicator
        ImGui.text(state.getType().name().toLowerCase());

        // Output pin (for outgoing transitions)
        ImNodes.beginOutputAttribute(getOutputPinId(state.getName()));
        ImGui.text("Out");
        ImNodes.endOutputAttribute();

        ImNodes.endNode();

        // Set position if layout exists
        if (layout != null && !layout.positionSet) {
            ImNodes.setNodeScreenSpacePos(nodeId, layout.x, layout.y);
            layout.positionSet = true;
        }
    }

    private void handleLinkCreated(AnimatorController controller, int startAttr, int endAttr) {
        String fromState = findStateByOutputPin(startAttr);
        String toState = findStateByInputPin(endAttr);

        if (fromState != null && toState != null) {
            // Open dialog to configure transition
            openNewTransitionDialog(fromState, toState);
        }
    }

    private void handleContextMenus(AnimatorController controller) {
        boolean isEditorHovered = ImNodes.isEditorHovered();

        if (ImGui.isMouseClicked(ImGuiMouseButton.Right)) {
            int hoveredNode = ImNodes.getHoveredNode();
            if (hoveredNode != -1) {
                ImGui.openPopup("node_context");
                selectedNodeId = hoveredNode;
            } else if (isEditorHovered) {
                ImGui.openPopup("canvas_context");
            }
        }

        // Node context menu
        if (ImGui.beginPopup("node_context")) {
            String nodeName = findStateByNodeId(selectedNodeId);
            if (ImGui.menuItem("Edit State...")) {
                openStateEditor(nodeName);
            }
            if (ImGui.menuItem("Set as Default")) {
                controller.setDefaultState(nodeName);
            }
            ImGui.separator();
            if (ImGui.menuItem("Delete State")) {
                controller.removeState(nodeName);
            }
            ImGui.endPopup();
        }

        // Canvas context menu (right-click on empty space)
        if (ImGui.beginPopup("canvas_context")) {
            if (ImGui.menuItem("Add State")) {
                openNewStateDialog();
            }
            if (ImGui.menuItem("Add Any State")) {
                // Add wildcard state node
            }
            ImGui.separator();
            if (ImGui.menuItem("Auto Layout")) {
                performAutoLayout();
            }
            ImGui.endPopup();
        }
    }

    public void destroy() {
        ImNodes.editorContextFree(editorContext);
        ImNodes.destroyContext();
    }
}
```

---

## Part 3: imgui-node-editor Example (Recommended)

### Basic Structure

```java
import imgui.ImVec2;
import imgui.extension.nodeditor.NodeEditor;
import imgui.extension.nodeditor.NodeEditorConfig;
import imgui.extension.nodeditor.NodeEditorContext;
import imgui.extension.nodeditor.NodeEditorStyle;
import imgui.extension.nodeditor.flag.NodeEditorPinKind;
import imgui.extension.nodeditor.flag.NodeEditorStyleColor;
import imgui.extension.nodeditor.flag.NodeEditorStyleVar;

public class AnimatorGraphNodeEditor {

    private NodeEditorContext context;

    // ID management
    private final Map<String, Long> stateToNodeId = new HashMap<>();
    private final Map<String, Long> stateToInputPin = new HashMap<>();
    private final Map<String, Long> stateToOutputPin = new HashMap<>();
    private final Map<Long, Integer> linkIdToTransitionIndex = new HashMap<>();
    private long nextId = 1;

    public void initialize() {
        NodeEditorConfig config = new NodeEditorConfig();
        config.setSettingsFile(null);  // Don't persist layout to file automatically
        context = NodeEditor.createEditor(config);
    }

    public void render(AnimatorController controller, AnimatorLayoutData layout) {
        NodeEditor.setCurrentEditor(context);

        // Apply custom styling
        applyAnimatorStyle();

        NodeEditor.begin("AnimatorGraph");

        // Render state nodes
        for (AnimatorState state : controller.getStates()) {
            renderStateNode(state, controller, layout);
        }

        // Render transition links
        renderTransitionLinks(controller);

        // Handle link creation (drag from pin to pin)
        handleLinkCreation(controller);

        // Handle link deletion
        handleLinkDeletion(controller);

        // Context menus (must be between suspend/resume)
        NodeEditor.suspend();
        handleContextMenus(controller, layout);
        NodeEditor.resume();

        NodeEditor.end();
    }

    private void applyAnimatorStyle() {
        NodeEditorStyle style = NodeEditor.getStyle();

        // Node colors
        style.setColor(NodeEditorStyleColor.NodeBg, 0.1f, 0.1f, 0.1f, 0.9f);
        style.setColor(NodeEditorStyleColor.NodeBorder, 0.3f, 0.3f, 0.3f, 1.0f);
        style.setColor(NodeEditorStyleColor.PinRect, 0.2f, 0.5f, 0.2f, 1.0f);
        style.setColor(NodeEditorStyleColor.PinRectBorder, 0.3f, 0.7f, 0.3f, 1.0f);

        // Link colors
        style.setColor(NodeEditorStyleColor.LinkSelRect, 0.2f, 0.4f, 0.8f, 1.0f);
        style.setColor(NodeEditorStyleColor.LinkSelRectBorder, 0.3f, 0.5f, 0.9f, 1.0f);

        // Rounding
        style.setVar(NodeEditorStyleVar.NodeRounding, 8.0f);
        style.setVar(NodeEditorStyleVar.NodePadding, 8.0f, 8.0f, 8.0f, 8.0f);
        style.setVar(NodeEditorStyleVar.PinRounding, 4.0f);
    }

    private void renderStateNode(AnimatorState state, AnimatorController controller,
                                  AnimatorLayoutData layout) {
        long nodeId = getOrCreateNodeId(state.getName());
        boolean isDefault = state.getName().equals(controller.getDefaultState());

        // Apply node position from layout
        NodeLayout nodeLayout = layout.getNodeLayout(state.getName());
        if (nodeLayout != null && !nodeLayout.isPositionApplied()) {
            NodeEditor.setNodePosition(nodeId, new ImVec2(nodeLayout.getX(), nodeLayout.getY()));
            nodeLayout.setPositionApplied(true);
        }

        // Begin node with custom color for default state
        if (isDefault) {
            NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg, 0.1f, 0.2f, 0.1f, 0.9f);
            NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBorder, 0.3f, 0.6f, 0.3f, 1.0f);
        }

        NodeEditor.beginNode(nodeId);

        // Header
        ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f);
        if (isDefault) {
            ImGui.text(MaterialIcons.Star + " " + state.getName());
        } else {
            ImGui.text(state.getName());
        }
        ImGui.popStyleColor();

        ImGui.spacing();

        // Input pin (left side)
        long inputPinId = getOrCreateInputPin(state.getName());
        NodeEditor.beginPin(inputPinId, NodeEditorPinKind.Input);
        ImGui.text(MaterialIcons.ArrowForward);
        NodeEditor.endPin();

        ImGui.sameLine();

        // State type in center
        ImGui.textDisabled(state.getType().name().toLowerCase());

        ImGui.sameLine();

        // Output pin (right side)
        long outputPinId = getOrCreateOutputPin(state.getName());
        NodeEditor.beginPin(outputPinId, NodeEditorPinKind.Output);
        ImGui.text(MaterialIcons.ArrowForward);
        NodeEditor.endPin();

        // Animation preview (if simple state)
        if (state.getType() == StateType.SIMPLE && state.getAnimation() != null) {
            ImGui.spacing();
            ImGui.textDisabled(truncatePath(state.getAnimation(), 20));
        }

        // Directional indicator (if directional state)
        if (state.getType() == StateType.DIRECTIONAL) {
            ImGui.spacing();
            int count = countSetAnimations(state);
            ImGui.textDisabled(count + "/4 directions");
        }

        NodeEditor.endNode();

        if (isDefault) {
            NodeEditor.popStyleColor(2);
        }

        // Save position back to layout
        ImVec2 pos = NodeEditor.getNodePosition(nodeId);
        if (nodeLayout != null) {
            nodeLayout.setX(pos.x);
            nodeLayout.setY(pos.y);
        }
    }

    private void renderTransitionLinks(AnimatorController controller) {
        List<AnimatorTransition> transitions = controller.getTransitions();

        for (int i = 0; i < transitions.size(); i++) {
            AnimatorTransition trans = transitions.get(i);

            long outputPin = getOutputPin(trans.getFrom());
            long inputPin = getInputPin(trans.getTo());

            if (outputPin == -1 || inputPin == -1) continue;

            long linkId = nextId++;
            linkIdToTransitionIndex.put(linkId, i);

            // Color based on transition type
            ImVec4 color = getTransitionColor(trans.getType());
            NodeEditor.link(linkId, outputPin, inputPin, color, 2.0f);
        }
    }

    private ImVec4 getTransitionColor(TransitionType type) {
        return switch (type) {
            case INSTANT -> new ImVec4(0.4f, 0.7f, 0.4f, 1.0f);           // Green
            case WAIT_FOR_COMPLETION -> new ImVec4(0.7f, 0.5f, 0.2f, 1.0f); // Orange
            case WAIT_FOR_LOOP -> new ImVec4(0.4f, 0.5f, 0.8f, 1.0f);      // Blue
        };
    }

    private void handleLinkCreation(AnimatorController controller) {
        if (NodeEditor.beginCreate()) {
            ImLong startPinId = new ImLong();
            ImLong endPinId = new ImLong();

            if (NodeEditor.queryNewLink(startPinId, endPinId)) {
                String fromState = findStateByOutputPin(startPinId.get());
                String toState = findStateByInputPin(endPinId.get());

                // Validate: can't link to self, can't duplicate
                boolean valid = fromState != null && toState != null
                    && !fromState.equals(toState)
                    && !transitionExists(controller, fromState, toState);

                if (valid) {
                    // Show green indicator
                    if (NodeEditor.acceptNewItem(0.3f, 0.8f, 0.3f, 1.0f)) {
                        // Create transition with default settings
                        AnimatorTransition newTrans = new AnimatorTransition(
                            fromState, toState, TransitionType.INSTANT, List.of()
                        );
                        controller.addTransition(newTrans);

                        // Open editor for the new transition
                        openTransitionEditor(controller.getTransitions().size() - 1);
                    }
                } else {
                    // Show red indicator for invalid link
                    NodeEditor.rejectNewItem(0.8f, 0.3f, 0.3f, 1.0f);
                }
            }

            NodeEditor.endCreate();
        }
    }

    private void handleLinkDeletion(AnimatorController controller) {
        if (NodeEditor.beginDelete()) {
            ImLong linkId = new ImLong();
            while (NodeEditor.queryDeletedLink(linkId)) {
                if (NodeEditor.acceptDeletedItem()) {
                    Integer transIndex = linkIdToTransitionIndex.get(linkId.get());
                    if (transIndex != null) {
                        controller.removeTransition(transIndex);
                    }
                }
            }

            ImLong nodeId = new ImLong();
            while (NodeEditor.queryDeletedNode(nodeId)) {
                String stateName = findStateByNodeId(nodeId.get());
                if (stateName != null && NodeEditor.acceptDeletedItem()) {
                    controller.removeState(stateName);
                }
            }

            NodeEditor.endDelete();
        }
    }

    private void handleContextMenus(AnimatorController controller, AnimatorLayoutData layout) {
        // Node context menu
        long nodeWithContextMenu = NodeEditor.getNodeWithContextMenu();
        if (nodeWithContextMenu != -1) {
            ImGui.openPopup("node_context");
            contextNodeId = nodeWithContextMenu;
        }

        if (ImGui.beginPopup("node_context")) {
            String stateName = findStateByNodeId(contextNodeId);
            if (stateName != null) {
                ImGui.text("State: " + stateName);
                ImGui.separator();

                if (ImGui.menuItem("Edit State...")) {
                    openStateEditor(stateName);
                }
                if (ImGui.menuItem("Set as Default")) {
                    controller.setDefaultState(stateName);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete State")) {
                    controller.removeState(stateName);
                    clearNodeIds(stateName);
                }
            }
            ImGui.endPopup();
        }

        // Link context menu
        long linkWithContextMenu = NodeEditor.getLinkWithContextMenu();
        if (linkWithContextMenu != -1) {
            ImGui.openPopup("link_context");
            contextLinkId = linkWithContextMenu;
        }

        if (ImGui.beginPopup("link_context")) {
            Integer transIndex = linkIdToTransitionIndex.get(contextLinkId);
            if (transIndex != null) {
                AnimatorTransition trans = controller.getTransition(transIndex);
                ImGui.text(trans.getFrom() + " -> " + trans.getTo());
                ImGui.separator();

                if (ImGui.menuItem("Edit Transition...")) {
                    openTransitionEditor(transIndex);
                }
                ImGui.separator();
                if (ImGui.menuItem("Delete Transition")) {
                    controller.removeTransition(transIndex);
                }
            }
            ImGui.endPopup();
        }

        // Background context menu
        if (NodeEditor.showBackgroundContextMenu()) {
            ImGui.openPopup("canvas_context");
        }

        if (ImGui.beginPopup("canvas_context")) {
            if (ImGui.menuItem("Add State...")) {
                ImVec2 canvasPos = NodeEditor.screenToCanvas(ImGui.getMousePos());
                openNewStateDialog(canvasPos.x, canvasPos.y);
            }
            if (ImGui.menuItem("Add Any State")) {
                ImVec2 canvasPos = NodeEditor.screenToCanvas(ImGui.getMousePos());
                addAnyStateNode(canvasPos.x, canvasPos.y);
            }
            ImGui.separator();
            if (ImGui.menuItem("Auto Layout")) {
                performAutoLayout(controller, layout);
            }
            if (ImGui.menuItem("Navigate to Content")) {
                NodeEditor.navigateToContent(1.0f);
            }
            ImGui.endPopup();
        }
    }

    public void destroy() {
        NodeEditor.destroyEditor(context);
    }
}
```

---

## Part 4: Integration with AnimatorEditorPanel

### Panel Layout with Graph View

```java
public class AnimatorEditorPanel {

    private AnimatorGraphNodeEditor graphEditor;
    private boolean showGraphView = true;  // Toggle between graph and list view

    public void initialize() {
        // ... existing initialization ...
        graphEditor = new AnimatorGraphNodeEditor();
        graphEditor.initialize();
    }

    public void render() {
        // ... toolbar rendering ...

        // View mode toggle in toolbar
        ImGui.sameLine();
        if (ImGui.button(showGraphView ? MaterialIcons.ViewList : MaterialIcons.AccountTree)) {
            showGraphView = !showGraphView;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(showGraphView ? "Switch to List View" : "Switch to Graph View");
        }

        ImGui.separator();

        if (editingController == null) {
            ImGui.textDisabled("No animator selected");
            return;
        }

        if (showGraphView) {
            renderGraphView();
        } else {
            renderListView();  // Original list-based UI
        }
    }

    private void renderGraphView() {
        float availHeight = ImGui.getContentRegionAvailY();

        // Main graph area
        ImGui.beginChild("GraphArea", 0, availHeight - 120, false);
        graphEditor.render(editingController, layoutData);
        ImGui.endChild();

        // Bottom panel: Selected item inspector
        ImGui.separator();
        ImGui.beginChild("InspectorArea", 0, 100, false);
        renderSelectedItemInspector();
        ImGui.endChild();

        // Parameters bar
        ImGui.separator();
        renderParametersBar();
    }

    private void renderSelectedItemInspector() {
        if (selectedStateIndex >= 0) {
            // Compact state editor
            AnimatorState state = editingController.getState(selectedStateIndex);
            ImGui.text("State: " + state.getName());
            ImGui.sameLine();
            ImGui.textDisabled("Type: " + state.getType());
            ImGui.sameLine();
            if (ImGui.button("Edit...")) {
                openStateEditorDialog(selectedStateIndex);
            }
        } else if (selectedTransitionIndex >= 0) {
            // Compact transition editor
            AnimatorTransition trans = editingController.getTransition(selectedTransitionIndex);
            ImGui.text(trans.getFrom() + " -> " + trans.getTo());
            ImGui.sameLine();
            ImGui.textDisabled("Type: " + trans.getType());
            ImGui.sameLine();
            if (ImGui.button("Edit...")) {
                openTransitionEditorDialog(selectedTransitionIndex);
            }
        } else {
            ImGui.textDisabled("Select a state or transition to edit");
        }
    }

    private void renderParametersBar() {
        ImGui.text("Parameters:");
        ImGui.sameLine();

        for (AnimatorParameter param : editingController.getParameters()) {
            ImGui.sameLine();
            ImGui.text(param.getName() + ":");
            ImGui.sameLine();
            ImGui.textDisabled(param.getType().name().toLowerCase());
            ImGui.sameLine();
            ImGui.text("|");
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 80);
        if (ImGui.button(MaterialIcons.Add + " Add")) {
            showAddParameterDialog = true;
        }
    }

    public void destroy() {
        graphEditor.destroy();
    }
}
```

---

## Part 5: ID Management

Both libraries use integer/long IDs for nodes, pins, and links. Here's a robust ID management system:

```java
public class AnimatorIdManager {

    // Ranges to avoid collision
    private static final long NODE_ID_BASE = 1_000_000L;
    private static final long INPUT_PIN_BASE = 2_000_000L;
    private static final long OUTPUT_PIN_BASE = 3_000_000L;
    private static final long LINK_ID_BASE = 4_000_000L;
    private static final long ANY_STATE_NODE_ID = 999_999L;

    private final Map<String, Long> stateNameToNodeId = new HashMap<>();
    private final Map<Long, String> nodeIdToStateName = new HashMap<>();
    private long nextNodeId = NODE_ID_BASE;
    private long nextLinkId = LINK_ID_BASE;

    public long getNodeId(String stateName) {
        if ("*".equals(stateName)) {
            return ANY_STATE_NODE_ID;
        }
        return stateNameToNodeId.computeIfAbsent(stateName, name -> {
            long id = nextNodeId++;
            nodeIdToStateName.put(id, name);
            return id;
        });
    }

    public long getInputPinId(String stateName) {
        return getNodeId(stateName) - NODE_ID_BASE + INPUT_PIN_BASE;
    }

    public long getOutputPinId(String stateName) {
        return getNodeId(stateName) - NODE_ID_BASE + OUTPUT_PIN_BASE;
    }

    public long getNextLinkId() {
        return nextLinkId++;
    }

    public String getStateName(long nodeId) {
        if (nodeId == ANY_STATE_NODE_ID) {
            return "*";
        }
        return nodeIdToStateName.get(nodeId);
    }

    public String getStateNameFromInputPin(long pinId) {
        long nodeId = pinId - INPUT_PIN_BASE + NODE_ID_BASE;
        return getStateName(nodeId);
    }

    public String getStateNameFromOutputPin(long pinId) {
        long nodeId = pinId - OUTPUT_PIN_BASE + NODE_ID_BASE;
        return getStateName(nodeId);
    }

    public void removeState(String stateName) {
        Long nodeId = stateNameToNodeId.remove(stateName);
        if (nodeId != null) {
            nodeIdToStateName.remove(nodeId);
        }
    }

    public void clear() {
        stateNameToNodeId.clear();
        nodeIdToStateName.clear();
        nextNodeId = NODE_ID_BASE;
        nextLinkId = LINK_ID_BASE;
    }
}
```

---

## Part 6: Play Mode Visualization

### Highlighting Active State

```java
private void renderStateNode(AnimatorState state, AnimatorController controller,
                              AnimatorLayoutData layout, String activeState) {
    long nodeId = idManager.getNodeId(state.getName());
    boolean isDefault = state.getName().equals(controller.getDefaultState());
    boolean isActive = state.getName().equals(activeState);

    // Push style for active state (green pulsing)
    if (isActive) {
        float pulse = (float)(0.5f + 0.5f * Math.sin(ImGui.getTime() * 4.0));
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBg,
            0.1f + pulse * 0.1f, 0.3f + pulse * 0.2f, 0.1f + pulse * 0.1f, 0.95f);
        NodeEditor.pushStyleColor(NodeEditorStyleColor.NodeBorder,
            0.3f, 0.8f, 0.3f, 1.0f);
    }

    // ... render node ...

    if (isActive) {
        NodeEditor.popStyleColor(2);
    }
}
```

### Live Parameter Display

```java
private void renderPlayModeOverlay(AnimatorComponent animator) {
    ImGui.setNextWindowPos(ImGui.getWindowPosX() + 10,
                           ImGui.getWindowPosY() + ImGui.getWindowHeight() - 60);
    ImGui.setNextWindowBgAlpha(0.8f);

    if (ImGui.begin("##PlayModeParams", ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize)) {

        ImGui.text("PLAY MODE");
        ImGui.sameLine();
        ImGui.textDisabled("Current: " + animator.getCurrentState());

        for (AnimatorParameter param : editingController.getParameters()) {
            ImGui.sameLine();
            ImGui.text("|");
            ImGui.sameLine();

            Object value = animator.getParameterValue(param.getName());
            if (param.getType() == ParameterType.BOOL) {
                boolean bVal = (Boolean) value;
                if (bVal) {
                    ImGui.textColored(0.3f, 0.8f, 0.3f, 1.0f,
                        param.getName() + ": true");
                } else {
                    ImGui.textColored(0.5f, 0.5f, 0.5f, 1.0f,
                        param.getName() + ": false");
                }
            } else if (param.getType() == ParameterType.DIRECTION) {
                ImGui.text(param.getName() + ": " + value);
            }
        }
    }
    ImGui.end();
}
```

---

## Part 7: Updated File List

| File | Action | Description |
|------|--------|-------------|
| `editor/panels/animator/AnimatorGraphNodeEditor.java` | **NEW** | Node editor using imgui-node-editor |
| `editor/panels/animator/AnimatorIdManager.java` | **NEW** | ID management for nodes/pins/links |
| `editor/panels/AnimatorEditorPanel.java` | Modify | Integrate graph view |
| `animation/AnimatorLayoutData.java` | **NEW** | Node positions persistence |

---

## Part 8: Revised Implementation Phases

Using imgui-node-editor significantly reduces development time:

| Phase | Task | Time |
|-------|------|------|
| 1 | Basic graph with nodes and links | 2-3 days |
| 2 | Link creation (drag between pins) | 1 day |
| 3 | Context menus and deletion | 1 day |
| 4 | Node styling (colors, icons) | 1 day |
| 5 | Layout persistence | 1 day |
| 6 | Auto-layout algorithm | 2 days |
| 7 | Play mode integration | 1 day |
| **Total** | | **~2 weeks** |

**Time saved vs custom implementation: ~3 weeks**

---

## Part 9: Caveats and Considerations

### Pros of Using imgui-node-editor

1. **Already included** - No new dependencies
2. **Mature library** - Battle-tested, fewer bugs
3. **Built-in features** - Pan, zoom, selection, deletion
4. **Good appearance** - Professional look out of the box
5. **Active maintenance** - Regular updates via imgui-java

### Cons / Limitations

1. **Less control** - Can't customize every visual detail
2. **Learning curve** - Need to understand the library's patterns
3. **ID management** - Must carefully manage node/pin/link IDs
4. **State sync** - Must sync library state with AnimatorController

### Recommendations

1. **Start simple** - Get basic nodes and links working first
2. **Use long IDs** - imgui-node-editor uses `long`, ImNodes uses `int`
3. **Suspend/Resume** - Always use suspend/resume for popups
4. **Test thoroughly** - Edge cases with link creation/deletion
