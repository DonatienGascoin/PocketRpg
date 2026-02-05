package com.pocket.rpg.editor;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.PrefabEditStartedEvent;
import com.pocket.rpg.editor.events.PrefabEditStoppedEvent;
import com.pocket.rpg.editor.events.RequestPrefabEditEvent;
import com.pocket.rpg.editor.events.SceneWillChangeEvent;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.prefab.JsonPrefab;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PrefabEditController — lifecycle, undo scope isolation, dirty tracking.
 * No ImGui calls — tests controller logic only.
 */
class PrefabEditControllerTest {

    private EditorContext context;
    private PrefabEditController controller;

    @BeforeEach
    void setUp() {
        EditorEventBus.reset();
        UndoManager.getInstance().clear();

        // Create a real EditorContext with just the pieces we need
        context = new TestableEditorContext();
        controller = new PrefabEditController(context);
    }

    @AfterEach
    void tearDown() {
        // Clean up any open scopes
        if (controller.isActive()) {
            controller.exitEditMode();
        }
        UndoManager.getInstance().clear();
    }

    private JsonPrefab createTestPrefab(String id, String displayName) {
        JsonPrefab prefab = new JsonPrefab();
        prefab.setId(id);
        prefab.setDisplayName(displayName);
        prefab.setCategory("test");
        prefab.setSourcePath("prefabs/" + id + ".prefab.json");

        List<Component> components = new ArrayList<>();
        Transform t = new Transform();
        t.setPosition(5, 10, 0);
        components.add(t);
        prefab.setComponents(components);
        return prefab;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Nested
    class EnterEditMode {

        @Test
        void setsStateToEditing() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test Prefab");
            controller.enterEditMode(prefab);

            assertEquals(PrefabEditController.State.EDITING, controller.getState());
            assertTrue(controller.isActive());
        }

        @Test
        void createsWorkingEntity() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test Prefab");
            controller.enterEditMode(prefab);

            EditorGameObject entity = controller.getWorkingEntity();
            assertNotNull(entity);
            assertEquals("Test Prefab", entity.getName());
        }

        @Test
        void createsWorkingScene() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test Prefab");
            controller.enterEditMode(prefab);

            assertNotNull(controller.getWorkingScene());
            assertEquals(1, controller.getWorkingScene().getEntities().size());
        }

        @Test
        void workingEntityHasTransform() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test Prefab");
            controller.enterEditMode(prefab);

            EditorGameObject entity = controller.getWorkingEntity();
            Transform t = entity.getTransform();
            assertNotNull(t, "Working entity should have Transform");

            // Transform should be a different instance from the prefab's
            Transform prefabTransform = null;
            for (Component c : prefab.getComponents()) {
                if (c instanceof Transform pt) {
                    prefabTransform = pt;
                    break;
                }
            }
            assertNotSame(prefabTransform, t, "Transform should be a deep copy");
        }

        @Test
        void pushesUndoScope() {
            // Push a command before entering
            UndoManager.getInstance().push(new DummyCommand("before"));
            assertTrue(UndoManager.getInstance().canUndo());

            controller.enterEditMode(createTestPrefab("test-01", "Test"));

            // The scope should be pushed — parent's command should be inaccessible
            assertFalse(UndoManager.getInstance().canUndo());
        }

        @Test
        void setsModeManagerToPrefabEdit() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            assertTrue(context.getModeManager().isPrefabEditMode());
        }

        @Test
        void publishesPrefabEditStartedEvent() {
            AtomicBoolean received = new AtomicBoolean(false);
            EditorEventBus.get().subscribe(PrefabEditStartedEvent.class, e -> received.set(true));

            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            assertTrue(received.get());
        }

        @Test
        void startsClean() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            assertFalse(controller.isDirty());
        }

        @Test
        void ignoresNullPrefab() {
            controller.enterEditMode(null);
            assertFalse(controller.isActive());
        }

        @Test
        void ignoresIfAlreadyEditingSamePrefab() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test");
            controller.enterEditMode(prefab);
            EditorGameObject firstEntity = controller.getWorkingEntity();

            controller.enterEditMode(prefab);
            assertSame(firstEntity, controller.getWorkingEntity());
        }
    }

    // ========================================================================
    // DEEP COPY ISOLATION
    // ========================================================================

    @Nested
    class DeepCopyIsolation {

        @Test
        void workingEntityComponentsAreDifferentInstances() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test");
            controller.enterEditMode(prefab);

            // Working entity's components should be different instances
            for (Component workingComp : controller.getWorkingEntity().getComponents()) {
                for (Component prefabComp : prefab.getComponents()) {
                    if (workingComp.getClass() == prefabComp.getClass()) {
                        assertNotSame(workingComp, prefabComp,
                                "Component " + workingComp.getClass().getSimpleName() + " should be a different instance");
                    }
                }
            }
        }
    }

    // ========================================================================
    // EXIT
    // ========================================================================

    @Nested
    class ExitEditMode {

        @Test
        void setsStateToInactive() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.exitEditMode();

            assertEquals(PrefabEditController.State.INACTIVE, controller.getState());
            assertFalse(controller.isActive());
        }

        @Test
        void restoresModeToScene() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.exitEditMode();

            assertTrue(context.getModeManager().isSceneMode());
        }

        @Test
        void popsUndoScope() {
            UndoManager.getInstance().push(new DummyCommand("parent"));
            controller.enterEditMode(createTestPrefab("test-01", "Test"));

            // In scope — parent command invisible
            assertFalse(UndoManager.getInstance().canUndo());

            controller.exitEditMode();

            // Scope popped — parent command visible again
            assertTrue(UndoManager.getInstance().canUndo());
        }

        @Test
        void publishesPrefabEditStoppedEvent() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));

            AtomicBoolean received = new AtomicBoolean(false);
            EditorEventBus.get().subscribe(PrefabEditStoppedEvent.class, e -> received.set(true));

            controller.exitEditMode();
            assertTrue(received.get());
        }

        @Test
        void clearsWorkingReferences() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.exitEditMode();

            assertNull(controller.getWorkingEntity());
            assertNull(controller.getWorkingScene());
            assertNull(controller.getTargetPrefab());
        }
    }

    // ========================================================================
    // REQUEST EXIT
    // ========================================================================

    @Nested
    class RequestExit {

        @Test
        void exitsImmediatelyWhenClean() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.requestExit(null);

            assertFalse(controller.isActive());
        }

        @Test
        void runsAfterExitCallbackWhenClean() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));

            AtomicBoolean ran = new AtomicBoolean(false);
            controller.requestExit(() -> ran.set(true));

            assertTrue(ran.get());
        }

        @Test
        void doesNotExitImmediatelyWhenDirty() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.markDirty();

            controller.requestExit(null);

            // Should still be active — waiting for confirmation
            assertTrue(controller.isActive());
        }

        @Test
        void runsCallbackIfNotEditing() {
            AtomicBoolean ran = new AtomicBoolean(false);
            controller.requestExit(() -> ran.set(true));
            assertTrue(ran.get());
        }
    }

    // ========================================================================
    // DIRTY TRACKING
    // ========================================================================

    @Nested
    class DirtyTracking {

        @Test
        void markDirtySetsFlag() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            assertFalse(controller.isDirty());

            controller.markDirty();
            assertTrue(controller.isDirty());
        }

        @Test
        void exitClearsDirty() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.markDirty();
            controller.exitEditMode();

            assertFalse(controller.isDirty());
        }
    }

    // ========================================================================
    // UNDO SCOPE ISOLATION
    // ========================================================================

    @Nested
    class UndoScopeIsolation {

        @Test
        void commandsInScopeDoNotAffectParent() {
            UndoManager.getInstance().push(new DummyCommand("parent-cmd"));

            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            UndoManager.getInstance().push(new DummyCommand("prefab-cmd"));

            assertTrue(UndoManager.getInstance().canUndo());
            assertEquals("prefab-cmd", UndoManager.getInstance().getUndoDescription());

            controller.exitEditMode();

            // Parent command should be restored
            assertTrue(UndoManager.getInstance().canUndo());
            assertEquals("parent-cmd", UndoManager.getInstance().getUndoDescription());
        }
    }

    // ========================================================================
    // SCENE WILL CHANGE EVENT
    // ========================================================================

    @Nested
    class SceneWillChangeEventHandling {

        @Test
        void cancelsEventWhenDirty() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));
            controller.markDirty();

            SceneWillChangeEvent event = new SceneWillChangeEvent();
            EditorEventBus.get().publish(event);

            assertTrue(event.isCancelled());
            assertTrue(controller.isActive()); // Still editing
        }

        @Test
        void exitsCleanlyAndDoesNotCancelWhenNotDirty() {
            controller.enterEditMode(createTestPrefab("test-01", "Test"));

            SceneWillChangeEvent event = new SceneWillChangeEvent();
            EditorEventBus.get().publish(event);

            assertFalse(event.isCancelled());
            assertFalse(controller.isActive()); // Exited
        }
    }

    // ========================================================================
    // REQUEST PREFAB EDIT EVENT
    // ========================================================================

    @Nested
    class RequestPrefabEditEventHandling {

        @Test
        void entersEditModeViaEvent() {
            JsonPrefab prefab = createTestPrefab("test-01", "Test");
            EditorEventBus.get().publish(new RequestPrefabEditEvent(prefab));

            assertTrue(controller.isActive());
            assertSame(prefab, controller.getTargetPrefab());
        }
    }

    // ========================================================================
    // INSTANCE COUNT
    // ========================================================================

    @Nested
    class InstanceCount {

        @Test
        void countsMatchingEntitiesInScene() {
            EditorScene scene = new EditorScene();
            scene.addEntity(new EditorGameObject("prefab-01", new Vector3f(), true));
            scene.addEntity(new EditorGameObject("prefab-01", new Vector3f(), true));
            scene.addEntity(new EditorGameObject("other-prefab", new Vector3f(), true));
            scene.addEntity(new EditorGameObject("Scratch", new Vector3f(), false));
            context.setCurrentScene(scene);

            JsonPrefab prefab = createTestPrefab("prefab-01", "Test");
            controller.enterEditMode(prefab);

            assertEquals(2, controller.getInstanceCount());
        }

        @Test
        void returnsZeroWhenNoScene() {
            context.setCurrentScene(null);
            JsonPrefab prefab = createTestPrefab("test-01", "Test");
            controller.enterEditMode(prefab);
            assertEquals(0, controller.getInstanceCount());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /**
     * Minimal EditorContext that can be used without native resources.
     * Only provides ModeManager, SelectionGuard, and CurrentScene.
     */
    private static class TestableEditorContext extends EditorContext {
        private final EditorModeManager modeManager = new EditorModeManager();
        private final EditorSelectionManager selectionManager = new EditorSelectionManager();
        private final SelectionGuard selectionGuard = new SelectionGuard(selectionManager, modeManager);
        private EditorScene currentScene;

        @Override
        public EditorModeManager getModeManager() {
            return modeManager;
        }

        @Override
        public EditorSelectionManager getSelectionManager() {
            return selectionManager;
        }

        @Override
        public SelectionGuard getSelectionGuard() {
            return selectionGuard;
        }

        @Override
        public EditorScene getCurrentScene() {
            return currentScene;
        }

        @Override
        public void setCurrentScene(EditorScene scene) {
            this.currentScene = scene;
            if (selectionManager != null) {
                selectionManager.setScene(scene);
            }
        }
    }

    /**
     * Simple command for testing undo stack state.
     */
    private record DummyCommand(String description) implements EditorCommand {
        @Override
        public void execute() {}

        @Override
        public void undo() {}

        @Override
        public String getDescription() {
            return description;
        }
    }
}
