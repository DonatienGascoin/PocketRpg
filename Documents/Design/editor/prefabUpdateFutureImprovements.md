# Prefab Update — Future Improvements

Follow-up features deferred from the core Prefab Edit Mode design (`prefabUpdateDesign.md`). These are independent from the main prefab editing workflow and can be implemented separately.

---

## 1. "Apply Overrides to Prefab" Workflow

A secondary workflow for updating field defaults without entering prefab edit mode. Available on prefab instances in the regular scene inspector.

### Use case

A user has placed a prefab instance, tweaked field values (creating overrides), and wants to "push" those values back into the prefab definition so all instances get them.

### Entry point

Inspector button on a prefab instance: **"Apply Overrides to Prefab"** (only for JSON prefabs).

### Behavior

1. For each overridden field on the instance, update the corresponding default value in the prefab definition
2. Remove the override from the instance (it now matches the new default)
3. Save the prefab to disk
4. Invalidate caches on all instances

This does **not** add or remove components -- it only updates default values. For structural changes, enter prefab edit mode.

### Implementation notes

- Add "Apply Overrides to Prefab" button in `EntityInspector.renderPrefabInfo()`
- Requires `PrefabRegistry.saveJsonPrefab()` (added by the core plan)

---

## 2. "Update Prefab from Entity" (Structural Re-export)

For cases where the user wants to completely redefine a prefab from a scratch entity.

### Entry point

Hierarchy context menu on a **scratch entity**: **"Save as Prefab"** (existing) -- but now, if the entered ID matches an existing JSON prefab, instead of blocking with "already exists", show a confirmation dialog:

```
+------------------------------------------------------+
|  Update Existing Prefab?                             |
+------------------------------------------------------+
|  A prefab with ID "chest" already exists.            |
|                                                      |
|  This will replace the prefab definition with the    |
|  components from this entity.                        |
|                                                      |
|  Changes:                                            |
|  + ChestInteraction (new component)                  |
|  ~ SpriteRenderer (2 fields differ)                  |
|  - OldComponent (will be removed)                    |
|                                                      |
|  Overrides on removed components will become          |
|  orphaned but won't cause errors.                    |
|                                                      |
|  [Update Prefab]  [Save as New]  [Cancel]            |
+------------------------------------------------------+
```

**"Update Prefab"**: Replaces the prefab definition entirely with the entity's current components. Same propagation logic as the core plan's instance impact section.

**"Save as New"**: Opens a new ID field so the user can create a separate prefab (current behavior).

### Implementation notes

- Modify `SavePrefabPopup` to allow overwriting existing JSON prefabs with confirmation dialog
- Reuses cache invalidation logic from the core plan
