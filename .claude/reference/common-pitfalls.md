# Common Pitfalls

**Read this before writing code.** These are frequent mistakes that cause bugs.

---

## ImGui Push/Pop Style Rules

**CRITICAL: Always use the same condition for push and pop.**

When using `pushStyleColor`/`popStyleColor` or `pushStyleVar`/`popStyleVar`, the push and pop **must** use the exact same condition value. If state changes between push and pop (e.g., a button click toggles a boolean), you get assertion errors.

**WRONG - state changes between push and pop:**
```java
if (isEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Button, ...);
}
if (ImGui.button("Toggle")) {
    isEnabled = !isEnabled;  // State changes here!
}
if (isEnabled) {  // Different value than push!
    ImGui.popStyleColor();
}
```

**CORRECT - store state before the widget:**
```java
boolean wasEnabled = isEnabled;  // Store state
if (wasEnabled) {
    ImGui.pushStyleColor(ImGuiCol.Button, ...);
}
if (ImGui.button("Toggle")) {
    isEnabled = !isEnabled;  // State can change safely
}
if (wasEnabled) {  // Same value as push
    ImGui.popStyleColor();
}
```

---

## ImGui NextItemData Consumption

**`setNextItemWidth()` (and other `setNext*` calls) are consumed by the next `ItemAdd()` — which includes `text()`.**

If you call `setNextItemWidth()` before a text label, the label consumes it and your input widget gets default width.

**WRONG:**
```java
ImGui.setNextItemWidth(width);
ImGui.text("X");
ImGui.sameLine();
ImGui.dragFloat("##x", buf);  // gets default width — NextItemData already consumed
```

**CORRECT — set width after the label:**
```java
ImGui.text("X");
ImGui.sameLine();
ImGui.setNextItemWidth(width);
ImGui.dragFloat("##x", buf);  // gets correct width
```

**CORRECT — use `FieldEditorUtils.inlineField()`:**
```java
FieldEditorUtils.inlineField("X", width, () -> ImGui.dragFloat("##x", buf));
```

---

## Component Lifecycle

- `onStart()` is called after all components are added but before the first `update()`. Don't assume other GameObjects exist during construction.
- `@ComponentReference` fields are resolved before `onStart()`, after hierarchy is established and `ComponentKeyRegistry` keys are registered. Hierarchy sources (`SELF`, `PARENT`, `CHILDREN`, `CHILDREN_RECURSIVE`) are transient. `KEY` source fields are non-transient, serialized as string keys, and resolved via `ComponentKeyRegistry`.
- When destroying GameObjects, `onDestroy()` is called on all components. Clean up any external references.

---

## Serialization

- Fields must have a no-arg constructor type or be a primitive/String to serialize properly.
- Transient fields and fields annotated with `@HideInInspector` are not serialized. Hierarchy-source `@ComponentReference` fields are transient so also not serialized. `componentKey` on `Component.class` is explicitly serialized (not in `meta.fields()`).
- Asset paths are relative to `gameData/assets/`. Use forward slashes even on Windows.

---

## Editor Undo/Redo

- Always capture state before modification, push after. Use `UndoManager.capture()` on widget activation, `push()` on deactivation.
- Batch related changes into a single undo action when possible.
- **Prefer `FieldEditors` methods** — they handle undo automatically via `FieldUndoTracker`.
- **Custom combos** (with special entries like "(same scene)", "Random"): Use `SetterUndoCommand` on each `ImGui.selectable()` click. Capture old value before the setter call.
- **dragInt2/dragFloat2** (compound values): Use `FieldEditors.drawDragInt2()` / `drawDragFloat2()` — they encapsulate activation/deactivation tracking and `SetterUndoCommand` push automatically.
- **Nested list mutations** (e.g., conditions inside ConditionalDialogue): `ListItemCommand` only works on Component-level fields. For nested lists, snapshot before/after with `SetterUndoCommand` and the parent object's setter.
- **Guard undo commands with `editorEntity() != null`** — in play mode there's no undo support.

---

## Asset Loading

- Use `Assets.load()` for cached loading. Direct file reads bypass the cache and hot-reload.
- Spritesheet sprite indices are 0-based, left-to-right, top-to-bottom.
- Missing assets throw by default. Use `LoadOptions.usePlaceholder()` for graceful fallback.

---

## Custom Inspectors (Play Mode)

Custom inspectors work in both editor mode and play mode. The `entity` field type changed from `EditorGameObject` to `HierarchyItem` to support this.

**Use `entity` for scene graph queries (always non-null):**
```java
// These work in both editor and play mode
Component parent = entity.getHierarchyParent();
List<? extends HierarchyItem> children = entity.getHierarchyChildren();
SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
```

**Use `editorEntity()` for undo commands (null in play mode):**
```java
// WRONG: Creates undo with HierarchyItem, won't compile or NPE in play mode
UndoManager.getInstance().push(new SetComponentFieldCommand(..., entity));

// CORRECT: Guard undo operations with editorEntity()
if (editorEntity() != null) {
    UndoManager.getInstance().push(
        new SetComponentFieldCommand(component, "field", oldVal, newVal, editorEntity())
    );
}
```

**Also use `editorEntity()` for:**
- `getPosition()` / `setPosition()` (EditorGameObject-specific)
- Prefab override checks
- Any method only available on EditorGameObject

---

## Gizmos

**Always use `ctx.getTransform()` in gizmo methods, not `getTransform()`.**

In the editor, components are stored as data - their `gameObject` field is null. The `GizmoRenderer` passes the correct transform via the context.

**WRONG:**
```java
@Override
public void onDrawGizmosSelected(GizmoContext ctx) {
    Vector3f pos = getTransform().getWorldPosition();  // NullPointerException!
}
```

**CORRECT:**
```java
@Override
public void onDrawGizmosSelected(GizmoContext ctx) {
    Transform transform = ctx.getTransform();
    if (transform == null) return;
    Vector3f pos = transform.getWorldPosition();
}
```

---

## Dialogue System

### DialogueChoiceGroup must be last entry

A `DialogueChoiceGroup` can only appear as the **last** entry in a `Dialogue.entries` list. The editor, loader, and runtime all enforce this. Maximum 4 choices per group.

### DialogueEventStore uses SaveManager global state

`DialogueEventStore.markFired()` / `hasFired()` are wrappers around `SaveManager.setGlobal("dialogue_events", ...)`. Ensure `SaveManager` is initialized before dialogue runtime runs.

### IPausable — don't disable, pause

During dialogue, `PlayerDialogueManager` calls `onPause()` on all `IPausable` components in the scene (found via `Scene.getComponentsImplementing()`). Components stay enabled — they just skip logic in their update loop. This avoids lifecycle side effects from disabling.

### InputMode gating

`PlayerInput` switches to `InputMode.DIALOGUE` during conversations. Other systems (movement, interaction) check the mode before consuming input. Don't access `Input` directly from player components — use `PlayerInput` as the single source of truth.

### DialogueUI hierarchy — keep enabled initially

`Scene.registerCachedComponents()` skips disabled GameObjects. The DialogueUI children must be enabled at scene start so their `@ComponentReference(source = KEY)` fields resolve. Hide them after references resolve (in `onStart()`), not via the enabled flag at scene load time.

### Sealed interface for DialogueEntry

`DialogueEntry` is a `sealed interface` permitting only `DialogueLine` and `DialogueChoiceGroup`. Use pattern matching (`instanceof`) or `switch` for exhaustive handling.
