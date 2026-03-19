package com.pocket.rpg.editor.panels.uidesigner;

import com.pocket.rpg.editor.scene.EditorGameObject;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pocket.rpg.editor.panels.uidesigner.UIDesignerSelectionHandler.SelectionAction;
import static com.pocket.rpg.editor.panels.uidesigner.UIDesignerSelectionHandler.decideAction;
import static com.pocket.rpg.editor.panels.uidesigner.UIDesignerSelectionHandler.getNextInCycle;
import static org.junit.jupiter.api.Assertions.*;

class UIDesignerSelectionHandlerTest {

    private static EditorGameObject createEntity(String name) {
        return new EditorGameObject(name, new Vector3f(), false);
    }

    // ========================================================================
    // CYCLING LOGIC TESTS
    // ========================================================================

    @Nested
    class CyclingLogicTests {

        @Test
        void cycleThrough3Entities_frontToBack() {
            // Scene order: a(back), b(middle), c(front)
            EditorGameObject a = createEntity("A");
            EditorGameObject b = createEntity("B");
            EditorGameObject c = createEntity("C");
            List<EditorGameObject> entities = List.of(a, b, c);

            // Cycling goes backward: c→b→a→c (front to back, then wraps)
            assertEquals(b, getNextInCycle(entities, c));
            assertEquals(a, getNextInCycle(entities, b));
            assertEquals(c, getNextInCycle(entities, a));
        }

        @Test
        void singleEntity_staysSelected() {
            EditorGameObject a = createEntity("A");
            List<EditorGameObject> entities = List.of(a);

            assertEquals(a, getNextInCycle(entities, a));
        }

        @Test
        void selectedNotInList_returnsFrontmost() {
            EditorGameObject a = createEntity("A");
            EditorGameObject b = createEntity("B");
            EditorGameObject outsider = createEntity("Outsider");
            List<EditorGameObject> entities = List.of(a, b);

            // b is last in scene order = frontmost
            assertEquals(b, getNextInCycle(entities, outsider));
        }

        @Test
        void emptyList_returnsNull() {
            EditorGameObject a = createEntity("A");
            assertNull(getNextInCycle(List.of(), a));
        }

        @Test
        void nullList_returnsNull() {
            assertNull(getNextInCycle(null, createEntity("A")));
        }

        @Test
        void nullCurrent_returnsFrontmost() {
            EditorGameObject a = createEntity("A");
            EditorGameObject b = createEntity("B");
            List<EditorGameObject> entities = List.of(a, b);

            // b is last in scene order = frontmost
            assertEquals(b, getNextInCycle(entities, null));
        }
    }

    // ========================================================================
    // SELECTION DECISION LOGIC TESTS
    // ========================================================================

    @Nested
    class SelectionDecisionTests {

        @Test
        void gizmoHit_returnsGizmo_regardlessOfOtherState() {
            assertEquals(SelectionAction.GIZMO,
                    decideAction(true, false, false, false, false));
            assertEquals(SelectionAction.GIZMO,
                    decideAction(true, true, true, true, true));
        }

        @Test
        void noModifiers_selectedUnderCursor_keepsSelection() {
            assertEquals(SelectionAction.KEEP_SELECTION,
                    decideAction(false, false, false, true, true));
        }

        @Test
        void noModifiers_selectedNotUnderCursor_picksTopmost() {
            assertEquals(SelectionAction.PICK_TOPMOST,
                    decideAction(false, false, false, false, true));
        }

        @Test
        void ctrlHeld_togglesSelection() {
            assertEquals(SelectionAction.TOGGLE,
                    decideAction(false, true, false, false, true));
        }

        @Test
        void ctrlHeld_evenIfSelectedUnderCursor_toggles() {
            assertEquals(SelectionAction.TOGGLE,
                    decideAction(false, true, false, true, true));
        }

        @Test
        void shiftHeld_addsToSelection() {
            assertEquals(SelectionAction.ADD_TO_SELECTION,
                    decideAction(false, false, true, false, true));
        }

        @Test
        void shiftHeld_evenIfSelectedUnderCursor_adds() {
            assertEquals(SelectionAction.ADD_TO_SELECTION,
                    decideAction(false, false, true, true, true));
        }

        @Test
        void noEntity_noModifiers_clears() {
            assertEquals(SelectionAction.CLEAR,
                    decideAction(false, false, false, false, false));
        }

        @Test
        void noEntity_withModifiers_clears() {
            // Ctrl/Shift with no entity doesn't do anything useful — returns CLEAR
            assertEquals(SelectionAction.CLEAR,
                    decideAction(false, true, false, false, false));
            assertEquals(SelectionAction.CLEAR,
                    decideAction(false, false, true, false, false));
        }
    }
}
