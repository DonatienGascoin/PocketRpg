# Plan 3: Editor Tooling

**PR:** `dialogue-editor`
**Depends on:** Plan 1 (data model, loaders), Plan 2 (components — for inspector targets)
**Design reference:** `Documents/Plans/active/dialogue-system/design.md`

All editor panels, inspectors, and asset renderers. Primarily manual testing.

---

## Phase 1: DialogueEditorPanel — Core

**Design ref:** §7 — DialogueEditorPanel

Basic two-column layout: dialogue list (left) + line/choice editor (right). Create, edit, save dialogues.

- [ ] Register `DIALOGUE_EDITOR` panel in `EditorUIController`
- [ ] Left column:
  - Dialogue list from asset browser (all `.dialogue.json` files)
  - Search/filter by name
  - `[+ Add]` creates new dialogue, `[- Remove]` deletes with confirmation popup
  - Warning icon `⚠` on dialogues with validation issues
- [ ] Right column — toolbar:
  - Dialogue name (read-only, derived from filename)
  - `[Save]` button, dirty tracking (`*` in title)
  - `[Variables ⇗]` `[Events ⇗]` quick links → `selectionManager.selectAsset()`
- [ ] Right column — lines:
  - Editable text fields for each line
  - `[+ Var ▾]` dropdown per line (populated from DialogueVariables asset)
  - `[╳]` delete button (disabled on last remaining line)
  - `[+ Add Line]` button
  - Drag handle for reorder
- [ ] Right column — choices:
  - `☑ Has choices` checkbox
  - Choice cards: text field, action type dropdown, target field (dialogue picker or event dropdown)
  - `[╳]` delete per choice
  - `[+ Add Choice]` button (disabled at 4)
- [ ] Opening the panel:
  - Double-click `.dialogue.json` in Asset Browser
  - Window menu → Dialogue Editor
- [ ] Manual testing:
  - [ ] Create new dialogue, add lines, save
  - [ ] Edit existing dialogue, modify lines
  - [ ] Add/remove lines, verify min-1 enforced
  - [ ] Add choices (max 4 enforced), set action types
  - [ ] `[+ Var]` inserts `[VAR_NAME]` at end of line
  - [ ] Delete dialogue with confirmation
  - [ ] Search filters dialogue list
  - [ ] Dirty tracking shows `*`, save clears it

**Files:**

| File | Change |
|------|--------|
| `editor/panels/DialogueEditorPanel.java` | **NEW** |
| `editor/EditorPanelType.java` | Already has `DIALOGUE_EDITOR` from Plan 1 |
| `editor/EditorUIController.java` | Register panel handler + menu item |

**Acceptance criteria:**
- Dialogue Editor panel opens from Window menu and from double-clicking `.dialogue.json` files
- Left column lists all `.dialogue.json` assets, search filters by name
- Creating a new dialogue produces a valid `.dialogue.json` file with one empty line
- Deleting a dialogue shows confirmation popup, then removes the file
- Lines are editable text fields; `[+ Var]` dropdown inserts `[VAR_NAME]` at end of text
- Cannot delete the last remaining line (button disabled)
- Choices section: `Has choices` checkbox toggles visibility; max 4 choices enforced (add button disabled at 4)
- Each choice has text, action type dropdown, and context-dependent target field
- Dirty tracking: `*` in title when unsaved, cleared on save
- `[Save]` button persists changes to disk via `DialogueLoader.save()`
- Quick links open Variables/Events assets in InspectorPanel

---

## Phase 2: Validation, Undo/Redo, Shortcuts

**Design ref:** §7 — Line Editor Details, Undo/Redo, Shortcuts

- [ ] Validation warnings:
  - Unknown variable `[TAG]` → orange `⚠` below line
  - Malformed tag `[BROKEN` → orange `⚠` below line
  - DIALOGUE action with no target → `⚠` on choice card + dialogue list
  - CUSTOM_EVENT action with no event → `⚠` on choice card
  - Empty choice text → `⚠` on choice card
  - `hasChoices=true` with empty choices → `⚠` on choices section
- [ ] Undo/redo:
  - `DialogueSnapshot` record: capture/restore deep copies
  - Own `Deque<DialogueSnapshot>` stacks (undoStack, redoStack, max 50)
  - Stacks cleared on dialogue switch
  - Unsaved changes confirmation popup on dialogue switch
  - `[Undo]` `[Redo]` toolbar buttons
- [ ] Shortcuts via `provideShortcuts(KeyboardLayout)`:
  - `Ctrl+S` → save (allowInInput=true)
  - `Ctrl+Z` / `Ctrl+W` (AZERTY) → undo (allowInInput=true)
  - `Ctrl+Y` → redo (allowInInput=true)
  - `Ctrl+Enter` → add line
- [ ] Manual testing:
  - [ ] Validation warnings appear for each case above
  - [ ] Warning icon in dialogue list reflects validation state
  - [ ] Undo/redo works for line edits, choice edits, add/remove
  - [ ] Switching dialogues clears undo stacks
  - [ ] Confirmation popup on switch with unsaved changes
  - [ ] All keyboard shortcuts work (including while typing)
  - [ ] AZERTY undo binding works

**Files:**

| File | Change |
|------|--------|
| `editor/panels/DialogueEditorPanel.java` | Add validation, undo/redo, shortcuts |

**Acceptance criteria:**
- Each validation case above produces a visible orange `⚠` warning at the correct location (below line, on choice card, in dialogue list)
- Dialogues with any validation warning show `⚠` icon in the left column list
- Undo reverses the last edit (line text, choice edit, add/remove); redo re-applies it
- Undo/redo stacks hold up to 50 entries
- Switching dialogues clears both undo and redo stacks
- If current dialogue has unsaved changes, switching shows a confirmation popup
- All 4 shortcuts work: Ctrl+S (save), Ctrl+Z/W (undo), Ctrl+Y (redo), Ctrl+Enter (add line)
- Shortcuts work while cursor is in a text input field (`allowInInput=true`)

---

## Phase 3: Component Inspectors

**Design ref:** §6 — Custom Inspector, §5 — DialogueEventListenerInspector

- [ ] `DialogueComponentInspector`:
  - Conditional dialogues list: add/remove/reorder entries
  - Per-entry: condition dropdowns (eventName from DialogueEvents asset, expectedState FIRED/NOT_FIRED), `[+ Add Condition]`, dialogue picker, `[Open]` button
  - Default dialogue picker with `[Open]` button
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
