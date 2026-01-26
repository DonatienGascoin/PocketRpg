# Animator Controller Editor Support

## Overview

This document details the editor integration for AnimatorController assets, including:
- Creating new animator controllers
- Editing states, transitions, and parameters
- Deleting animator controllers
- Asset browser integration
- Future visual state machine editor

---

## Part 1: Editor Infrastructure

### EditorPanelType Registration

Add `ANIMATOR_EDITOR` to the enum:

```java
// EditorPanelType.java
@Getter
public enum EditorPanelType {
    ANIMATION_EDITOR("Animation Editor"),
    SPRITE_EDITOR("Sprite Editor"),
    ANIMATOR_EDITOR("Animator Editor");  // NEW

    private final String windowName;
    // ...
}
```

### AnimatorControllerLoader Editor Methods

The loader must implement editor integration methods:

```java
public class AnimatorControllerLoader implements AssetLoader<AnimatorController> {

    @Override
    public String getIconCodepoint() {
        return MaterialIcons.AccountTree;  // Node-graph style icon
    }

    @Override
    public EditorPanelType getEditorPanelType() {
        return EditorPanelType.ANIMATOR_EDITOR;
    }

    @Override
    public boolean canInstantiate() {
        return false;  // Animator controllers aren't dropped into scenes
    }
}
```

### Asset Browser Integration

The Asset Browser will automatically:
1. Show `.animator.json` files with the AccountTree icon
2. Double-click opens the Animator Editor panel
3. Context menu shows "Open in Animator Editor"

---

## Part 2: AnimatorEditorPanel

### Panel Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Toolbar: [New] [Delete] [Save] [Refresh] | [Undo] [Redo] | [Dropdown]   │
├────────────────┬────────────────────────────────────────────────────────┤
│                │                                                         │
│   Parameters   │              State Graph / State List                   │
│                │                                                         │
│   [+ Add]      │   ┌─────────┐     ┌─────────┐     ┌─────────┐         │
│   - isMoving   │   │  idle   │────▶│  walk   │────▶│ attack  │         │
│   - direction  │   └─────────┘     └─────────┘     └─────────┘         │
│                │                                                         │
├────────────────┼────────────────────────────────────────────────────────┤
│                │                                                         │
│   States       │              Selected State/Transition                  │
│                │                                                         │
│   [+ Add]      │   State: "walk"                                        │
│   - idle       │   Type: [Directional ▼]                                │
│   - walk  ◀──  │   Animations:                                          │
│   - attack     │     UP:    [animations/walk_up.anim    ] [Browse]      │
│                │     DOWN:  [animations/walk_down.anim  ] [Browse]      │
│                │     LEFT:  [animations/walk_left.anim  ] [Browse]      │
│                │     RIGHT: [animations/walk_right.anim ] [Browse]      │
│                │                                                         │
├────────────────┴────────────────────────────────────────────────────────┤
│                           Transitions                                    │
│ ┌───────────┬───────────┬──────────────────┬──────────────────────────┐ │
│ │   From    │    To     │      Type        │       Conditions         │ │
│ ├───────────┼───────────┼──────────────────┼──────────────────────────┤ │
│ │   idle    │   walk    │     INSTANT      │ isMoving == true         │ │
│ │   walk    │   idle    │     INSTANT      │ isMoving == false        │ │
│ │     *     │  attack   │     INSTANT      │ (manual trigger)         │ │
│ │  attack   │     *     │ WAIT_COMPLETION  │ (auto on finish)         │ │
│ └───────────┴───────────┴──────────────────┴──────────────────────────┘ │
│                                                     [+ Add Transition]   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Class Structure

```java
public class AnimatorEditorPanel {

    // ========================================================================
    // STATE
    // ========================================================================

    // Asset list
    private final List<AnimatorEntry> animators = new ArrayList<>();
    private AnimatorEntry selectedEntry = null;
    private AnimatorController editingController = null;
    private boolean hasUnsavedChanges = false;

    // Selection state
    private int selectedStateIndex = -1;
    private int selectedTransitionIndex = -1;
    private int selectedParameterIndex = -1;

    // Dialogs
    private boolean showNewDialog = false;
    private boolean showDeleteConfirmDialog = false;
    private boolean showUnsavedChangesDialog = false;
    private boolean showAddStateDialog = false;
    private boolean showAddTransitionDialog = false;
    private boolean showAddParameterDialog = false;

    // Undo/Redo
    private final Deque<AnimatorState> undoStack = new ArrayDeque<>();
    private final Deque<AnimatorState> redoStack = new ArrayDeque<>();

    // Asset picker
    private final AssetPickerPopup animationPicker = new AssetPickerPopup();

    // ========================================================================
    // ENTRY CLASS
    // ========================================================================

    private static class AnimatorEntry {
        String path;
        String filename;
        AnimatorController controller;
        boolean modified = false;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public void initialize() { ... }
    public void refresh() { ... }
    public void render() { ... }
    public void destroy() { ... }
}
```

---

## Part 3: Creating Animator Controllers

### New Animator Dialog

```
┌─────────────────────────────────────────────┐
│              New Animator Controller         │
├─────────────────────────────────────────────┤
│                                             │
│  Name: [player_animator          ]          │
│                                             │
│  Initial State: [idle            ]          │
│                                             │
│  ☑ Create default idle state                │
│  ☐ Create directional walk state            │
│                                             │
├─────────────────────────────────────────────┤
│              [Create]    [Cancel]           │
└─────────────────────────────────────────────┘
```

### Create Flow

```java
private void createNewAnimator(String name, String initialState, boolean createIdle, boolean createWalk) {
    // 1. Sanitize name
    String baseName = name.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    String filename = baseName + ".animator.json";
    String path = "animators/" + filename;
    Path filePath = Paths.get(Assets.getAssetRoot(), path);

    // 2. Ensure unique filename
    int counter = 1;
    while (Files.exists(filePath)) {
        filename = baseName + "_" + counter + ".animator.json";
        path = "animators/" + filename;
        filePath = Paths.get(Assets.getAssetRoot(), path);
        counter++;
    }

    // 3. Create directories
    Files.createDirectories(filePath.getParent());

    // 4. Build AnimatorController
    AnimatorController controller = new AnimatorController();
    controller.setName(baseName);
    controller.setDefaultState(initialState);

    // Add default parameters
    controller.addParameter(new AnimatorParameter("isMoving", ParameterType.BOOL, false));
    controller.addParameter(new AnimatorParameter("direction", ParameterType.DIRECTION, Direction.DOWN));

    // Create idle state if requested
    if (createIdle) {
        AnimatorState idleState = new AnimatorState("idle", StateType.SIMPLE);
        idleState.setAnimation("animations/idle.anim");  // Placeholder
        controller.addState(idleState);
    }

    // Create walk state if requested (directional)
    if (createWalk) {
        AnimatorState walkState = new AnimatorState("walk", StateType.DIRECTIONAL);
        walkState.setDirectionalAnimation(Direction.UP, "animations/walk_up.anim");
        walkState.setDirectionalAnimation(Direction.DOWN, "animations/walk_down.anim");
        walkState.setDirectionalAnimation(Direction.LEFT, "animations/walk_left.anim");
        walkState.setDirectionalAnimation(Direction.RIGHT, "animations/walk_right.anim");
        controller.addState(walkState);

        // Add default transitions
        if (createIdle) {
            controller.addTransition(new AnimatorTransition(
                "idle", "walk", TransitionType.INSTANT,
                List.of(new TransitionCondition("isMoving", true))
            ));
            controller.addTransition(new AnimatorTransition(
                "walk", "idle", TransitionType.INSTANT,
                List.of(new TransitionCondition("isMoving", false))
            ));
        }
    }

    // 5. Save
    AnimatorControllerLoader loader = new AnimatorControllerLoader();
    loader.save(controller, filePath.toString());

    // 6. Refresh and select
    refresh();
    selectAnimatorByPath(path);
    showStatus("Created animator: " + filename);
}
```

### File Location

New animator controllers are created in:
```
gameData/assets/animators/<name>.animator.json
```

---

## Part 4: Editing Animator Controllers

### Editing Parameters

```
┌─────────────────────────────────────────────┐
│ Parameters                           [+ Add] │
├─────────────────────────────────────────────┤
│ ▸ isMoving     bool      default: false     │
│ ▸ direction    direction default: DOWN   [x]│
│ ▸ isSliding    bool      default: false  [x]│
└─────────────────────────────────────────────┘
```

**Add Parameter Dialog:**
```
┌─────────────────────────────────────┐
│          Add Parameter              │
├─────────────────────────────────────┤
│ Name: [newParam          ]          │
│ Type: [bool ▼]                      │
│       • bool                        │
│       • direction                   │
│       • int (future)                │
│       • float (future)              │
│ Default: [false ▼]                  │
├─────────────────────────────────────┤
│         [Add]    [Cancel]           │
└─────────────────────────────────────┘
```

**Implementation:**
```java
private void renderParametersSection() {
    ImGui.text("Parameters");
    ImGui.sameLine(ImGui.getContentRegionAvailX() - 50);
    if (ImGui.button(MaterialIcons.Add + "##AddParam")) {
        showAddParameterDialog = true;
    }
    ImGui.separator();

    if (editingController == null) {
        ImGui.textDisabled("No animator selected");
        return;
    }

    List<AnimatorParameter> params = editingController.getParameters();
    for (int i = 0; i < params.size(); i++) {
        AnimatorParameter param = params.get(i);
        boolean isSelected = selectedParameterIndex == i;

        ImGui.pushID(i);

        // Selectable row
        if (ImGui.selectable(param.getName(), isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
            selectedParameterIndex = i;
        }

        ImGui.sameLine(100);
        ImGui.textDisabled(param.getType().name().toLowerCase());

        ImGui.sameLine(180);
        ImGui.textDisabled("default: " + param.getDefaultValue());

        // Delete button
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
        if (ImGui.button(MaterialIcons.Close + "##DeleteParam" + i)) {
            captureUndoState();
            editingController.removeParameter(i);
            markModified();
            if (selectedParameterIndex >= params.size()) {
                selectedParameterIndex = params.size() - 1;
            }
        }

        ImGui.popID();
    }

    if (params.isEmpty()) {
        ImGui.textDisabled("No parameters defined");
    }
}
```

### Editing States

```
┌─────────────────────────────────────────────┐
│ States                               [+ Add] │
├─────────────────────────────────────────────┤
│ ◉ idle       (default)                   [x]│
│ ○ walk       directional                 [x]│
│ ○ attack     simple                      [x]│
└─────────────────────────────────────────────┘
```

**State Inspector (when state selected):**
```
┌─────────────────────────────────────────────────────────┐
│ State: walk                                              │
├─────────────────────────────────────────────────────────┤
│ Name: [walk                    ]                         │
│                                                         │
│ Type: [Directional ▼]                                   │
│       • Simple (single animation)                       │
│       • Directional (4 directions)                      │
│                                                         │
│ ☐ Set as Default State                                  │
├─────────────────────────────────────────────────────────┤
│ Animations:                                              │
│                                                         │
│ UP:    [animations/player/walk_up.anim   ] [Browse]     │
│ DOWN:  [animations/player/walk_down.anim ] [Browse]     │
│ LEFT:  [animations/player/walk_left.anim ] [Browse]     │
│ RIGHT: [animations/player/walk_right.anim] [Browse]     │
└─────────────────────────────────────────────────────────┘
```

**Add State Dialog:**
```
┌─────────────────────────────────────┐
│            Add State                │
├─────────────────────────────────────┤
│ Name: [new_state         ]          │
│                                     │
│ Type: [Simple ▼]                    │
│       • Simple                      │
│       • Directional                 │
├─────────────────────────────────────┤
│         [Add]    [Cancel]           │
└─────────────────────────────────────┘
```

**Implementation:**
```java
private void renderStateInspector() {
    if (editingController == null || selectedStateIndex < 0) {
        ImGui.textDisabled("Select a state to edit");
        return;
    }

    AnimatorState state = editingController.getState(selectedStateIndex);

    ImGui.text("State: " + state.getName());
    ImGui.separator();

    // Name field
    ImString nameInput = new ImString(state.getName(), 64);
    ImGui.text("Name:");
    ImGui.setNextItemWidth(-1);
    if (ImGui.inputText("##StateName", nameInput)) {
        captureUndoState();
        state.setName(nameInput.get());
        markModified();
    }

    ImGui.spacing();

    // Type dropdown
    ImGui.text("Type:");
    StateType[] types = StateType.values();
    int currentType = state.getType().ordinal();
    ImGui.setNextItemWidth(-1);
    if (ImGui.combo("##StateType", new int[]{currentType},
            Arrays.stream(types).map(Enum::name).toArray(String[]::new))) {
        captureUndoState();
        state.setType(types[currentType]);
        markModified();
    }

    ImGui.spacing();

    // Default state checkbox
    boolean isDefault = state.getName().equals(editingController.getDefaultState());
    if (ImGui.checkbox("Set as Default State", isDefault)) {
        if (!isDefault) {
            captureUndoState();
            editingController.setDefaultState(state.getName());
            markModified();
        }
    }

    ImGui.separator();
    ImGui.text("Animations:");
    ImGui.spacing();

    // Animation fields based on type
    if (state.getType() == StateType.SIMPLE) {
        renderSimpleAnimationField(state);
    } else if (state.getType() == StateType.DIRECTIONAL) {
        renderDirectionalAnimationFields(state);
    }
}

private void renderDirectionalAnimationFields(AnimatorState state) {
    for (Direction dir : Direction.values()) {
        String animPath = state.getDirectionalAnimation(dir);
        String displayPath = animPath != null ? truncatePath(animPath, 30) : "(none)";

        ImGui.text(dir.name() + ":");
        ImGui.sameLine(60);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 70);
        ImGui.inputText("##Anim" + dir.name(), new ImString(displayPath), ImGuiInputTextFlags.ReadOnly);

        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.FolderOpen + "##Browse" + dir.name())) {
            final Direction d = dir;
            animationPicker.open(Animation.class, animPath, selected -> {
                if (selected != null) {
                    captureUndoState();
                    state.setDirectionalAnimation(d, Assets.getPathForResource(selected));
                    markModified();
                }
            });
        }
    }
}
```

### Editing Transitions

**Transitions Table:**
```
┌────────────────────────────────────────────────────────────────────────────┐
│ Transitions                                                    [+ Add]     │
├───────────┬───────────┬──────────────────┬───────────────────────┬────────┤
│   From    │    To     │      Type        │      Conditions       │        │
├───────────┼───────────┼──────────────────┼───────────────────────┼────────┤
│   idle    │   walk    │     INSTANT      │ isMoving == true      │  [x]   │
│   walk    │   idle    │     INSTANT      │ isMoving == false     │  [x]   │
│     *     │  attack   │     INSTANT      │ (trigger only)        │  [x]   │
│  attack   │     *     │ WAIT_COMPLETION  │ (on finish)           │  [x]   │
└───────────┴───────────┴──────────────────┴───────────────────────┴────────┘
```

**Add Transition Dialog:**
```
┌─────────────────────────────────────────────────────────┐
│                  Add Transition                          │
├─────────────────────────────────────────────────────────┤
│ From: [idle ▼]                                          │
│       • idle                                            │
│       • walk                                            │
│       • attack                                          │
│       • * (any state)                                   │
│                                                         │
│ To:   [walk ▼]                                          │
│       • idle                                            │
│       • walk                                            │
│       • attack                                          │
│       • * (previous state)                              │
│                                                         │
│ Type: [INSTANT ▼]                                       │
│       • INSTANT                                         │
│       • WAIT_FOR_COMPLETION                             │
│       • WAIT_FOR_LOOP                                   │
│                                                         │
│ Conditions:                                              │
│ ┌───────────────┬────────┬─────────────────┬──────────┐ │
│ │   Parameter   │   Op   │      Value      │          │ │
│ ├───────────────┼────────┼─────────────────┼──────────┤ │
│ │ isMoving      │   ==   │     true        │   [x]    │ │
│ └───────────────┴────────┴─────────────────┴──────────┘ │
│                                        [+ Add Condition] │
├─────────────────────────────────────────────────────────┤
│                [Add]    [Cancel]                         │
└─────────────────────────────────────────────────────────┘
```

**Transition Inspector (when transition selected):**
```java
private void renderTransitionInspector() {
    if (editingController == null || selectedTransitionIndex < 0) {
        ImGui.textDisabled("Select a transition to edit");
        return;
    }

    AnimatorTransition trans = editingController.getTransition(selectedTransitionIndex);

    ImGui.text("Transition");
    ImGui.separator();

    // From state dropdown
    ImGui.text("From:");
    ImGui.sameLine(60);
    ImGui.setNextItemWidth(150);
    String[] fromOptions = getStateOptionsWithWildcard();
    int fromIndex = getIndexOf(fromOptions, trans.getFrom());
    int[] fromSelected = {fromIndex};
    if (ImGui.combo("##TransFrom", fromSelected, fromOptions)) {
        captureUndoState();
        trans.setFrom(fromOptions[fromSelected[0]]);
        markModified();
    }

    // To state dropdown
    ImGui.text("To:");
    ImGui.sameLine(60);
    ImGui.setNextItemWidth(150);
    String[] toOptions = getStateOptionsWithWildcard();
    int toIndex = getIndexOf(toOptions, trans.getTo());
    int[] toSelected = {toIndex};
    if (ImGui.combo("##TransTo", toSelected, toOptions)) {
        captureUndoState();
        trans.setTo(toOptions[toSelected[0]]);
        markModified();
    }

    // Type dropdown
    ImGui.text("Type:");
    ImGui.sameLine(60);
    ImGui.setNextItemWidth(150);
    TransitionType[] types = TransitionType.values();
    int typeIndex = trans.getType().ordinal();
    int[] typeSelected = {typeIndex};
    if (ImGui.combo("##TransType", typeSelected,
            Arrays.stream(types).map(Enum::name).toArray(String[]::new))) {
        captureUndoState();
        trans.setType(types[typeSelected[0]]);
        markModified();
    }

    ImGui.separator();
    ImGui.text("Conditions:");

    // Conditions list
    List<TransitionCondition> conditions = trans.getConditions();
    for (int i = 0; i < conditions.size(); i++) {
        TransitionCondition cond = conditions.get(i);
        renderConditionRow(trans, cond, i);
    }

    if (conditions.isEmpty()) {
        ImGui.textDisabled("No conditions (manual trigger only)");
    }

    if (ImGui.button(MaterialIcons.Add + " Add Condition")) {
        captureUndoState();
        trans.addCondition(new TransitionCondition(
            editingController.getParameters().get(0).getName(),
            true
        ));
        markModified();
    }
}

private void renderConditionRow(AnimatorTransition trans, TransitionCondition cond, int index) {
    ImGui.pushID(index);

    // Parameter dropdown
    ImGui.setNextItemWidth(100);
    String[] paramNames = editingController.getParameters().stream()
        .map(AnimatorParameter::getName)
        .toArray(String[]::new);
    int paramIndex = getIndexOf(paramNames, cond.getParameter());
    int[] paramSelected = {paramIndex};
    if (ImGui.combo("##CondParam", paramSelected, paramNames)) {
        captureUndoState();
        cond.setParameter(paramNames[paramSelected[0]]);
        markModified();
    }

    ImGui.sameLine();
    ImGui.text("==");
    ImGui.sameLine();

    // Value based on parameter type
    AnimatorParameter param = editingController.getParameter(cond.getParameter());
    if (param != null && param.getType() == ParameterType.BOOL) {
        ImGui.setNextItemWidth(80);
        String[] boolOptions = {"true", "false"};
        int boolIndex = cond.getValue().equals(true) ? 0 : 1;
        int[] boolSelected = {boolIndex};
        if (ImGui.combo("##CondValue", boolSelected, boolOptions)) {
            captureUndoState();
            cond.setValue(boolSelected[0] == 0);
            markModified();
        }
    } else if (param != null && param.getType() == ParameterType.DIRECTION) {
        ImGui.setNextItemWidth(80);
        String[] dirOptions = Arrays.stream(Direction.values())
            .map(Enum::name)
            .toArray(String[]::new);
        int dirIndex = ((Direction) cond.getValue()).ordinal();
        int[] dirSelected = {dirIndex};
        if (ImGui.combo("##CondValue", dirSelected, dirOptions)) {
            captureUndoState();
            cond.setValue(Direction.values()[dirSelected[0]]);
            markModified();
        }
    }

    // Delete button
    ImGui.sameLine();
    if (ImGui.button(MaterialIcons.Close + "##DeleteCond")) {
        captureUndoState();
        trans.removeCondition(index);
        markModified();
    }

    ImGui.popID();
}
```

---

## Part 5: Deleting Animator Controllers

### Delete Confirmation Dialog

```
┌─────────────────────────────────────────────┐
│        Delete Animator Controller?           │
├─────────────────────────────────────────────┤
│                                             │
│  Are you sure you want to delete this       │
│  animator controller?                       │
│                                             │
│  player_animator.animator.json              │
│                                             │
│  This action cannot be undone.              │
│                                             │
├─────────────────────────────────────────────┤
│            [Delete]    [Cancel]             │
└─────────────────────────────────────────────┘
```

### Implementation

```java
private void deleteCurrentAnimator() {
    if (selectedEntry == null) return;

    String filename = selectedEntry.filename;
    try {
        Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
        Files.deleteIfExists(filePath);

        animators.remove(selectedEntry);
        selectAnimator(null);
        showStatus("Deleted animator: " + filename);
    } catch (IOException e) {
        System.err.println("[AnimatorEditorPanel] Failed to delete animator: " + e.getMessage());
        showStatus("Error deleting animator: " + e.getMessage());
    }
}
```

### Safety Checks

Before deletion, optionally check for references:

```java
private List<String> findAnimatorReferences(String animatorPath) {
    List<String> references = new ArrayList<>();

    // Scan scene files for AnimatorComponent references
    List<String> scenePaths = Assets.scanByType(Scene.class);
    for (String scenePath : scenePaths) {
        String content = Files.readString(Paths.get(Assets.getAssetRoot(), scenePath));
        if (content.contains(animatorPath)) {
            references.add(scenePath);
        }
    }

    return references;
}

// In delete dialog:
List<String> refs = findAnimatorReferences(selectedEntry.path);
if (!refs.isEmpty()) {
    ImGui.textColored(1f, 0.5f, 0f, 1f, "Warning: This animator is referenced by:");
    for (String ref : refs) {
        ImGui.bulletText(ref);
    }
}
```

---

## Part 6: Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | New animator controller |
| `Ctrl+S` | Save current animator |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` / `Ctrl+Shift+Z` | Redo |
| `Delete` | Delete selected state/transition/parameter |
| `F5` | Refresh list |

```java
private void processKeyboardShortcuts() {
    if (anyDialogOpen() || ImGui.getIO().getWantTextInput()) {
        return;
    }

    boolean ctrl = ImGui.isKeyDown(ImGuiKey.LeftCtrl) || ImGui.isKeyDown(ImGuiKey.RightCtrl);
    boolean shift = ImGui.isKeyDown(ImGuiKey.LeftShift) || ImGui.isKeyDown(ImGuiKey.RightShift);

    if (ctrl && ImGui.isKeyPressed(ImGuiKey.S, false)) {
        if (selectedEntry != null && hasUnsavedChanges) {
            saveCurrentAnimator();
        }
    }

    if (ctrl && ImGui.isKeyPressed(ImGuiKey.N, false)) {
        openNewDialog();
    }

    if (ctrl && ImGui.isKeyPressed(ImGuiKey.Z, false)) {
        if (shift) redo(); else undo();
    }

    if (ctrl && ImGui.isKeyPressed(ImGuiKey.Y, false)) {
        redo();
    }

    if (ImGui.isKeyPressed(ImGuiKey.Delete)) {
        deleteSelectedItem();
    }

    if (ImGui.isKeyPressed(ImGuiKey.F5)) {
        needsRefresh = true;
    }
}
```

---

## Part 7: Undo/Redo System

### State Snapshot

```java
private static class AnimatorSnapshot {
    final String name;
    final String defaultState;
    final List<AnimatorParameter> parameters;
    final List<AnimatorState> states;
    final List<AnimatorTransition> transitions;

    // Selection state
    final int selectedStateIndex;
    final int selectedTransitionIndex;
    final int selectedParameterIndex;

    AnimatorSnapshot(AnimatorController controller, int stateIdx, int transIdx, int paramIdx) {
        this.name = controller.getName();
        this.defaultState = controller.getDefaultState();
        this.parameters = deepCopy(controller.getParameters());
        this.states = deepCopy(controller.getStates());
        this.transitions = deepCopy(controller.getTransitions());
        this.selectedStateIndex = stateIdx;
        this.selectedTransitionIndex = transIdx;
        this.selectedParameterIndex = paramIdx;
    }

    void restore(AnimatorController controller) {
        controller.setName(name);
        controller.setDefaultState(defaultState);
        controller.setParameters(deepCopy(parameters));
        controller.setStates(deepCopy(states));
        controller.setTransitions(deepCopy(transitions));
    }
}
```

### Undo/Redo Implementation

```java
private static final int MAX_UNDO_HISTORY = 50;
private final Deque<AnimatorSnapshot> undoStack = new ArrayDeque<>();
private final Deque<AnimatorSnapshot> redoStack = new ArrayDeque<>();

private void captureUndoState() {
    if (editingController == null) return;

    redoStack.clear();
    undoStack.push(new AnimatorSnapshot(
        editingController,
        selectedStateIndex,
        selectedTransitionIndex,
        selectedParameterIndex
    ));

    while (undoStack.size() > MAX_UNDO_HISTORY) {
        undoStack.removeLast();
    }
}

private void undo() {
    if (undoStack.isEmpty() || editingController == null) return;

    redoStack.push(new AnimatorSnapshot(
        editingController,
        selectedStateIndex,
        selectedTransitionIndex,
        selectedParameterIndex
    ));

    AnimatorSnapshot state = undoStack.pop();
    state.restore(editingController);
    selectedStateIndex = clampSelection(state.selectedStateIndex, editingController.getStates().size());
    selectedTransitionIndex = clampSelection(state.selectedTransitionIndex, editingController.getTransitions().size());
    selectedParameterIndex = clampSelection(state.selectedParameterIndex, editingController.getParameters().size());

    hasUnsavedChanges = true;
}

private void redo() {
    if (redoStack.isEmpty() || editingController == null) return;

    undoStack.push(new AnimatorSnapshot(
        editingController,
        selectedStateIndex,
        selectedTransitionIndex,
        selectedParameterIndex
    ));

    AnimatorSnapshot state = redoStack.pop();
    state.restore(editingController);
    selectedStateIndex = clampSelection(state.selectedStateIndex, editingController.getStates().size());
    selectedTransitionIndex = clampSelection(state.selectedTransitionIndex, editingController.getTransitions().size());
    selectedParameterIndex = clampSelection(state.selectedParameterIndex, editingController.getParameters().size());

    hasUnsavedChanges = true;
}
```

---

## Part 8: EditorUIController Integration

### Panel Registration

```java
// EditorUIController.java
private AnimatorEditorPanel animatorEditorPanel;

public void initialize() {
    // ... existing panels ...
    animatorEditorPanel = new AnimatorEditorPanel();
    animatorEditorPanel.initialize();
    animatorEditorPanel.setStatusCallback(statusBar::showMessage);
}

public void renderPanels() {
    // ... existing panels ...
    if (showAnimatorEditor) {
        animatorEditorPanel.render();
    }
}

public void openAssetEditor(EditorPanelType type, String assetPath) {
    switch (type) {
        case ANIMATION_EDITOR -> {
            showAnimationEditor = true;
            animationEditorPanel.selectAnimationByPath(assetPath);
        }
        case ANIMATOR_EDITOR -> {
            showAnimatorEditor = true;
            animatorEditorPanel.selectAnimatorByPath(assetPath);
        }
        // ...
    }
}

public void destroy() {
    // ... existing cleanup ...
    animatorEditorPanel.destroy();
}
```

### Menu Integration

```java
// In renderMainMenu():
if (ImGui.beginMenu("Window")) {
    // ... existing items ...
    if (ImGui.menuItem("Animator Editor", null, showAnimatorEditor)) {
        showAnimatorEditor = !showAnimatorEditor;
    }
    ImGui.endMenu();
}
```

---

## Part 9: Future Visual State Machine Editor

### Phase 1: List-Based (Current Plan)

The editor described above uses lists and inspectors. This is simpler to implement and sufficient for most use cases.

### Phase 2: Node Graph Editor (Future)

A visual node-based editor would show:
- States as draggable boxes
- Transitions as arrows between states
- Click-and-drag to create transitions
- Visual indication of current state during play mode

**Technical Requirements:**
- Custom ImGui drawing for nodes and connections
- Hit testing for node selection
- Bezier curves for transition arrows
- Grid snapping for node positions
- Zoom and pan support

**Estimated Effort:** 3-4 additional weeks

---

## Files to Create/Modify

| File | Action | Description |
|------|--------|-------------|
| `editor/EditorPanelType.java` | Modify | Add `ANIMATOR_EDITOR` enum value |
| `editor/panels/AnimatorEditorPanel.java` | **NEW** | Main editor panel class |
| `editor/EditorUIController.java` | Modify | Register and render AnimatorEditorPanel |
| `resources/loaders/AnimatorControllerLoader.java` | Modify | Add editor integration methods |

---

## Implementation Phases

### Phase 1: Basic Infrastructure
- [ ] Add `ANIMATOR_EDITOR` to `EditorPanelType`
- [ ] Create `AnimatorEditorPanel` skeleton
- [ ] Register in `EditorUIController`
- [ ] Asset browser integration (icon, double-click)

### Phase 2: Create/Delete
- [ ] New animator dialog
- [ ] Delete confirmation dialog
- [ ] File operations (create, save, delete)
- [ ] Asset list refresh

### Phase 3: State Editing
- [ ] States list panel
- [ ] Add/remove states
- [ ] State inspector (name, type, animations)
- [ ] Directional animation picker

### Phase 4: Transition Editing
- [ ] Transitions table
- [ ] Add/remove transitions
- [ ] Transition inspector (from, to, type)
- [ ] Condition editor

### Phase 5: Parameter Editing
- [ ] Parameters list panel
- [ ] Add/remove parameters
- [ ] Parameter type selection
- [ ] Default value editing

### Phase 6: Polish
- [ ] Undo/redo system
- [ ] Keyboard shortcuts
- [ ] Unsaved changes warning
- [ ] Status messages
