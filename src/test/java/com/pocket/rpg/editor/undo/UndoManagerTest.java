package com.pocket.rpg.editor.undo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UndoManagerTest {

    private UndoManager manager;

    @BeforeEach
    void setUp() {
        manager = UndoManager.getInstance();
        manager.clear();
        manager.setEnabled(true);
        manager.setMaxHistorySize(100);
    }

    // ========================================================================
    // BASIC OPERATIONS
    // ========================================================================

    @Nested
    class BasicOperations {

        @Test
        void execute_runsCommandAndAddsToUndoStack() {
            var cmd = new TrackingCommand("test");
            manager.execute(cmd);

            assertTrue(cmd.executed);
            assertTrue(manager.canUndo());
            assertEquals(1, manager.getUndoCount());
        }

        @Test
        void execute_nullCommand_doesNothing() {
            manager.execute(null);
            assertFalse(manager.canUndo());
        }

        @Test
        void undo_reversesLastCommand() {
            var cmd = new TrackingCommand("test");
            manager.execute(cmd);

            boolean result = manager.undo();

            assertTrue(result);
            assertTrue(cmd.undone);
            assertFalse(manager.canUndo());
            assertTrue(manager.canRedo());
        }

        @Test
        void undo_emptyStack_returnsFalse() {
            assertFalse(manager.undo());
        }

        @Test
        void redo_reExecutesUndoneCommand() {
            var cmd = new TrackingCommand("test");
            manager.execute(cmd);
            manager.undo();

            boolean result = manager.redo();

            assertTrue(result);
            assertEquals(2, cmd.executeCount);
            assertTrue(manager.canUndo());
            assertFalse(manager.canRedo());
        }

        @Test
        void redo_emptyStack_returnsFalse() {
            assertFalse(manager.redo());
        }

        @Test
        void execute_clearsRedoStack() {
            var cmd1 = new TrackingCommand("first");
            var cmd2 = new TrackingCommand("second");
            manager.execute(cmd1);
            manager.undo();
            assertTrue(manager.canRedo());

            manager.execute(cmd2);

            assertFalse(manager.canRedo());
        }

        @Test
        void multipleUndoRedo_cycle() {
            var cmd1 = new TrackingCommand("A");
            var cmd2 = new TrackingCommand("B");
            var cmd3 = new TrackingCommand("C");

            manager.execute(cmd1);
            manager.execute(cmd2);
            manager.execute(cmd3);

            assertEquals(3, manager.getUndoCount());

            manager.undo(); // undo C
            manager.undo(); // undo B

            assertEquals(1, manager.getUndoCount());
            assertEquals(2, manager.getRedoCount());

            manager.redo(); // redo B

            assertEquals(2, manager.getUndoCount());
            assertEquals(1, manager.getRedoCount());
        }
    }

    // ========================================================================
    // DESCRIPTIONS
    // ========================================================================

    @Nested
    class Descriptions {

        @Test
        void getUndoDescription_returnsTopOfStack() {
            manager.execute(new TrackingCommand("Move"));
            assertEquals("Move", manager.getUndoDescription());
        }

        @Test
        void getRedoDescription_returnsTopOfRedoStack() {
            manager.execute(new TrackingCommand("Scale"));
            manager.undo();
            assertEquals("Scale", manager.getRedoDescription());
        }

        @Test
        void getUndoDescription_emptyStack_returnsNull() {
            assertNull(manager.getUndoDescription());
        }

        @Test
        void getRedoDescription_emptyStack_returnsNull() {
            assertNull(manager.getRedoDescription());
        }
    }

    // ========================================================================
    // PUSH (pre-executed commands)
    // ========================================================================

    @Nested
    class PushTests {

        @Test
        void push_addsToUndoWithoutExecuting() {
            var cmd = new TrackingCommand("drag");
            manager.push(cmd);

            assertFalse(cmd.executed);
            assertTrue(manager.canUndo());
            assertEquals(1, manager.getUndoCount());
        }

        @Test
        void push_clearsRedoStack() {
            manager.execute(new TrackingCommand("first"));
            manager.undo();
            assertTrue(manager.canRedo());

            manager.push(new TrackingCommand("pushed"));

            assertFalse(manager.canRedo());
        }

        @Test
        void push_nullCommand_doesNothing() {
            manager.push(null);
            assertFalse(manager.canUndo());
        }
    }

    // ========================================================================
    // ENABLED / DISABLED
    // ========================================================================

    @Nested
    class EnableDisable {

        @Test
        void disabled_execute_doesNotRunOrRecord() {
            manager.setEnabled(false);
            var cmd = new TrackingCommand("test");
            manager.execute(cmd);

            assertFalse(cmd.executed);
            assertFalse(manager.canUndo());
        }

        @Test
        void disabled_push_doesNotRecord() {
            manager.setEnabled(false);
            manager.push(new TrackingCommand("test"));
            assertFalse(manager.canUndo());
        }

        @Test
        void executeWithoutHistory_runsButDoesNotRecord() {
            var ran = new boolean[]{false};
            manager.executeWithoutHistory(() -> ran[0] = true);

            assertTrue(ran[0]);
            assertFalse(manager.canUndo());
            assertTrue(manager.isEnabled()); // re-enabled after
        }

        @Test
        void executeWithoutHistory_restoresEnabledOnException() {
            try {
                manager.executeWithoutHistory(() -> {
                    throw new RuntimeException("oops");
                });
            } catch (RuntimeException ignored) {}

            assertTrue(manager.isEnabled());
        }
    }

    // ========================================================================
    // MAX HISTORY SIZE
    // ========================================================================

    @Nested
    class MaxHistorySize {

        @Test
        void enforcesMaxSize_onExecute() {
            manager.setMaxHistorySize(3);

            for (int i = 0; i < 5; i++) {
                manager.execute(new TrackingCommand("cmd" + i));
            }

            assertEquals(3, manager.getUndoCount());
        }

        @Test
        void enforcesMaxSize_onPush() {
            manager.setMaxHistorySize(2);

            for (int i = 0; i < 4; i++) {
                manager.push(new TrackingCommand("cmd" + i));
            }

            assertEquals(2, manager.getUndoCount());
        }

        @Test
        void setMaxHistorySize_minimumIsOne() {
            manager.setMaxHistorySize(0);
            manager.execute(new TrackingCommand("test"));
            assertEquals(1, manager.getUndoCount());
        }
    }

    // ========================================================================
    // COMMAND MERGING
    // ========================================================================

    @Nested
    class CommandMerging {

        @Test
        void mergesRapidCommandsOfSameType() {
            var cmd1 = new MergeableCommand("field", "A", "B");
            var cmd2 = new MergeableCommand("field", "B", "C");

            manager.execute(cmd1);
            // cmd2 should merge into cmd1 since same field and within time window
            manager.execute(cmd2);

            assertEquals(1, manager.getUndoCount());
            assertEquals("C", cmd1.newValue); // merged
        }

        @Test
        void doesNotMerge_differentFields() {
            var cmd1 = new MergeableCommand("fieldA", "A", "B");
            var cmd2 = new MergeableCommand("fieldB", "X", "Y");

            manager.execute(cmd1);
            manager.execute(cmd2);

            assertEquals(2, manager.getUndoCount());
        }

        @Test
        void doesNotMerge_nonMergeableCommand() {
            var cmd1 = new MergeableCommand("field", "A", "B");
            var cmd2 = new TrackingCommand("other");

            manager.execute(cmd1);
            manager.execute(cmd2);

            assertEquals(2, manager.getUndoCount());
        }

        @Test
        void undo_breaksMergeChain() {
            var cmd1 = new MergeableCommand("field", "A", "B");
            manager.execute(cmd1);
            manager.undo();

            var cmd2 = new MergeableCommand("field", "X", "Y");
            manager.execute(cmd2);

            // cmd2 should NOT merge with cmd1 since undo broke the chain.
            // undo clears lastCommand, so cmd2 is a fresh entry.
            // The redo stack is also cleared by execute.
            assertEquals(1, manager.getUndoCount());
            assertEquals(0, manager.getRedoCount());
            // Verify cmd1 was not mutated by a merge
            assertEquals("B", cmd1.newValue);
        }
    }

    // ========================================================================
    // CLEAR
    // ========================================================================

    @Nested
    class ClearTests {

        @Test
        void clear_emptiesBothStacks() {
            manager.execute(new TrackingCommand("A"));
            manager.execute(new TrackingCommand("B"));
            manager.undo();

            manager.clear();

            assertFalse(manager.canUndo());
            assertFalse(manager.canRedo());
            assertEquals(0, manager.getUndoCount());
            assertEquals(0, manager.getRedoCount());
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    /** Simple command that tracks execute/undo calls. */
    static class TrackingCommand implements EditorCommand {
        final String desc;
        boolean executed = false;
        boolean undone = false;
        int executeCount = 0;

        TrackingCommand(String desc) {
            this.desc = desc;
        }

        @Override
        public void execute() {
            executed = true;
            executeCount++;
        }

        @Override
        public void undo() {
            undone = true;
        }

        @Override
        public String getDescription() {
            return desc;
        }
    }

    /** Command that supports merging on same field name. */
    static class MergeableCommand implements EditorCommand {
        final String field;
        final String oldValue;
        String newValue;

        MergeableCommand(String field, String oldValue, String newValue) {
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void execute() {}

        @Override
        public void undo() {}

        @Override
        public String getDescription() {
            return "Change " + field;
        }

        @Override
        public boolean canMergeWith(EditorCommand other) {
            return other instanceof MergeableCommand m && m.field.equals(this.field);
        }

        @Override
        public void mergeWith(EditorCommand other) {
            if (other instanceof MergeableCommand m) {
                this.newValue = m.newValue;
            }
        }
    }
}
