# Editor Event Bus Implementation Plan

**Status: COMPLETE**

## Overview

Replace scattered callback patterns with a centralized Event Bus system for cleaner inter-component communication in the editor.

**Goals:**
- Decouple publishers from subscribers
- Centralize event definitions for discoverability
- Eliminate null-check boilerplate
- Make it easy to add new listeners without modifying producers

---

## Current State Analysis

### Existing Patterns Found

| Pattern | Example | Problem |
|---------|---------|---------|
| Single callbacks | `setOnSaveCallback(Runnable)` | Null checks, only one listener |
| Consumer lists | `EditorSelectionManager.addListener()` | Manual iteration, scattered |
| Context bundles | `AnimationTimelineContext` with 7+ callbacks | Bloated constructors |
| Direct wiring | `spriteEditorPanel.setOnSaveCallback(assetBrowserPanel::refresh)` | Tight coupling |

### Events Currently in the System

| Event | Current Implementation | Producers | Consumers |
|-------|------------------------|-----------|-----------|
| Selection changed | `EditorSelectionManager.addListener()` | SelectionManager | InspectorPanel, HierarchyPanel, various tools |
| Scene changed | `EditorContext.onSceneChanged()` | EditorSceneController | SceneViewport, HierarchyPanel, SelectionManager |
| Asset metadata saved | `SpriteEditorPanel.setOnSaveCallback()` | SpriteEditorPanel | AssetBrowserPanel |
| Tiles picked | `TilePickerTool.setOnTilesPicked()` | TilePickerTool | TileBrushTool |
| Trigger selected | `CollisionPanel.setOnTriggerSelected()` | CollisionPanel | InspectorPanel |
| Trigger focus | `CollisionPanel.setOnTriggerFocus()` | CollisionPanel | SceneViewport |
| Recent scenes changed | `EditorSceneController.setOnRecentScenesChanged()` | SceneController | MenuBar |
| Preview frame changed | `AnimationPreviewRenderer.setOnPreviewFrameChanged()` | AnimationPreviewRenderer | AnimationEditorPanel |
| Tool switched | Various `setOnSwitchToXxx()` | SelectionTool, etc. | ToolManager |

---

## Proposed Event Bus Design

### Core Classes

```java
// Central event bus - singleton or via EditorContext
public class EditorEventBus {
    private static EditorEventBus instance;

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new HashMap<>();

    public static EditorEventBus get() { ... }

    public <T extends EditorEvent> void subscribe(Class<T> eventType, Consumer<T> handler);
    public <T extends EditorEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler);
    public void publish(EditorEvent event);
}

// Base event interface
public interface EditorEvent {
    // Marker interface, can add timestamp/source later if needed
}
```

### Event Definitions

```java
// Asset events
public record AssetChangedEvent(String path, ChangeType type) implements EditorEvent {
    public enum ChangeType { CREATED, MODIFIED, DELETED }
}

// Selection events
public record SelectionChangedEvent(SelectionType type, Object selection) implements EditorEvent {}

// Scene events
public record SceneChangedEvent(EditorScene scene) implements EditorEvent {}
public record SceneModifiedEvent(EditorScene scene) implements EditorEvent {}

// Tool events
public record ToolChangedEvent(EditorTool previousTool, EditorTool newTool) implements EditorEvent {}
public record TilesPickedEvent(TileSelection selection) implements EditorEvent {}
public record CollisionTypePickedEvent(CollisionType type) implements EditorEvent {}

// Trigger/collision events
public record TriggerSelectedEvent(TileCoordinate coordinate, Trigger trigger) implements EditorEvent {}
public record TriggerFocusRequestEvent(TileCoordinate coordinate) implements EditorEvent {}

// Animation events
public record AnimationFrameChangedEvent(int frameIndex) implements EditorEvent {}

// UI events
public record StatusMessageEvent(String message) implements EditorEvent {}
```

### Usage Examples

**Publishing:**
```java
// In SpriteEditorPanel.saveChanges():
EditorEventBus.get().publish(new AssetChangedEvent(texturePath, ChangeType.MODIFIED));

// In EditorSelectionManager:
EditorEventBus.get().publish(new SelectionChangedEvent(type, selection));
```

**Subscribing:**
```java
// In AssetBrowserPanel.initialize():
EditorEventBus.get().subscribe(AssetChangedEvent.class, event -> {
    if (event.type() == ChangeType.MODIFIED || event.type() == ChangeType.CREATED) {
        refresh();
    }
});

// In InspectorPanel:
EditorEventBus.get().subscribe(SelectionChangedEvent.class, this::onSelectionChanged);
```

---

## Implementation Phases

### Phase 1: Core Event Bus

Create the event bus infrastructure.

**Files:**

| File | Change |
|------|--------|
| `editor/events/EditorEventBus.java` | **NEW** - Core event bus |
| `editor/events/EditorEvent.java` | **NEW** - Base event interface |

**Tasks:**
- [x] Create `EditorEvent` marker interface
- [x] Create `EditorEventBus` singleton with subscribe/unsubscribe/publish
- [x] Add thread-safety if needed (editor is single-threaded, not needed)
- [x] Add optional event logging for debugging

---

### Phase 2: Asset Events

Migrate asset-related callbacks to events.

**Files:**

| File | Change |
|------|--------|
| `editor/events/AssetChangedEvent.java` | **NEW** |
| `panels/SpriteEditorPanel.java` | Publish `AssetChangedEvent` on save |
| `panels/AssetBrowserPanel.java` | Subscribe to `AssetChangedEvent` |
| `EditorUIController.java` | Remove direct callback wiring |

**Tasks:**
- [x] Create `AssetChangedEvent` record
- [x] Publish event in `SpriteEditorPanel.saveChanges()`
- [x] Subscribe in `AssetBrowserPanel.initialize()`
- [x] Remove `setOnSaveCallback` and direct wiring
- [x] Test: saving sprite metadata refreshes asset browser

---

### Phase 3: Selection Events

Migrate selection system to events (optional - current system works well).

**Files:**

| File | Change |
|------|--------|
| `editor/events/SelectionChangedEvent.java` | **NEW** |
| `EditorSelectionManager.java` | Publish event alongside existing listeners |

**Tasks:**
- [x] Create `SelectionChangedEvent` record
- [x] Publish event when selection changes
- [x] Keep existing `addListener()` for backwards compatibility initially
- [x] Migrate consumers to event bus (CollisionPanel, TilesetPalettePanel)

**Note:** EditorSelectionManager already has a good listener pattern. This phase is optional - we can keep the existing pattern or gradually migrate.

---

### Phase 4: Tool Events

Migrate tool-related callbacks.

**Files:**

| File | Change |
|------|--------|
| `editor/events/TilesPickedEvent.java` | **NEW** |
| `editor/events/ToolChangedEvent.java` | **NEW** |
| `tools/TilePickerTool.java` | Publish event instead of callback |
| `tools/TileBrushTool.java` | Subscribe to event |
| `EditorToolController.java` | Remove callback wiring |

**Tasks:**
- [x] Create tool event records (TilesPickedEvent, CollisionTypePickedEvent)
- [x] Migrate `TilePickerTool.setOnTilesPicked()` to event
- [x] Migrate `CollisionPickerTool.setOnCollisionPicked()` to event
- [x] Update consumers to subscribe (EditorToolController)

---

### Phase 5: Trigger/Collision Events

Migrate trigger selection events.

**Files:**

| File | Change |
|------|--------|
| `editor/events/TriggerSelectedEvent.java` | **NEW** |
| `editor/events/TriggerFocusRequestEvent.java` | **NEW** |
| `panels/collision/CollisionPanel.java` | Publish events |
| `panels/InspectorPanel.java` | Subscribe to trigger selection |

**Tasks:**
- [x] Create trigger event records (TriggerSelectedEvent, TriggerFocusRequestEvent)
- [x] Migrate `CollisionPanel.setOnTriggerSelected()` to event
- [x] Migrate `CollisionPanel.setOnTriggerFocus()` to event
- [x] Migrate `CollisionPickerTool.setOnTriggerSelected()` to event
- [x] Migrate `SelectionTool.setOnTriggerSelected()` to event
- [x] Update EditorUIController to subscribe to events

---

### Phase 6: Scene Events

Migrate scene change notifications.

**Files:**

| File | Change |
|------|--------|
| `editor/events/SceneChangedEvent.java` | **NEW** |
| `EditorContext.java` | Publish event (keep existing for now) |

**Tasks:**
- [x] Create `SceneChangedEvent` record
- [x] Create `RecentScenesChangedEvent` record
- [x] Publish event in `EditorContext.notifySceneChanged()`
- [x] Publish event in `EditorSceneController.addToRecentScenes()`
- [ ] Optionally migrate consumers (lower priority - existing pattern works)

---

### Phase 7: Status Message Events (Optional)

Unify status bar messaging.

**Files:**

| File | Change |
|------|--------|
| `editor/events/StatusMessageEvent.java` | **NEW** |
| `ui/StatusBar.java` | Subscribe to status events |
| Various panels | Publish status events instead of callbacks |

**Tasks:**
- [x] Create `StatusMessageEvent` record
- [x] Subscribe in StatusBar
- [x] Migrate EditorToolController to publish event
- [x] Migrate EditorSceneController to publish event
- [ ] Replace `setStatusCallback` pattern across remaining panels (future)

---

## Migration Strategy

### Approach: Gradual Migration

1. **Phase 1-2 first** - Get core working with one concrete use case (asset changes)
2. **Validate pattern** - Ensure it works well before broader migration
3. **Phases 3-6** - Migrate other events incrementally
4. **Keep backwards compatibility** - Don't break existing patterns immediately

### Backwards Compatibility

During migration, components can support both:
```java
// Old way (deprecated but still works)
spriteEditorPanel.setOnSaveCallback(assetBrowserPanel::refresh);

// New way
EditorEventBus.get().subscribe(AssetChangedEvent.class, e -> assetBrowserPanel.refresh());
```

Eventually remove old callbacks once all consumers migrated.

---

## Event Catalog (Final State)

| Event | Data | Publishers | Subscribers |
|-------|------|------------|-------------|
| `AssetChangedEvent` | path, type | SpriteEditorPanel, AnimationEditor, future editors | AssetBrowserPanel, InspectorPanel |
| `SelectionChangedEvent` | type, selection | EditorSelectionManager | InspectorPanel, HierarchyPanel, tools |
| `SceneChangedEvent` | scene | EditorSceneController | SceneViewport, HierarchyPanel, etc. |
| `ToolChangedEvent` | old, new | ToolManager | UI panels, status bar |
| `TilesPickedEvent` | selection | TilePickerTool | TileBrushTool |
| `TriggerSelectedEvent` | coordinate, trigger | CollisionPanel | InspectorPanel |
| `TriggerFocusRequestEvent` | coordinate | CollisionPanel, InspectorPanel | SceneViewport |
| `StatusMessageEvent` | message | Various | StatusBar |

---

## File Structure

```
editor/
  events/
    EditorEventBus.java      # Core event bus
    EditorEvent.java         # Base interface
    AssetChangedEvent.java   # Asset events
    SelectionChangedEvent.java
    SceneChangedEvent.java
    ToolChangedEvent.java
    TilesPickedEvent.java
    TriggerSelectedEvent.java
    TriggerFocusRequestEvent.java
    StatusMessageEvent.java
```

---

## Testing Strategy

### Phase 1-2 Testing
- [ ] Event bus correctly dispatches to multiple subscribers
- [ ] Unsubscribe works correctly
- [ ] Saving in Sprite Editor triggers Asset Browser refresh

### Phase 3-6 Testing
- [ ] Selection events propagate correctly
- [ ] Tool events update brush tool
- [ ] Trigger selection updates inspector
- [ ] No regressions in existing functionality

---

## Notes

- Keep event bus simple - no async, no filtering, no priorities initially
- Use Java records for events (immutable, concise)
- Consider adding event source tracking later if debugging becomes difficult
- The existing `EditorSelectionManager` pattern is actually good - may not need to fully migrate it

