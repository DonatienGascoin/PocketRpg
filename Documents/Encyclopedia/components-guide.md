# Components Guide

> **Summary:** Components are the building blocks of game object behavior in PocketRpg. This guide covers how to create, edit, and work with components in the editor inspector.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Inspector Panel](#inspector-panel)
4. [Field Types](#field-types)
5. [List Fields](#list-fields)
6. [Workflows](#workflows)
7. [Tips & Best Practices](#tips--best-practices)
8. [Troubleshooting](#troubleshooting)
9. [Code Integration](#code-integration)
10. [Related](#related)

---

## Quick Reference

| Task | How |
|------|-----|
| Add component | Select entity > Inspector > "+" button > Choose component |
| Remove component | Click "X" next to component header |
| Edit field | Click on field value in inspector |
| Add list item | Click "+" button next to list header |
| Remove list item | Click "X" next to list element |
| Reset field | Click reset button (appears for overridden fields) |

---

## Overview

Components define the behavior and data of game objects. Each entity can have multiple components, each handling a specific aspect of functionality (rendering, physics, audio, etc.).

The component system follows Unity-style architecture:
- **Entity**: A container for components (EditorGameObject in editor, GameObject at runtime)
- **Component**: A class extending `Component` that holds data and behavior
- **Inspector**: The UI panel for editing component fields

Components are discovered automatically from anywhere under `com.pocket.rpg` and registered at startup. Any class extending `Component` with a no-arg constructor will be found and made available in the editor.

---

## Inspector Panel

When you select an entity, the Inspector panel shows all its components:

```
┌─────────────────────────────────────────┐
│ Entity_42                               │
├─────────────────────────────────────────┤
│ ▼ Transform                         [-] │
│   Position    X: [0.0]  Y: [0.0]        │
│   Rotation    [0.0]                     │
│   Scale       X: [1.0]  Y: [1.0]        │
├─────────────────────────────────────────┤
│ ▼ SpriteRenderer                    [X] │
│   Sprite      [...] player.sprite       │
│   Color       [■■■■■■■]                 │
│   Z Index     [0]                       │
├─────────────────────────────────────────┤
│ ▼ PlayerController                  [X] │
│   Speed       [5.0]                     │
│   Items (3)                         [+] │
│     0   "Sword"                     [x] │
│     1   "Shield"                    [x] │
│     2   "Potion"                    [x] │
├─────────────────────────────────────────┤
│              [+ Add Component]          │
└─────────────────────────────────────────┘
```

---

## Field Types

The inspector automatically creates appropriate editors based on field type:

| Type | Editor | Notes |
|------|--------|-------|
| `int` | Integer input | Supports drag to change |
| `float` | Drag slider | Speed: 0.1 by default |
| `boolean` | Checkbox | |
| `String` | Text input | |
| `Vector2f` | X/Y inputs | Side-by-side layout |
| `Vector3f` | X/Y/Z inputs | |
| `Vector4f` | Color picker | When named "color" or "tint" |
| `Enum` | Dropdown | Shows all enum values |
| `Sprite` | Asset picker | Click "..." to browse |
| `AudioClip` | Asset picker | Includes play/stop button |
| `List<T>` | List editor | Collapsible with add/remove |

---

## List Fields

List fields support adding, removing, and editing elements inline.

### Supported Element Types

- Primitives: `int`, `float`, `double`, `boolean`
- `String`
- Enums
- Asset types (`Sprite`, `Texture`, `AudioClip`, etc.)

### UI Structure

```
Items (3)                               [+]
  0   "First item"                     [x]
  1   "Second item"                    [x]
  2   "Third item"                     [x]

Sprites (2)                             [+]
  0   [...] player.sprite              [x]
  1   [...] enemy.sprite               [x]
```

### Adding Elements

1. Click the `+` button next to the list header
2. A new element with default value is added at the end
3. Edit the value inline

### Removing Elements

1. Click the `x` button next to the element
2. Element is removed immediately
3. All operations support undo (Ctrl+Z)

---

## Workflows

### Creating a Custom Component

1. Create a new class anywhere under `com.pocket.rpg` (e.g., `src/main/java/com/pocket/rpg/components/`)
2. Extend `Component`
3. Optionally add `@ComponentMeta` annotation for category grouping
4. Add fields (private with default values)
5. Rebuild project (mvn compile)
6. Component appears in "Add Component" menu

```java
import com.pocket.rpg.components.ComponentMeta;

@ComponentMeta(category = "Gameplay")
public class HealthComponent extends Component {
    private int maxHealth = 100;
    private int currentHealth = 100;
    private List<String> statusEffects;

    @Override
    public void update(float deltaTime) {
        // Game logic here
    }
}
```

### The @ComponentMeta Annotation

Use `@ComponentMeta` to control how your component appears in the component browser:

```java
@ComponentMeta(category = "Rendering")
public class ParticleEmitter extends Component { ... }

@ComponentMeta(category = "Audio")
public class SoundEmitter extends Component { ... }

@ComponentMeta(category = "Physics")
public class RigidBody extends Component { ... }
```

| Property | Description | Default |
|----------|-------------|---------|
| `category` | Group name in component browser | "Other" |

Components without `@ComponentMeta` appear in the "Other" category.

### Component Browser

The component browser supports fuzzy search:
- Type partial matches: "sprite" matches "Sprite Renderer"
- Word prefixes: "sprite r" matches "Sprite Renderer"
- Subsequences: "spritr" matches "Sprite Renderer"

Keyboard navigation:
- **Down Arrow** from search: Jump to first result
- **Up Arrow** from first result: Return to search
- **Enter**: Select focused component

### Editing Component Fields

1. Select an entity in the scene
2. Find the component in the inspector
3. Click on any field value to edit
4. Changes apply immediately
5. Use Ctrl+Z to undo

### Adding a List Field

1. Declare field as `List<ElementType>`
2. The inspector auto-detects the element type
3. List editor appears with add/remove controls

```java
private List<String> items;      // String list
private List<Sprite> sprites;    // Asset list
private List<Integer> counts;    // Primitive list
```

---

## Tips & Best Practices

- **Initialize lists**: Declare lists with `= new ArrayList<>()` for immediate use
- **Use appropriate types**: Use `int` for counts, `float` for continuous values
- **Hide internal fields**: Use `@HideInInspector` for fields that shouldn't be edited
- **Provide defaults**: Always set sensible default values
- **Name clearly**: Field names become display labels (camelCase -> "Camel Case")

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| List shows as "List<?>" | Element type couldn't be determined; ensure generic is specified |
| Field not appearing | Check if field is `transient`, `static`, or has `@HideInInspector` |
| Component not in menu | Ensure class extends `Component` and has no-arg constructor |
| Changes not saving | Check for serialization errors in console |

---

## Code Integration

### Accessing List Fields at Runtime

```java
public class InventoryComponent extends Component {
    private List<String> items = new ArrayList<>();

    public void addItem(String item) {
        items.add(item);
    }

    public boolean hasItem(String item) {
        return items.contains(item);
    }
}
```

### Working with Asset Lists

```java
public class AnimationComponent extends Component {
    private List<Sprite> frames = new ArrayList<>();
    private int currentFrame = 0;

    public Sprite getCurrentFrame() {
        return frames.isEmpty() ? null : frames.get(currentFrame);
    }
}
```

---

## Related

- [Custom Inspector Guide](custom-inspector-guide.md)
- [Asset Loader Guide](asset-loader-guide.md)
- [Animation Editor Guide](animation-editor-guide.md)
