package com.pocket.rpg.integration;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.Transform;
import com.pocket.rpg.engine.GameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameObjectComponentIntegrationTest {

    private GameObject gameObject;

    @BeforeEach
    void setUp() {
        gameObject = new GameObject("TestObject", new Vector3f(100, 200, 0));
    }

    @Test
    void testGameObjectWithMultipleComponents() {
        TestComponent1 comp1 = new TestComponent1();
        TestComponent2 comp2 = new TestComponent2();
        TestComponent3 comp3 = new TestComponent3();

        gameObject.addComponent(comp1);
        gameObject.addComponent(comp2);
        gameObject.addComponent(comp3);

        gameObject.start();

        assertTrue(comp1.isStarted());
        assertTrue(comp2.isStarted());
        assertTrue(comp3.isStarted());

        gameObject.update(0.016f);

        assertTrue(comp1.updateCalled);
        assertTrue(comp2.updateCalled);
        assertTrue(comp3.updateCalled);
    }

    @Test
    void testComponentInteraction() {
        MovementComponent movement = new MovementComponent();
        RotationComponent rotation = new RotationComponent();

        gameObject.addComponent(movement);
        gameObject.addComponent(rotation);
        gameObject.start();

        Vector3f initialPos = new Vector3f(gameObject.getTransform().getPosition());
        Vector3f initialRot = new Vector3f(gameObject.getTransform().getRotation());

        gameObject.update(0.016f);

        assertNotEquals(initialPos.x, gameObject.getTransform().getPosition().x);
        assertNotEquals(initialRot.z, gameObject.getTransform().getRotation().z);
    }

    @Test
    void testComponentLifecycle() {
        LifecycleComponent component = new LifecycleComponent();
        gameObject.addComponent(component);

        assertFalse(component.startCalled);
        assertFalse(component.enableCalled);

        gameObject.start();

        assertTrue(component.startCalled);
        assertTrue(component.enableCalled);

        component.setEnabled(false);
        assertTrue(component.disableCalled);

        component.setEnabled(true);
        assertEquals(2, component.enableCallCount);

        gameObject.setEnabled(false); // Disabling GameObject does not disable components
        assertEquals(1, component.disableCallCount);
    }

    @Test
    void testComponentAdditionDuringUpdate() {
        DynamicComponent component = new DynamicComponent();
        gameObject.addComponent(component);
        gameObject.start();

        gameObject.update(0.016f);

        assertNotNull(gameObject.getComponent(TestComponent1.class));
    }

    @Test
    void testComponentRemovalDuringUpdate() {
        RemovalComponent removalComp = new RemovalComponent();
        TestComponent1 targetComp = new TestComponent1();

        gameObject.addComponent(removalComp);
        gameObject.addComponent(targetComp);
        gameObject.start();

        gameObject.update(0.016f);

        assertNull(gameObject.getComponent(TestComponent1.class));
    }

    @Test
    void testDisabledComponentsSkipUpdate() {
        TestComponent1 comp1 = new TestComponent1();
        TestComponent2 comp2 = new TestComponent2();

        gameObject.addComponent(comp1);
        gameObject.addComponent(comp2);
        gameObject.start();

        comp1.setEnabled(false);

        gameObject.update(0.016f);

        assertFalse(comp1.updateCalled);
        assertTrue(comp2.updateCalled);
    }

    @Test
    void testDisabledGameObjectSkipsAllUpdates() {
        TestComponent1 comp1 = new TestComponent1();
        TestComponent2 comp2 = new TestComponent2();

        gameObject.addComponent(comp1);
        gameObject.addComponent(comp2);
        gameObject.start();

        gameObject.setEnabled(false);

        gameObject.update(0.016f);

        assertFalse(comp1.updateCalled);
        assertFalse(comp2.updateCalled);
    }

    @Test
    void testLateUpdateOrder() {
        OrderTrackingComponent comp1 = new OrderTrackingComponent("Comp1");
        OrderTrackingComponent comp2 = new OrderTrackingComponent("Comp2");

        gameObject.addComponent(comp1);
        gameObject.addComponent(comp2);
        gameObject.start();

        gameObject.lateUpdate(0.016f);

        assertFalse(comp1.updateCalled);
        assertFalse(comp2.updateCalled);
        assertTrue(comp1.lateUpdateCalled);
        assertTrue(comp2.lateUpdateCalled);
    }

    @Test
    void testDestroyCallsAllComponents() {
        TestComponent1 comp1 = new TestComponent1();
        TestComponent2 comp2 = new TestComponent2();
        TestComponent3 comp3 = new TestComponent3();

        gameObject.addComponent(comp1);
        gameObject.addComponent(comp2);
        gameObject.addComponent(comp3);
        gameObject.start();

        gameObject.destroy();

        assertTrue(comp1.destroyCalled);
        assertTrue(comp2.destroyCalled);
        assertTrue(comp3.destroyCalled);
    }

    private static class TestComponent1 extends Component {
        boolean updateCalled = false;
        boolean destroyCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }

        @Override
        protected void onDestroy() {
            destroyCalled = true;
        }
    }

    private static class TestComponent2 extends Component {
        boolean updateCalled = false;
        boolean destroyCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }

        @Override
        protected void onDestroy() {
            destroyCalled = true;
        }
    }

    private static class TestComponent3 extends Component {
        boolean updateCalled = false;
        boolean destroyCalled = false;

        @Override
        public void update(float deltaTime) {
            updateCalled = true;
        }

        @Override
        protected void onDestroy() {
            destroyCalled = true;
        }
    }

    private static class MovementComponent extends Component {
        @Override
        public void update(float deltaTime) {
            getTransform().translate(100 * deltaTime, 0, 0);
        }
    }

    private static class RotationComponent extends Component {
        @Override
        public void update(float deltaTime) {
            getTransform().rotate(0, 0, 90 * deltaTime);
        }
    }

    private static class LifecycleComponent extends Component {
        boolean startCalled = false;
        boolean enableCalled = false;
        boolean disableCalled = false;
        int enableCallCount = 0;
        int disableCallCount = 0;

        @Override
        protected void onStart() {
            startCalled = true;
        }

        @Override
        protected void onEnable() {
            enableCalled = true;
            enableCallCount++;
        }

        @Override
        protected void onDisable() {
            disableCalled = true;
            disableCallCount++;
        }
    }

    private static class DynamicComponent extends Component {
        @Override
        public void update(float deltaTime) {
            gameObject.addComponent(new TestComponent1());
        }
    }

    private static class RemovalComponent extends Component {
        @Override
        public void update(float deltaTime) {
            TestComponent1 toRemove = gameObject.getComponent(TestComponent1.class);
            if (toRemove != null) {
                gameObject.removeComponent(toRemove);
            }
        }
    }

    private static class OrderTrackingComponent extends Component {
        private final String name;
        boolean updateCalled = false;
        boolean lateUpdateCalled = false;

        public OrderTrackingComponent(String name) {
            this.name = name;
        }

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
