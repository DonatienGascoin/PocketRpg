# Plan 3: Editor Tooling

**PR:** `dialogue-editor`
**Depends on:** Plan 1 (data model, loaders), Plan 2 (components — for inspector targets)
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

All editor panels, inspectors, and asset renderers. Primarily manual testing.

---

## Phase 1: DialogueEditorPanel — Core ✅

**Design ref:** §7 — DialogueEditorPanel
**Status:** COMPLETE

Basic two-column layout: dialogue list (left) + line/choice editor (right). Create, edit, save dialogues.

- [x] Register `DIALOGUE_EDITOR` panel in `EditorUIController`
- [x] Left column:
  - Dialogue list from `Assets.scanByType(Dialogue.class)` with cooldown-based refresh
  - Search/filter by name via `ImString` input
  - `[+ Add]` creates new dialogue (modal popup with name input), `[- Remove]` deletes with confirmation popup
  - Selection highlight via path-based matching (survives list rebuilds)
- [x] Right column — toolbar:
  - Dialogue name (read-only, derived from filename)
  - `[Save]` button: amber when dirty, disabled when clean (follows AnimatorEditorPanel pattern), dirty tracking (`*` in window title)
  - `[Variables ⇗]` `[Events ⇗]` quick links right-aligned → `selectionManager.selectAsset()`
- [x] Right column — lines:
  - Editable multiline text fields for each line
  - `[+ Var]` dropdown per line (populated from DialogueVariables asset) — right-aligned
  - Optional `onCompleteEvent` per line: inline selectable toggle on header row (`▸ On Complete: [none]`), expanding to category dropdown (BUILT_IN / CUSTOM) + event selector + `[╳]` clear button
  - `[╳]` red delete button (disabled on last remaining line) — right-aligned
  - `[+ Add Line]` button (inserts before choice group if present)
  - Alternating row backgrounds for visual clarity
- [x] Drag handle for reorder:
  - Drag indicator icon as `invisibleButton` drag source (text widgets aren't interactive for drag detection)
  - Thin gap-based drop zones between lines (no duplicate drop targets)
  - Blue line + triangle indicator for valid moves, gray indicator for no-op drops (cancel by dropping in place)
  - `AcceptNoDrawDefaultRect` to suppress default ImGui highlight box
  - Escape key cancellation (follows `HierarchyDragDropHandler` pattern)
  - Deferred move execution via `pendingLineMove` with `lineEventExpanded` state preservation
- [x] Right column — choices:
  - `☑ Has choices` checkbox
  - Choice cards: text field, action type dropdown, target field (dialogue picker or event dropdown)
  - `[╳]` delete per choice
  - `[+ Add Choice]` button (disabled at 4)
- [x] Opening the panel:
  - Double-click `.dialogue.json` in Asset Browser
  - Window menu → Dialogue Editor
- [x] Warning icon `⚠` on dialogues with validation issues — deferred to Phase 2

**Files:**

| File | Change |
|------|--------|
| `editor/panels/DialogueEditorPanel.java` | **NEW** — ~900 lines |
| `editor/shortcut/EditorShortcuts.java` | Added `DIALOGUE_EDITOR` panel ID |
| `editor/EditorPanelType.java` | Already had `DIALOGUE_EDITOR` from Plan 1 |
| `editor/EditorUIController.java` | Register panel: creation, rendering, Window menu, asset browser handler, status callback |

**Design decisions:**

| Decision | Rationale |
|----------|-----------|
| `ImString()` (dynamic) instead of `ImString(256)` (fixed buffer) | Fixed-size buffers include trailing null bytes that break `get().isEmpty()` checks in imgui-java |
| Path-based selection matching instead of reference equality | `refreshDialogueList()` rebuilds records; `==` breaks after refresh. `DialogueListEntry.matches()` compares by path, and `selectedEntry` is re-linked after each refresh |
| Amber save button when dirty, disabled when clean | Follows `AnimatorEditorPanel` pattern: `pushStyleColor` (amber) when dirty, `beginDisabled` when clean. Avoids the earlier push/pop pitfall where `saveCurrentDialogue()` cleared `dirty` between `beginDisabled`/`endDisabled` |
| Auto-create missing Variables/Events asset files on quick-link click | `variables.dialogue-vars.json` didn't exist yet. Rather than disabling the button or showing an error, creating an empty asset on demand is the smoothest UX |
| Alternating row backgrounds via `getWindowDrawList().addRectFilled()` | Drawn after all line widgets so the rect covers the full block height. Uses 12% white alpha for visibility without overwhelming |
| onCompleteEvent as inline `selectable` with fixed width | Tree nodes didn't work well inline with `sameLine()`. A fixed-width selectable provides consistent click target and familiar highlight behavior |
| Regular `button()` instead of `smallButton()` | Small buttons were too hard to see and click in the editor UI |
| `sameLine()` only inside `if (eventRef != null)` | Dangling `sameLine()` with no following widget eats the next line break, hiding expanded content |
| Drag reorder via gap-based drop zones + deferred move | Thin `invisibleButton` gaps between lines (not full-line overlay) to avoid duplicate drop targets. `AcceptNoDrawDefaultRect` suppresses default highlight; custom blue/gray indicator drawn via `ImDrawList`. No-op drops (adjacent gaps) shown in gray as cancel targets. Escape cancellation follows `HierarchyDragDropHandler` pattern. Static import of `intToBytes`/`bytesToInt` from `AnimationTimelineContext` |
| `invisibleButton` as drag source, not `text()` | `ImGui.text()` is not an interactive widget — `beginDragDropSource` needs a hoverable item. The drag handle uses an `invisibleButton` with the label drawn on top via `addText()` |

**Acceptance criteria:** ✅
- Dialogue Editor panel opens from Window menu and from double-clicking `.dialogue.json` files
- Left column lists all `.dialogue.json` assets with selection highlight, search filters by name
- Creating a new dialogue produces a valid `.dialogue.json` file with one empty line (auto-focus on name input)
- Deleting a dialogue shows confirmation popup, then removes the file
- Lines are editable text fields; `[+ Var]` dropdown inserts `[VAR_NAME]` at end of text
- Each line has a collapsible `onCompleteEvent` section with a `DialogueEventRef` editor (category + event selector + clear button)
- Cannot delete the last remaining line (button disabled)
- Choices section: `Has choices` checkbox toggles visibility; max 4 choices enforced (add button disabled at 4)
- Each choice has text, action type dropdown, and context-dependent target field
- Dirty tracking: `*` in title when unsaved, cleared on save
- `[Save]` button: amber when dirty, disabled when clean; persists changes to disk via `DialogueLoader.save()`, reloads asset cache
- Quick links open Variables/Events assets in InspectorPanel (auto-creates missing files)
- Drag reorder: grab drag handle to reorder lines; blue indicator for valid moves, gray for no-op (cancel); Escape cancels drag; no default highlight box

---

## Phase 2: Validation, Undo/Redo, Shortcuts ✅

**Design ref:** §7 — Line Editor Details, Undo/Redo, Shortcuts
**Status:** COMPLETE

- [x] Validation warnings:
  - Unknown variable `[TAG]` → orange `⚠` below line
  - Malformed tag `[BROKEN` → orange `⚠` below line
  - DIALOGUE action with no target → `⚠` on choice card + dialogue list
  - CUSTOM_EVENT action with no event or unknown event → `⚠` on choice card
  - Empty choice text → `⚠` on choice card
  - `hasChoices=true` with empty choices → `⚠` on choices section
  - `onCompleteEvent` with stale/unknown custom event → `⚠` below the event ref on the line
  - Dialogue list: amber text + `⚠` icon for dialogues with any validation issue
- [x] Undo/redo:
  - `DialogueSnapshot` record: deep copies of all entries (lines, choices, events)
  - Own `Deque<DialogueSnapshot>` stacks (undoStack, redoStack, max 50)
  - `captureUndoState()` called *before* every mutation, `dirty = true` after
  - Stacks cleared on dialogue switch and delete
  - `[Undo]` `[Redo]` toolbar buttons (disabled when empty, with tooltips)
- [x] Unsaved changes confirmation popup on dialogue switch:
  - "Save & Switch" / "Discard & Switch" / "Cancel"
  - Discard reloads current dialogue from disk via `Assets.reload()` to restore cached asset
- [x] Shortcuts via `provideShortcuts(KeyboardLayout)`:
  - `Ctrl+S` → save (allowInInput=true)
  - `Ctrl+Z` / `Ctrl+W` (AZERTY) → undo (allowInInput=true)
  - `Ctrl+Y` / `Ctrl+Shift+Z` / `Ctrl+Shift+W` (AZERTY) → redo (allowInInput=true)
  - `Ctrl+Enter` → add line (allowInInput=true)
- [x] UX: hand cursor on drag handle hover

**Files:**

| File | Change |
|------|--------|
| `editor/panels/DialogueEditorPanel.java` | Add validation, undo/redo, shortcuts, unsaved changes popup |

**Design decisions:**

| Decision | Rationale |
|----------|-----------|
| `captureUndoState()` before mutation, not via `markDirty()` | `markDirty()` was called after mutations, capturing the already-modified state. Every mutation site now explicitly calls `captureUndoState()` before the change, then sets `dirty = true` directly. Removed `markDirty()` entirely |
| `DialogueSnapshot` deep copies via manual construction | `Dialogue.copyFrom()` is shallow (shares entry references). Snapshot manually constructs new `DialogueLine`, `DialogueChoiceGroup`, `Choice`, `ChoiceAction`, and `DialogueEventRef` instances. Avoids JSON round-trip overhead |
| `Assets.reload()` on discard | Editing mutates the cached asset in place. Discarding must reload from disk to restore the original state, otherwise navigating back shows the discarded mutations |
| Regex-based tag validation | `TAG_PATTERN` (`\[([^\]]*)]`) extracts closed tags, `UNCLOSED_TAG_PATTERN` (`\[[^\]]*$`) detects malformed unclosed brackets. Validated against `DialogueVariables` asset names |
| `hasDialogueWarnings()` for list icons | Separate method checks all validation cases without rendering, used by the left column to show `⚠` icon + amber text on dialogues with issues |

**Acceptance criteria:** ✅
- Each validation case produces a visible orange `⚠` warning at the correct location (below line, on choice card, in dialogue list)
- Dialogues with any validation warning show `⚠` icon in the left column list (amber text)
- Undo reverses the last edit (line text, choice edit, add/remove, reorder, event changes); redo re-applies it
- Undo/redo stacks hold up to 50 entries
- Switching dialogues clears both undo and redo stacks
- If current dialogue has unsaved changes, switching shows confirmation popup with Save & Switch / Discard & Switch / Cancel
- Discard correctly reloads from disk (navigating back shows saved state)
- All 5 shortcuts work: Ctrl+S (save), Ctrl+Z/W (undo), Ctrl+Y/Ctrl+Shift+Z/W (redo), Ctrl+Enter (add line)
- Shortcuts work while cursor is in a text input field (`allowInInput=true`)
- Hand cursor on drag handle hover

---

## Phase 3: Component Inspectors

**Design ref:** §6 — Custom Inspector, §5 — DialogueEventListenerInspector

- [ ] `DialogueComponentInspector`:
  - Conditional dialogues list: add/remove/reorder entries
  - Per-entry: condition dropdowns (eventName from DialogueEvents asset, expectedState FIRED/NOT_FIRED), `[+ Add Condition]`, dialogue picker, `[Open]` button
  - Default dialogue picker with `[Open]` button
  - `onConversationEnd` field: optional `DialogueEventRef` editor (category + event selector + clear button). This is where NPC-specific post-dialogue triggers go (e.g. trainer sets `START_BATTLE`). Placed below the default dialogue picker.
  - Collapsible preview section (read-only, default dialogue)
  - Variable table: AUTO=disabled "auto", STATIC=editable, RUNTIME=disabled "runtime"
  - Warning for empty static variable values
- [ ] `DialogueEventListenerInspector`:
  - eventName dropdown from DialogueEvents asset (not free-text)
  - Stale event name → `⚠ Unknown event: X` warning
  - @Required: red highlight when no event selected
  - Reaction enum dropdown
- [ ] Manual testing:
  - [ ] DialogueComponent: add/remove conditional dialogues, set conditions via dropdowns
  - [ ] Condition ordering: drag or up/down to reorder
  - [ ] Default dialogue picker works, "Open" opens DialogueEditorPanel
  - [ ] `onConversationEnd` event ref editor works (set, clear, dropdown selection)
  - [ ] Variable table shows correct types (auto/static/runtime)
  - [ ] Static variable editing saves to component
  - [ ] Preview section shows default dialogue content
  - [ ] DialogueEventListener: event dropdown populated from asset
  - [ ] Stale event warning visible
  - [ ] Empty event name shows red highlight

**Files:**

| File | Change |
|------|--------|
| `editor/ui/inspectors/DialogueComponentInspector.java` | **NEW** |
| `editor/ui/inspectors/DialogueEventListenerInspector.java` | **NEW** |

**Acceptance criteria:**
- `DialogueComponentInspector` renders when a `DialogueComponent` is selected in the scene
- Conditional dialogues can be added, removed, and reordered; each entry has condition dropdowns and a dialogue picker
- Condition event names are dropdowns populated from `DialogueEvents` asset (no free-text input)
- Default dialogue picker shows `.dialogue.json` assets; `[Open]` button opens the DialogueEditorPanel
- Variable table derives from the global `DialogueVariables` asset: AUTO shows "auto" (disabled), STATIC shows editable text field, RUNTIME shows "runtime" (disabled)
- Empty static variable values show a warning icon
- Preview section is collapsible and shows the default dialogue content (read-only)
- `DialogueEventListenerInspector` renders event name as a dropdown from `DialogueEvents` asset
- Stale event names (removed from asset) show `⚠ Unknown event: X` warning
- No event selected → @Required red highlight visible
- Reaction field renders as an enum dropdown

---

## Phase 4: Asset Inspector Renderers

**Design ref:** §1 — Editor editing (DialogueVariables), §5 — Editor editing (DialogueEvents)

- [ ] `DialogueVariablesInspectorRenderer`:
  - Renders variable list: name, type (AUTO/STATIC/RUNTIME) dropdown
  - Add/remove/edit variables
  - Save button
  - Register in `AssetInspectorRegistry`
- [ ] `DialogueEventsInspectorRenderer`:
  - Renders event name list
  - Add/remove/rename events
  - Save button
  - Register in `AssetInspectorRegistry`
- [ ] Manual testing:
  - [ ] Click `variables.dialogue-vars.json` in AssetBrowser → shows in InspectorPanel
  - [ ] Add/remove/edit variables, save persists
  - [ ] Click `events.dialogue-events.json` in AssetBrowser → shows in InspectorPanel
  - [ ] Add/remove/rename events, save persists
  - [ ] Quick links from DialogueEditorPanel toolbar open correct assets

**Files:**

| File | Change |
|------|--------|
| `editor/panels/inspector/DialogueVariablesInspectorRenderer.java` | **NEW** |
| `editor/panels/inspector/DialogueEventsInspectorRenderer.java` | **NEW** |
| `editor/panels/inspector/AssetInspectorRegistry.java` | Register both renderers |

**Acceptance criteria:**
- Clicking `variables.dialogue-vars.json` in AssetBrowser shows the custom renderer in InspectorPanel
- Variables can be added, removed, edited (name + type); save button persists changes to disk
- Clicking `events.dialogue-events.json` in AssetBrowser shows the custom renderer in InspectorPanel
- Events can be added, removed, renamed; save button persists changes to disk
- Quick links (`[Variables ⇗]` / `[Events ⇗]`) in DialogueEditorPanel toolbar select the correct asset and show it in InspectorPanel
- Both renderers follow the existing `SpriteInspectorRenderer` / `AnimationInspectorRenderer` pattern

---

## Plan Acceptance Criteria

- [ ] `mvn compile` passes
- [ ] Editor runs without errors
- [ ] Full editor workflow end-to-end: create Variables/Events assets → create dialogue with lines, variables, choices, events → validate (warnings visible for issues) → save → assign dialogue to NPC via DialogueComponent inspector → configure conditional dialogues → add DialogueEventListener to door → play test in editor → dialogue plays correctly
- [ ] All editor panels follow existing UI patterns (ImGui push/pop rules, undo patterns, shortcut registration)
- [ ] No data loss: every edit that is saved persists correctly across editor restarts
