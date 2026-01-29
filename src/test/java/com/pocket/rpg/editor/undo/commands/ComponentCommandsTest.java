package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ComponentCommandsTest {

    private EditorGameObject createEntity(String name) {
        return new EditorGameObject(name, new Vector3f(0, 0, 0), false);
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
}
