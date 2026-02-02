package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SelectionGuardTest {

    private EditorSelectionManager selectionManager;
    private EditorModeManager modeManager;
    private SelectionGuard guard;
    private EditorScene scene;

    @BeforeEach
    void setUp() {
        EditorEventBus.reset();
        selectionManager = new EditorSelectionManager();
        scene = new EditorScene();
        selectionManager.setScene(scene);
        modeManager = new EditorModeManager();
        guard = new SelectionGuard(selectionManager, modeManager);
    }

    private EditorGameObject createEntity(String name) {
        EditorGameObject entity = new EditorGameObject(name, new Vector3f(), false);
        scene.addEntity(entity);
        return entity;
    }

    // ========================================================================
    // SCENE MODE — PASS-THROUGH
    // ========================================================================

    @Nested
    class SceneMode {

        @Test
        void selectEntity_passesThrough() {
            EditorGameObject entity = createEntity("player");
            guard.selectEntity(entity);

            assertTrue(selectionManager.getSelectedEntities().contains(entity));
        }

        @Test
        void selectEntities_passesThrough() {
            EditorGameObject e1 = createEntity("a");
            EditorGameObject e2 = createEntity("b");
            guard.selectEntities(Set.of(e1, e2));

            assertEquals(2, selectionManager.getSelectedEntities().size());
        }

        @Test
        void clearSelection_passesThrough() {
            guard.selectEntity(createEntity("x"));
            guard.clearSelection();

            assertFalse(selectionManager.hasEntitySelection());
        }

        @Test
        void selectCamera_passesThrough() {
            guard.selectCamera();
            assertTrue(selectionManager.isCameraSelected());
        }

        @Test
        void selectTilemapLayer_passesThrough() {
            guard.selectTilemapLayer(2);
            assertTrue(selectionManager.isTilemapLayerSelected());
        }

        @Test
        void selectCollisionLayer_passesThrough() {
            guard.selectCollisionLayer();
            assertTrue(selectionManager.isCollisionLayerSelected());
        }

        @Test
        void selectAsset_passesThrough() {
            guard.selectAsset("test.png", Object.class);
            assertTrue(selectionManager.isAssetSelected());
        }
    }

    // ========================================================================
    // PLAY MODE — PASS-THROUGH (no guard in play mode)
    // ========================================================================

    @Nested
    class PlayMode {

        @BeforeEach
        void enterPlayMode() {
            modeManager.setMode(EditorMode.PLAY);
        }

        @Test
        void selectEntity_passesThrough_inPlayMode() {
            EditorGameObject entity = createEntity("player");
            guard.selectEntity(entity);

            assertTrue(selectionManager.getSelectedEntities().contains(entity));
        }
    }

    // ========================================================================
    // PREFAB EDIT MODE — INTERCEPTED
    // ========================================================================

    @Nested
    class PrefabEditMode {

        @BeforeEach
        void enterPrefabMode() {
            modeManager.setMode(EditorMode.PREFAB_EDIT);
        }

        @Test
        void selectEntity_interceptedByDefault_stillExecutes() {
            // Default interceptor is Runnable::run (passthrough)
            EditorGameObject entity = createEntity("player");
            guard.selectEntity(entity);

            assertTrue(selectionManager.getSelectedEntities().contains(entity));
        }

        @Test
        void selectEntity_deferredWhenInterceptorSet() {
            AtomicReference<Runnable> deferred = new AtomicReference<>();
            guard.setInterceptor(deferred::set);

            EditorGameObject entity = createEntity("player");
            guard.selectEntity(entity);

            // Not yet executed
            assertFalse(selectionManager.hasEntitySelection());

            // Execute deferred action
            deferred.get().run();
            assertTrue(selectionManager.getSelectedEntities().contains(entity));
        }

        @Test
        void clearSelection_deferredWhenInterceptorSet() {
            // Pre-select an entity (bypass guard by using manager directly)
            selectionManager.selectEntity(createEntity("x"));

            AtomicReference<Runnable> deferred = new AtomicReference<>();
            guard.setInterceptor(deferred::set);

            guard.clearSelection();

            // Not yet cleared
            assertTrue(selectionManager.hasEntitySelection());

            // Execute deferred action
            deferred.get().run();
            assertFalse(selectionManager.hasEntitySelection());
        }

        @Test
        void selectCamera_deferredWhenInterceptorSet() {
            AtomicReference<Runnable> deferred = new AtomicReference<>();
            guard.setInterceptor(deferred::set);

            guard.selectCamera();
            assertFalse(selectionManager.isCameraSelected());

            deferred.get().run();
            assertTrue(selectionManager.isCameraSelected());
        }

        @Test
        void selectAsset_notGuarded_executesImmediately() {
            // Asset selections bypass mode guard — they don't conflict with prefab editing
            AtomicReference<Runnable> deferred = new AtomicReference<>();
            guard.setInterceptor(deferred::set);

            guard.selectAsset("test.png", Object.class);
            assertTrue(selectionManager.isAssetSelected()); // Immediate, not deferred
            assertNull(deferred.get()); // Interceptor was not called
        }
    }

    // ========================================================================
    // INTERCEPTOR LIFECYCLE
    // ========================================================================

    @Nested
    class InterceptorLifecycle {

        @Test
        void setInterceptor_null_resetsToPassthrough() {
            modeManager.setMode(EditorMode.PREFAB_EDIT);

            AtomicBoolean intercepted = new AtomicBoolean(false);
            guard.setInterceptor(action -> intercepted.set(true));

            // Clear interceptor
            guard.setInterceptor(null);

            guard.selectEntity(createEntity("test"));

            // Should pass through, not intercept
            assertFalse(intercepted.get());
            assertTrue(selectionManager.hasEntitySelection());
        }

        @Test
        void interceptor_notCalledOutsidePrefabMode() {
            AtomicBoolean intercepted = new AtomicBoolean(false);
            guard.setInterceptor(action -> intercepted.set(true));

            // In SCENE mode, interceptor should NOT be called
            guard.selectEntity(createEntity("test"));

            assertFalse(intercepted.get());
        }
    }

    // ========================================================================
    // DELEGATE ACCESS
    // ========================================================================

    @Nested
    class DelegateAccess {

        @Test
        void getSelectionManager_returnsUnderlyingManager() {
            assertSame(selectionManager, guard.getSelectionManager());
        }
    }
}
