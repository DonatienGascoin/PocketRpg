# Quick Reference

How to add common things to the codebase.

## Add a New Component

1. Create class extending `Component` in `com.pocket.rpg.components`
2. Add no-arg constructor (required for serialization)
3. Override lifecycle methods (`onStart`, `update`, `onDestroy`)
4. Use `@ComponentReference(source)` for dependencies, `@Required` for mandatory fields

```java
public class MyComponent extends Component {
    @Required
    private String requiredField;

    @ComponentReference(source = Source.SELF)
    private transient Transform transform;

    public MyComponent() {} // Required

    @Override
    public void onStart() {
        // Initialize
    }

    @Override
    public void update(float deltaTime) {
        // Update logic
    }
}
```

## Add a New Asset Type

1. Create loader implementing `AssetLoader<T>` in `resources/loaders/`
2. Implement `load()`, `getSupportedExtensions()`
3. Register in `AssetManager.registerDefaultLoaders()`
4. Optional: implement `getEditorPanel()` for double-click editing

```java
public class MyAssetLoader implements AssetLoader<MyAsset> {
    @Override
    public MyAsset load(String path, LoadOptions options) {
        // Load and return asset
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[] { ".myasset" };
    }
}
```

## Add a New Editor Panel

1. Create class extending `EditorPanel` in `editor/panels/`
2. Call `super(panelId, defaultOpen)` in the constructor
3. Implement `render()` — check `isOpen()`, call `setContentVisible()` and `setFocused()`
4. Register in `EditorUIController`
5. Optionally override `provideShortcuts()` for panel-scoped shortcuts

```java
public class MyPanel extends EditorPanel {

    public MyPanel() {
        super("myPanel", true); // panelId, defaultOpen
    }

    @Override
    public void render() {
        if (!isOpen()) return;

        boolean visible = ImGui.begin(getDisplayName());
        setContentVisible(visible);
        setFocused(ImGui.isWindowFocused());

        if (visible) {
            ImGui.text("My panel content");
        }
        ImGui.end();
    }

    @Override
    public String getDisplayName() {
        return "My Panel";
    }

    // Optional: panel-scoped shortcuts (auto-registered)
    @Override
    public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
        return List.of(
            panelShortcut()
                .id("editor.myPanel.doThing")
                .displayName("Do Thing")
                .defaultBinding(ShortcutBinding.key(ImGuiKey.Space))
                .handler(this::doThing)
                .build()
        );
    }
}
```

Panel features you get automatically:
- Open/close state persisted via `EditorConfig`
- `toggle()`, `isOpen()`, `setOpen()` visibility management
- Focus tracked for shortcut scope resolution
- Registered in `EditorPanel.getAllPanels()` for context auto-discovery

## Add a New Collision Behavior

1. Create class implementing `TileBehavior` in `collision/behavior/`
2. Implement `checkMove()`, optionally `onEnter()`/`onExit()`
3. Register in `CollisionSystem` behavior map

```java
public class MyBehavior implements TileBehavior {
    @Override
    public boolean checkMove(GridMovement mover, int fromX, int fromY, int toX, int toY) {
        // Return true if move is allowed
        return true;
    }

    @Override
    public void onEnter(GridMovement mover, int x, int y) {
        // Called when entity enters tile
    }
}
```

## Add a Custom Inspector

1. Create class extending `CustomComponentInspector<T>`
2. Annotate with `@InspectorFor(MyComponent.class)`
3. Implement `draw()` — return true if any field changed

```java
@InspectorFor(MyComponent.class)
public class MyComponentInspector extends CustomComponentInspector<MyComponent> {
    @Override
    public boolean draw() {
        boolean changed = false;
        // 'component' is typed as MyComponent
        // 'entity' is HierarchyItem (always non-null)
        // 'editorEntity()' returns EditorGameObject or null in play mode

        changed |= FieldEditors.drawFloat("Speed", component, "speed", 0.1f);

        // Guard undo with editorEntity()
        if (editorEntity() != null) {
            // undo operations...
        }
        return changed;
    }
}
```

### Inspector Entity Access

| Need | Use |
|------|-----|
| `getComponent()`, `getHierarchyParent()`, `getHierarchyChildren()` | `entity` |
| Undo commands, `getPosition()`, prefab overrides | `editorEntity()` |
