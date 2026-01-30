# Prefab System Guide

> **Summary:** Save reusable entity templates as prefabs. Instantiate prefabs to quickly create pre-configured entities with components and child hierarchies.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Workflows](#workflows)
4. [Prefab File Format](#prefab-file-format)
5. [Built-in Prefabs](#built-in-prefabs)
6. [Code Integration](#code-integration)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Create prefab from entity | Right-click entity in Hierarchy → **Save as Prefab** |
| Instantiate prefab | Drag `.prefab.json` from Asset Browser into Scene View or Hierarchy |
| Use prefab in code | `PrefabRegistry.get("player").instantiate(scene)` |

---

## Overview

A prefab is a reusable entity template. It stores an entity's name, components, property values, and child hierarchy as a JSON file. When you instantiate a prefab, a new entity is created with all the saved configuration.

**Use prefabs for:**
- Entities you place multiple times (trees, NPCs, collectibles)
- Complex entity setups you don't want to recreate manually
- Templates that game code spawns at runtime

Prefab files are stored as `.prefab.json` in `gameData/assets/prefabs/`.

There are two types of prefabs:
- **JSON prefabs** — Saved from the editor, stored as JSON files
- **Code prefabs** — Defined in Java code, registered in the `PrefabRegistry`

---

## Workflows

### Creating a Prefab from an Entity

1. Set up an entity in the scene with all desired components and children
2. Right-click the entity in the [Hierarchy](hierarchy-panel-guide.md)
3. Select **Save as Prefab**
4. Enter a name in the save dialog
5. The prefab is saved to `gameData/assets/prefabs/`

The saved prefab captures:
- Entity name
- All components and their current property values
- Child entities (recursively)

### Instantiating a Prefab in the Editor

**Drag and drop:**
1. Open the [Asset Browser](asset-browser-guide.md)
2. Navigate to the `prefabs/` folder
3. Drag a `.prefab.json` file into the Scene View or Hierarchy
4. A new entity is created from the prefab template

### Instantiating a Prefab at Runtime

Use code to spawn prefab instances during gameplay:

```java
// By registry name
Prefab playerPrefab = PrefabRegistry.get("player");
GameObject player = playerPrefab.instantiate(scene);

// By JSON file
JsonPrefab treePrefab = Assets.load("prefabs/tree.prefab.json", JsonPrefab.class);
GameObject tree = treePrefab.instantiate(scene);
tree.getTransform().setPosition(5, 3);
```

---

## Prefab File Format

Prefab files use JSON format (`.prefab.json`):

```json
{
  "name": "Tree",
  "components": [
    {
      "type": "Transform",
      "properties": {
        "position": { "x": 0, "y": 0 },
        "scale": { "x": 1, "y": 1 }
      }
    },
    {
      "type": "SpriteRenderer",
      "properties": {
        "sprite": "sprites/environment/tree.png",
        "sortOrder": 0
      }
    }
  ],
  "children": [
    {
      "name": "Shadow",
      "components": [
        {
          "type": "SpriteRenderer",
          "properties": {
            "sprite": "sprites/environment/shadow.png",
            "sortOrder": -1
          }
        }
      ]
    }
  ]
}
```

---

## Built-in Prefabs

The engine includes code-defined prefabs registered in `PrefabRegistry`:

| Prefab | Description |
|--------|-------------|
| **PlayerPrefab** | Pre-configured player entity with SpriteRenderer, AnimatorComponent, GridMovement, PlayerMovement, PlayerCameraFollow |

Code prefabs are defined as Java classes implementing `Prefab` and registered at startup.

---

## Code Integration

### Registering a Code Prefab

```java
public class EnemyPrefab implements Prefab {
    @Override
    public String getName() {
        return "enemy";
    }

    @Override
    public GameObject instantiate(Scene scene) {
        GameObject enemy = new GameObject("Enemy");
        enemy.addComponent(new SpriteRenderer());
        enemy.addComponent(new AnimatorComponent());
        enemy.addComponent(new GridMovement());
        scene.addGameObject(enemy);
        return enemy;
    }
}

// Register at startup
PrefabRegistry.register(new EnemyPrefab());
```

### Spawning Prefabs at Runtime

```java
// From registry
GameObject npc = PrefabRegistry.get("enemy").instantiate(scene);
npc.getTransform().setPosition(10, 5);

// From JSON asset
JsonPrefab prefab = Assets.load("prefabs/collectible.prefab.json", JsonPrefab.class);
GameObject coin = prefab.instantiate(scene);
```

---

## Tips & Best Practices

- **Save common setups**: If you configure the same entity more than once, save it as a prefab
- **Update prefabs**: To update a prefab, modify an instance in the scene and save it again with the same name
- **Use code prefabs for complex logic**: When instantiation requires runtime setup beyond simple component values
- **Organize prefabs**: Use subfolders in `prefabs/` for categories (enemies, items, environment)
- **Prefabs are copies**: Instantiated entities are independent copies — changing one doesn't affect others or the prefab file

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Prefab not in Asset Browser | Check it's saved in `gameData/assets/prefabs/` with `.prefab.json` extension |
| Drag-drop not creating entity | Make sure you're dropping onto the Scene View or Hierarchy |
| Missing components after instantiation | Re-save the prefab — the original may be outdated |
| Code prefab not found | Verify it's registered in `PrefabRegistry` before use |
| Child entities missing | Check the prefab JSON has a `children` array |

---

## Related

- [Hierarchy Panel Guide](hierarchy-panel-guide.md) — Creating entities and saving as prefabs
- [Asset Browser Guide](asset-browser-guide.md) — Browsing and dragging prefabs
- [Components Guide](components-guide.md) — Components stored in prefabs
- [Save System Guide](save-system-guide.md) — Serialization format used by prefabs
