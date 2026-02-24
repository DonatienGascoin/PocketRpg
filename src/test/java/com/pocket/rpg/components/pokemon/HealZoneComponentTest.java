package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.save.SaveManager;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.Serializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HealZoneComponentTest {

    @TempDir
    Path tempDir;

    private SceneManager sceneManager;
    private static Pokedex testPokedex;

    @BeforeAll
    static void initSerializer() {
        testPokedex = PlayerPartyComponentTest.createTestPokedex();
        com.pocket.rpg.resources.Assets.setContext(new PlayerPartyComponentTest.PokedexStubContext(testPokedex));
        Serializer.init(com.pocket.rpg.resources.Assets.getContext());
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        sceneManager = new SceneManager(
                new ViewportConfig(GameConfig.builder()
                        .gameWidth(800).gameHeight(600)
                        .windowWidth(800).windowHeight(600)
                        .build()),
                RenderingConfig.builder().defaultOrthographicSize(7.5f).build()
        );
        SaveManager.initialize(sceneManager, tempDir);
        SaveManager.newGame();
    }

    @Test
    @DisplayName("interact heals all party Pokemon when player has PlayerPartyComponent")
    void interactHealsParty() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            player.addComponent(new PlayerPartyComponent());
            scene.addGameObject(player);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        PlayerPartyComponent party = player.getComponent(PlayerPartyComponent.class);

        // Add a damaged Pokemon
        PokemonInstance p = PokemonFactory.createWild(testPokedex, "bulbasaur", 10);
        party.addToParty(p);
        int maxHp = p.calcMaxHp();
        p.damage(maxHp / 2);
        p.setStatusCondition(StatusCondition.POISON);
        p.getMoves().getFirst().usePp();

        // Interact with heal zone
        HealZoneComponent healZone = new HealZoneComponent();
        healZone.interact(player);

        assertEquals(maxHp, p.getCurrentHp());
        assertEquals(StatusCondition.NONE, p.getStatusCondition());
        assertEquals(p.getMoves().getFirst().getMaxPp(), p.getMoves().getFirst().getCurrentPp());
    }

    @Test
    @DisplayName("interact with player lacking PlayerPartyComponent — no crash")
    void interactWithoutPartyComponent() {
        TestScene scene = new TestScene("test");
        scene.setSetupAction(() -> {
            GameObject player = new GameObject("Player");
            scene.addGameObject(player);
        });
        sceneManager.loadScene(scene);

        GameObject player = scene.findGameObject("Player");
        HealZoneComponent healZone = new HealZoneComponent();

        assertDoesNotThrow(() -> healZone.interact(player));
    }

    @Test
    @DisplayName("getInteractionPrompt returns 'Heal'")
    void getInteractionPrompt() {
        HealZoneComponent healZone = new HealZoneComponent();
        assertEquals("Heal", healZone.getInteractionPrompt());
    }

    // ========================================================================
    // Test infrastructure
    // ========================================================================

    private static class TestScene extends Scene {
        private Runnable setupAction;

        public TestScene(String name) { super(name); }

        void setSetupAction(Runnable action) { this.setupAction = action; }

        @Override
        public void onLoad() {
            if (setupAction != null) setupAction.run();
        }
    }
}
