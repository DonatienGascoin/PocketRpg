# Components Guide

> **Summary:** Components are the building blocks of game object behavior in PocketRpg. This guide covers how to create, edit, and work with components in the editor inspector, including all available annotations and the component lifecycle.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Overview](#overview)
3. [Inspector Panel](#inspector-panel)
4. [Field Types](#field-types)
5. [List Fields](#list-fields)
6. [Annotations Reference](#annotations-reference)
7. [Component Lifecycle](#component-lifecycle)
8. [Workflows](#workflows)
9. [Tips & Best Practices](#tips--best-practices)
10. [Troubleshooting](#troubleshooting)
11. [Code Integration](#code-integration)
12. [Related](#related)

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
| Hide field from editor | Add `@HideInInspector` annotation |
| Reference another component | Add `@ComponentRef` to a transient field |
| Reference a UI element by key | Add `@UiKeyReference` to a non-transient UIComponent field |
| Mark field as required | Add `@Required` annotation |

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
+---------------------------------------------+
| Entity_42                                   |
+---------------------------------------------+
| v Transform                             [-] |
|   Position    X: [0.0]  Y: [0.0]           |
|   Rotation    [0.0]                         |
|   Scale       X: [1.0]  Y: [1.0]           |
+---------------------------------------------+
| v SpriteRenderer                        [X] |
|   Sprite      [...] player.sprite          |
|   Color       [*******]                     |
|   Z Index     [0]                           |
+---------------------------------------------+
| v PlayerController                      [X] |
|   Speed       [5.0]                         |
|   Items (3)                             [+] |
|     0   "Sword"                         [x] |
|     1   "Shield"                        [x] |
|     2   "Potion"                        [x] |
+---------------------------------------------+
|              [+ Add Component]              |
+---------------------------------------------+
```

---

## Field Types

The inspector automatically creates appropriate editors based on field type:

| Type | Editor | Notes |
|------|--------|-------|
| `int` | Integer input | Supports drag to change |
| `float` | Drag slider | Speed: 0.1 by default |
| `boolean` | Checkbox | |
| `String` | Text input | Or dropdown if backing a `@UiKeyReference` |
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

## Annotations Reference

PocketRpg provides several annotations to control how components behave in the editor and at runtime. All annotations are in the `com.pocket.rpg.components` or `com.pocket.rpg.serialization` packages.

### @ComponentMeta

**Target:** Class (on Component subclasses)
**Package:** `com.pocket.rpg.components`

Controls how the component appears in the component browser.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `category` | `String` | `"Other"` | Group name in the component browser |

```java
@ComponentMeta(category = "Gameplay")
public class HealthComponent extends Component {
    private int maxHealth = 100;
}
```

The annotation is `@Inherited`, so a category set on a base class applies to all subclasses.

**Standard categories:** UI, Rendering, Audio, Player, Gameplay, Physics — but any string works.

---

### @HideInInspector

**Target:** Field
**Package:** `com.pocket.rpg.components`

Prevents a field from appearing in the editor inspector. The field is still serialized (saved to the scene file) — it's just hidden from the UI.

```java
public class EnemyAI extends Component {
    private float aggroRange = 10f;           // Visible in inspector

    @HideInInspector
    private int internalState = 0;            // Serialized but hidden
}
```

Use this for internal bookkeeping fields that shouldn't be manually edited.

> **Note:** `transient` fields are never serialized and never shown in the inspector. Use `@HideInInspector` when you want the field saved but not editable.

---

### @Required

**Target:** Field
**Package:** `com.pocket.rpg.serialization`

Marks a field as required. When the field is `null` or empty, the inspector row is highlighted with a red background to alert the user.

```java
public class WarpZone extends Component {
    @Required
    private String targetScene;         // Red highlight if empty

    private String spawnPointName;      // No highlight when empty
}
```

Works with `String` (empty check), asset types (null check), and other reference types.

---

### @ComponentRef

**Target:** Field (must be `transient`)
**Package:** `com.pocket.rpg.components`

Marks a field as a component reference that is automatically resolved at runtime. The field is **not serialized** — instead, the engine searches for the component at the specified location when the scene initializes.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `source` | `Source` enum | `SELF` | Where to search for the component |
| `required` | `boolean` | `true` | Log warning if not found |

**Source options:**

| Source | Description |
|--------|-------------|
| `SELF` | Same GameObject (default) |
| `PARENT` | Parent GameObject |
| `CHILDREN` | Direct children only |
| `CHILDREN_RECURSIVE` | All descendants (depth-first) |

```java
public class PlayerMovement extends Component {
    // Resolved from same GameObject
    @ComponentRef
    private GridMovement movement;

    // Resolved from parent
    @ComponentRef(source = Source.PARENT)
    private Inventory parentInventory;

    // Collect all from children
    @ComponentRef(source = Source.CHILDREN)
    private List<Collider> childColliders;

    // Optional reference — no warning if missing
    @ComponentRef(required = false)
    private AudioSource audioSource;
}
```

**In the inspector**, `@ComponentRef` fields appear as read-only status indicators:
- Green checkmark: Component found on the entity
- Red error: Required component missing
- Gray question mark: Cannot verify (parent/children references)

**Resolution timing:** Resolved after hierarchy is established, before `onStart()`. See [Component Lifecycle](#component-lifecycle).

---

### @UiKeyReference

**Target:** Field (non-transient `UIComponent` subclass)
**Package:** `com.pocket.rpg.components`

Marks a UIComponent field to be automatically resolved from a UIManager key at runtime. Works like `@ComponentRef` but looks up UI components registered in the scene's `UIManager` by their `uiKey` string.

The annotated field is serialized as a plain JSON string (the uiKey value), not as a UIComponent object. In the editor, it is rendered as a **dropdown** of all available UI keys in the scene, filtered by the expected UIComponent type.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `required` | `boolean` | `true` | Log warning if key is empty or not found |

```java
@ComponentMeta(category = "Player")
public class PlayerUI extends Component {
    @ComponentRef
    private GridMovement movement;

    // Serialized as JSON string key, resolved at runtime via UIManager
    @UiKeyReference
    private UIText elevationText;

    @Override
    public void update(float deltaTime) {
        if (elevationText != null) {
            elevationText.setText(String.valueOf(movement.getZLevel()));
        }
    }
}
```

**In the editor**, the field is rendered as a combo dropdown. The dropdown lists all UI components in the scene that have a `uiKey` set and match the expected type (e.g., only `UIText` keys for a `UIText` field). A `(none)` option clears the reference.

If the saved key no longer exists in the scene, it appears as `"KeyName (missing)"` in the dropdown.

**How it works step by step:**

1. In the editor, you set a `uiKey` on a UI component (e.g., `"ElevationText"` on a UIText)
2. On another component, you select that key from the dropdown
3. At runtime, `UIManager` registers all UI components with their keys during scene load
4. `UiKeyRefResolver` reads the stored key, calls `UIManager.get(key, type)`, and injects the result
5. Your component uses the resolved field directly — no `UIManager.getText(...)` calls needed

**Resolution timing:** Resolved after UIManager keys are registered, before `onStart()`. See [Component Lifecycle](#component-lifecycle).

---

### Annotation Summary Table

| Annotation | Target | Serialized? | Editor Effect | Runtime Effect |
|------------|--------|-------------|---------------|----------------|
| `@ComponentMeta` | Class | N/A | Category in component browser | None |
| `@HideInInspector` | Field | Yes | Hidden from inspector | None |
| `@Required` | Field | Yes | Red highlight when empty | None |
| `@ComponentRef` | Field | No (transient) | Status indicator | Auto-resolved from hierarchy |
| `@UiKeyReference` | Field | Yes (as string key) | Dropdown | Auto-resolved from UIManager |

---

## Component Lifecycle

Understanding the initialization order is important when accessing references between components.

### Initialization Order

```
Scene Load
  |
  v
1. Components created and added to GameObjects
   - UIComponents register their uiKey with UIManager
  |
  v
2. Parent-child hierarchy established
  |
  v
3. @ComponentRef fields resolved (ComponentRefResolver)
   - Searches SELF / PARENT / CHILDREN per annotation
  |
  v
4. @UiKeyReference fields resolved (UiKeyRefResolver)
   - Reads stored key, looks up UIManager.get(key, type)
  |
  v
5. onStart() called on all components
   - All references are available here
  |
  v
6. update(deltaTime) called every frame
```

### What you can access where

| Location | @ComponentRef | @UiKeyReference | Other Components | UIManager |
|----------|:---:|:---:|:---:|:---:|
| Constructor | No | No | No | No |
| `onStart()` | Yes | Yes | Yes (via getComponent) | Yes |
| `update()` | Yes | Yes | Yes | Yes |
| `onDestroy()` | Yes | Yes | Depends | Depends |

### Key Rules

- **Never** access other components in the constructor
- `@ComponentRef` and `@UiKeyReference` fields are available starting from `onStart()`
- Always null-check optional references before use
- `transient` fields are reset after deserialization — don't rely on initializers for transient fields
- `@UiKeyReference` fields are non-transient but are serialized as string keys, not UIComponent objects

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

### Using @ComponentRef

1. Declare a `transient` field of the component type you need
2. Add `@ComponentRef` with the appropriate `source`
3. Use the field in `onStart()` or `update()` — it's already resolved

```java
public class PlayerController extends Component {
    @ComponentRef
    private GridMovement movement;

    @ComponentRef(source = Source.CHILDREN, required = false)
    private List<SpriteRenderer> childRenderers;

    @Override
    public void update(float deltaTime) {
        movement.move(Direction.UP);  // Already resolved
    }
}
```

### Using @UiKeyReference

1. Add a non-transient UIComponent field with `@UiKeyReference`
2. In the editor, set a `uiKey` on the target UI component
3. On your component, select the key from the dropdown
4. At runtime, the UIComponent is injected automatically

```java
@ComponentMeta(category = "UI")
public class ScoreDisplay extends Component {
    @UiKeyReference
    private UIText scoreText;

    @UiKeyReference(required = false)
    private UIImage healthBar;

    @Override
    public void update(float deltaTime) {
        if (scoreText != null) {
            scoreText.setText("Score: " + GameState.getScore());
        }
    }
}
```

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

---

## Tips & Best Practices

- **Initialize lists**: Declare lists with `= new ArrayList<>()` for immediate use
- **Use appropriate types**: Use `int` for counts, `float` for continuous values
- **Hide internal fields**: Use `@HideInInspector` for fields that shouldn't be edited
- **Provide defaults**: Always set sensible default values
- **Name clearly**: Field names become display labels (camelCase -> "Camel Case")
- **Prefer @ComponentRef over getComponent()**: It's resolved once at startup, not every frame
- **Prefer @UiKeyReference over UIManager.getText()**: Eliminates magic strings and resolves at startup
- **Null-check optional references**: Always guard with `if (ref != null)` for `required = false` references
- **Use @Required for critical fields**: Gives visual feedback in the editor when fields are empty

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| List shows as "List<?>" | Element type couldn't be determined; ensure generic is specified |
| Field not appearing | Check if field is `transient`, `static`, or has `@HideInInspector` |
| Component not in menu | Ensure class extends `Component` and has no-arg constructor |
| Changes not saving | Check for serialization errors in console |
| @ComponentRef field is null | Ensure the target component exists on the correct entity; check `source` |
| @UiKeyReference field is null | Ensure the UI component has a `uiKey` set and the key is selected in the dropdown |
| UI key dropdown is empty | No UI components with matching type have a `uiKey` set in the scene |
| UI key shows "(missing)" | The key was set but the UI component no longer exists or its key changed |
| @Required field not highlighting | Field must be null or empty string; 0 doesn't trigger for numbers |

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

### Combining Annotations

```java
@ComponentMeta(category = "Gameplay")
public class DamageZone extends Component {
    @Required
    private String damageType;

    private float damageAmount = 10f;

    @HideInInspector
    private int tickCounter = 0;

    @ComponentRef
    private Transform transform;

    @ComponentRef(source = Source.CHILDREN_RECURSIVE)
    private List<SpriteRenderer> renderers;

    @UiKeyReference(required = false)
    private UIText warningText;

    @Override
    public void update(float deltaTime) {
        if (warningText != null) {
            warningText.setText("Danger: " + damageType);
        }
    }
}
```

---

## Related

- [Custom Inspector Guide](customInspectorGuide.md) — Building custom editor UIs for components
- [Asset Loader Guide](assetLoaderGuide.md) — Loading sprites, textures, audio
- [UI Designer Guide](uiDesignerGuide.md) — Creating UI elements with uiKey
- [Inspector Panel Guide](inspectorPanelGuide.md) — General inspector usage
