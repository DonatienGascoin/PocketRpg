# Component Migration Guide

> **Summary:** The component migration system handles scene/prefab files that reference component classes that have been moved or renamed. It automatically resolves stale class paths and prompts you to save to update the references.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [The Stale References Popup](#the-stale-references-popup)
4. [How Resolution Works](#how-resolution-works)
5. [Adding Explicit Migrations](#adding-explicit-migrations)
6. [Tips & Best Practices](#tips--best-practices)
7. [Troubleshooting](#troubleshooting)
8. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Fix stale references | Click "Save" in the popup that appears when loading |
| Add migration for renamed class | Add `addMigration("old.Name", "new.Name")` in `ComponentRegistry` static block |
| Check if fallback was used | `ComponentRegistry.wasFallbackUsed()` after loading |
| Get resolution details | `ComponentRegistry.getFallbackResolutions()` returns list of "old\|new" strings |

---

## Overview

Scene and prefab files store component types as fully qualified class names:

```json
{
  "type": "com.pocket.rpg.components.core.Transform",
  "properties": { ... }
}
```

When you move a component to a different package (e.g., reorganizing code), old scene files still reference the old path. Without migration support, these scenes would fail to load.

The component migration system solves this by:

1. **Trying the exact class name first** - Works for unchanged components
2. **Checking the migration map** - Handles explicitly renamed classes
3. **Falling back to simple name lookup** - Finds moved classes by their short name

When fallback resolution is used, the editor shows a popup prompting you to save the file, which updates all references to current class paths.

---

## The Stale References Popup

When you open a scene or prefab that contains outdated component class paths, a modal popup appears:

```
┌─────────────────────────────────────────────┐
│         Stale Component References          │
├─────────────────────────────────────────────┤
│ This scene contains outdated component      │
│ class paths. Components loaded correctly,   │
│ but save to update references.              │
│                                             │
│ • com.pocket.rpg.components.old.Transform   │
│   -> com.pocket.rpg.components.core.Transform    │
│                                             │
│ • com.pocket.rpg.components.OldName         │
│   -> com.pocket.rpg.components.NewName      │
│                                             │
├─────────────────────────────────────────────┤
│  [Don't Save]              [Save]           │
└─────────────────────────────────────────────┘
```

| Button | Action |
|--------|--------|
| **Save** | Saves the file immediately, updating all component class paths to current values |
| **Don't Save** | Closes popup without saving. References remain stale until you save manually. |

**Note:** The scene/prefab loads correctly either way. The popup is just a convenience to update references while you're already editing.

---

## How Resolution Works

The system uses a three-step resolution process:

```
Load component JSON
        │
        ▼
┌───────────────────┐
│ Class.forName()   │──── Found ────► Use class
│ (exact path)      │
└───────────────────┘
        │
     Not Found
        │
        ▼
┌───────────────────┐
│ Migration Map     │──── Found ────► Use migrated class
│ Lookup            │                 Log + track
└───────────────────┘
        │
     Not Found
        │
        ▼
┌───────────────────┐
│ Simple Name       │──── Found ────► Use resolved class
│ Fallback          │                 Log + track
└───────────────────┘
        │
     Not Found
        │
        ▼
   JsonParseException
   (with helpful message)
```

### Step 1: Exact Class Name

Tries to load the class exactly as stored in the JSON. This is the fast path for current files.

### Step 2: Migration Map

Checks `ComponentRegistry`'s migration map for an explicit old→new mapping. Use this for **renamed** classes where the simple name changed.

### Step 3: Simple Name Fallback

Extracts the simple class name (e.g., `Transform` from `com.old.package.Transform`) and looks it up in `ComponentRegistry`. This works because the registry indexes all components by both full name and simple name.

**Limitation:** If two components share the same simple name (e.g., `TestComponent` in different packages), the fallback cannot distinguish them. The editor logs an error at startup:

```
[ERROR] Duplicate component simple name 'TestComponent':
    com.pocket.rpg.components.TestComponent and
    com.pocket.rpg.other.TestComponent
    — simple name fallback will not work for these.
```

For such cases, use explicit migrations instead.

---

## Adding Explicit Migrations

When you **rename** a component class (not just move it), add an explicit migration:

**File:** `src/main/java/com/pocket/rpg/serialization/ComponentRegistry.java`

```java
public class ComponentRegistry {
    private static final Map<String, String> migrationMap = new HashMap<>();

    static {
        // Add migrations for renamed component classes
        addMigration(
            "com.pocket.rpg.components.OldComponentName",
            "com.pocket.rpg.components.NewComponentName"
        );

        // Can also handle package moves with name changes
        addMigration(
            "com.pocket.rpg.old.MyComponent",
            "com.pocket.rpg.new.subpackage.RenamedComponent"
        );
    }

    private static void addMigration(String oldName, String newName) {
        migrationMap.put(oldName, newName);
    }
}
```

### When to Use Explicit Migrations

| Scenario | Solution |
|----------|----------|
| Moved to different package, same name | Simple name fallback handles it automatically |
| Renamed class (different simple name) | **Add explicit migration** |
| Both moved and renamed | **Add explicit migration** |
| Two components have same simple name | **Add explicit migrations** for both |

---

## Tips & Best Practices

- **Save immediately when prompted** - Don't leave stale references in your scene files
- **Add migrations before refactoring** - If you're about to rename a component, add the migration first, then rename
- **Avoid duplicate simple names** - Keep component names unique across the codebase
- **Check logs during development** - Watch for WARNING messages about resolved fallbacks
- **Test scene loading after refactors** - Load all scenes after moving/renaming components to verify they still work

### Workflow for Refactoring Components

1. **Before renaming:** Add migration entry in `ComponentRegistry`
2. **Rename/move** the component class
3. **Open and save** all affected scenes/prefabs
4. **Remove migration** (optional, can keep for safety)

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Scene fails to load with "Unknown component type" | Component was renamed without migration. Add `addMigration()` entry. |
| Popup shows wrong resolution target | Duplicate simple names exist. Add explicit migration for the correct target. |
| Popup doesn't appear but logs show warnings | Scene was loaded programmatically (not via editor). Call `ComponentRegistry.wasFallbackUsed()` to check. |
| "Duplicate simple name" error at startup | Two components have the same class name. Rename one or add explicit migrations. |
| Saving doesn't update the references | Ensure you're saving the scene/prefab itself, not just the project. |

### Reading the Error Message

When a component cannot be resolved, the exception includes guidance:

```
Unknown component type: com.pocket.rpg.components.OldThing
Not found by full name, migration map, or simple name 'OldThing'.
If the class was renamed, add a migration to ComponentRegistry's static block:
  addMigration("com.pocket.rpg.components.OldThing", "com.pocket.rpg.components.NewName");
```

---

## Related

- [Components Guide](componentsGuide.md) - Component system overview
- [Prefab System Guide](prefabSystemGuide.md) - Prefab loading and editing
- [Asset Loader Guide](assetLoaderGuide.md) - General asset loading pipeline
