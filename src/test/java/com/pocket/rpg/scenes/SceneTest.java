package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.engine.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneTest {

    private TestScene scene;

    @BeforeEach
    void setUp() {
        scene = new TestScene("TestScene");
    }

    @Test
    void testGetName() {
        assertEquals("TestScene", scene.getName());
    }

    @Test
    void testAddGameObject() {
        GameObject go = new GameObject("Test");
        scene.addGameObject(go);

        assertTrue(scene.getGameObjects().contains(go));
        assertSame(scene, go.getScene());
    }

    @Test
    void testRemoveGameObject() {
        GameObject go = new GameObject("Test");
        scene.addGameObject(go);

        scene.removeGameObject(go);

        assertFalse(scene.getGameObjects().contains(go));
    }

    @Test
    void testFindGameObject() {
        GameObject go1 = new GameObject("Object1");
        GameObject go2 = new GameObject("Object2");
        scene.addGameObject(go1);
        scene.addGameObject(go2);

        assertSame(go1, scene.findGameObject("Object1"));
        assertSame(go2, scene.findGameObject("Object2"));
        assertNull(scene.findGameObject("NotFound"));
    }

    @Test
    void testInitialize() {
        scene.initialize();

        assertTrue(scene.onLoadCalled);
    }

    @Test
    void testUpdate() {
        GameObject go = new GameObject("Test");
        TestComponent component = new TestComponent();
        go.addComponent(component);

        scene.addGameObject(go);
        scene.initialize();
        scene.update(0.016f);

        assertTrue(component.updateCalled);
    }

    private static class TestScene extends Scene {
        boolean onLoadCalled = false;

        public TestScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {
            onLoadCalled = true;
        }
    }

    private static class TestComponent extends Component {
        boolean updateCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }
    }
}
