# Inspector System Refactor — Implementation Progress

## Plan 1: Quick Wins + Undo Tracker + UITransformInspector Refactor — COMPLETE

Merged in PR #10. All 4 phases done.

### What Was Done

- **Phase 1: Quick Wins** — `findComponentInParent<T>()` on HierarchyItem, `accentButton()` in FieldEditorUtils (smallButton + full-width overloads), fixed `pushOverrideStyle`/`popOverrideStyle` asymmetry (critical style stack corruption bug).
- **Phase 2: FieldUndoTracker** — Centralized undo tracking with `track()` (setter-based) and `trackReflection()` (prefab-override-aware). Selection-change clearing via `EntityInspector`. Removed stale undo maps from EditorFields.
- **Phase 3: UITransformInspector Refactor** — Replaced EditorLayout/EditorFields/LayoutContext with direct ImGui + shared helpers. Added `inlineField(label, fieldWidth, field)` to FieldEditorUtils. Added `drawFloatInline` width overloads to PrimitiveEditors. Replaced [Match texture size] with [M] toggle for 100% parent match. All [M] and MATCH PARENT buttons always visible (disabled with tooltip when no parent UITransform). Deleted EditorLayout.java, EditorFields.java (layout), LayoutContext.java.
- **Phase 4: Code Review** — User-reviewed during implementation. Post-merge fix: rotation drag field now uses direct `ImGui.dragFloat` + `FieldUndoTracker` to avoid `inspectorRow` width override consuming remaining space.

### Key Artifacts

| Item | Detail |
|------|--------|
| PR | #10 (merged to main) |
| Branch | `inspector-system-refactor` (deleted) |
| Net LOC | -246 lines (368 added, 614 removed) |
| Files deleted | EditorLayout.java, EditorFields.java (layout), LayoutContext.java |
| Files created | FieldUndoTracker.java |
| Reference updates | `.claude/reference/field-editors.md`, `.claude/reference/common-pitfalls.md` (NextItemData pitfall) |

### Lessons Learned

- `ImGui.setNextItemWidth()` is consumed by any `ItemAdd()` call including `text()`. Always set width after label text, not before. Use `FieldEditorUtils.inlineField(label, fieldWidth, field)`.
- `inspectorRow` sets its own `setNextItemWidth` internally, overriding any external call. For fields that need full remaining width outside of `inspectorRow`, use direct `ImGui.dragFloat` + `FieldUndoTracker.track()`.
- ImGui `beginDisabled()`/`endDisabled()` nests correctly. Use `ImGuiHoveredFlags.AllowWhenDisabled` for tooltips on disabled items.

---

## Plan 2: Inspector Authoring Improvements — NOT STARTED

See `plan2-inspector-authoring.md` for full spec. Depends on Plan 1 (complete).

### Future Work

- **Phase 1: `InspectorRow` utility** — Public layout helper for "label + button + field" rows, fixing 5 bugs from v2 review. Would simplify UITransformInspector's `calculateFlexWidth` and any future complex inspector rows.
- **Phase 2: `drawOptionalColor()` helper** — Extract UIButtonInspector's 70-line nullable color pattern into a 1-line call. Reusable for any optional override color field.
- **Phase 3: Encyclopedia guide update** — Add FieldUndoTracker, accentButton, InspectorRow, drawOptionalColor to customInspectorGuide.md.
- **Phase 4: Code review.**

---

## Reference Documents

| Document | Purpose |
|----------|---------|
| `designDoc.md` | Design document v3 — architecture, rationale, expert review findings |
| `comparison.md` | Unity vs PocketRpg inspector comparison |
| `plan1-quick-wins-undo-refactor.md` | Plan 1 spec (complete) |
| `plan2-inspector-authoring.md` | Plan 2 spec (future) |
