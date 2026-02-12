package com.pocket.rpg.editor.panels.dialogue;

import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure validation logic and warning rendering for dialogue assets.
 * All methods are static — no state.
 */
public final class DialogueValidation {

    static final Pattern TAG_PATTERN = Pattern.compile("\\[([^\\]]*)]");
    static final Pattern UNCLOSED_TAG_PATTERN = Pattern.compile("\\[[^\\]]*$");

    private DialogueValidation() {}

    // ========================================================================
    // WARNING RENDERING
    // ========================================================================

    public static void renderWarning(String message) {
        ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.7f, 0.2f, 1f);
        ImGui.text(MaterialIcons.Warning + " " + message);
        ImGui.popStyleColor();
    }

    public static void renderLineValidationWarnings(DialogueLine line, Set<String> validVarNames) {
        String text = line.getText();

        // Check for malformed tags (unclosed bracket)
        Matcher unclosed = UNCLOSED_TAG_PATTERN.matcher(text);
        if (unclosed.find()) {
            renderWarning("Malformed tag: " + unclosed.group());
        }

        // Check for unknown variable tags
        Matcher tags = TAG_PATTERN.matcher(text);
        while (tags.find()) {
            String tagName = tags.group(1);
            if (!validVarNames.contains(tagName)) {
                renderWarning("Unknown variable: [" + tagName + "]");
            }
        }
    }

    public static void renderEventRefValidationWarning(DialogueEventRef ref, Set<String> validEventNames) {
        if (ref == null || !ref.isCustom()) return;
        String customEvent = ref.getCustomEvent();
        if (customEvent == null || customEvent.isEmpty()) return;
        if (!validEventNames.contains(customEvent)) {
            renderWarning("Unknown custom event: " + customEvent);
        }
    }

    public static void renderChoiceValidationWarnings(Choice choice, Set<String> validEventNames) {
        // Empty choice text
        if (choice.getText() == null || choice.getText().trim().isEmpty()) {
            renderWarning("Empty choice text");
        }

        ChoiceAction action = choice.getAction();
        if (action == null || action.getType() == null) return;

        switch (action.getType()) {
            case DIALOGUE -> {
                if (action.getDialoguePath() == null || action.getDialoguePath().isEmpty()) {
                    renderWarning("DIALOGUE action with no target");
                }
            }
            case CUSTOM_EVENT -> {
                if (action.getCustomEvent() == null || action.getCustomEvent().isEmpty()) {
                    renderWarning("CUSTOM_EVENT action with no event");
                } else if (!validEventNames.contains(action.getCustomEvent())) {
                    renderWarning("Unknown custom event: " + action.getCustomEvent());
                }
            }
            default -> {}
        }
    }

    // ========================================================================
    // BOOLEAN CHECKS (for left column warning icons)
    // ========================================================================

    public static boolean hasDialogueWarnings(Dialogue dialogue, Set<String> validVarNames, Set<String> validEventNames) {
        for (DialogueEntry entry : dialogue.getEntries()) {
            if (entry instanceof DialogueLine line) {
                if (UNCLOSED_TAG_PATTERN.matcher(line.getText()).find()) return true;
                Matcher tags = TAG_PATTERN.matcher(line.getText());
                while (tags.find()) {
                    if (!validVarNames.contains(tags.group(1))) return true;
                }
                if (hasStaleEventRef(line.getOnCompleteEvent(), validEventNames)) return true;
            } else if (entry instanceof DialogueChoiceGroup cg) {
                if (cg.isHasChoices()) {
                    if (cg.getChoices().isEmpty()) return true;
                    for (Choice c : cg.getChoices()) {
                        if (c.getText() == null || c.getText().trim().isEmpty()) return true;
                        if (hasChoiceActionWarning(c.getAction(), validEventNames)) return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasStaleEventRef(DialogueEventRef ref, Set<String> validEventNames) {
        if (ref == null || !ref.isCustom()) return false;
        String custom = ref.getCustomEvent();
        return custom != null && !custom.isEmpty() && !validEventNames.contains(custom);
    }

    public static boolean hasChoiceActionWarning(ChoiceAction action, Set<String> validEventNames) {
        if (action == null || action.getType() == null) return false;
        return switch (action.getType()) {
            case DIALOGUE -> action.getDialoguePath() == null || action.getDialoguePath().isEmpty();
            case CUSTOM_EVENT -> {
                String ce = action.getCustomEvent();
                yield ce == null || ce.isEmpty() || !validEventNames.contains(ce);
            }
            default -> false;
        };
    }
}
