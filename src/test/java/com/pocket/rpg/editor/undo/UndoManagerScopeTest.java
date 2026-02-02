package com.pocket.rpg.editor.undo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UndoManagerScopeTest {

    private UndoManager manager;

    @BeforeEach
    void setUp() {
        manager = UndoManager.getInstance();
        manager.clear();
        manager.setEnabled(true);
        manager.setMaxHistorySize(100);
        // Ensure no leftover scopes from previous tests
        while (manager.isInScope()) {
            manager.popScope();
        }
    }

    // ========================================================================
    // PUSH / POP LIFECYCLE
    // ========================================================================

    @Nested
    class PushPopLifecycle {

        @Test
        void pushScope_startsWithEmptyStacks() {
            manager.execute(new UndoManagerTest.TrackingCommand("before"));
            assertEquals(1, manager.getUndoCount());

            manager.pushScope();

            assertFalse(manager.canUndo());
            assertFalse(manager.canRedo());
            assertEquals(0, manager.getUndoCount());
            assertEquals(0, manager.getRedoCount());
        }

        @Test
        void popScope_restoresPreviousState() {
            manager.execute(new UndoManagerTest.TrackingCommand("A"));
            manager.execute(new UndoManagerTest.TrackingCommand("B"));
            manager.undo(); // undo B -> 1 undo, 1 redo

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("scoped"));

            manager.popScope();

            assertEquals(1, manager.getUndoCount());
            assertEquals(1, manager.getRedoCount());
            assertEquals("A", manager.getUndoDescription());
            assertEquals("B", manager.getRedoDescription());
        }

        @Test
        void popScope_discardsCurrentScopeCommands() {
            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("X"));
            manager.execute(new UndoManagerTest.TrackingCommand("Y"));
            assertEquals(2, manager.getUndoCount());

            manager.popScope();

            assertEquals(0, manager.getUndoCount());
        }

        @Test
        void popScope_onEmpty_throwsIllegalState() {
            assertThrows(IllegalStateException.class, () -> manager.popScope());
        }
    }

    // ========================================================================
    // NESTED SCOPES
    // ========================================================================

    @Nested
    class NestedScopes {

        @Test
        void nestedScopes_isolateCorrectly() {
            manager.execute(new UndoManagerTest.TrackingCommand("root"));

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("scope1"));

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("scope2"));

            assertEquals(1, manager.getUndoCount());
            assertEquals("scope2", manager.getUndoDescription());

            manager.popScope(); // back to scope1
            assertEquals(1, manager.getUndoCount());
            assertEquals("scope1", manager.getUndoDescription());

            manager.popScope(); // back to root
            assertEquals(1, manager.getUndoCount());
            assertEquals("root", manager.getUndoDescription());
        }
    }

    // ========================================================================
    // UNDO / REDO ISOLATION
    // ========================================================================

    @Nested
    class UndoRedoIsolation {

        @Test
        void undoInScope_doesNotAffectParent() {
            manager.execute(new UndoManagerTest.TrackingCommand("parent"));

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("child1"));
            manager.execute(new UndoManagerTest.TrackingCommand("child2"));
            manager.execute(new UndoManagerTest.TrackingCommand("child3"));

            manager.undo(); // undo child3
            manager.undo(); // undo child2

            assertEquals(1, manager.getUndoCount());
            assertEquals(2, manager.getRedoCount());

            manager.popScope();

            // Parent state restored
            assertEquals(1, manager.getUndoCount());
            assertEquals(0, manager.getRedoCount());
            assertEquals("parent", manager.getUndoDescription());
        }

        @Test
        void canUndo_canRedo_freshScope() {
            manager.pushScope();
            assertFalse(manager.canUndo());
            assertFalse(manager.canRedo());
            manager.popScope();
        }
    }

    // ========================================================================
    // SCOPE DEPTH / IS IN SCOPE
    // ========================================================================

    @Nested
    class ScopeDepthTests {

        @Test
        void getScopeDepth_startsAtZero() {
            assertEquals(0, manager.getScopeDepth());
        }

        @Test
        void getScopeDepth_incrementsOnPush() {
            manager.pushScope();
            assertEquals(1, manager.getScopeDepth());
            manager.pushScope();
            assertEquals(2, manager.getScopeDepth());
            manager.popScope();
            assertEquals(1, manager.getScopeDepth());
            manager.popScope();
            assertEquals(0, manager.getScopeDepth());
        }

        @Test
        void isInScope_falseAtRoot() {
            assertFalse(manager.isInScope());
        }

        @Test
        void isInScope_trueAfterPush() {
            manager.pushScope();
            assertTrue(manager.isInScope());
            manager.popScope();
            assertFalse(manager.isInScope());
        }
    }

    // ========================================================================
    // MERGE CHAIN RESET
    // ========================================================================

    @Nested
    class MergeChainReset {

        @Test
        void pushScope_breaksMergeChain() {
            var cmd1 = new UndoManagerTest.MergeableCommand("field", "A", "B");
            manager.execute(cmd1);

            manager.pushScope();

            // This should NOT merge with cmd1 since scope was pushed
            var cmd2 = new UndoManagerTest.MergeableCommand("field", "B", "C");
            manager.execute(cmd2);

            assertEquals(1, manager.getUndoCount());
            // cmd1 should not have been mutated
            assertEquals("B", cmd1.newValue);

            manager.popScope();
        }
    }

    // ========================================================================
    // CLEAR INSIDE SCOPE
    // ========================================================================

    @Nested
    class ClearInsideScope {

        @Test
        void clear_onlyClearsCurrentScope() {
            manager.execute(new UndoManagerTest.TrackingCommand("parent"));

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("child"));
            manager.clear();

            assertFalse(manager.canUndo());
            assertFalse(manager.canRedo());

            manager.popScope();

            // Parent state should be intact
            assertEquals(1, manager.getUndoCount());
            assertEquals("parent", manager.getUndoDescription());
        }
    }

    // ========================================================================
    // DESCRIPTION / COUNT IN SCOPE
    // ========================================================================

    @Nested
    class DescriptionAndCountInScope {

        @Test
        void getUndoDescription_returnsScopedDescription() {
            manager.execute(new UndoManagerTest.TrackingCommand("parent"));

            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("scoped"));

            assertEquals("scoped", manager.getUndoDescription());
            manager.popScope();
        }

        @Test
        void getUndoCount_getRedoCount_reflectCurrentScope() {
            manager.pushScope();
            manager.execute(new UndoManagerTest.TrackingCommand("A"));
            manager.execute(new UndoManagerTest.TrackingCommand("B"));
            manager.undo();

            assertEquals(1, manager.getUndoCount());
            assertEquals(1, manager.getRedoCount());
            manager.popScope();
        }

        @Test
        void executeAfterPop_goesToRestoredScope() {
            manager.pushScope();
            manager.popScope();

            manager.execute(new UndoManagerTest.TrackingCommand("afterPop"));
            assertEquals(1, manager.getUndoCount());
            assertEquals("afterPop", manager.getUndoDescription());
        }
    }
}
