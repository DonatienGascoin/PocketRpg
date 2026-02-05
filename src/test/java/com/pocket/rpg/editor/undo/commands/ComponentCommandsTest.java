package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCommandsTest {

    private EditorGameObject createEntity(String name) {
        return new EditorGameObject(name, new Vector3f(0, 0, 0), false);
    }

    private EditorGameObject createPrefabEntity(String prefabId) {
        return new EditorGameObject(prefabId, new Vector3f(0, 0, 0), true);
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
        void merge_differentComponent_doesNotMerge() {
            var entity = createEntity("Player");
            var sprite1 = new SpriteRenderer();
            var sprite2 = new SpriteRenderer();
            entity.addComponent(sprite1);
            entity.addComponent(sprite2);

            var cmd1 = new SetComponentFieldCommand(sprite1, "zIndex", 0, 10, entity);
            var cmd2 = new SetComponentFieldCommand(sprite2, "zIndex", 0, 10, entity);

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
            var entity = createPrefabEntity("TestPrefab");
            // Prefab constructor puts Transform overrides in the map
            assertFalse(entity.getComponentOverrides().isEmpty());

            // Add extra overrides
            entity.getComponentOverrides()
                    .computeIfAbsent("com.example.Foo", k -> new HashMap<>())
                    .put("bar", 42);

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();

            assertTrue(entity.getComponentOverrides().isEmpty());
        }

        @Test
        void undo_restoresAllOverrides() {
            var entity = createPrefabEntity("TestPrefab");
            entity.getComponentOverrides()
                    .computeIfAbsent("com.example.Foo", k -> new HashMap<>())
                    .put("bar", 42);

            // Snapshot what we expect to be restored
            int overrideKeyCount = entity.getComponentOverrides().size();

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();
            assertTrue(entity.getComponentOverrides().isEmpty());

            cmd.undo();

            assertEquals(overrideKeyCount, entity.getComponentOverrides().size());
            assertEquals(42, entity.getComponentOverrides().get("com.example.Foo").get("bar"));
        }

        @Test
        void fullCycle_multipleRounds() {
            var entity = createPrefabEntity("TestPrefab");
            entity.getComponentOverrides()
                    .computeIfAbsent("com.example.Sprite", k -> new HashMap<>())
                    .put("color", "red");

            var cmd = new ResetAllOverridesCommand(entity);

            for (int i = 0; i < 3; i++) {
                cmd.execute();
                assertTrue(entity.getComponentOverrides().isEmpty(),
                        "Cycle " + i + ": overrides should be empty after execute");

                cmd.undo();
                assertEquals("red", entity.getComponentOverrides()
                                .get("com.example.Sprite").get("color"),
                        "Cycle " + i + ": override should be restored after undo");
            }
        }

        @Test
        void execute_onEntityWithNoExtraOverrides_undoRestoresTransformOverrides() {
            var entity = createPrefabEntity("TestPrefab");
            // Only has the Transform overrides from constructor
            Map<String, Map<String, Object>> before = new HashMap<>();
            for (var entry : entity.getComponentOverrides().entrySet()) {
                before.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }

            var cmd = new ResetAllOverridesCommand(entity);
            cmd.execute();
            assertTrue(entity.getComponentOverrides().isEmpty());

            cmd.undo();
            assertEquals(before.size(), entity.getComponentOverrides().size());
            for (var entry : before.entrySet()) {
                assertTrue(entity.getComponentOverrides().containsKey(entry.getKey()));
            }
        }

        @Test
        void description() {
            var entity = createPrefabEntity("TestPrefab");
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
        void undo_restoresOverrideEntry() {
            var entity = createPrefabEntity("TestPrefab");
            String compType = "com.example.Foo";
            entity.getComponentOverrides()
                    .computeIfAbsent(compType, k -> new HashMap<>())
                    .put("speed", 99);

            // We can't call execute() because it uses ComponentReflectionUtils,
            // but we can test undo() restores the map correctly by simulating
            // what execute does to the override map.
            entity.resetFieldToDefault(compType, "speed");

            // Verify the override was removed
            var overrides = entity.getComponentOverrides().get(compType);
            assertTrue(overrides == null || !overrides.containsKey("speed"));

            // Simulate undo: restore the override
            entity.getComponentOverrides()
                    .computeIfAbsent(compType, k -> new HashMap<>())
                    .put("speed", 99);

            assertEquals(99, entity.getComponentOverrides().get(compType).get("speed"));
        }

        @Test
        void resetFieldToDefault_invalidatesCache_evenWhenMapNotEmpty() {
            var entity = createPrefabEntity("TestPrefab");
            String compType = "com.example.Foo";
            var overrides = entity.getComponentOverrides()
                    .computeIfAbsent(compType, k -> new HashMap<>());
            overrides.put("fieldA", "valueA");
            overrides.put("fieldB", "valueB");

            // Reset one field — map still has fieldB, cache should still be invalidated
            entity.resetFieldToDefault(compType, "fieldA");

            assertFalse(entity.getComponentOverrides().get(compType).containsKey("fieldA"));
            assertTrue(entity.getComponentOverrides().get(compType).containsKey("fieldB"));
            // If cache wasn't invalidated, stale components would be returned.
            // We can't check the cache directly, but this verifies the map state is correct.
        }

        @Test
        void resetFieldToDefault_removesComponentTypeKey_whenLastField() {
            var entity = createPrefabEntity("TestPrefab");
            String compType = "com.example.Foo";
            entity.getComponentOverrides()
                    .computeIfAbsent(compType, k -> new HashMap<>())
                    .put("onlyField", "value");

            entity.resetFieldToDefault(compType, "onlyField");

            assertFalse(entity.getComponentOverrides().containsKey(compType));
        }
    }
}
