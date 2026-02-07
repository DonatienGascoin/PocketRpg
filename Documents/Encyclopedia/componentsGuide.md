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
| Reference another component (hierarchy) | Add `@ComponentReference(source = Source.SELF)` to a transient field |
| Reference a component by key | Add `@ComponentReference(source = Source.KEY)` and set a `componentKey` on the target |
| Mark field as required | Add `@Required` annotation |
| Auto-add dependency | Add `@RequiredComponent(Type.class)` on the class |
| Add inspector tooltip | Add `@Tooltip("description")` on a field |

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
| v SpriteRenderer                   [K] [X] |
|   Sprite      [...] player.sprite          |
|   Color       [*******]                     |
|   Z Index     [0]                           |
+---------------------------------------------+
| v PlayerController                 [K] [X] |
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

### @ComponentReference

**Target:** Field
**Package:** `com.pocket.rpg.components`

Unified annotation for all component references. The `source` parameter determines how the reference is resolved.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `source` | `Source` enum | *(required)* | Where/how to resolve the component |

**Source options:**

| Source | Field modifier | Serialized? | Description |
|--------|---------------|-------------|-------------|
| `SELF` | `transient` | No | Same GameObject (sibling component) |
| `PARENT` | `transient` | No | Parent GameObject |
| `CHILDREN` | `transient` | No | Direct children only |
| `CHILDREN_RECURSIVE` | `transient` | No | All descendants (depth-first) |
| `KEY` | non-transient | Yes (as string) | Resolved via `ComponentKeyRegistry` by key |

#### Hierarchy sources (SELF, PARENT, CHILDREN, CHILDREN_RECURSIVE)

Fields must be `transient`. The engine searches for the component at the specified location when the scene initializes.

```java
public class PlayerMovement extends Component {
    // Resolved from same GameObject
    @ComponentReference(source = Source.SELF)
    private transient GridMovement movement;

    // Resolved from parent
    @ComponentReference(source = Source.PARENT)
    private transient Inventory parentInventory;

    // Collect all from children
    @ComponentReference(source = Source.CHILDREN)
    private transient List<SpriteRenderer> childRenderers;

    // Deep search through all descendants
    @ComponentReference(source = Source.CHILDREN_RECURSIVE)
    private transient SpriteRenderer deepRenderer;
}
```

**In the inspector**, hierarchy references appear as read-only status indicators:
- Green checkmark: Component found
- Red error: Component missing
- Gray question mark: Cannot verify at edit time

#### KEY source

Fields are non-transient and serialized as a plain JSON string (the component key). At runtime, `ComponentReferenceResolver` looks up the key in `ComponentKeyRegistry` and injects the component.

The target component must have a `componentKey` set (visible via the key toggle button in the component header).

```java
@ComponentMeta(category = "Player")
public class PlayerUI extends Component {
    @ComponentReference(source = Source.SELF)
    private transient GridMovement movement;

    // Serialized as string key, resolved at runtime via ComponentKeyRegistry
    @ComponentReference(source = Source.KEY)
    private Component elevationText;

    @Override
    public void update(float deltaTime) {
        if (elevationText instanceof UIText text) {
            text.setText(String.valueOf(movement.getZLevel()));
        }
    }
}
```

**In the editor**, KEY reference fields are rendered as a combo dropdown listing all components in the scene that have a `componentKey` set, filtered by the expected type. A `(none)` option clears the reference.

**Component Key field:** Every component has a `componentKey` field (on `Component.class`). It is hidden by default in the inspector — click the key icon button in the component header to reveal it. When a key is set, the field stays visible and the button is disabled.

**How it works step by step:**

1. In the editor, click the key icon on a component header to reveal the Component Key field
2. Set a key (e.g., `"ElevationText"` on a UIText)
3. On another component, select that key from the KEY reference dropdown
4. At runtime, `ComponentKeyRegistry` registers all components with keys during scene load
5. `ComponentReferenceResolver` reads the stored key and injects the component
6. Your component uses the resolved field directly

**Resolution timing:** All references (hierarchy and key) are resolved after hierarchy is established and keys are registered, before `onStart()`. See [Component Lifecycle](#component-lifecycle).

---

### @RequiredComponent

**Target:** Class (on Component subclasses)
**Package:** `com.pocket.rpg.components`

Declares that this component requires another component on the same GameObject. When the component is added (in both editor and runtime), the required component is automatically added if not already present.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `value` | `Class<? extends Component>` | — | The required component class |

```java
@RequiredComponent(TriggerZone.class)
public abstract class InteractableComponent extends Component {
    // TriggerZone is auto-added when any subclass is added to a GameObject
}
```

The annotation is `@Repeatable` — you can declare multiple requirements:

```java
@RequiredComponent(TriggerZone.class)
@RequiredComponent(SpriteRenderer.class)
public class MyComponent extends Component { ... }
```

**Editor behavior:**
- Adding a component auto-adds its requirements (tracked for undo — undoing removes both)
- The remove button is greyed out on required components, with a tooltip showing the dependent (e.g., "Required by Sign")

**Runtime behavior:**
- `GameObject.addComponent()` adds missing requirements before starting the component

**Requirements:**
- Target component must have a public no-arg constructor (validated at startup)
- Works with inheritance: `@RequiredComponent` on a base class applies to all subclasses

---

### @Tooltip

**Target:** Field
**Package:** `com.pocket.rpg.components`

Adds a tooltip to a field's label in the inspector. Shown when the user hovers over the label. If the label is truncated (too long for the label column), the tooltip shows both the full label name and the description.

```java
public class Sign extends InteractableComponent {
    @Tooltip("The message shown when the player reads this sign")
    private String message = "Hello, world!";
}
```

---

### Annotation Summary Table

| Annotation | Target | Serialized? | Editor Effect | Runtime Effect |
|------------|--------|-------------|---------------|----------------|
| `@ComponentMeta` | Class | N/A | Category in component browser | None |
| `@HideInInspector` | Field | Yes | Hidden from inspector | None |
| `@Required` | Field | Yes | Red highlight when empty | None |
| `@ComponentReference` (hierarchy) | Field | No (transient) | Status indicator | Auto-resolved from hierarchy |
| `@ComponentReference` (KEY) | Field | Yes (as string key) | Dropdown | Auto-resolved from ComponentKeyRegistry |
| `@RequiredComponent` | Class | N/A | Auto-adds dependency; blocks removal | Auto-adds dependency |
| `@Tooltip` | Field | N/A | Hover tooltip on label | None |

---

## Component Lifecycle

Understanding the initialization order is important when accessing references between components.

### Initialization Order

```
Scene Load
  |
  v
1. Components created and added to GameObjects
   - Components with componentKey register with ComponentKeyRegistry
  |
  v
2. Parent-child hierarchy established
  |
  v
3. @ComponentReference fields resolved (ComponentReferenceResolver)
   - Hierarchy sources: searches SELF / PARENT / CHILDREN / CHILDREN_RECURSIVE
   - KEY sources: reads stored key, looks up ComponentKeyRegistry
  |
  v
4. onStart() called on all components
   - All references are available here
  |
  v
5. update(deltaTime) called every frame
```

### What you can access where

| Location | @ComponentReference (hierarchy) | @ComponentReference (KEY) | Other Components |
|----------|:---:|:---:|:---:|
| Constructor | No | No | No |
| `onStart()` | Yes | Yes | Yes (via getComponent) |
| `update()` | Yes | Yes | Yes |
| `onDestroy()` | Yes | Yes | Depends |

### Key Rules

- **Never** access other components in the constructor
- `@ComponentReference` fields are available starting from `onStart()`
- Always null-check optional references before use
- `transient` fields are reset after deserialization — don't rely on initializers for transient fields
- KEY source fields are non-transient but serialized as string keys, not Component objects

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

### Using @ComponentReference (hierarchy)

1. Declare a `transient` field of the component type you need
2. Add `@ComponentReference` with the appropriate `source`
3. Use the field in `onStart()` or `update()` — it's already resolved

```java
public class PlayerController extends Component {
    @ComponentReference(source = Source.SELF)
    private transient GridMovement movement;

    @ComponentReference(source = Source.CHILDREN)
    private transient List<SpriteRenderer> childRenderers;

    @Override
    public void update(float deltaTime) {
        movement.move(Direction.UP);  // Already resolved
    }
}
```

### Using @ComponentReference (KEY)

1. On the target component, click the key icon in its header to reveal the Component Key field
2. Set a unique key string (e.g., `"ScoreText"`)
3. On your component, add a non-transient field with `@ComponentReference(source = Source.KEY)`
4. In the editor, select the key from the dropdown
5. At runtime, the component is injected automatically

```java
@ComponentMeta(category = "UI")
public class ScoreDisplay extends Component {
    @ComponentReference(source = Source.KEY)
    private Component scoreText;

    @ComponentReference(source = Source.KEY)
    private Component healthBar;

    @Override
    public void update(float deltaTime) {
        if (scoreText instanceof UIText text) {
            text.setText("Score: " + GameState.getScore());
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
- **Prefer @ComponentReference over getComponent()**: It's resolved once at startup, not every frame
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
| @ComponentReference field is null (hierarchy) | Ensure the target component exists on the correct entity; check `source` |
| @ComponentReference field is null (KEY) | Ensure the target component has a `componentKey` set and the key is selected in the dropdown |
| KEY dropdown is empty | No components with a `componentKey` set in the scene match the expected type |
| KEY shows "(missing)" | The key was set but the target component no longer exists or its key changed |
| @Required field not highlighting | Field must be null or empty string; 0 doesn't trigger for numbers |
| @RequiredComponent not auto-adding | Ensure the target class has a public no-arg constructor; check console for warnings at startup |
| Can't remove a component | It may be required by another component — hover the greyed-out button to see which |
| @Tooltip not showing | Ensure annotation is on the field, not the getter; check that `@Tooltip` is imported from `com.pocket.rpg.components` |

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

    @ComponentReference(source = Source.SELF)
    private transient Transform transform;

    @ComponentReference(source = Source.CHILDREN_RECURSIVE)
    private transient List<SpriteRenderer> renderers;

    @ComponentReference(source = Source.KEY)
    private Component warningText;

    @Override
    public void update(float deltaTime) {
        if (warningText instanceof UIText text) {
            text.setText("Danger: " + damageType);
        }
    }
}
```

---

## Related

- [Custom Inspector Guide](customInspectorGuide.md) — Building custom editor UIs for components
- [Asset Loader Guide](assetLoaderGuide.md) — Loading sprites, textures, audio
- [UI Designer Guide](uiDesignerGuide.md) — Creating UI elements
- [Inspector Panel Guide](inspectorPanelGuide.md) — General inspector usage
- [Interactable System Guide](interactableSystemGuide.md) — Building interactable objects (Sign, Chest, etc.)
