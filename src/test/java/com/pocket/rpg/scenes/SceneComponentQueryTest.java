package com.pocket.rpg.scenes;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SceneComponentQueryTest {

    private TestScene scene;

    @BeforeEach
    void setUp() {
        scene = new TestScene("TestScene");
    }

    @Test
    void getComponentsImplementing_returnsMatchingComponents() {
        GameObject go1 = new GameObject("Go1");
        PausableComponent pausable1 = new PausableComponent();
        go1.addComponent(pausable1);

        GameObject go2 = new GameObject("Go2");
        PausableComponent pausable2 = new PausableComponent();
        go2.addComponent(pausable2);
        go2.addComponent(new PlainComponent()); // does not implement TestInterface

        scene.addGameObject(go1);
        scene.addGameObject(go2);

        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertEquals(2, results.size());
        assertTrue(results.contains(pausable1));
        assertTrue(results.contains(pausable2));
    }

    @Test
    void getComponentsImplementing_emptyScene_returnsEmptyList() {
        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void getComponentsImplementing_noMatches_returnsEmptyList() {
        GameObject go = new GameObject("Go");
        go.addComponent(new PlainComponent());
        scene.addGameObject(go);

        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void getComponentsImplementing_multipleOnSameGameObject_allReturned() {
        GameObject go = new GameObject("Go");
        PausableComponent p1 = new PausableComponent();
        AnotherPausableComponent p2 = new AnotherPausableComponent();
        go.addComponent(p1);
        go.addComponent(p2);
        scene.addGameObject(go);

        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertEquals(2, results.size());
        assertTrue(results.contains(p1));
        assertTrue(results.contains(p2));
    }

    @Test
    void getComponentsImplementing_findsComponentsInChildren() {
        GameObject parent = new GameObject("Parent");
        parent.addComponent(new PlainComponent());

        GameObject child = new GameObject("Child");
        PausableComponent childPausable = new PausableComponent();
        child.addComponent(childPausable);
        parent.addChild(child);

        scene.addGameObject(parent);

        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertEquals(1, results.size());
        assertTrue(results.contains(childPausable));
    }

    @Test
    void getComponentsImplementing_findsComponentsInDeeplyNestedChildren() {
        GameObject root = new GameObject("Root");
        GameObject mid = new GameObject("Mid");
        GameObject leaf = new GameObject("Leaf");

        PausableComponent rootPausable = new PausableComponent();
        PausableComponent leafPausable = new PausableComponent();

        root.addComponent(rootPausable);
        leaf.addComponent(leafPausable);

        root.addChild(mid);
        mid.addChild(leaf);

        scene.addGameObject(root);

        List<TestInterface> results = scene.getComponentsImplementing(TestInterface.class);
        assertEquals(2, results.size());
        assertTrue(results.contains(rootPausable));
        assertTrue(results.contains(leafPausable));
    }

    @Test
    void getComponentsImplementing_worksWithConcreteComponentClass() {
        GameObject go = new GameObject("Go");
        PausableComponent pausable = new PausableComponent();
        go.addComponent(pausable);
        go.addComponent(new PlainComponent());
        scene.addGameObject(go);

        List<PausableComponent> results = scene.getComponentsImplementing(PausableComponent.class);
        assertEquals(1, results.size());
        assertSame(pausable, results.getFirst());
    }

    // --- Test helpers ---

    interface TestInterface {
        void onPause();
        void onResume();
    }

    private static class PausableComponent extends Component implements TestInterface {
        @Override public void onPause() {}
        @Override public void onResume() {}
    }

    private static class AnotherPausableComponent extends Component implements TestInterface {
        @Override public void onPause() {}
        @Override public void onResume() {}
    }

    private static class PlainComponent extends Component {}

    private static class TestScene extends Scene {
        public TestScene(String name) { super(name); }
        @Override public void onLoad() {}
    }
}
