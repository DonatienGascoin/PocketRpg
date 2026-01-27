package com.pocket.rpg.animation.animator;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.resources.loaders.AnimatorControllerLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimatorControllerLoaderTest {

    private AnimatorControllerLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new AnimatorControllerLoader();
    }

    @Test
    void testSupportedExtensions() {
        String[] extensions = loader.getSupportedExtensions();

        assertEquals(1, extensions.length);
        assertEquals(".animator.json", extensions[0]);
    }

    @Test
    void testSupportsHotReload() {
        assertTrue(loader.supportsHotReload());
    }

    @Test
    void testCanInstantiate() {
        assertFalse(loader.canInstantiate());
    }

    @Test
    void testGetPlaceholder() {
        AnimatorController placeholder = loader.getPlaceholder();

        assertNotNull(placeholder);
        assertEquals("placeholder", placeholder.getName());
        assertTrue(placeholder.getStateCount() > 0);
    }

    @Test
    void testSaveAndLoad() throws IOException {
        // Create a controller
        AnimatorController controller = new AnimatorController("test_controller");

        // Add parameters
        controller.addParameter(new AnimatorParameter("isMoving", false));
        controller.addParameter(new AnimatorParameter("direction", Direction.DOWN));
        controller.addParameter(AnimatorParameter.trigger("attack"));

        // Add states
        AnimatorState idle = new AnimatorState("idle", "animations/idle.anim");
        controller.addState(idle);

        AnimatorState walk = new AnimatorState("walk", StateType.DIRECTIONAL);
        walk.setDirectionalAnimation(Direction.UP, "animations/walk_up.anim");
        walk.setDirectionalAnimation(Direction.DOWN, "animations/walk_down.anim");
        walk.setDirectionalAnimation(Direction.LEFT, "animations/walk_left.anim");
        walk.setDirectionalAnimation(Direction.RIGHT, "animations/walk_right.anim");
        controller.addState(walk);

        // Add transitions
        AnimatorTransition idleToWalk = new AnimatorTransition("idle", "walk", TransitionType.INSTANT);
        idleToWalk.addCondition(new TransitionCondition("isMoving", true));
        controller.addTransition(idleToWalk);

        AnimatorTransition walkToIdle = new AnimatorTransition("walk", "idle", TransitionType.WAIT_FOR_COMPLETION);
        walkToIdle.addCondition(new TransitionCondition("isMoving", false));
        controller.addTransition(walkToIdle);

        // Save
        Path filePath = tempDir.resolve("test.animator.json");
        loader.save(controller, filePath.toString());

        assertTrue(Files.exists(filePath));

        // Load
        AnimatorController loaded = loader.load(filePath.toString());

        // Verify
        assertEquals("test_controller", loaded.getName());
        assertEquals("idle", loaded.getDefaultState());

        // Parameters
        assertEquals(3, loaded.getParameterCount());
        AnimatorParameter isMovingParam = loaded.getParameter("isMoving");
        assertNotNull(isMovingParam);
        assertEquals(ParameterType.BOOL, isMovingParam.getType());
        assertEquals(false, isMovingParam.getDefaultValue());

        AnimatorParameter dirParam = loaded.getParameter("direction");
        assertNotNull(dirParam);
        assertEquals(ParameterType.DIRECTION, dirParam.getType());
        assertEquals(Direction.DOWN, dirParam.getDefaultValue());

        AnimatorParameter attackParam = loaded.getParameter("attack");
        assertNotNull(attackParam);
        assertEquals(ParameterType.TRIGGER, attackParam.getType());

        // States
        assertEquals(2, loaded.getStateCount());

        AnimatorState loadedIdle = loaded.getState("idle");
        assertNotNull(loadedIdle);
        assertEquals(StateType.SIMPLE, loadedIdle.getType());
        assertEquals("animations/idle.anim", loadedIdle.getAnimation());

        AnimatorState loadedWalk = loaded.getState("walk");
        assertNotNull(loadedWalk);
        assertEquals(StateType.DIRECTIONAL, loadedWalk.getType());
        assertEquals("animations/walk_up.anim", loadedWalk.getDirectionalAnimation(Direction.UP));
        assertEquals("animations/walk_down.anim", loadedWalk.getDirectionalAnimation(Direction.DOWN));

        // Transitions
        assertEquals(2, loaded.getTransitionCount());

        AnimatorTransition loadedTrans = loaded.getTransition(0);
        assertEquals("idle", loadedTrans.getFrom());
        assertEquals("walk", loadedTrans.getTo());
        assertEquals(TransitionType.INSTANT, loadedTrans.getType());
        assertTrue(loadedTrans.hasConditions());
        assertEquals("isMoving", loadedTrans.getCondition(0).getParameter());
        assertEquals(true, loadedTrans.getCondition(0).getValue());
    }

    @Test
    void testLoadMinimalFile() throws IOException {
        String json = """
            {
              "name": "minimal",
              "defaultState": "idle",
              "states": [
                {
                  "name": "idle",
                  "type": "simple",
                  "animation": "idle.anim"
                }
              ]
            }
            """;

        Path filePath = tempDir.resolve("minimal.animator.json");
        Files.writeString(filePath, json);

        AnimatorController loaded = loader.load(filePath.toString());

        assertEquals("minimal", loaded.getName());
        assertEquals("idle", loaded.getDefaultState());
        assertEquals(1, loaded.getStateCount());
        assertEquals(0, loaded.getParameterCount());
        assertEquals(0, loaded.getTransitionCount());
    }

    @Test
    void testLoadWithWildcardTransitions() throws IOException {
        String json = """
            {
              "name": "wildcards",
              "defaultState": "idle",
              "states": [
                {"name": "idle", "type": "simple", "animation": "idle.anim"},
                {"name": "attack", "type": "simple", "animation": "attack.anim"}
              ],
              "transitions": [
                {"from": "*", "to": "attack", "type": "INSTANT"},
                {"from": "attack", "to": "*", "type": "WAIT_FOR_COMPLETION"}
              ]
            }
            """;

        Path filePath = tempDir.resolve("wildcards.animator.json");
        Files.writeString(filePath, json);

        AnimatorController loaded = loader.load(filePath.toString());

        assertEquals(2, loaded.getTransitionCount());

        AnimatorTransition anyToAttack = loaded.getTransition(0);
        assertTrue(anyToAttack.isFromAnyState());
        assertEquals("attack", anyToAttack.getTo());

        AnimatorTransition attackBack = loaded.getTransition(1);
        assertEquals("attack", attackBack.getFrom());
        assertTrue(attackBack.isToPreviousState());
    }

    @Test
    void testReload() throws IOException {
        // Create and save initial version
        AnimatorController original = new AnimatorController("original");
        original.addState(new AnimatorState("idle", "idle.anim"));

        Path filePath = tempDir.resolve("reload_test.animator.json");
        loader.save(original, filePath.toString());

        // Load it
        AnimatorController loaded = loader.load(filePath.toString());
        assertEquals("original", loaded.getName());
        assertEquals(1, loaded.getStateCount());

        // Create a modified version
        String modifiedJson = """
            {
              "name": "modified",
              "defaultState": "walk",
              "states": [
                {"name": "walk", "type": "simple", "animation": "walk.anim"},
                {"name": "run", "type": "simple", "animation": "run.anim"}
              ]
            }
            """;
        Files.writeString(filePath, modifiedJson);

        // Reload
        AnimatorController reloaded = loader.reload(loaded, filePath.toString());

        assertSame(loaded, reloaded); // Same instance
        assertEquals("modified", loaded.getName());
        assertEquals(2, loaded.getStateCount());
        assertTrue(loaded.hasState("walk"));
        assertTrue(loaded.hasState("run"));
        assertFalse(loaded.hasState("idle"));
    }

    @Test
    void testLoadInvalidFile() {
        Path filePath = tempDir.resolve("invalid.animator.json");

        assertThrows(IOException.class, () -> loader.load(filePath.toString()));
    }

    @Test
    void testLoadMalformedJson() throws IOException {
        String malformed = "{ this is not valid json }";
        Path filePath = tempDir.resolve("malformed.animator.json");
        Files.writeString(filePath, malformed);

        assertThrows(IOException.class, () -> loader.load(filePath.toString()));
    }

    @Test
    void testSaveCreatesDirectories() throws IOException {
        AnimatorController controller = new AnimatorController("test");
        controller.addState(new AnimatorState("idle", "idle.anim"));

        Path deepPath = tempDir.resolve("deep/nested/directory/test.animator.json");
        loader.save(controller, deepPath.toString());

        assertTrue(Files.exists(deepPath));
    }
}
