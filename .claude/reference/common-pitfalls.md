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

## Component Lifecycle

- `onStart()` is called after all components are added but before the first `update()`. Don't assume other GameObjects exist during construction.
- `@ComponentRef` fields are resolved before `onStart()`, after hierarchy is established. Access them in `onStart()` or `update()`, not in the constructor.
- `@UiKeyReference` fields are resolved before `onStart()`, after UIManager keys are registered. The annotation goes on a non-transient `UIComponent` field. The field is serialized as a plain JSON string (the uiKey value) and rendered as a dropdown in the editor. At runtime, the resolver looks up the UIComponent via `UIManager.get(key, type)` and injects it.
- When destroying GameObjects, `onDestroy()` is called on all components. Clean up any external references.

---

## Serialization

- Fields must have a no-arg constructor type or be a primitive/String to serialize properly.
- Transient fields and fields annotated with `@HideInInspector` or `@ComponentRef` are not serialized.
- Asset paths are relative to `gameData/assets/`. Use forward slashes even on Windows.

---

## Editor Undo/Redo

- Always capture state before modification, push after. Use `UndoManager.capture()` on widget activation, `push()` on deactivation.
- Batch related changes into a single undo action when possible.

---

## Asset Loading

- Use `Assets.load()` for cached loading. Direct file reads bypass the cache and hot-reload.
- Spritesheet sprite indices are 0-based, left-to-right, top-to-bottom.
- Missing assets throw by default. Use `LoadOptions.usePlaceholder()` for graceful fallback.

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
