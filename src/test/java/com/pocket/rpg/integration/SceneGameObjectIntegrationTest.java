package com.pocket.rpg.integration;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.engine.GameObject;
import com.pocket.rpg.scenes.Scene;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneGameObjectIntegrationTest {

    private TestScene scene;

    @BeforeEach
    void setUp() {
        scene = new TestScene("TestScene");
    }

    @Test
    void testSceneWithMultipleGameObjects() {
        GameObject go1 = new GameObject("Object1", new Vector3f(0, 0, 0));
        GameObject go2 = new GameObject("Object2", new Vector3f(100, 100, 0));
        GameObject go3 = new GameObject("Object3", new Vector3f(200, 200, 0));

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.addGameObject(go3);

        scene.initialize();

        assertEquals(3, scene.getGameObjects().size());
        assertTrue(go1.getTransform() != null);
        assertTrue(go2.getTransform() != null);
        assertTrue(go3.getTransform() != null);
    }

    @Test
    void testSceneInitializationStartsGameObjects() {
        GameObject go = new GameObject("Test");
        TestComponent component = new TestComponent();
        go.addComponent(component);

        scene.addGameObject(go);
        scene.initialize();

        assertTrue(component.isStarted());
    }

    @Test
    void testSceneUpdateCallsAllGameObjects() {
        GameObject go1 = new GameObject("Object1");
        GameObject go2 = new GameObject("Object2");
        TestComponent comp1 = new TestComponent();
        TestComponent comp2 = new TestComponent();

        go1.addComponent(comp1);
        go2.addComponent(comp2);

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.initialize();

        scene.update(0.016f);

        assertTrue(comp1.updateCalled);
        assertTrue(comp2.updateCalled);
        assertTrue(comp1.lateUpdateCalled);
        assertTrue(comp2.lateUpdateCalled);
    }

    @Test
    void testAddGameObjectDuringUpdate() {
        GameObject initial = new GameObject("Initial");
        SpawnerComponent spawner = new SpawnerComponent();
        initial.addComponent(spawner);

        scene.addGameObject(initial);
        scene.initialize();

        scene.update(0.016f);

        assertEquals(2, scene.getGameObjects().size());
        assertNotNull(scene.findGameObject("SpawnedObject"));
    }

    @Test
    void testRemoveGameObjectDuringUpdate() {
        GameObject go1 = new GameObject("Object1");
        GameObject go2 = new GameObject("Object2");
        RemoverComponent remover = new RemoverComponent("Object2");
        go1.addComponent(remover);

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.initialize();

        scene.update(0.016f);

        assertEquals(1, scene.getGameObjects().size());
        assertNull(scene.findGameObject("Object2"));
    }

    @Test
    void testSceneDestroyDestroysAllGameObjects() {
        GameObject go1 = new GameObject("Object1");
        GameObject go2 = new GameObject("Object2");
        TestComponent comp1 = new TestComponent();
        TestComponent comp2 = new TestComponent();

        go1.addComponent(comp1);
        go2.addComponent(comp2);

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.initialize();

        scene.destroy();

        assertTrue(comp1.destroyCalled);
        assertTrue(comp2.destroyCalled);
        assertTrue(scene.onUnloadCalled);
    }

    @Test
    void testFindGameObjectInScene() {
        GameObject go1 = new GameObject("Player");
        GameObject go2 = new GameObject("Enemy");
        GameObject go3 = new GameObject("Coin");

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.addGameObject(go3);

        assertSame(go1, scene.findGameObject("Player"));
        assertSame(go2, scene.findGameObject("Enemy"));
        assertSame(go3, scene.findGameObject("Coin"));
        assertNull(scene.findGameObject("NotFound"));
    }

    @Test
    void testSceneReferencesSetCorrectly() {
        GameObject go = new GameObject("Test");
        scene.addGameObject(go);

        assertSame(scene, go.getScene());
    }

    @Test
    void testRemoveGameObjectClearsScene() {
        GameObject go = new GameObject("Test");
        scene.addGameObject(go);
        scene.removeGameObject(go);

        assertNull(go.getScene());
    }

    @Test
    void testMultipleUpdatesCallComponents() {
        GameObject go = new GameObject("Test");
        CountingComponent counter = new CountingComponent();
        go.addComponent(counter);

        scene.addGameObject(go);
        scene.initialize();

        scene.update(0.016f);
        scene.update(0.016f);
        scene.update(0.016f);

        assertEquals(3, counter.updateCount);
        assertEquals(3, counter.lateUpdateCount);
    }

    @Test
    void testDisabledGameObjectsSkipUpdate() {
        GameObject go1 = new GameObject("Enabled");
        GameObject go2 = new GameObject("Disabled");
        TestComponent comp1 = new TestComponent();
        TestComponent comp2 = new TestComponent();

        go1.addComponent(comp1);
        go2.addComponent(comp2);

        scene.addGameObject(go1);
        scene.addGameObject(go2);
        scene.initialize();

        go2.setEnabled(false);

        scene.update(0.016f);

        assertTrue(comp1.updateCalled);
        assertFalse(comp2.updateCalled);
    }

    private static class TestScene extends Scene {
        boolean onUnloadCalled = false;

        public TestScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {
        }

        @Override
        public void onUnload() {
            onUnloadCalled = true;
        }
    }

    private static class TestComponent extends Component {
        boolean updateCalled = false;
        boolean lateUpdateCalled = false;
        boolean destroyCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }

        @Override
        public void lateUpdate(float deltaTime) {
            lateUpdateCalled = true;
        }

        @Override
        protected void onDestroy() {
            destroyCalled = true;
        }
    }

    private static class SpawnerComponent extends Component {
        @Override
        public void update(float deltaTime) {
            GameObject spawned = new GameObject("SpawnedObject");
            gameObject.getScene().addGameObject(spawned);
        }
    }

    private static class RemoverComponent extends Component {
        private final String targetName;

        public RemoverComponent(String targetName) {
            this.targetName = targetName;
        }

        @Override
        public void update(float deltaTime) {
            GameObject target = gameObject.getScene().findGameObject(targetName);
            if (target != null) {
                gameObject.getScene().removeGameObject(target);
            }
        }
    }

    private static class CountingComponent extends Component {
        int updateCount = 0;
        int lateUpdateCount = 0;

        @Override
        public void update(float deltaTime) {
            updateCount++;
        }

        @Override
        public void lateUpdate(float deltaTime) {
            lateUpdateCount++;
        }
    }
}
