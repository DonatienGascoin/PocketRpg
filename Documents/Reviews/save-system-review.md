# Save System Code Review

**Date:** 2026-01-22
**Reviewer:** Claude
**Branch:** save-system
**Files Reviewed:** 7 new files in `com.pocket.rpg.save`

---

## Summary

The save system implementation follows the approved architecture plan. It provides a clean, static API for save/load operations with scene integration via `SceneLifecycleListener`. The code compiles successfully and all existing tests pass.

**Overall Assessment:** APPROVED with minor recommendations for future improvements.

---

## Files Reviewed

### 1. ISaveable.java (Interface)

**Purpose:** Contract for components with custom save/load logic.

**Strengths:**
- Clean interface with sensible defaults (`hasSaveableState()` returns true)
- Well-documented with guidelines for implementers
- Flexible Map-based state allows any data structure

**No issues found.**

---

### 2. SaveData.java (Data Class)

**Purpose:** Root save file structure.

**Strengths:**
- Follows `SceneData` versioning pattern for migration support
- Clear separation of global state vs scene states
- UUID generated in constructor ensures unique save IDs

**No issues found.**

---

### 3. SavedSceneState.java (Data Class)

**Purpose:** Per-scene delta state.

**Strengths:**
- Stores only modified entities (delta approach)
- Supports destroyed entities tracking
- Scene flags for non-entity state

**No issues found.**

---

### 4. SavedEntityState.java (Data Class)

**Purpose:** Single entity runtime state.

**Strengths:**
- Position stored as nullable float[] (only saved if moved)
- Component states keyed by fully-qualified class name

**No issues found.**

---

### 5. SaveSlotInfo.java (Record)

**Purpose:** Lightweight metadata for UI display.

**Strengths:**
- Java record for immutable data
- Contains only display info, not full save data

**No issues found.**

---

### 6. PersistentId.java (Component)

**Purpose:** Marks GameObjects as saveable with stable IDs.

**Strengths:**
- Auto-generates ID if not set
- `deterministicId()` helper for predictable IDs
- Registers/unregisters with SaveManager in lifecycle hooks

**Minor Recommendations:**
- Consider adding validation to reject IDs containing path separators or special characters
- Could add `@Required` annotation on `id` field for editor visibility (though it auto-generates)

---

### 7. SaveManager.java (Static API)

**Purpose:** Main API for save/load operations.

**Strengths:**
- Singleton with `initialize()` pattern matches existing systems
- Scene lifecycle integration via `SceneLifecycleListener`
- Robust null checks throughout
- Number type conversion in `getGlobal()` handles Gson deserialization quirks
- Cross-platform save directory detection

**Code Quality:**
- Well-organized sections with clear comments
- Consistent error handling with informative messages
- No threading issues (single-threaded game assumed)

**Minor Recommendations:**

1. **Entity destruction timing (line 377-386):** Destroying an entity during `onStart()` via `removeGameObject()` could cause issues if scene iteration is in progress. Consider deferring destruction to next frame or using a "pending destruction" list.

2. **Save file atomicity:** Currently writes directly to the save file. If the game crashes mid-write, the save could be corrupted. Consider:
   ```java
   // Write to temp file first
   Path tempPath = savePath.resolveSibling(slotName + ".save.tmp");
   Files.writeString(tempPath, json);
   Files.move(tempPath, savePath, StandardCopyOption.ATOMIC_MOVE);
   ```

3. **Scene state capture timing:** `onSceneUnloaded()` currently does nothing. Could optionally auto-capture scene state here for safety:
   ```java
   private void onSceneUnloaded(Scene scene) {
       if (currentSave != null && currentSceneName != null) {
           SavedSceneState state = captureCurrentSceneState();
           currentSave.getSceneStates().put(currentSceneName, state);
       }
   }
   ```

4. **Testing consideration:** The static singleton pattern makes unit testing harder. Consider adding a `reset()` method for tests:
   ```java
   // For testing only
   static void reset() {
       instance = null;
   }
   ```

---

## Integration Points

| Integration | Status |
|-------------|--------|
| SceneLifecycleListener | Correctly hooks into scene load/unload |
| Serializer | Uses existing Gson infrastructure |
| Component lifecycle | PersistentId uses onStart/onDestroy correctly |
| Transform | Reads position via getTransform().getPosition() |

---

## Test Coverage

**Current:** No dedicated unit tests for save system.

**Recommended tests to add:**

1. `SaveDataTest` - Serialization round-trip
2. `SaveManagerTest` - Save/load operations with mock scene
3. `PersistentIdTest` - ID generation, registration

The lack of tests is acceptable for initial implementation, but tests should be added before significant changes.

---

## Documentation

The implementation matches the plan documents in `Documents/Plans/`:
- `save-system-architecture.md`
- `save-system-data-structures.md`
- `save-system-api.md`
- `save-system-examples.md`
- `save-system-scenarios.md`

**CLAUDE.md should be updated** to document the save system under Core Systems.

---

## Conclusion

The save system is well-implemented and follows established patterns in the codebase. It correctly uses:
- Existing `Serializer` for JSON handling
- `SceneLifecycleListener` for scene integration
- Standard component lifecycle hooks

The minor recommendations above are improvements for robustness but are not blockers for merging.

**Verdict:** Ready for user validation and merge.
