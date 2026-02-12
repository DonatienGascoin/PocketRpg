package com.pocket.rpg.editor.panels.dialogue;

import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Renders the choices section of the dialogue editor.
 * Stateless — all state lives in the data model.
 */
public class DialogueChoicesEditor {

    /** Simple record for dialogue picker options, avoiding coupling to the panel's DialogueListEntry. */
    public record DialogueOption(String path, String displayName) {}

    private final Runnable captureUndoState;
    private final Runnable markDirty;
    private final Supplier<Set<String>> validEventNames;
    private final Supplier<List<String>> customEventNames;
    private final Supplier<List<DialogueOption>> dialogueOptions;

    public DialogueChoicesEditor(
            Runnable captureUndoState,
            Runnable markDirty,
            Supplier<Set<String>> validEventNames,
            Supplier<List<String>> customEventNames,
            Supplier<List<DialogueOption>> dialogueOptions
    ) {
        this.captureUndoState = captureUndoState;
        this.markDirty = markDirty;
        this.validEventNames = validEventNames;
        this.customEventNames = customEventNames;
        this.dialogueOptions = dialogueOptions;
    }

    public void render(Dialogue dialogue) {
        List<DialogueEntry> entries = dialogue.getEntries();

        // Find or check for existing choice group (must be last entry)
        DialogueChoiceGroup choiceGroup = null;
        if (!entries.isEmpty() && entries.getLast() instanceof DialogueChoiceGroup cg) {
            choiceGroup = cg;
        }

        boolean hasChoices = choiceGroup != null && choiceGroup.isHasChoices();
        if (ImGui.checkbox("Has Choices", hasChoices)) {
            captureUndoState.run();
            if (hasChoices) {
                if (choiceGroup != null) {
                    choiceGroup.setHasChoices(false);
                }
            } else {
                if (choiceGroup == null) {
                    choiceGroup = new DialogueChoiceGroup(true, new ArrayList<>());
                    entries.add(choiceGroup);
                } else {
                    choiceGroup.setHasChoices(true);
                }
            }
            markDirty.run();
        }

        if (choiceGroup == null || !choiceGroup.isHasChoices()) {
            return;
        }

        ImGui.spacing();
        ImGui.text("Choices");
        if (choiceGroup.getChoices().isEmpty()) {
            DialogueValidation.renderWarning("Has choices enabled but no choices defined");
        }
        ImGui.spacing();

        Set<String> evtNames = validEventNames.get();
        List<Choice> choices = choiceGroup.getChoices();
        int choiceToRemove = -1;

        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            ImGui.pushID("choice_" + i);

            ImGui.beginGroup();
            ImGui.text("Choice " + (i + 1));
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Close + "##delChoice")) {
                choiceToRemove = i;
            }

            // Choice text
            imgui.type.ImString choiceText = new imgui.type.ImString(choice.getText(), 512);
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputText("##choiceText", choiceText)) {
                captureUndoState.run();
                choice.setText(choiceText.get());
                markDirty.run();
            }

            // Action type dropdown
            ChoiceAction action = choice.getAction();
            if (action == null) {
                action = new ChoiceAction();
                choice.setAction(action);
            }

            ChoiceActionType currentType = action.getType();
            String typeLabel = currentType != null ? currentType.name() : "Select action...";
            ImGui.setNextItemWidth(150);
            if (ImGui.beginCombo("##actionType", typeLabel)) {
                for (ChoiceActionType type : ChoiceActionType.values()) {
                    if (ImGui.selectable(type.name(), type == currentType)) {
                        captureUndoState.run();
                        action.setType(type);
                        action.setDialoguePath(null);
                        action.setBuiltInEvent(null);
                        action.setCustomEvent(null);
                        markDirty.run();
                    }
                }
                ImGui.endCombo();
            }

            // Target field
            if (currentType != null) {
                ImGui.sameLine();
                renderActionTarget(action, i);
            }

            // Validation warnings
            DialogueValidation.renderChoiceValidationWarnings(choice, evtNames);

            ImGui.endGroup();
            ImGui.separator();
            ImGui.spacing();
            ImGui.popID();
        }

        // Remove choice
        if (choiceToRemove >= 0) {
            captureUndoState.run();
            choices.remove(choiceToRemove);
            markDirty.run();
        }

        // Add choice button (max 4)
        boolean atMax = choices.size() >= DialogueChoiceGroup.MAX_CHOICES;
        if (atMax) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Add + " Add Choice")) {
            captureUndoState.run();
            choices.add(new Choice("", ChoiceAction.builtInEvent(DialogueEvent.END_CONVERSATION)));
            markDirty.run();
        }
        if (atMax) ImGui.endDisabled();
        if (atMax) {
            ImGui.sameLine();
            ImGui.textDisabled("(max " + DialogueChoiceGroup.MAX_CHOICES + ")");
        }
    }

    private void renderActionTarget(ChoiceAction action, int choiceIndex) {
        switch (action.getType()) {
            case DIALOGUE -> {
                String currentPath = action.getDialoguePath() != null ? action.getDialoguePath() : "";
                List<DialogueOption> options = dialogueOptions.get();
                String displayLabel = currentPath.isEmpty() ? "Select dialogue..." :
                        options.stream()
                                .filter(o -> o.path().equals(currentPath))
                                .map(DialogueOption::displayName)
                                .findFirst()
                                .orElse(currentPath);
                ImGui.setNextItemWidth(200);
                if (ImGui.beginCombo("##dialogueTarget_" + choiceIndex, displayLabel)) {
                    for (DialogueOption option : options) {
                        if (ImGui.selectable(option.displayName(), option.path().equals(currentPath))) {
                            captureUndoState.run();
                            action.setDialoguePath(option.path());
                            markDirty.run();
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case BUILT_IN_EVENT -> {
                DialogueEvent current = action.getBuiltInEvent();
                String label = current != null ? current.name() : "Select...";
                ImGui.setNextItemWidth(200);
                if (ImGui.beginCombo("##builtInTarget_" + choiceIndex, label)) {
                    for (DialogueEvent event : DialogueEvent.values()) {
                        if (ImGui.selectable(event.name(), event == current)) {
                            captureUndoState.run();
                            action.setBuiltInEvent(event);
                            markDirty.run();
                        }
                    }
                    ImGui.endCombo();
                }
            }
            case CUSTOM_EVENT -> {
                String currentCustom = action.getCustomEvent() != null ? action.getCustomEvent() : "";
                List<String> eventNames = customEventNames.get();

                ImGui.setNextItemWidth(200);
                if (ImGui.beginCombo("##customTarget_" + choiceIndex, currentCustom.isEmpty() ? "Select..." : currentCustom)) {
                    for (String name : eventNames) {
                        if (ImGui.selectable(name, name.equals(currentCustom))) {
                            captureUndoState.run();
                            action.setCustomEvent(name);
                            markDirty.run();
                        }
                    }
                    ImGui.endCombo();
                }
            }
        }
    }
}
