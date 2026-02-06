# Inspector System Refactor — Implementation Progress

## What Must Be Done

### Plan 1: Quick Wins + Undo Tracker + UITransformInspector Refactor

- Phase 1: Quick wins — `findComponentInParent<T>()`, `accentButton()`, fix `pushOverrideStyle`/`popOverrideStyle` asymmetry (Critical bug fix).
- Phase 2: `FieldUndoTracker` — centralized undo tracking with string-key API, selection-change detection for `clear()`, legacy map clearing.
- Phase 3: UITransformInspector targeted refactor using Phase 1+2 tools. Delete `EditorLayout`/`EditorFields` (zero other consumers).
- Phase 4: Code review.

---

## Reference Documents

| Document | Purpose |
|----------|---------|
| `designDoc.md` | Design document v3 — architecture, rationale, expert review findings |
| `comparison.md` | Unity vs PocketRpg inspector comparison |

---

## Dependency Graph

```
Plan 1 Phase 1 (Quick Wins) ──────┐
                                   ├──> Plan 1 Phase 3 (UITransformInspector Refactor)
Plan 1 Phase 2 (FieldUndoTracker) ┘
```

Phases 1 and 2 are independent (can be parallel). Phase 3 depends on both.

---

## Implementation Status

### Plan 1: Quick Wins + Undo Tracker + UITransformInspector Refactor — IN PROGRESS
