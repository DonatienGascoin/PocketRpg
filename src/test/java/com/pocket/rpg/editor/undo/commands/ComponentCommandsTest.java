package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.rendering.SpritePostEffect;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.JsonPrefabHierarchyTest;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.ComponentRegistry;
import org.joml.Vector3f;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCommandsTest {

    private static final String PREFAB_ID = "guard_tower";
    private static final String SR_TYPE = SpriteRenderer.class.getName();

    @BeforeAll
    static void initRegistry() {
        ComponentRegistry.initialize();
    }

    @BeforeEach
    void setUp() {
        PrefabRegistry.getInstance().clear();
        JsonPrefab guardTower = JsonPrefabHierarchyTest.buildGuardTowerFixture();
        PrefabRegistry.getInstance().register(guardTower);
    }

    @AfterAll
    static void cleanup() {
        PrefabRegistry.getInstance().clear();
    }

    private EditorGameObject createEntity(String name) {
        return new EditorGameObject(name, new Vector3f(0, 0, 0), false);
    }

    private EditorGameObject createPrefabEntity() {
        return new EditorGameObject(PREFAB_ID, new Vector3f(0, 0, 0));
    }

    // ========================================================================
    // ADD COMPONENT COMMAND
    // ========================================================================

    @Nested
    class AddComponentCommandTests {

        @Test
        void execute_addsComponentToEntity() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            var cmd = new AddComponentCommand(entity, sprite);

            cmd.execute();

            assertTrue(entity.getComponents().contains(sprite));
        }

        @Test
        void undo_removesComponentFromEntity() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            var cmd = new AddComponentCommand(entity, sprite);
            cmd.execute();

            cmd.undo();

            assertFalse(entity.getComponents().contains(sprite));
        }

        @Test
        void fullCycle() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            var cmd = new AddComponentCommand(entity, sprite);

            cmd.execute();
            assertTrue(entity.getComponents().contains(sprite));

            cmd.undo();
            assertFalse(entity.getComponents().contains(sprite));

            cmd.execute();
            assertTrue(entity.getComponents().contains(sprite));
        }

        @Test
        void canMergeWith_returnsFalse() {
            var entity = createEntity("Player");
            var cmd = new AddComponentCommand(entity, new SpriteRenderer());
            assertFalse(cmd.canMergeWith(cmd));
        }
    }

    // ========================================================================
    // REMOVE COMPONENT COMMAND
    // ========================================================================

    @Nested
    class RemoveComponentCommandTests {

        @Test
        void execute_removesComponentFromEntity() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            entity.addComponent(sprite);

            var cmd = new RemoveComponentCommand(entity, sprite);
            cmd.execute();

            assertFalse(entity.getComponents().contains(sprite));
        }

        @Test
        void undo_reAddsComponentToEntity() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            entity.addComponent(sprite);

            var cmd = new RemoveComponentCommand(entity, sprite);
            cmd.execute();
            cmd.undo();

            assertTrue(entity.getComponents().contains(sprite));
        }

        @Test
        void fullCycle() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            entity.addComponent(sprite);

            var cmd = new RemoveComponentCommand(entity, sprite);

            cmd.execute();
            assertFalse(entity.getComponents().contains(sprite));

            cmd.undo();
            assertTrue(entity.getComponents().contains(sprite));

            cmd.execute();
            assertFalse(entity.getComponents().contains(sprite));
        }
    }

    // ========================================================================
    // SET COMPONENT FIELD COMMAND (merge logic only — execute/undo
    // requires ComponentRegistry which needs engine initialization)
    // ========================================================================

    @Nested
    class SetComponentFieldCommandTests {

        @Test
        void merge_sameComponentAndField_merges() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            entity.addComponent(sprite);

            var cmd1 = new SetComponentFieldCommand(sprite, "zIndex", 0, 10, entity);
            var cmd2 = new SetComponentFieldCommand(sprite, "zIndex", 10, 20, entity);

            assertTrue(cmd1.canMergeWith(cmd2));
        }

        @Test
        void merge_differentField_doesNotMerge() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            entity.addComponent(sprite);

            var cmd1 = new SetComponentFieldCommand(sprite, "zIndex", 0, 10, entity);
            var cmd2 = new SetComponentFieldCommand(sprite, "sprite", null, null, entity);

            assertFalse(cmd1.canMergeWith(cmd2));
        }

        @Test
        void merge_differentComponentType_doesNotMerge() {
            var entity = createEntity("Player");
            var sprite = new SpriteRenderer();
            var postEffect = new SpritePostEffect();
            entity.addComponent(sprite);
            entity.addComponent(postEffect);

            var cmd1 = new SetComponentFieldCommand(sprite, "zIndex", 0, 10, entity);
            var cmd2 = new SetComponentFieldCommand(postEffect, "zIndex", 0, 10, entity);

            assertFalse(cmd1.canMergeWith(cmd2));
        }
    }

    // ========================================================================
    // SETTER UNDO COMMAND
    // ========================================================================

    @Nested
    class SetterUndoCommandTests {

        @Test
        void execute_callsSetterWithNewValue() {
            var ref = new AtomicReference<>("old");
            var cmd = new SetterUndoCommand<>(ref::set, "old", "new", "test");

            cmd.execute();

            assertEquals("new", ref.get());
        }

        @Test
        void undo_callsSetterWithOldValue() {
            var ref = new AtomicReference<>("old");
            var cmd = new SetterUndoCommand<>(ref::set, "old", "new", "test");
            cmd.execute();

            cmd.undo();

            assertEquals("old", ref.get());
        }

        @Test
        void merge_sameSetter_merges() {
            var ref = new AtomicReference<>("A");
            var setter = (java.util.function.Consumer<String>) ref::set;
            var cmd1 = new SetterUndoCommand<>(setter, "A", "B", "test");
            var cmd2 = new SetterUndoCommand<>(setter, "B", "C", "test");

            assertTrue(cmd1.canMergeWith(cmd2));

            cmd1.mergeWith(cmd2);
            cmd1.execute();
            assertEquals("C", ref.get());

            cmd1.undo();
            assertEquals("A", ref.get());
        }

        @Test
        void merge_differentSetter_doesNotMerge() {
            var ref1 = new AtomicReference<>("A");
            var ref2 = new AtomicReference<>("X");
            var cmd1 = new SetterUndoCommand<>(ref1::set, "A", "B", "test1");
            var cmd2 = new SetterUndoCommand<>(ref2::set, "X", "Y", "test2");

            assertFalse(cmd1.canMergeWith(cmd2));
        }
    }

    // ========================================================================
    // RESET ALL OVERRIDES COMMAND
    // ========================================================================

    @Nested
    class ResetAllOverridesCommandTests {

        @Test
        void execute_clearsAllOverrides() {
            var entity = createPrefabEntity();
            // Apply an override on the SpriteRenderer
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 42)));
            assertFalse(entity.getComponentOverrides().isEmpty());

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();

            assertTrue(entity.getComponentOverrides().isEmpty());
            // Component should have reverted to prefab default (5 for root)
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(5, sr.getZIndex());
        }

        @Test
        void undo_restoresAllOverrides() {
            var entity = createPrefabEntity();
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 42)));

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();
            assertTrue(entity.getComponentOverrides().isEmpty());

            cmd.undo();

            assertFalse(entity.getComponentOverrides().isEmpty());
            assertEquals(42, entity.getComponentOverrides().get(SR_TYPE).get("zIndex"));
            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            assertEquals(42, sr.getZIndex());
        }

        @Test
        void fullCycle_multipleRounds() {
            var entity = createPrefabEntity();
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            var cmd = new ResetAllOverridesCommand(entity);

            for (int i = 0; i < 3; i++) {
                cmd.execute();
                assertTrue(entity.getComponentOverrides().isEmpty(),
                        "Cycle " + i + ": overrides should be empty after execute");
                assertEquals(5, entity.getComponent(SpriteRenderer.class).getZIndex(),
                        "Cycle " + i + ": should revert to default");

                cmd.undo();
                assertEquals(99, ((Number) entity.getComponentOverrides()
                                .get(SR_TYPE).get("zIndex")).intValue(),
                        "Cycle " + i + ": override should be restored after undo");
                assertEquals(99, entity.getComponent(SpriteRenderer.class).getZIndex(),
                        "Cycle " + i + ": component should have restored value");
            }
        }

        @Test
        void execute_onEntityWithNoOverrides_isNoop() {
            var entity = createPrefabEntity();
            // No overrides applied — map should already be empty
            assertTrue(entity.getComponentOverrides().isEmpty());

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();
            assertTrue(entity.getComponentOverrides().isEmpty());

            cmd.undo();
            assertTrue(entity.getComponentOverrides().isEmpty());
        }

        @Test
        void description() {
            var entity = createPrefabEntity();
            var cmd = new ResetAllOverridesCommand(entity);
            assertEquals("Reset All Overrides", cmd.getDescription());
        }
    }

    // ========================================================================
    // RESET FIELD OVERRIDE COMMAND (override map only — execute/undo
    // uses ComponentReflectionUtils so we test the map restoration logic)
    // ========================================================================

    @Nested
    class ResetFieldOverrideCommandTests {

        @Test
        void execute_resetsFieldAndUndoRestores() {
            var entity = createPrefabEntity();
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            SpriteRenderer sr = entity.getComponent(SpriteRenderer.class);
            var cmd = new ResetFieldOverrideCommand(entity, SR_TYPE, "zIndex");

            cmd.execute();

            // Field should be reset to default (5 for root guard_tower)
            assertEquals(5, sr.getZIndex());
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));

            cmd.undo();

            // Field should be restored to the overridden value
            assertEquals(99, sr.getZIndex());
            assertTrue(entity.isFieldOverridden(SR_TYPE, "zIndex"));
        }

        @Test
        void resetFieldToDefault_partialReset_preservesOtherOverrides() {
            var entity = createPrefabEntity();
            // Override two fields on the same component
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));
            // Mark a second field as overridden
            entity.markFieldOverridden(SR_TYPE, "flipX");

            // Reset only zIndex
            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            // zIndex should be cleared from overrides
            assertFalse(entity.isFieldOverridden(SR_TYPE, "zIndex"));
            // flipX should still be overridden
            assertTrue(entity.isFieldOverridden(SR_TYPE, "flipX"));
        }

        @Test
        void resetFieldToDefault_removesComponentTypeKey_whenLastField() {
            var entity = createPrefabEntity();
            entity.applySerializedOverrides(Map.of(SR_TYPE, Map.of("zIndex", 99)));

            entity.resetFieldToDefault(SR_TYPE, "zIndex");

            // No more overridden fields for this component type
            assertFalse(entity.getComponentOverrides().containsKey(SR_TYPE));
        }
    }
}
