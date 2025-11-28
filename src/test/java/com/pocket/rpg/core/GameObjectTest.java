package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameObjectTest {

    private GameObject gameObject;

    @BeforeEach
    void setUp() {
        gameObject = new GameObject("TestObject");
    }

    @Test
    void testConstructor() {
        assertEquals("TestObject", gameObject.getName());
        assertTrue(gameObject.isEnabled());
        assertNotNull(gameObject.getTransform());
    }

    @Test
    void testConstructorWithPosition() {
        GameObject go = new GameObject("Test", new Vector3f(10, 20, 30));
        assertEquals(10, go.getTransform().getPosition().x);
        assertEquals(20, go.getTransform().getPosition().y);
        assertEquals(30, go.getTransform().getPosition().z);
    }

    @Test
    void testAddComponent() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);

        assertSame(component, gameObject.getComponent(TestComponent.class));
        assertSame(gameObject, component.getGameObject());
    }

    @Test
    void testGetComponent() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);

        assertSame(component, gameObject.getComponent(TestComponent.class));
    }

    @Test
    void testRemoveComponent() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);

        gameObject.removeComponent(component);

        assertNull(gameObject.getComponent(TestComponent.class));
    }

    @Test
    void testSetEnabled() {
        gameObject.setEnabled(false);
        assertFalse(gameObject.isEnabled());

        gameObject.setEnabled(true);
        assertTrue(gameObject.isEnabled());
    }

    @Test
    void testStart() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);

        gameObject.start();

        assertTrue(component.isStarted());
    }

    @Test
    void testUpdate() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);
        gameObject.start();

        gameObject.update(0.016f);

        assertTrue(component.updateCalled);
    }

    @Test
    void testLateUpdate() {
        TestComponent component = new TestComponent();
        gameObject.addComponent(component);
        gameObject.start();

        gameObject.lateUpdate(0.016f);

        assertTrue(component.lateUpdateCalled);
    }

    private static class TestComponent extends Component {
        boolean updateCalled = false;
        boolean lateUpdateCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }

        @Override
        public void lateUpdate(float deltaTime) {
            lateUpdateCalled = true;
        }
    }
}
