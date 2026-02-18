# Dialogue System Inspector Code Review

**Date:** 2026-02-18
**Reviewer:** Claude (Senior Software Engineer)
**Files Reviewed:** 6 files across `editor/ui/inspectors/`, `editor/panels/inspector/`, `editor/events/`, `components/dialogue/`

---

## Overview

The dialogue inspector subsystem provides editor UI for:
- **DialogueInteractableInspector** — Custom component inspector for `DialogueInteractable` (NPC dialogue setup)
- **DialogueEventListenerInspector** — Custom component inspector for `DialogueEventListener` (event reactions)
- **DialogueEventsInspectorRenderer** — Asset inspector for `.dialogue-events.json` files
- **DialogueVariablesInspectorRenderer** — Asset inspector for `.dialogue-vars.json` files
- **OpenDialogueEditorEvent** — Editor event bus record for opening the Dialogue Editor panel

---

## Architecture

```
Custom Component Inspectors (scene entities):
    DialogueInteractableInspector extends CustomComponentInspector<DialogueInteractable>
    DialogueEventListenerInspector extends CustomComponentInspector<DialogueEventListener>

Asset Inspector Renderers (asset browser):
    DialogueEventsInspectorRenderer implements AssetInspectorRenderer<DialogueEvents>
    DialogueVariablesInspectorRenderer implements AssetInspectorRenderer<DialogueVariables>

Event:
    OpenDialogueEditorEvent (record) implements EditorEvent
```

---

## File-by-File Review

### 1. `DialogueInteractableInspector.java` — Good with Issues

**Strengths:**
- Clean section-based layout (A through F) with clear separation of concerns
- Proper use of `FieldEditors` facade for boolean/asset fields
- Deferred removal pattern for list items (prevents ConcurrentModification)
- Correct use of `FieldEditorContext.beginRequiredRowHighlight` / `endRequiredRowHighlight` with try/finally
- `OpenDialogueEditorEvent` integration for "Open in Dialogue Editor" button
- Good tooltip on the "On Conversation End" section explaining the semantic difference from per-line events

**Issues:**

1. **Lines 99-114 — Direction checkboxes don't set `changed = true`:**

   The direction checkboxes toggle `interactFrom` and call `markSceneDirty()`, but they never propagate `changed` back to the calling `drawInteractionSettings()` method. The `boolean changed` in that method only tracks the `FieldEditors.drawBoolean("Directional", ...)` result.

   ```java
   if (ImGui.checkbox(dir.name() + "##interactDir", active)) {
       if (active) {
           interactFrom.remove(dir);
       } else {
           interactFrom.add(dir);
       }
       markSceneDirty();
       // Missing: changed = true (or mechanism to propagate)
   }
   ```

   **Impact:** Low — `markSceneDirty()` ensures the scene is saved, so data isn't lost. But the `draw()` return value is inconsistent: `FieldEditors`-based fields report changes via the return value, while direct ImGui widgets use `markSceneDirty()` as a side channel. This dual mechanism makes it harder to reason about change propagation.

2. **Lines 291-357 — `drawOnConversationEnd()` returns void, never contributes to `changed`:**

   All mutations inside this method call `markSceneDirty()` but the method returns `void`. The parent `draw()` method ignores any changes made here in its return value.

   Same issue with `drawVariableTable()` (lines 363-417).

   **Recommendation:** Either make all draw methods return `boolean` and accumulate into the top-level `changed`, or document the dual-mechanism as intentional and accept that `draw()` return value is best-effort.

3. **Line 110 — Direction layout assumes `RIGHT` is last in enum:**

   ```java
   if (dir != Direction.RIGHT) {
       ImGui.sameLine();
   }
   ```

   If `Direction` gains new values (e.g. diagonals) or is reordered, this layout breaks. Better approach:

   ```java
   if (dir.ordinal() < Direction.values().length - 1) {
       ImGui.sameLine();
   }
   ```

4. **Lines 463-480 — `loadEventNames()` called up to twice per frame:**

   Both `drawConditionalDialogues()` (line 126) and `drawOnConversationEnd()` (line 301) call `loadEventNames()`, which calls `Assets.load()`. The asset cache makes this cheap, but it's still two map lookups + null checks per frame, every frame.

   **Recommendation:** Cache the result once at the top of `draw()` and pass it as a parameter.

5. **Lines 382-416 — `varBuffer` reuse across variables is correct but fragile:**

   The single `ImString varBuffer` is reused for all STATIC variable rows. This works because ImGui's `inputText` reads the buffer value at call time and writes changes back immediately, all within a `pushID` scope. Correct pattern, but adding a comment would help maintainability.

6. **Lines 382-391 — Redundant `beginDisabled()` around `textDisabled()`:**

   ```java
   case AUTO -> FieldEditors.inspectorRow(name, () -> {
       ImGui.beginDisabled();
       ImGui.textDisabled("auto");
       ImGui.endDisabled();
   });
   ```

   `textDisabled()` already renders with the disabled color. The `beginDisabled()/endDisabled()` wrapper adds nothing visible. Not harmful, but unnecessary.

7. **No undo support for custom widgets:**

   The `FieldEditors.drawBoolean` and `FieldEditors.drawAsset` calls go through the undo system, but all direct ImGui widgets (direction checkboxes, conditional dialogue combos, condition event/state combos, onConversationEnd combos, variable text inputs) bypass undo entirely. This means:
   - Ctrl+Z won't revert direction changes, condition edits, event ref changes, or variable values
   - Only the "Directional" toggle and "Dialogue" asset picker support undo

   **Impact:** Medium — this is a known trade-off for custom inspectors with complex widgets (same as `WarpZoneInspector` and `DoorInspector`). Should be documented or addressed incrementally.

---

### 2. `DialogueEventListenerInspector.java` — Excellent

**Strengths:**
- Compact, focused inspector with clear purpose
- Correct use of `FieldEditorContext.beginRequiredRowHighlight` with try/finally
- Stale event warning (event name not in global events list) is a nice UX touch
- Uses `FieldEditors.drawEnum` for the reaction field — gets free undo support
- `pushStyleColor`/`popStyleColor` properly paired for the warning text

**Issues:**

1. **Lines 50-66 — `boolean[]` array trick for lambda capture:**

   ```java
   final boolean[] comboChanged = {false};
   FieldEditors.inspectorRow("Event Name", () -> {
       // ...
       comboChanged[0] = true;
   });
   changed |= comboChanged[0];
   ```

   This works correctly but is less readable than the `AtomicBoolean` pattern used in `WarpZoneInspector`. Low priority, stylistic preference.

2. **Lines 81-91 — Duplicated `loadEventNames()` and `markSceneDirty()`:**

   Both this inspector and `DialogueInteractableInspector` have identical `loadEventNames()` and `markSceneDirty()` private methods. Could be extracted to a shared utility.

   **Recommendation:** Create a `DialogueInspectorUtils` class or add to an existing utility.

3. **Line 70-73 — Warning uses raw `pushStyleColor` instead of `EditorColors`:**

   ```java
   ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.WARNING[0], EditorColors.WARNING[1], ...);
   ImGui.textWrapped("...");
   ImGui.popStyleColor();
   ```

   The rest of the codebase uses `EditorColors.textColored(EditorColors.WARNING, text)` for colored text. This inconsistency is minor but breaks the convention.

   **Recommendation:** Replace with `EditorColors.textColored(EditorColors.WARNING, "\u26A0 Unknown event: " + current)`.

---

### 3. `DialogueEventsInspectorRenderer.java` — Good

**Strengths:**
- Proper `AssetInspectorRenderer` contract implementation
- Undo/redo with bounded stack (MAX_UNDO = 50)
- Clean snapshot/restore pattern for list state
- Deferred removal pattern for event list
- Proper `onDeselect()` cleanup
- `hasUnsavedChanges()` correctly tracks dirty state

**Issues:**

1. **Lines 66-71 — `captureUndo` called on every keystroke in `inputText`:**

   ```java
   if (ImGui.inputText("##name", nameBuffer)) {
       captureUndo(events);
       eventNames.set(i, nameBuffer.get());
       hasChanges = true;
   }
   ```

   `ImGui.inputText` returns `true` on every character typed. This pushes a new undo snapshot for every keystroke (e.g. typing "BATTLE_WON" pushes 10 snapshots). The undo stack fills rapidly with nearly-identical states.

   **Recommendation:** Use `isItemActivated()` to capture the "before" state on focus, and apply the undo only on `isItemDeactivatedAfterEdit()`:

   ```java
   ImGui.setNextItemWidth(...);
   ImGui.inputText("##name", nameBuffer);
   if (ImGui.isItemActivated()) {
       captureUndo(events);
   }
   if (ImGui.isItemDeactivatedAfterEdit()) {
       eventNames.set(i, nameBuffer.get());
       hasChanges = true;
   }
   ```

2. **Line 102 — `render()` returns `hasChanges` (cumulative, not per-frame):**

   This is correct per the `AssetInspectorRenderer` contract ("returns true if there are unsaved changes"), but it's worth noting that this differs from `CustomComponentInspector.draw()` which returns "changed this frame." The different semantics could confuse someone reading both systems.

3. **Lines 104-123 — Undo/redo buttons clickable while disabled:**

   ```java
   if (!canUndo) ImGui.beginDisabled();
   if (ImGui.button(MaterialIcons.Undo + "##undoEvents")) {
       undo(events);
   }
   if (ImGui.isItemHovered()) ImGui.setTooltip("Undo");
   if (!canUndo) ImGui.endDisabled();
   ```

   When `ImGui.beginDisabled()` is active, `button()` returns false and `isItemHovered()` also returns false, so this is functionally correct. But the tooltip won't show on disabled buttons — consider using `isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)` if you want hover feedback on disabled state.

---

### 4. `DialogueVariablesInspectorRenderer.java` — Good

**Strengths:**
- Same solid patterns as `DialogueEventsInspectorRenderer`
- Proper deep copy in `snapshot()` — correctly clones `DialogueVariable` objects
- Type dropdown with all `DialogueVariable.Type` values

**Issues:**

1. **Same keystroke-granularity undo issue** as `DialogueEventsInspectorRenderer` (line 70-74).

2. **Lines 78-89 — Type combo width may truncate long type names:**

   ```java
   ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.55f);
   ```

   After the delete button is placed via `sameLine()`, the remaining width is computed from `getContentRegionAvailX()` at that point. The 0.55f multiplier might clip on narrow panels. Low priority.

3. **Massive code duplication with `DialogueEventsInspectorRenderer`:**

   Both files share ~80 lines of identical boilerplate:
   - `MAX_UNDO` constant
   - `undoStack`/`redoStack` declarations and management
   - `captureUndo()`, `undo()`, `redo()` methods
   - `renderUndoRedo()` UI
   - `hasChanges`, `cachedPath`, `currentAsset` state management
   - `onDeselect()`, `hasUnsavedChanges()`, `hasEditableProperties()` implementations

   **Recommendation:** Extract a generic `UndoableAssetInspectorRenderer<T>` base class that provides:
   - Bounded undo/redo stack management
   - `captureUndo(T)` / `undo(T)` / `redo(T)` with abstract `snapshot(T)` / `restore(T, snapshot)` methods
   - `renderUndoRedo()` UI
   - Common lifecycle (`onDeselect`, `hasUnsavedChanges`, `hasEditableProperties`)

   This would reduce each concrete renderer to just the `render()` body, `snapshot()`, `restore()`, and `save()`.

---

### 5. `OpenDialogueEditorEvent.java` — Excellent

```java
public record OpenDialogueEditorEvent(String dialoguePath) implements EditorEvent {}
```

Clean, minimal record. Follows the established `EditorEvent` pattern. No issues.

---

### 6. `DialogueInteractable.java` (Component) — Excellent

**Strengths:**
- Clear Javadoc explaining the conditional dialogue evaluation model
- `@Required` annotation on `dialogue` field
- `@ComponentMeta(category = "Dialogue")` for proper inspector categorization
- `selectDialogue()` is package-private (testable) with clean top-to-bottom evaluation
- Guard against `manager.isActive()` prevents re-triggering during active dialogue
- Gizmo setup in constructor with distinct purple color

**Minor Issues:**

1. **Line 108 — `variables` map passed by reference to `startDialogue`:**

   ```java
   manager.startDialogue(selected, variables);
   ```

   If `PlayerDialogueManager` modifies this map (e.g. adding runtime variables), it mutates the component's serialized state. Consider passing `new HashMap<>(variables)` as a defensive copy.

   **Impact:** Depends on `startDialogue` implementation — if it's read-only, this is fine.

---

## Cross-Cutting Issues

### 1. Inspector Undo Shadows Scene Undo (High Priority)

The `InspectorPanel` registers `inspector.undo` / `inspector.redo` shortcuts at `PANEL_FOCUSED` scope, which have higher priority than the global `editor.edit.undo` (`GLOBAL` scope). The shortcut system's `findBestMatch()` executes **only the highest-priority match** and consumes the key:

```
User presses Ctrl+Z with InspectorPanel focused:
  → ShortcutRegistry finds: inspector.undo (PANEL_FOCUSED, priority 1)
                              editor.edit.undo (GLOBAL, priority 3)
  → Picks inspector.undo → calls AssetInspectorRegistry::undo
  → If inspecting a component (not an asset): currentInspector == null → does nothing
  → Global scene undo (UndoManager.undo()) NEVER fires — key was consumed
```

**Impact:** When the user is editing a `DialogueInteractable` in the Inspector and presses Ctrl+Z, nothing happens. They must click another panel (Scene View, Hierarchy) first for scene undo to work. This is especially painful for this inspector since many of its custom widgets (direction checkboxes, condition combos, event refs, variable inputs) don't push undo commands — the user can't undo those changes at all, and can't even undo *other* scene changes without first switching focus.

**This affects all component inspectors**, not just the dialogue ones, but the dialogue inspector is the most complex and most affected.

**Quick fix (band-aid):** Have `AssetInspectorRegistry.undo()` fall through to `UndoManager.getInstance().undo()` when `currentInspector == null`. This unblocks Ctrl+Z for component inspection but doesn't address the underlying design issue.

**Proper refactor:** The root cause is that the InspectorPanel serves two fundamentally different roles — component inspection (scene undo) and asset inspection (asset-level undo) — but unconditionally registers a single set of `PANEL_FOCUSED` undo shortcuts that always route to the asset system. A cleaner design would:

1. **Unified undo dispatch in InspectorPanel:** The panel's undo handler should check what's currently being inspected and route accordingly:
   - Asset selected → delegate to `AssetInspectorRegistry.undo()`
   - Component selected → delegate to `UndoManager.getInstance().undo()`
   - Nothing selected → delegate to `UndoManager.getInstance().undo()`

2. **Component-level undo for custom widgets:** The custom ImGui widgets in `DialogueInteractableInspector` (direction checkboxes, condition combos, event ref combos, variable inputs) should push `SetComponentFieldCommand` entries onto the scene `UndoManager`, so Ctrl+Z actually has something to revert. This ties into cross-cutting issue #4 (no undo for custom widgets).

3. **Consider whether `PANEL_FOCUSED` is even the right scope:** If the inspector undo should always be available regardless of focus (e.g. user edits an asset field then clicks the scene view — should Ctrl+Z still undo the asset edit?), the scope model itself may need rethinking.

This is a broader editor-wide concern, not specific to the dialogue inspectors, but the dialogue inspector is where it hurts the most due to the number of custom widgets.

### 2. Dual Dirty-Marking Mechanism (Medium Priority)


`DialogueInteractableInspector` uses two different mechanisms to signal changes:
- **Return value:** `FieldEditors.drawBoolean()` / `drawAsset()` changes propagate via `boolean changed`
- **Side channel:** Direct ImGui widgets call `markSceneDirty()` directly

The `draw()` return value is supposed to be the canonical signal, but the side-channel bypasses it. This means:
- The registry caller may not know about some changes
- Any future logic that relies on `draw()` returning true will miss direct widget changes

**Recommendation:** Standardize on one mechanism. Either:
- (a) Make all sub-draw methods return boolean and accumulate, or
- (b) Add a `markSceneDirty()` method to the base class and remove the private reimplementation

### 3. Duplicated Helper Methods (Medium Priority)

`loadEventNames()` and `markSceneDirty()` are copy-pasted between `DialogueInteractableInspector` and `DialogueEventListenerInspector`. Extract to a shared utility.

### 4. Duplicated Asset Inspector Boilerplate (Medium Priority)

`DialogueEventsInspectorRenderer` and `DialogueVariablesInspectorRenderer` share ~80 lines of identical undo/lifecycle code. Extract a base class (see recommendation in file review #4).

### 5. No Undo for Custom Widgets (Low Priority — Known Trade-off)

Direction checkboxes, condition combos, event ref combos, and variable text inputs all bypass undo. This is consistent with how other custom inspectors (DoorInspector, WarpZoneInspector) handle complex widgets, but it's a gap in the user experience. Note that this compounds the undo shadow issue (#1): even if the dispatch is fixed, these widgets still have nothing to undo.

### 6. Per-Keystroke Undo Snapshots in Asset Inspectors (Medium Priority)

Both asset inspectors capture undo snapshots on every character typed in `inputText`. This fills the undo stack with near-identical states. Use the activation/deactivation pattern instead.

---

## Summary

### Strengths
- Clean section-based architecture in the main inspector
- Proper use of established patterns (`FieldEditors`, `FieldEditorContext`, `EditorColors`, deferred removal)
- Good UX touches (stale event warnings, required field highlighting, "Open in Dialogue Editor" buttons, descriptive tooltips)
- Solid `AssetInspectorRenderer` implementations with undo/redo and save support
- Consistent with existing inspector conventions (WarpZoneInspector, DoorInspector)

### Issues by Priority

**High Priority — Refactor Candidate:**
1. Inspector undo shadows scene undo (Ctrl+Z does nothing when InspectorPanel focused during component inspection — affects all component inspectors, worst in dialogue inspector due to many custom widgets). Needs a unified undo dispatch that routes to scene vs. asset undo based on inspection context.

**High Priority:**
2. Per-keystroke undo snapshots in asset inspectors (floods undo stack)

**Medium Priority:**
1. Dual dirty-marking mechanism (return value vs. `markSceneDirty()` side channel)
2. Duplicated boilerplate between the two asset inspector renderers (~80 lines)
3. Duplicated `loadEventNames()` / `markSceneDirty()` across component inspectors
4. Warning text in `DialogueEventListenerInspector` uses raw `pushStyleColor` instead of `EditorColors.textColored()`

**Low Priority:**
1. No undo for custom widgets (known trade-off, consistent with existing inspectors — but compounds the high-priority undo shadow issue)
2. Direction layout hardcodes `RIGHT` as last enum value
3. Redundant `beginDisabled()` around `textDisabled()` in variable table
4. `loadEventNames()` called twice per frame (cached, but unnecessary)
5. `variables` map passed by reference to `startDialogue` (may need defensive copy)

---

## Verdict

**Overall Rating: Good**

The dialogue inspector subsystem is well-structured and follows established editor patterns. The code is readable with clear section organization, proper ImGui ID management, and thoughtful UX details like stale event warnings.

The most significant finding is the **undo dispatch conflict** — a pre-existing editor-wide issue where `PANEL_FOCUSED` inspector shortcuts shadow the global scene undo during component inspection. This is not a bug in the dialogue code itself, but the dialogue inspector's heavy use of custom widgets (which lack undo support) makes it the worst-affected inspector. A proper fix requires refactoring the InspectorPanel's undo routing to be context-aware (asset vs. component inspection).

The per-keystroke undo in asset inspectors and the code duplication are the main dialogue-specific concerns.

Recommended for production use. The undo dispatch refactor should be tracked as a separate editor-wide improvement.