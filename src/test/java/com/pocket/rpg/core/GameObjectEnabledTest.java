package com.pocket.rpg.core;

import com.pocket.rpg.components.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GameObject enable/disable wiring:
 * - setEnabled() notifies components via triggerEnable/triggerDisable
 * - propagateParentEnabledChange() cascades to children
 * - Individually disabled children/components are respected
 */
class GameObjectEnabledTest {

    private GameObject parent;
    private GameObject child;
    private GameObject grandchild;

    @BeforeEach
    void setUp() {
        parent = new GameObject("Parent");
        child = new GameObject("Child");
        grandchild = new GameObject("Grandchild");
        child.setParent(parent);
        grandchild.setParent(child);
    }

    // ========================================================================
    // Basic enable/disable callbacks
    // ========================================================================

    @Test
    void disableGameObject_componentsReceiveOnDisable() {
        TrackingComponent comp = new TrackingComponent();
        parent.addComponent(comp);
        parent.start();

        parent.setEnabled(false);

        assertEquals(1, comp.disableCount);
    }

    @Test
    void reEnableGameObject_componentsReceiveOnEnable() {
        TrackingComponent comp = new TrackingComponent();
        parent.addComponent(comp);
        parent.start();

        parent.setEnabled(false);
        comp.reset();

        parent.setEnabled(true);

        assertEquals(1, comp.enableCount);
    }

    @Test
    void setEnabledSameValue_noCallback() {
        TrackingComponent comp = new TrackingComponent();
        parent.addComponent(comp);
        parent.start();
        comp.reset();

        parent.setEnabled(true); // already true

        assertEquals(0, comp.enableCount);
        assertEquals(0, comp.disableCount);
    }

    // ========================================================================
    // Parent -> child propagation
    // ========================================================================

    @Test
    void disableParent_childComponentsReceiveOnDisable() {
        TrackingComponent childComp = new TrackingComponent();
        child.addComponent(childComp);
        parent.start();

        parent.setEnabled(false);

        assertEquals(1, childComp.disableCount);
    }

    @Test
    void reEnableParent_childComponentsReceiveOnEnable() {
        TrackingComponent childComp = new TrackingComponent();
        child.addComponent(childComp);
        parent.start();

        parent.setEnabled(false);
        childComp.reset();

        parent.setEnabled(true);

        assertEquals(1, childComp.enableCount);
    }

    @Test
    void grandchildPropagation_threeDeep() {
        TrackingComponent gcComp = new TrackingComponent();
        grandchild.addComponent(gcComp);
        parent.start();

        parent.setEnabled(false);
        assertEquals(1, gcComp.disableCount);

        gcComp.reset();
        parent.setEnabled(true);
        assertEquals(1, gcComp.enableCount);
    }

    // ========================================================================
    // Individually disabled children/components respected
    // ========================================================================

    @Test
    void individuallyDisabledComponent_doesNotReceiveOnEnable_whenParentEnabled() {
        TrackingComponent comp = new TrackingComponent();
        parent.addComponent(comp);
        parent.start();

        comp.setEnabled(false); // individually disabled
        comp.reset();

        parent.setEnabled(false);
        parent.setEnabled(true);

        // triggerEnable checks `started && enabled` — comp.enabled is false, so no callback
        assertEquals(0, comp.enableCount);
    }

    @Test
    void individuallyDisabledChild_noCallbacksWhenParentToggles() {
        child.setEnabled(false); // individually disabled
        TrackingComponent childComp = new TrackingComponent();
        child.addComponent(childComp);
        parent.start();
        childComp.reset();

        parent.setEnabled(false);

        // propagateParentEnabledChange short-circuits at disabled child
        assertEquals(0, childComp.disableCount);

        parent.setEnabled(true);
        assertEquals(0, childComp.enableCount);
    }

    @Test
    void individuallyDisabledChild_grandchildAlsoSkipped() {
        child.setEnabled(false);
        TrackingComponent gcComp = new TrackingComponent();
        grandchild.addComponent(gcComp);
        parent.start();
        gcComp.reset();

        parent.setEnabled(false);
        assertEquals(0, gcComp.disableCount);

        parent.setEnabled(true);
        assertEquals(0, gcComp.enableCount);
    }

    // ========================================================================
    // Edge cases
    // ========================================================================

    @Test
    void componentDisablesOwnGameObject_duringUpdate_noCrash() {
        Component selfDisabler = new Component() {
            @Override
            public void update(float deltaTime) {
                gameObject.setEnabled(false);
            }
        };
        parent.addComponent(selfDisabler);
        parent.start();

        assertDoesNotThrow(() -> parent.update(0.016f));
        assertFalse(parent.isEnabled());
    }

    @Test
    void exceptionInOnDisable_doesNotBlockSiblings() {
        Component thrower = new Component() {
            @Override
            protected void onDisable() {
                throw new RuntimeException("intentional test exception");
            }
        };
        TrackingComponent sibling = new TrackingComponent();
        parent.addComponent(thrower);
        parent.addComponent(sibling);
        parent.start();

        // triggerDisable has try-catch, so sibling should still get called
        parent.setEnabled(false);

        assertEquals(1, sibling.disableCount);
    }

    @Test
    void componentIsOwnEnabled_returnsRawField() {
        TrackingComponent comp = new TrackingComponent();
        parent.addComponent(comp);

        assertTrue(comp.isOwnEnabled());

        comp.setEnabled(false);
        assertFalse(comp.isOwnEnabled());

        // isEnabled() is hierarchical, isOwnEnabled() is just the field
        comp.setEnabled(true);
        parent.setEnabled(false);

        assertTrue(comp.isOwnEnabled());
        assertFalse(comp.isEnabled());
    }

    // ========================================================================
    // Test helper
    // ========================================================================

    private static class TrackingComponent extends Component {
        int enableCount = 0;
        int disableCount = 0;

        @Override
        protected void onEnable() {
            enableCount++;
        }

        @Override
        protected void onDisable() {
            disableCount++;
        }

        void reset() {
            enableCount = 0;
            disableCount = 0;
        }
    }
}
