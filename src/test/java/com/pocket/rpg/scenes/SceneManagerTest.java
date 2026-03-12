package com.pocket.rpg.scenes;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.window.ViewportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneManagerTest {

    private TestScene scene1;
    private TestScene scene2;

    @BeforeEach
    void setUp() {
        DefaultSceneManagerContext ctx = new DefaultSceneManagerContext(new ViewportConfig(GameConfig.builder()
                .gameWidth(800)
                .gameHeight(600)
                .windowWidth(800)
                .windowHeight(600)
                .build()), RenderingConfig.builder().defaultOrthographicSize(7.5f).build());
        SceneManager.setContext(ctx);
        scene1 = new TestScene("Scene1");
        scene2 = new TestScene("Scene2");
    }

    @AfterEach
    void tearDown() {
        SceneManager.setContext(null);
    }

    @Test
    void testRegisterScene() {
        SceneManager.registerScene(scene1);
        SceneManager.loadScene("Scene1");

        assertSame(scene1, SceneManager.getCurrentScene());
    }

    @Test
    void testLoadScene() {
        SceneManager.loadScene(scene1);

        assertSame(scene1, SceneManager.getCurrentScene());
        assertTrue(scene1.loaded);
    }

    @Test
    void testLoadSceneUnloadsCurrent() {
        SceneManager.loadScene(scene1);
        SceneManager.loadScene(scene2);

        assertTrue(scene1.unloaded);
        assertSame(scene2, SceneManager.getCurrentScene());
    }

    @Test
    void testUpdate() {
        SceneManager.loadScene(scene1);

        SceneManager.update(0.016f);

        assertTrue(scene1.updated);
    }

    @Test
    void testLifecycleListener() {
        TestListener listener = new TestListener();
        SceneManager.addLifecycleListener(listener);

        SceneManager.loadScene(scene1);

        assertSame(scene1, listener.lastLoaded);
    }

    private static class TestScene extends Scene {
        boolean loaded = false;
        boolean unloaded = false;
        boolean updated = false;

        public TestScene(String name) {
            super(name);
        }

        @Override
        public void onLoad() {
            loaded = true;
        }

        @Override
        public void onUnload() {
            unloaded = true;
        }

        @Override
        public void update(float deltaTime) {
            updated = true;
            super.update(deltaTime);
        }
    }

    private static class TestListener implements SceneLifecycleListener {
        Scene lastLoaded = null;

        @Override
        public void onSceneLoaded(Scene scene) {
            lastLoaded = scene;
        }

        @Override
        public void onSceneUnloaded(Scene scene) {
        }
    }
}
