# EditorGameObject Refactor: Architecture Comparison

Visual comparison of the CURRENT system vs the NEW system after the refactor described in `editor-gameobject-refactor.md`. All diagrams reflect final design decisions from `editor-gameobject-refactor-review.md`.

---

## 1. Class Hierarchy

### BEFORE

`EditorGameObject` and `GameObject` are **unrelated classes** connected only by the `IGameObject` interface. `HierarchyItem` extends `IGameObject`, adding hierarchy-specific methods. `Component` references its owner via two fields: `IGameObject owner` and `GameObject gameObject`.

```mermaid
classDiagram
    class IGameObject {
        <<interface>>
        +getName() String
        +getId() String
        +getTransform() Transform
        +getComponent(Class~T~) T
        +getComponents(Class~T~) List~T~
        +getAllComponents() List~Component~
        +isEnabled() boolean
        +isRuntime() boolean
        +isEditor() boolean
        +hasChildren() boolean
        +getParent() IGameObject
        +getChildren() List~IGameObject~
    }

    class HierarchyItem {
        <<interface -- extends IGameObject>>
        +getHierarchyParent() HierarchyItem
        +getHierarchyChildren() List~HierarchyItem~
        +isEditable() boolean
        +findComponentInParent(Class~T~) T
    }

    class GameObject {
        -String name
        -boolean enabled
        -Scene scene
        -List~Component~ components
        -Transform transform
        -GameObject parent
        -List~GameObject~ children
    }

    class EditorGameObject {
        -String id
        -String prefabId
        -String name
        -List~Component~ components
        -Map componentOverrides
        -EditorGameObject parent
        -List~EditorGameObject~ children
        -List~Component~ cachedMergedComponents
    }

    class RuntimeGameObjectAdapter {
        -GameObject gameObject
    }

    class Component {
        #IGameObject owner
        #GameObject gameObject
    }

    HierarchyItem --|> IGameObject : extends
    GameObject ..|> IGameObject : implements
    EditorGameObject ..|> Renderable : implements
    EditorGameObject ..|> HierarchyItem : implements
    RuntimeGameObjectAdapter ..|> HierarchyItem : implements
    Component --> IGameObject : owner
    Component --> GameObject : gameObject (nullable)
```

### AFTER

`EditorGameObject` **extends** `GameObject`. `IGameObject` is deleted. `HierarchyItem` is a standalone interface. `Component` has a single `gameObject` field. `EditorGameObject` no longer implements `Renderable` — rendering goes through `SpriteRenderer` components.

```mermaid
classDiagram
    class GameObject {
        -String name
        -boolean enabled
        -List~Component~ components
        -Transform transform
        -GameObject parent
        -List~GameObject~ children
        #setEnabledDirect(boolean)
        +isAncestorOf(GameObject) boolean
        +getAllComponents() List~Component~
        +isRuntime() boolean  %%default true
        +isEditor() boolean   %%default false
    }

    class EditorGameObject {
        -String id
        -String prefabId
        -Map~String,Set~String~~ overriddenFields
        +getId() String
        +setEnabled(boolean)  %%calls setEnabledDirect
        +isEnabled() boolean  %%walks parent chain
        +setParent(GameObject)  %%override, rejects non-EGO
        +replaceComponent(Component, Component)
        +refreshFromTemplate()
        +isRuntime() false
        +isEditor() true
        +update(float)  %%no-op
        +lateUpdate(float)  %%no-op
        +start()  %%no-op
        +destroy()  %%no-op
    }

    class HierarchyItem {
        <<interface -- standalone>>
        +getName() String
        +getId() String
        +getComponent(Class~T~) T
        +getAllComponents() List~Component~
        +isEnabled() boolean
        +isEditor() boolean
        +getHierarchyParent() HierarchyItem
        +getHierarchyChildren() List~HierarchyItem~
        +isEditable() boolean
    }

    class RuntimeGameObjectAdapter {
        -GameObject gameObject
    }

    class Component {
        #GameObject gameObject
        +setGameObject(GameObject)
    }

    EditorGameObject --|> GameObject : extends
    EditorGameObject ..|> HierarchyItem : implements
    RuntimeGameObjectAdapter ..|> HierarchyItem : implements
    Component --> GameObject : gameObject (single field, never null)
```

**Key changes from BEFORE:**
- `IGameObject` deleted entirely
- `Component` has one field (`gameObject`), not two — `setOwner(IGameObject)` becomes `setGameObject(GameObject)`
- `EditorGameObject` no longer implements `Renderable` (rendering goes through `SpriteRenderer` components)
- `EditorGameObject` overrides 4 lifecycle methods as no-ops (`update`, `lateUpdate`, `start`, `destroy`)
- `HierarchyItem` is standalone (not extending `IGameObject`), adds `isEditor()` method
- `GameObject` fields stay **private**; one protected helper (`setEnabledDirect`) for subclass access
- `GameObject.isAncestorOf()` made **public** (was private) — EGO deletes its own version, uses inherited
- `GameObject`'s unsafe generic no-arg `getComponents()` removed; replaced by `getAllComponents()`
- `EditorGameObject` adds `replaceComponent()` and `refreshFromTemplate()` methods
- `GameObject` no longer has a `scene` field (removed in Pre-work)
- No typed accessors (`getEditorChildren`/`getEditorParent`) — `getChildren()` returns `List<GameObject>`, ~5 sites cast inline

---

## 2. Component Ownership

### BEFORE

`Component.setOwner(IGameObject)` stores two references. The `gameObject` field is only set when the owner is a real `GameObject`. When `EditorGameObject` is the owner, `gameObject` is **null** -- causing failures in components that call `gameObject.getChildren()`.

```mermaid
flowchart TD
    subgraph "Component.setOwner(IGameObject owner)"
        A[setOwner called] --> B{owner instanceof GameObject?}
        B -->|Yes| C["this.owner = owner\nthis.gameObject = (GameObject) owner"]
        B -->|No| D["this.owner = owner\nthis.gameObject = null"]
    end

    subgraph "Problem: EditorGameObject as owner"
        D --> E["LayoutGroup calls\ngameObject.getChildren()"]
        E --> F["NullPointerException!\ngameObject is null"]
    end

    subgraph "Workaround: EditorUIBridge"
        G["bridge.createWrapper()"] --> H["comp.setOwner(wrapperGO)"]
        H --> I["gameObject = wrapperGO\n(non-null, temporary)"]
        I --> J["UIRenderer uses wrapper\ngetChildren() works"]
        J --> K["Next frame: wrapper recreated\nCached refs go stale"]
    end
```

### AFTER

`Component.setGameObject(GameObject)` stores a single reference. Since `EditorGameObject IS-A GameObject`, the field is always non-null and always valid. No instanceof check needed.

```mermaid
flowchart TD
    subgraph "Component.setGameObject(GameObject go)"
        A[setGameObject called] --> B["this.gameObject = go"]
    end

    subgraph "Runtime context"
        C[GameObject] --> B
        B --> D["gameObject is GameObject\ngameObject.getChildren() works"]
    end

    subgraph "Editor context"
        E[EditorGameObject] --> B
        B --> F["gameObject is EditorGameObject\nwhich IS-A GameObject\ngameObject.getChildren() works"]
    end
```

---

## 3. UI Rendering Flow

### BEFORE

`UIDesignerPanel` uses `EditorUIBridge` to create temporary `GameObject` wrappers. The bridge rebuilds wrappers on hierarchy changes, uses **reflection hacks** to bypass `GameObject` private fields, and temporarily reassigns component ownership.

```mermaid
sequenceDiagram
    participant Panel as UIDesignerPanel
    participant Bridge as EditorUIBridge
    participant EGO as EditorGameObject
    participant Wrapper as Wrapper GameObject
    participant Renderer as UIRenderer

    Panel->>Bridge: getUICanvases(scene)
    Bridge->>Bridge: needsRebuild?

    rect rgb(255, 230, 230)
        Note over Bridge: Rebuild path (~308 lines)
        Bridge->>Bridge: wrapperCache.clear()
        loop Each EditorGameObject with UI components
            Bridge->>EGO: getComponents()
            Bridge->>Wrapper: new GameObject(name)
            Note over Bridge: reflection: getDeclaredField("components")<br/>to remove auto-created Transform
            Note over Bridge: reflection: getDeclaredField("transform")<br/>to set UITransform on wrapper
            loop Each UI Component
                Bridge->>EGO: comp.setOwner(wrapper)
                Note over EGO: Component now owned by wrapper,<br/>not EditorGameObject
                Note over Bridge: reflection: getDeclaredField("components")<br/>to add component to wrapper
            end
        end
        Bridge->>Bridge: Set up wrapper parent-child hierarchy
        Bridge->>Bridge: Sort children by order
        Bridge->>Bridge: Collect root UICanvases
    end

    Bridge-->>Panel: List of UICanvas
    Panel->>Renderer: render(canvases)
    Renderer->>Wrapper: root.getChildren()
    Renderer->>Wrapper: child.getComponent(UIImage.class)
```

### AFTER

`UIDesignerPanel` collects `UICanvas` components directly from `EditorGameObject` entities and passes them to `UIRenderer`. No bridge, no wrappers, no reflection, no ownership reassignment. `EditorSceneRenderer` submits `SpriteRenderer` components (not `EditorGameObject` entities) for unified rendering with runtime.

```mermaid
sequenceDiagram
    participant Panel as UIDesignerPanel
    participant EGO as EditorGameObject
    participant Renderer as UIRenderer

    Panel->>EGO: scene.getEntities()
    Panel->>EGO: entity.getComponent(UICanvas.class)
    Note over Panel: Collect root canvases directly<br/>(EditorGameObject IS-A GameObject)
    Panel->>Renderer: render(canvases)
    Renderer->>EGO: root.getChildren()
    Note over Renderer: getChildren() returns<br/>List of GameObject (inherited)<br/>containing EditorGameObjects
    Renderer->>EGO: child.getComponent(UIImage.class)
    Note over Renderer: Components owned by<br/>the same EditorGameObject<br/>throughout -- no stale refs
```

---

## 4. Prefab Instance Model

### BEFORE

Prefab instances store **no real components**. Field values live in a `Map<String, Map<String, Object>>` override map. Components are cloned from the prefab template **on demand** into a transient cache, which is invalidated on any change. Transform data is stored as raw `float[]` arrays in the override map.

```mermaid
flowchart TD
    subgraph "EditorGameObject -- Prefab Instance"
        A["componentOverrides\nMap of String to Map of String to Object"]
        B["cachedMergedComponents\n(transient, nullable)"]
        C["components list\n(empty for prefab instances)"]
    end

    subgraph "On getComponents()"
        D{cachedMergedComponents\n== null?}
        D -->|Yes| E[getMergedComponents]
        D -->|No| F[Return cache]
        E --> G["Load Prefab template"]
        G --> H["Clone each component"]
        H --> I["Apply overrides from map"]
        I --> J["Apply transform from\nfloat array in overrides"]
        J --> K["ensureOwnerSet()\n(deferred ownership)"]
        K --> B
    end

    subgraph "On field change"
        L["setFieldValue()"] --> M["Write to componentOverrides map"]
        M --> N["invalidateComponentCache()"]
        N --> O["cachedMergedComponents = null"]
        O --> P["Next getComponents() reclones everything"]
    end
```

### AFTER

Prefab instances store **real component instances** (cloned at creation time) in the inherited `components` list. An `overriddenFields` Set tracks which fields the user explicitly changed. No caching, no invalidation, no deferred ownership.

```mermaid
flowchart TD
    subgraph "EditorGameObject -- Prefab Instance"
        A["components (inherited from GameObject)\nList of Component -- real instances"]
        B["overriddenFields\nMap of String to Set of String"]
    end

    subgraph "On creation / load"
        C["Clone components from prefab template"] --> D["Apply override values"]
        D --> E["Store in inherited components list"]
        E --> F["component.setGameObject(this) -- immediate"]
    end

    subgraph "On field change"
        G["Set field on real component"] --> H["Add field name to overriddenFields"]
    end

    subgraph "On save"
        I["Diff current values vs template defaults"] --> J["Write prefabId + overridden fields only"]
    end

    subgraph "On template save -- refreshFromTemplate()"
        K["Capture overridden field values"] --> L["Re-clone all components from updated template"]
        L --> M2["Re-apply captured overridden values"]
        M2 --> N2["Status bar: 'Refreshed N instances'"]
    end

    subgraph "Closed scenes"
        O2["Scene file stores prefabId + diffs only"] --> P2["On open: clones from latest template"]
        P2 --> Q2["Always up to date"]
    end
```

---

## 5. Scene Access

### BEFORE

`GameObject` holds a `Scene scene` back-reference set by `Scene.addGameObject()`. Components access scene systems via `gameObject.getScene()`. `EditorGameObject` has no `scene` field, so after inheritance it would inherit a null `scene` -- causing NPEs.

```mermaid
flowchart LR
    subgraph "Runtime"
        GO[GameObject] -->|"scene field"| S[Scene]
        S -->|"addGameObject: go.setScene this"| GO
        C1[Component] -->|"gameObject.getScene()"| S
        C1 -->|".getCollisionSystem()"| CS[CollisionSystem]
        C1 -->|".getCamera()"| Cam[GameCamera]
    end

    subgraph "Editor"
        EGO[EditorGameObject] -.->|"no scene field"| X["null"]
        C2[Component on EGO] -.->|"gameObject is null"| X
    end
```

### AFTER

The `scene` field is removed from `GameObject` (Pre-work phase). Components use static `SceneManager.getCurrentScene()` for scene access. Works naturally for both runtime (scene exists) and editor (returns null, skipped gracefully).

```mermaid
flowchart LR
    subgraph "SceneManager -- static access"
        SM["SceneManager.getCurrentScene()"]
    end

    subgraph "Runtime"
        C1[Component] -->|"SceneManager.getCurrentScene()"| SM
        SM --> S[Scene]
        S --> CS[CollisionSystem]
        S --> Cam[GameCamera]
    end

    subgraph "Editor"
        C2[Component on EGO] -->|"SceneManager.getCurrentScene()"| SM
        SM --> N["null -- no active scene"]
        N --> Skip["Guard: if scene != null"]
    end

    subgraph "GameObject"
        GO["No scene field\nNo setScene/getScene"]
    end
```

---

## 6. setEnabled() Flow

### BEFORE

`GameObject.setEnabled()` does three things: notifies components (`triggerEnable/triggerDisable`), invalidates Scene caches, and propagates to children. `EditorGameObject.setEnabled()` is a simple Lombok setter with no side effects. The two behaviors are incompatible -- the bridge's wrapper GameObjects may have stale enabled state.

```mermaid
flowchart TD
    subgraph "GameObject.setEnabled boolean"
        A[setEnabled called] --> B{enabled changed?}
        B -->|No| Z[Return early]
        B -->|Yes| C["this.enabled = enabled"]
        C --> D["Notify components\ntriggerEnable / triggerDisable"]
        D --> E{scene != null?}
        E -->|Yes| F["scene.registerCachedComponents\nor unregisterCachedComponents"]
        E -->|No| G[Skip]
        F --> H["Propagate to children\npropagateParentEnabledChange"]
        G --> H
        H --> I["Each child: notify components\n+ update scene caches\n+ recurse to grandchildren"]
    end

    subgraph "EditorGameObject.setEnabled boolean"
        J["Lombok @Setter"] --> K["this.enabled = enabled"]
        K --> L["No notifications\nNo cache updates\nNo propagation"]
    end

    subgraph "Bug: enabled state mismatch"
        M["EGO.setEnabled false"] --> N["Hierarchy panel sees: disabled"]
        M --> O["Bridge wrapper: still enabled\nuntil next rebuild"]
        N --> P["Visual mismatch"]
        O --> P
    end
```

### AFTER

`EditorGameObject` overrides `setEnabled()` with a simple field set (no component notifications, no scene caches -- editor does not need them). `GameObject.setEnabled()` uses `SceneManager.getCurrentScene()` instead of `this.scene` for cache registration. Since EGO IS-A GO, there is only one object -- no mismatch possible.

```mermaid
flowchart TD
    subgraph "GameObject.setEnabled boolean -- runtime"
        A[setEnabled called] --> B["this.enabled = enabled"]
        B --> C["Notify components"]
        C --> D{"SceneManager.getCurrentScene()\n!= null?"}
        D -->|Yes| E["Register/unregister caches"]
        D -->|No| F["Skip -- editor context"]
        E --> G["Propagate to children"]
        F --> G
    end

    subgraph "EditorGameObject.setEnabled boolean -- override"
        H[setEnabled called] --> I["setEnabledDirect(enabled)"]
        I --> J["Done -- no notifications\nno caches, no propagation"]
    end

    subgraph "isEnabled -- override"
        K["Check own enabled"] --> L{parent != null?}
        L -->|Yes| M["return parent.isEnabled()"]
        L -->|No| N["return true"]
    end

    subgraph "Single source of truth"
        O["UIRenderer reads EGO.isEnabled()"] --> P["Same object the hierarchy panel reads"]
        P --> Q["No mismatch possible"]
    end
```

---

## 7. Summary Table

| Metric | Before | After | Net Change |
|--------|--------|-------|------------|
| **Files deleted** | -- | `EditorUIBridge.java` (~308 lines), `IGameObject.java` (~125 lines) | **-433 lines** |
| **Files simplified** | -- | ~10+ files (Component, AlphaGroup, RenderDispatcher, UIDesignerPanel, etc.) | **~-200 lines** |
| **EGO internal reduction** | Reimplemented hierarchy, component mgmt, enabled state | Delegates to `super` for scratch entities | **~-300 lines** |
| **Prefab model** | `componentOverrides` map + `cachedMergedComponents` + `invalidateComponentCache()` + deferred ownership | Real components + `overriddenFields` Set | Simpler, fewer code paths |
| **Component owner fields** | 2 fields (`IGameObject owner`, `GameObject gameObject`) | 1 field (`GameObject gameObject`) | -1 field, -instanceof check |
| **Component setter** | `setOwner(IGameObject)` with instanceof dispatch | `setGameObject(GameObject)` — single assignment | Simpler, type-safe |
| **Reflection hacks** | 3 in EditorUIBridge (`getDeclaredField` for components, transform) | 0 | All removed |
| **`instanceof` dispatch** | 5+ checks (AlphaGroup, RenderDispatcher, Component.setOwner, etc.) | Most eliminated | Fewer type checks |
| **Stale cache bugs** | Wrapper recreation causes stale refs in UIScrollView, UIScrollbar | No wrappers, no stale refs | **4 HIGH + 2 MEDIUM bugs fixed** |
| **Scene access** | `gameObject.getScene()` back-reference | `SceneManager.getCurrentScene()` static | Decoupled, null-safe |
| **Hierarchy interfaces** | `IGameObject` (bridge) + `HierarchyItem extends IGameObject` | `HierarchyItem` standalone (with `isEditor()`) | Simpler contract |
| **Rendering** | EGO implements `Renderable`, RenderDispatcher has `instanceof` chain | EGO does NOT implement `Renderable`, EditorSceneRenderer submits `SpriteRenderer` components | Unified path |
| **Prefab auto-propagation** | Silent cache invalidation | Explicit `refreshFromTemplate()` on open scene; closed scenes auto-update on load | Clearer UX |
| **Protected helpers** | N/A (separate classes) | Private fields + 1 protected helper (`setEnabledDirect`) + `isAncestorOf()` made public | Minimal surface area |
| **Lifecycle safety** | EGO has no lifecycle methods | EGO overrides `update`, `lateUpdate`, `start`, `destroy` as no-ops | Prevents accidental component lifecycle |
| **Estimated total** | -- | ~930 lines deleted/simplified, ~100 new overrides | **Net ~-830 lines** |