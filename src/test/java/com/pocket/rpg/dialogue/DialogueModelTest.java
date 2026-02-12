package com.pocket.rpg.dialogue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueModelTest {

    // ========================================================================
    // DIALOGUE — mixed entries
    // ========================================================================

    @Nested
    class DialogueTests {

        @Test
        void dialogueWithMixedEntries() {
            DialogueLine line1 = new DialogueLine("Hello!");
            DialogueLine line2 = new DialogueLine("What would you like?");
            DialogueChoiceGroup choices = new DialogueChoiceGroup(true, List.of(
                    new Choice("Tell me more", ChoiceAction.dialogue("dialogues/lore.dialogue.json")),
                    new Choice("Goodbye", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
            ));

            Dialogue dialogue = new Dialogue("test", List.of(line1, line2, choices));

            assertEquals("test", dialogue.getName());
            assertEquals(3, dialogue.getEntries().size());
            assertInstanceOf(DialogueLine.class, dialogue.getEntries().get(0));
            assertInstanceOf(DialogueLine.class, dialogue.getEntries().get(1));
            assertInstanceOf(DialogueChoiceGroup.class, dialogue.getEntries().get(2));
        }

        @Test
        void dialogueWithOnlyLines() {
            Dialogue dialogue = new Dialogue("simple", List.of(
                    new DialogueLine("Line 1"),
                    new DialogueLine("Line 2")
            ));

            assertEquals(2, dialogue.getEntries().size());
            for (DialogueEntry entry : dialogue.getEntries()) {
                assertInstanceOf(DialogueLine.class, entry);
            }
        }

        @Test
        void emptyDialogue() {
            Dialogue dialogue = new Dialogue("empty");
            assertTrue(dialogue.getEntries().isEmpty());
        }

        @Test
        void defaultConstructorSetsDefaults() {
            Dialogue dialogue = new Dialogue();
            assertEquals("", dialogue.getName());
            assertNotNull(dialogue.getEntries());
            assertTrue(dialogue.getEntries().isEmpty());
        }

        @Test
        void copyFromReplacesContent() {
            Dialogue original = new Dialogue("original", List.of(new DialogueLine("Hello")));
            Dialogue target = new Dialogue("target", List.of(new DialogueLine("Old")));

            target.copyFrom(original);

            assertEquals("original", target.getName());
            assertEquals(1, target.getEntries().size());
            assertInstanceOf(DialogueLine.class, target.getEntries().get(0));
            assertEquals("Hello", ((DialogueLine) target.getEntries().get(0)).getText());
        }
    }

    // ========================================================================
    // DIALOGUE LINE
    // ========================================================================

    @Nested
    class DialogueLineTests {

        @Test
        void lineWithText() {
            DialogueLine line = new DialogueLine("Hello [PLAYER_NAME]!");
            assertEquals("Hello [PLAYER_NAME]!", line.getText());
            assertNull(line.getOnCompleteEvent());
        }

        @Test
        void lineWithOnCompleteEvent() {
            DialogueEventRef event = DialogueEventRef.custom("PLAY_RUMBLE");
            DialogueLine line = new DialogueLine("Did you hear that?", event);

            assertEquals("Did you hear that?", line.getText());
            assertNotNull(line.getOnCompleteEvent());
            assertTrue(line.getOnCompleteEvent().isCustom());
            assertEquals("PLAY_RUMBLE", line.getOnCompleteEvent().getCustomEvent());
        }

        @Test
        void defaultConstructor() {
            DialogueLine line = new DialogueLine();
            assertEquals("", line.getText());
            assertNull(line.getOnCompleteEvent());
        }
    }

    // ========================================================================
    // CHOICE GROUP
    // ========================================================================

    @Nested
    class ChoiceGroupTests {

        @Test
        void choiceGroupWithChoices() {
            Choice c1 = new Choice("Tell me more", ChoiceAction.dialogue("dialogues/lore.dialogue.json"));
            Choice c2 = new Choice("Leave", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION));

            DialogueChoiceGroup group = new DialogueChoiceGroup(true, List.of(c1, c2));

            assertTrue(group.isHasChoices());
            assertEquals(2, group.getChoices().size());
        }

        @Test
        void choiceGroupDisabled() {
            DialogueChoiceGroup group = new DialogueChoiceGroup(false, List.of());
            assertFalse(group.isHasChoices());
            assertTrue(group.getChoices().isEmpty());
        }

        @Test
        void defaultConstructor() {
            DialogueChoiceGroup group = new DialogueChoiceGroup();
            assertTrue(group.isHasChoices());
            assertNotNull(group.getChoices());
            assertTrue(group.getChoices().isEmpty());
        }

        @Test
        void constructorClampsToMaxChoices() {
            List<Choice> five = List.of(
                    new Choice("A", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("C", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("D", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("E", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
            );

            DialogueChoiceGroup group = new DialogueChoiceGroup(true, five);

            assertEquals(DialogueChoiceGroup.MAX_CHOICES, group.getChoices().size());
            assertEquals("A", group.getChoices().get(0).getText());
            assertEquals("D", group.getChoices().get(3).getText());
        }

        @Test
        void setterClampsToMaxChoices() {
            DialogueChoiceGroup group = new DialogueChoiceGroup();
            List<Choice> six = List.of(
                    new Choice("1", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("2", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("3", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("4", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("5", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("6", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
            );

            group.setChoices(six);

            assertEquals(DialogueChoiceGroup.MAX_CHOICES, group.getChoices().size());
            assertEquals("1", group.getChoices().get(0).getText());
            assertEquals("4", group.getChoices().get(3).getText());
        }

        @Test
        void exactlyMaxChoicesIsNotClamped() {
            List<Choice> four = List.of(
                    new Choice("A", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("B", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("C", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)),
                    new Choice("D", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION))
            );

            DialogueChoiceGroup group = new DialogueChoiceGroup(true, four);

            assertEquals(4, group.getChoices().size());
        }
    }

    // ========================================================================
    // CHOICE ACTION — path strings
    // ========================================================================

    @Nested
    class ChoiceActionTests {

        @Test
        void dialogueActionStoresPath() {
            ChoiceAction action = ChoiceAction.dialogue("dialogues/professor_lore.dialogue.json");

            assertEquals(ChoiceActionType.DIALOGUE, action.getType());
            assertEquals("dialogues/professor_lore.dialogue.json", action.getDialoguePath());
            assertNull(action.getBuiltInEvent());
            assertNull(action.getCustomEvent());
        }

        @Test
        void builtInEventAction() {
            ChoiceAction action = ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION);

            assertEquals(ChoiceActionType.BUILT_IN_EVENT, action.getType());
            assertEquals(DialogueEvent.END_CONVERSATION, action.getBuiltInEvent());
            assertNull(action.getDialoguePath());
            assertNull(action.getCustomEvent());
        }

        @Test
        void customEventAction() {
            ChoiceAction action = ChoiceAction.customEvent("OPEN_DOOR");

            assertEquals(ChoiceActionType.CUSTOM_EVENT, action.getType());
            assertEquals("OPEN_DOOR", action.getCustomEvent());
            assertNull(action.getDialoguePath());
            assertNull(action.getBuiltInEvent());
        }

        @Test
        void defaultConstructor() {
            ChoiceAction action = new ChoiceAction();
            assertNull(action.getType());
            assertNull(action.getDialoguePath());
            assertNull(action.getBuiltInEvent());
            assertNull(action.getCustomEvent());
        }
    }

    // ========================================================================
    // DIALOGUE EVENT REF — built-in vs custom
    // ========================================================================

    @Nested
    class DialogueEventRefTests {

        @Test
        void builtInRef() {
            DialogueEventRef ref = DialogueEventRef.builtIn(DialogueEvent.END_CONVERSATION);

            assertTrue(ref.isBuiltIn());
            assertFalse(ref.isCustom());
            assertEquals(DialogueEventRef.Category.BUILT_IN, ref.getCategory());
            assertEquals(DialogueEvent.END_CONVERSATION, ref.getBuiltInEvent());
            assertNull(ref.getCustomEvent());
        }

        @Test
        void customRef() {
            DialogueEventRef ref = DialogueEventRef.custom("OPEN_DOOR");

            assertFalse(ref.isBuiltIn());
            assertTrue(ref.isCustom());
            assertEquals(DialogueEventRef.Category.CUSTOM, ref.getCategory());
            assertEquals("OPEN_DOOR", ref.getCustomEvent());
            assertNull(ref.getBuiltInEvent());
        }

        @Test
        void defaultConstructor() {
            DialogueEventRef ref = new DialogueEventRef();
            assertNull(ref.getCategory());
            assertNull(ref.getBuiltInEvent());
            assertNull(ref.getCustomEvent());
        }

        @Test
        void endConversationBuiltIn() {
            DialogueEventRef ref = DialogueEventRef.builtIn(DialogueEvent.END_CONVERSATION);
            assertEquals(DialogueEvent.END_CONVERSATION, ref.getBuiltInEvent());
            assertTrue(ref.isBuiltIn());
        }
    }

    // ========================================================================
    // CHOICE
    // ========================================================================

    @Nested
    class ChoiceTests {

        @Test
        void choiceWithTextAndAction() {
            ChoiceAction action = ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION);
            Choice choice = new Choice("Goodbye", action);

            assertEquals("Goodbye", choice.getText());
            assertSame(action, choice.getAction());
        }

        @Test
        void defaultConstructor() {
            Choice choice = new Choice();
            assertEquals("", choice.getText());
            assertNotNull(choice.getAction());
        }
    }

    // ========================================================================
    // SEALED INTERFACE — exhaustive pattern matching
    // ========================================================================

    @Nested
    class SealedInterfaceTests {

        @Test
        void switchExpressionCoversAllTypes() {
            DialogueEntry line = new DialogueLine("text");
            DialogueEntry choices = new DialogueChoiceGroup();

            assertEquals("LINE", classify(line));
            assertEquals("CHOICES", classify(choices));
        }

        private String classify(DialogueEntry entry) {
            return switch (entry) {
                case DialogueLine l -> "LINE";
                case DialogueChoiceGroup c -> "CHOICES";
            };
        }
    }
}
