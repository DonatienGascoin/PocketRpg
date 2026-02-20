package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for onBeforeSceneUnload() and onPostSceneInitialize() lifecycle hooks.
 */
class SceneLifecycleHooksTest {

    private SceneManager sceneManager;
    private ViewportConfig viewportConfig;
    private RenderingConfig renderingConfig;

    @BeforeEach
    void setUp() {
        viewportConfig = new ViewportConfig(GameConfig.builder()
                .gameWidth(800).gameHeight(600)
                .windowWidth(800).windowHeight(600)
                .build());
        renderingConfig = RenderingConfig.builder().defaultOrthographicSize(7.5f).build();
        sceneManager = new SceneManager(viewportConfig, renderingConfig);
    }

    // ========================================================================
    // onBeforeSceneUnload
    // ========================================================================

    @Nested
    @DisplayName("onBeforeSceneUnload")
    class OnBeforeSceneUnloadTests {

        @Test
        @DisplayName("fires on started+enabled components before scene destruction")
        void firesBeforeDestroy() {
            List<String> events = new ArrayList<>();

            TestScene scene1 = new TestScene("Scene1");
            TrackingComponent tracker = new TrackingComponent(events);

            GameObject go = new GameObject("Player");
            go.addComponent(tracker);
            scene1.addDeferredGameObject(go);

            sceneManager.loadScene(scene1);
            assertTrue(tracker.isStarted(), "Component should be started after scene load");

            // Load another scene to trigger unload of scene1
            TestScene scene2 = new TestScene("Scene2");
            sceneManager.loadScene(scene2);

            assertTrue(events.contains("onBeforeSceneUnload"),
                    "onBeforeSceneUnload should have been called");
            int unloadIdx = events.indexOf("onBeforeSceneUnload");
            int destroyIdx = events.indexOf("onDestroy");
            assertTrue(unloadIdx < destroyIdx,
                    "onBeforeSceneUnload should fire before onDestroy");
        }

        @Test
        @DisplayName("does NOT fire on first scene load (no previous scene)")
        void doesNotFireOnFirstLoad() {
            List<String> events = new ArrayList<>();

            TestScene scene1 = new TestScene("Scene1");
            TrackingComponent tracker = new TrackingComponent(events);

            GameObject go = new GameObject("Player");
            go.addComponent(tracker);
            scene1.addDeferredGameObject(go);

            sceneManager.loadScene(scene1);

            assertFalse(events.contains("onBeforeSceneUnload"),
                    "onBeforeSceneUnload should NOT fire on first scene load");
        }

        @Test
        @DisplayName("exception in one component does not prevent others from running")
        void exceptionDoesNotPreventOthers() {
            List<String> events = new ArrayList<>();

            TestScene scene1 = new TestScene("Scene1");
            ThrowingComponent thrower = new ThrowingComponent();
            TrackingComponent tracker = new TrackingComponent(events);

            GameObject go = new GameObject("Player");
            go.addComponent(thrower);
            go.addComponent(tracker);
            scene1.addDeferredGameObject(go);

            sceneManager.loadScene(scene1);

            // Load another scene to trigger unload
            TestScene scene2 = new TestScene("Scene2");
            sceneManager.loadScene(scene2);

            assertTrue(events.contains("onBeforeSceneUnload"),
                    "Second component's onBeforeSceneUnload should still fire despite first throwing");
        }

        @Test
        @DisplayName("does not fire on disabled components")
        void doesNotFireOnDisabledComponents() {
            List<String> events = new ArrayList<>();

            TestScene scene1 = new TestScene("Scene1");
            TrackingComponent tracker = new TrackingComponent(events);

            GameObject go = new GameObject("Player");
            go.addComponent(tracker);
            scene1.addDeferredGameObject(go);

            sceneManager.loadScene(scene1);
            tracker.setEnabled(false);

            TestScene scene2 = new TestScene("Scene2");
            sceneManager.loadScene(scene2);

            assertFalse(events.contains("onBeforeSceneUnload"),
                    "onBeforeSceneUnload should NOT fire on disabled components");
        }

        @Test
        @DisplayName("fires on child game objects recursively")
        void firesOnChildren() {
            List<String> events = new ArrayList<>();

            TestScene scene1 = new TestScene("Scene1");
            TrackingComponent childTracker = new TrackingComponent(events);

            GameObject parent = new GameObject("Parent");
            GameObject child = new GameObject("Child");
            child.addComponent(childTracker);
            parent.addChild(child);
            scene1.addDeferredGameObject(parent);

            sceneManager.loadScene(scene1);

            TestScene scene2 = new TestScene("Scene2");
            sceneManager.loadScene(scene2);

            assertTrue(events.contains("onBeforeSceneUnload"),
                    "onBeforeSceneUnload should fire on child components");
        }
    }

    // ========================================================================
    // onPostSceneInitialize
    // ========================================================================

    @Nested
    @DisplayName("onPostSceneInitialize")
    class OnPostSceneInitializeTests {

        @Test
        @DisplayName("fires after scene initialize, before onSceneLoaded")
        void firesAfterInitializeBeforeLoaded() {
            List<String> events = new ArrayList<>();

            sceneManager.addLifecycleListener(new SceneLifecycleListener() {
                @Override
                public void onSceneLoaded(Scene scene) {
                    events.add("onSceneLoaded");
                }

                @Override
                public void onSceneUnloaded(Scene scene) {
                    events.add("onSceneUnloaded");
                }

                @Override
                public void onPostSceneInitialize(Scene scene) {
                    events.add("onPostSceneInitialize");
                }
            });

            TestScene scene1 = new TestScene("Scene1");
            sceneManager.loadScene(scene1);

            int postInitIdx = events.indexOf("onPostSceneInitialize");
            int loadedIdx = events.indexOf("onSceneLoaded");

            assertTrue(postInitIdx >= 0, "onPostSceneInitialize should have fired");
            assertTrue(loadedIdx >= 0, "onSceneLoaded should have fired");
            assertTrue(postInitIdx < loadedIdx,
                    "onPostSceneInitialize should fire before onSceneLoaded");
        }

        @Test
        @DisplayName("fires after all component onStart() calls complete")
        void firesAfterAllComponentStarts() {
            List<String> events = new ArrayList<>();

            sceneManager.addLifecycleListener(new SceneLifecycleListener() {
                @Override
                public void onSceneLoaded(Scene scene) {}

                @Override
                public void onSceneUnloaded(Scene scene) {}

                @Override
                public void onPostSceneInitialize(Scene scene) {
                    events.add("onPostSceneInitialize");
                }
            });

            TrackingComponent tracker = new TrackingComponent(events);
            TestScene scene1 = new TestScene("Scene1");
            GameObject go = new GameObject("Player");
            go.addComponent(tracker);
            scene1.addDeferredGameObject(go);

            sceneManager.loadScene(scene1);

            int startIdx = events.indexOf("onStart");
            int postInitIdx = events.indexOf("onPostSceneInitialize");

            assertTrue(startIdx >= 0, "onStart should have fired");
            assertTrue(postInitIdx >= 0, "onPostSceneInitialize should have fired");
            assertTrue(startIdx < postInitIdx,
                    "onStart should fire before onPostSceneInitialize");
        }

        @Test
        @DisplayName("existing onSceneLoaded still fires (ordering preserved)")
        void existingOnSceneLoadedStillFires() {
            List<String> events = new ArrayList<>();

            sceneManager.addLifecycleListener(new SceneLifecycleListener() {
                @Override
                public void onSceneLoaded(Scene scene) {
                    events.add("onSceneLoaded:" + scene.getName());
                }

                @Override
                public void onSceneUnloaded(Scene scene) {
                    events.add("onSceneUnloaded:" + scene.getName());
                }
            });

            TestScene scene1 = new TestScene("Scene1");
            sceneManager.loadScene(scene1);

            assertTrue(events.contains("onSceneLoaded:Scene1"),
                    "onSceneLoaded should still fire");
        }

        @Test
        @DisplayName("default method means existing listeners compile without changes")
        void defaultMethodDoesNotBreakExisting() {
            // This test verifies that the existing TestListener pattern
            // (not implementing onPostSceneInitialize) still compiles
            SceneLifecycleListener listener = new SceneLifecycleListener() {
                @Override
                public void onSceneLoaded(Scene scene) {}

                @Override
                public void onSceneUnloaded(Scene scene) {}
                // Note: onPostSceneInitialize NOT overridden — uses default
            };

            sceneManager.addLifecycleListener(listener);

            TestScene scene1 = new TestScene("Scene1");
            assertDoesNotThrow(() -> sceneManager.loadScene(scene1));
        }

        @Test
        @DisplayName("full lifecycle ordering: unload hooks → init → postInit → spawnTeleport → loaded")
        void fullLifecycleOrdering() {
            List<String> events = new ArrayList<>();

            sceneManager.addLifecycleListener(new SceneLifecycleListener() {
                @Override
                public void onSceneLoaded(Scene scene) {
                    events.add("listener:onSceneLoaded");
                }

                @Override
                public void onSceneUnloaded(Scene scene) {
                    events.add("listener:onSceneUnloaded");
                }

                @Override
                public void onPostSceneInitialize(Scene scene) {
                    events.add("listener:onPostSceneInitialize");
                }
            });

            // Load scene1 with a component
            TrackingComponent tracker1 = new TrackingComponent(events);
            TestScene scene1 = new TestScene("Scene1");
            GameObject go = new GameObject("Player");
            go.addComponent(tracker1);
            scene1.addDeferredGameObject(go);
            sceneManager.loadScene(scene1);

            events.clear();

            // Load scene2, which triggers unload of scene1
            TrackingComponent tracker2 = new TrackingComponent(events);
            TestScene scene2 = new TestScene("Scene2");
            GameObject go2 = new GameObject("Player2");
            go2.addComponent(tracker2);
            scene2.addDeferredGameObject(go2);
            sceneManager.loadScene(scene2);

            // Expected order:
            // 1. scene1 component onBeforeSceneUnload
            // 2. scene1 component onDestroy
            // 3. listener onSceneUnloaded (scene1)
            // 4. scene2 component onStart
            // 5. listener onPostSceneInitialize (scene2)
            // 6. listener onSceneLoaded (scene2)
            int beforeUnloadIdx = events.indexOf("onBeforeSceneUnload");
            int destroyIdx = events.indexOf("onDestroy");
            int unloadedIdx = events.indexOf("listener:onSceneUnloaded");
            int startIdx = events.indexOf("onStart");
            int postInitIdx = events.indexOf("listener:onPostSceneInitialize");
            int loadedIdx = events.indexOf("listener:onSceneLoaded");

            assertTrue(beforeUnloadIdx < destroyIdx, "beforeUnload before destroy");
            assertTrue(destroyIdx < unloadedIdx, "destroy before unloaded listener");
            assertTrue(unloadedIdx < startIdx, "unloaded before new scene start");
            assertTrue(startIdx < postInitIdx, "start before postInit");
            assertTrue(postInitIdx < loadedIdx, "postInit before loaded");
        }
    }

    // ========================================================================
    // Test Helpers
    // ========================================================================

    /** Scene that allows adding GameObjects before initialization via onLoad(). */
    private static class TestScene extends Scene {
        private final List<GameObject> deferredObjects = new ArrayList<>();

        public TestScene(String name) {
            super(name);
        }

        void addDeferredGameObject(GameObject go) {
            deferredObjects.add(go);
        }

        @Override
        public void onLoad() {
            for (GameObject go : deferredObjects) {
                addGameObject(go);
            }
        }
    }

    /** Component that tracks lifecycle events. */
    private static class TrackingComponent extends Component {
        private final List<String> events;

        TrackingComponent(List<String> events) {
            this.events = events;
        }

        @Override
        protected void onStart() {
            events.add("onStart");
        }

        @Override
        protected void onBeforeSceneUnload() {
            events.add("onBeforeSceneUnload");
        }

        @Override
        protected void onDestroy() {
            events.add("onDestroy");
        }
    }

    /** Component that throws in onBeforeSceneUnload to test exception isolation. */
    private static class ThrowingComponent extends Component {
        @Override
        protected void onBeforeSceneUnload() {
            throw new RuntimeException("Test exception in onBeforeSceneUnload");
        }
    }
}
