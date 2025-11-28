package com.pocket.rpg.components;

import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {

    private GameObject gameObject;
    private TestComponent component;

    @BeforeEach
    void setUp() {
        gameObject = new GameObject("Test");
        component = new TestComponent();
        gameObject.addComponent(component);
    }

    @Test
    void testInitialState() {
        assertTrue(component.isEnabled());
        assertFalse(component.isStarted());
        assertSame(gameObject, component.getGameObject());
    }

    @Test
    void testStart() {
        component.start();

        assertTrue(component.startCalled);
        assertTrue(component.isStarted());
    }

    @Test
    void testSetEnabled() {
        component.setEnabled(false);
        assertFalse(component.enabled);
    }

    @Test
    void testGetTransform() {
        assertSame(gameObject.getTransform(), component.getTransform());
    }

    private static class TestComponent extends Component {
        boolean startCalled = false;

        @Override
        protected void onStart() {
            startCalled = true;
        }
    }
}
