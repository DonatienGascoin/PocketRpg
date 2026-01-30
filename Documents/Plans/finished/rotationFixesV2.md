# Rotation Fixes V2 - Complete Plan

## Issue 1: Rotation Undo Still Broken

### Root Cause
UITransformInspector manually handles ImGui activation/deactivation/undo instead of using the existing field editor infrastructure.

---

## Step 1: Create SetterUndoCommand

**File:** `src/main/java/com/pocket/rpg/editor/undo/commands/SetterUndoCommand.java`

Generic command that uses Consumer for execute/undo:
- `Consumer<T> setter` - the setter function
- `T oldValue`, `T newValue` - values for undo/redo
- Supports command merging for smooth drag operations

---

## Step 2: Add Getter/Setter Methods to Field Editors

### PrimitiveEditors.java - Add methods:

| Existing (reflection) | New (getter/setter) |
|----------------------|---------------------|
| `drawInt(label, component, fieldName)` | `drawInt(label, key, IntSupplier, IntConsumer)` |
| `drawFloat(label, component, fieldName, speed)` | `drawFloat(label, key, DoubleSupplier, DoubleConsumer, speed)` |
| `drawFloat(label, component, fieldName, speed, min, max, format)` | `drawFloat(label, key, DoubleSupplier, DoubleConsumer, speed, min, max, format)` |
| `drawFloatSlider(label, component, fieldName, min, max)` | `drawFloatSlider(label, key, DoubleSupplier, DoubleConsumer, min, max)` |
| `drawBoolean(label, component, fieldName)` | `drawBoolean(label, key, BooleanSupplier, Consumer<Boolean>)` |
| `drawString(label, component, fieldName)` | `drawString(label, key, Supplier<String>, Consumer<String>)` |

### VectorEditors.java - Add methods:

| Existing (reflection) | New (getter/setter) |
|----------------------|---------------------|
| `drawVector2f(label, component, fieldName, speed)` | `drawVector2f(label, key, Supplier<Vector2f>, Consumer<Vector2f>, speed)` |
| `drawVector3f(label, component, fieldName, speed)` | `drawVector3f(label, key, Supplier<Vector3f>, Consumer<Vector3f>, speed)` |
| `drawVector4f(label, component, fieldName, speed)` | `drawVector4f(label, key, Supplier<Vector4f>, Consumer<Vector4f>, speed)` |
| `drawColor(label, component, fieldName)` | `drawColor(label, key, Supplier<Vector4f>, Consumer<Vector4f>)` |

### EnumEditor.java - Add methods:

| Existing (reflection) | New (getter/setter) |
|----------------------|---------------------|
| `drawEnum(label, component, fieldName, enumClass)` | `drawEnum(label, key, Supplier<Enum<?>>, Consumer<Enum<?>>, enumClass)` |

### AssetEditor.java - Add methods:

| Existing (reflection) | New (getter/setter) |
|----------------------|---------------------|
| `drawAsset(label, component, fieldName, assetType, entity)` | `drawAsset(label, key, Supplier<T>, Consumer<T>, assetType)` |

---

## Step 3: Update FieldEditors Facade

**File:** `src/main/java/com/pocket/rpg/editor/ui/fields/FieldEditors.java`

Add facade methods for getter/setter pattern (delegates to specialized editors):

```java
// === PRIMITIVES (getter/setter) ===
public static boolean drawInt(String label, String key, IntSupplier getter, IntConsumer setter)
public static boolean drawFloat(String label, String key, DoubleSupplier getter, DoubleConsumer setter, float speed)
public static boolean drawFloat(String label, String key, DoubleSupplier getter, DoubleConsumer setter, float speed, float min, float max, String format)
public static boolean drawFloatSlider(String label, String key, DoubleSupplier getter, DoubleConsumer setter, float min, float max)
public static boolean drawBoolean(String label, String key, BooleanSupplier getter, Consumer<Boolean> setter)
public static boolean drawString(String label, String key, Supplier<String> getter, Consumer<String> setter)

// === VECTORS (getter/setter) ===
public static boolean drawVector2f(String label, String key, Supplier<Vector2f> getter, Consumer<Vector2f> setter, float speed)
public static boolean drawVector3f(String label, String key, Supplier<Vector3f> getter, Consumer<Vector3f> setter, float speed)
public static boolean drawVector4f(String label, String key, Supplier<Vector4f> getter, Consumer<Vector4f> setter, float speed)
public static boolean drawColor(String label, String key, Supplier<Vector4f> getter, Consumer<Vector4f> setter)

// === ENUM (getter/setter) ===
public static boolean drawEnum(String label, String key, Supplier<Enum<?>> getter, Consumer<Enum<?>> setter, Class<?> enumClass)

// === ASSET (getter/setter) ===
public static <T> boolean drawAsset(String label, String key, Supplier<T> getter, Consumer<T> setter, Class<T> assetType)
```

---

## Step 4: Convert CustomComponentInspector to Abstract Class

**File:** `src/main/java/com/pocket/rpg/editor/ui/inspectors/CustomComponentInspector.java`

Convert from interface to abstract class with bind/draw pattern for cached casting:

```java
public abstract class CustomComponentInspector<T extends Component> {

    protected T component;  // Cached cast - set once on bind()
    protected EditorGameObject entity;

    /**
     * Called once when component is selected for editing.
     * Caches the cast so draw() doesn't cast every frame.
     */
    @SuppressWarnings("unchecked")
    public void bind(Component component, EditorGameObject entity) {
        this.component = (T) component;
        this.entity = entity;
    }

    /**
     * Called when component is deselected or inspector is closed.
     */
    public void unbind() {
        this.component = null;
        this.entity = null;
    }

    /**
     * Check if currently bound to a component.
     */
    public boolean isBound() {
        return component != null;
    }

    /**
     * Draws the custom editor UI.
     * Uses cached component from bind() - no casting per frame.
     *
     * @return true if any field was changed
     */
    public abstract boolean draw();
}
```

### Update CustomComponentEditorRegistry

Call `bind()` when component selection changes, `unbind()` when deselected:

```java
public void setSelectedComponent(Component component, EditorGameObject entity) {
    if (currentInspector != null) {
        currentInspector.unbind();
    }
    currentInspector = getInspectorFor(component);
    if (currentInspector != null) {
        currentInspector.bind(component, entity);
    }
}

public boolean drawCurrentInspector() {
    if (currentInspector != null && currentInspector.isBound()) {
        return currentInspector.draw();
    }
    return false;
}
```

### Update all CustomComponentInspector implementations

Example - UITransformInspector:

```java
public class UITransformInspector extends CustomComponentInspector<UITransform> {

    @Override
    public boolean draw() {
        // component is already cached and typed as UITransform!
        boolean changed = false;

        // Direct access - no casting needed
        FieldEditors.drawFloat("##rotation", "uiTransform.rotation",
            component::getLocalRotation2D,
            component::setRotation2D,
            0.5f, -360f, 360f, "%.1f°");

        return changed;
    }
}
```

### Implementations to update:
- `UITransformInspector`
- `CameraInspector` (if exists)
- Any other custom inspectors

---

## Step 5: Simplify UITransformInspector

**File:** `src/main/java/com/pocket/rpg/editor/ui/inspectors/UITransformInspector.java`

Remove all manual undo handling code:
- Remove `isEditingRotation`, `editStartRotation` fields
- Remove `isEditingScale`, `editStartScale` fields
- Remove `createRotationCommand()`, `createScaleCommand()` methods
- Remove `UIRotationCommand`, `UIScaleCommand` classes (now use SetterUndoCommand)

Replace with simple FieldEditors calls:
```java
UITransform t = castComponent(component);

// Rotation - one line!
FieldEditors.drawFloat("##rotation", "uiTransform.rotation",
    t::getLocalRotation2D, t::setRotation2D,
    0.5f, -360f, 360f, "%.1f°");

// Scale X
FieldEditors.drawFloat("X##scale", "uiTransform.scaleX",
    () -> t.getLocalScale2D().x,
    v -> t.setScale2D(v, t.getLocalScale2D().y),
    0.01f, 0.01f, 10f, "%.2f");

// Scale Y
FieldEditors.drawFloat("Y##scale", "uiTransform.scaleY",
    () -> t.getLocalScale2D().y,
    v -> t.setScale2D(t.getLocalScale2D().x, v),
    0.01f, 0.01f, 10f, "%.2f");
```

---

## Issue 2: UIText Rotation Not Applied

### Root Cause
UIText.render() calculates the pivot in absolute screen coordinates:
```java
float pivotX = boxX + pivot.x * boxWidth;
float pivotY = boxY + pivot.y * boxHeight;
```

Then passes these to batchSprite() for EACH glyph:
```java
backend.batchSprite(glyphX, glyphY, ..., rotation, pivotX, pivotY, textColor);
```

The UIRenderingBackend.batchSprite() rotates each glyph quad around the pivot correctly, BUT the issue is that we're batching multiple sprites and each one is being transformed independently.

The REAL problem: UIDesignerRenderer.renderTextElement() calls `transform.setCalculatedPosition(bounds[0], bounds[1])` which should work, BUT the transform's `screenBounds` are set to the element's own size, not the canvas size:
```java
transform.setScreenBounds(width, height);  // width/height are element size, not canvas!
```

This breaks the coordinate system because UIText.render() uses `transform.getScreenPosition()` which depends on proper screen bounds.

### Fix
In UIDesignerRenderer.renderTextElement():
1. Set screen bounds to canvas dimensions
2. Use setCalculatedPosition for the element's actual position
3. OR: Bypass UIText.render() and render text directly with correct rotation

---

## Files to Modify

1. `UITransformInspector.java` - Fix rotation capture timing
2. `UIDesignerRenderer.java` - Fix text rendering bounds setup

---

## Verification

1. Change rotation in inspector, undo → should restore exact previous value
2. Add UIText to a rotated parent → text should rotate with parent
3. Rotate UITransform directly → text should rotate
