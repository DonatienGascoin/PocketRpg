package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.dialogue.DialogueInteractable;
import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.OpenDialogueEditorEvent;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.Collections;
import java.util.List;

/**
 * Custom inspector for {@link DialogueInteractable}.
 * <p>
 * Sections:
 * <ol>
 *   <li>Interaction Settings (directional interaction, interactFrom directions)</li>
 *   <li>Conditional Dialogues (conditions + dialogue pickers)</li>
 *   <li>Default Dialogue (required asset picker + Open button)</li>
 *   <li>onConversationEnd (event ref editor)</li>
 *   <li>Variable Table (AUTO/RUNTIME disabled, STATIC editable)</li>
 *   <li>Preview (collapsible, read-only dialogue content)</li>
 * </ol>
 */
@InspectorFor(DialogueInteractable.class)
public class DialogueInteractableInspector extends CustomComponentInspector<DialogueInteractable> {

    private static final String EVENTS_ASSET_PATH = "dialogues/events.dialogue-events.json";
    private static final String VARIABLES_ASSET_PATH = "dialogues/variables.dialogue-vars.json";

    private final ImString varBuffer = new ImString();

    @Override
    public boolean draw() {
        boolean changed = false;

        // A. Interaction Settings
        changed |= drawInteractionSettings();

        ImGui.spacing();
        ImGui.spacing();

        // B. Conditional Dialogues toggle + section
        changed |= FieldEditors.drawBoolean("Conditionals", component, "hasConditionalDialogues");
        if (component.isHasConditionalDialogues()) {
            ImGui.spacing();
            changed |= drawConditionalDialogues();
        }

        ImGui.spacing();
        ImGui.spacing();

        // C. Default Dialogue
        changed |= drawDefaultDialogue();

        ImGui.spacing();
        ImGui.spacing();

        // D. onConversationEnd
        drawOnConversationEnd();

        ImGui.spacing();
        ImGui.spacing();

        // E. Variable Table
        drawVariableTable();

        ImGui.spacing();
        ImGui.spacing();

        // F. Preview
        drawPreview();

        return changed;
    }

    // ========================================================================
    // A. INTERACTION SETTINGS
    // ========================================================================

    private boolean drawInteractionSettings() {
        boolean changed = false;

        ImGui.text("Interaction Settings");
        ImGui.separator();

        changed |= FieldEditors.drawBoolean("Directional", component, "directionalInteraction");

        if (component.isDirectionalInteraction()) {
            List<Direction> interactFrom = component.getInteractFrom();

            FieldEditors.inspectorRow("Interact From", () -> {
                for (Direction dir : Direction.values()) {
                    boolean active = interactFrom.contains(dir);
                    if (ImGui.checkbox(dir.name() + "##interactDir", active)) {
                        if (active) {
                            interactFrom.remove(dir);
                        } else {
                            interactFrom.add(dir);
                        }
                        markSceneDirty();
                    }
                    if (dir != Direction.RIGHT) {
                        ImGui.sameLine();
                    }
                }
            });
        }

        return changed;
    }

    // ========================================================================
    // B. CONDITIONAL DIALOGUES
    // ========================================================================

    private boolean drawConditionalDialogues() {
        boolean changed = false;
        List<String> eventNames = loadEventNames();

        ImGui.text("Conditional Dialogues");
        ImGui.separator();

        List<ConditionalDialogue> conditionals = component.getConditionalDialogues();
        int removeIndex = -1;

        for (int i = 0; i < conditionals.size(); i++) {
            ConditionalDialogue cd = conditionals.get(i);
            ImGui.pushID("cd_" + i);

            if (ImGui.collapsingHeader("Condition #" + (i + 1) + "##cdHeader")) {
                // Conditions list
                changed |= drawConditionsList(cd, eventNames);

                ImGui.spacing();

                // Dialogue picker
                changed |= FieldEditors.drawAsset("Dialogue", "cd_dialogue_" + i,
                        cd::getDialogue, cd::setDialogue, Dialogue.class);

                // Open button
                if (cd.getDialogue() != null) {
                    ImGui.sameLine();
                    if (ImGui.button(MaterialIcons.OpenInNew + "##openCd")) {
                        String path = Assets.getPathForResource(cd.getDialogue());
                        EditorEventBus.get().publish(new OpenDialogueEditorEvent(path));
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip("Open in Dialogue Editor");
                    }
                }

                // Delete entry button
                ImGui.spacing();
                EditorColors.pushDangerButton();
                if (ImGui.button(MaterialIcons.Delete + " Remove##removeCd")) {
                    removeIndex = i;
                }
                EditorColors.popButtonColors();
            }

            ImGui.popID();
        }

        // Remove deferred
        if (removeIndex >= 0) {
            conditionals.remove(removeIndex);
            markSceneDirty();
            changed = true;
        }

        // Add button
        if (ImGui.button(MaterialIcons.Add + " Add Conditional Dialogue")) {
            conditionals.add(new ConditionalDialogue());
            markSceneDirty();
            changed = true;
        }

        return changed;
    }

    private boolean drawConditionsList(ConditionalDialogue cd, List<String> eventNames) {
        boolean changed = false;
        List<DialogueCondition> conditions = cd.getConditions();
        int removeCondIndex = -1;

        for (int j = 0; j < conditions.size(); j++) {
            DialogueCondition cond = conditions.get(j);
            ImGui.pushID("cond_" + j);

            // Event name combo
            String currentEvent = cond.getEventName() != null ? cond.getEventName() : "";
            String eventLabel = currentEvent.isEmpty() ? "Select..." : currentEvent;
            ImGui.setNextItemWidth(120);
            if (ImGui.beginCombo("##condEvent", eventLabel)) {
                for (String name : eventNames) {
                    if (ImGui.selectable(name, name.equals(currentEvent))) {
                        cond.setEventName(name);
                        markSceneDirty();
                        changed = true;
                    }
                }
                ImGui.endCombo();
            }

            // Expected state combo
            ImGui.sameLine();
            DialogueCondition.ExpectedState currentState = cond.getExpectedState();
            String stateLabel = currentState != null ? currentState.name() : "FIRED";
            ImGui.setNextItemWidth(100);
            if (ImGui.beginCombo("##condState", stateLabel)) {
                for (DialogueCondition.ExpectedState state : DialogueCondition.ExpectedState.values()) {
                    if (ImGui.selectable(state.name(), state == currentState)) {
                        cond.setExpectedState(state);
                        markSceneDirty();
                        changed = true;
                    }
                }
                ImGui.endCombo();
            }

            // Delete condition button
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Close + "##removeCond")) {
                removeCondIndex = j;
            }

            ImGui.popID();
        }

        // Remove deferred
        if (removeCondIndex >= 0) {
            conditions.remove(removeCondIndex);
            markSceneDirty();
            changed = true;
        }

        // Add condition button
        if (ImGui.button(MaterialIcons.Add + " Add Condition")) {
            conditions.add(new DialogueCondition());
            markSceneDirty();
            changed = true;
        }

        return changed;
    }

    // ========================================================================
    // C. DEFAULT DIALOGUE
    // ========================================================================

    private boolean drawDefaultDialogue() {
        boolean changed = false;

        ImGui.text("Default Dialogue");
        ImGui.separator();

        boolean highlighted = FieldEditorContext.beginRequiredRowHighlight("dialogue");
        try {
            changed |= FieldEditors.drawAsset("Dialogue", component, "dialogue", Dialogue.class, editorEntity());

            // Open button
            if (component.getDialogue() != null) {
                ImGui.sameLine();
                if (ImGui.button(MaterialIcons.OpenInNew + "##openDefault")) {
                    String path = Assets.getPathForResource(component.getDialogue());
                    EditorEventBus.get().publish(new OpenDialogueEditorEvent(path));
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("Open in Dialogue Editor");
                }
            }
        } finally {
            FieldEditorContext.endRequiredRowHighlight(highlighted);
        }

        return changed;
    }

    // ========================================================================
    // D. ON CONVERSATION END
    // ========================================================================

    private void drawOnConversationEnd() {
        ImGui.text("On Conversation End");
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Fires when the entire conversation with this NPC ends.\n"
                    + "Unlike per-line events (set in the Dialogue Editor),\n"
                    + "this triggers after all dialogue chaining completes.");
        }
        ImGui.separator();

        DialogueEventRef eventRef = component.getOnConversationEnd();
        List<String> eventNames = loadEventNames();

        // Category dropdown
        DialogueEventRef.Category currentCategory = eventRef != null ? eventRef.getCategory() : null;
        String categoryLabel = currentCategory != null ? currentCategory.name() : "Select...";

        ImGui.setNextItemWidth(120);
        if (ImGui.beginCombo("##onEndCategory", categoryLabel)) {
            if (ImGui.selectable("BUILT_IN", currentCategory == DialogueEventRef.Category.BUILT_IN)) {
                component.setOnConversationEnd(DialogueEventRef.builtIn(DialogueEvent.END_CONVERSATION));
                markSceneDirty();
            }
            if (ImGui.selectable("CUSTOM", currentCategory == DialogueEventRef.Category.CUSTOM)) {
                component.setOnConversationEnd(DialogueEventRef.custom(""));
                markSceneDirty();
            }
            ImGui.endCombo();
        }

        // Event selector (if ref exists)
        if (eventRef != null) {
            ImGui.sameLine();
            if (eventRef.isBuiltIn()) {
                DialogueEvent current = eventRef.getBuiltInEvent();
                String builtInLabel = current != null ? current.name() : "Select...";
                ImGui.setNextItemWidth(160);
                if (ImGui.beginCombo("##onEndBuiltIn", builtInLabel)) {
                    for (DialogueEvent event : DialogueEvent.values()) {
                        if (ImGui.selectable(event.name(), event == current)) {
                            eventRef.setBuiltInEvent(event);
                            markSceneDirty();
                        }
                    }
                    ImGui.endCombo();
                }
            } else if (eventRef.isCustom()) {
                String currentCustom = eventRef.getCustomEvent() != null ? eventRef.getCustomEvent() : "";
                ImGui.setNextItemWidth(160);
                if (ImGui.beginCombo("##onEndCustom", currentCustom.isEmpty() ? "Select..." : currentCustom)) {
                    for (String name : eventNames) {
                        if (ImGui.selectable(name, name.equals(currentCustom))) {
                            eventRef.setCustomEvent(name);
                            markSceneDirty();
                        }
                    }
                    ImGui.endCombo();
                }
            }

            // Clear button
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Close + "##clearOnEnd")) {
                component.setOnConversationEnd(null);
                markSceneDirty();
            }
        }
    }

    // ========================================================================
    // E. VARIABLE TABLE
    // ========================================================================

    private void drawVariableTable() {
        DialogueVariables varsAsset = loadVariablesAsset();
        if (varsAsset == null || varsAsset.getVariables() == null || varsAsset.getVariables().isEmpty()) {
            ImGui.textDisabled("Variables");
            ImGui.separator();
            ImGui.textDisabled("No variables defined");
            return;
        }

        ImGui.text("Variables");
        ImGui.separator();

        for (DialogueVariable varDef : varsAsset.getVariables()) {
            String name = varDef.getName();
            DialogueVariable.Type type = varDef.getType();

            ImGui.pushID("var_" + name);

            switch (type) {
                case AUTO -> FieldEditors.inspectorRow(name, () -> {
                    ImGui.beginDisabled();
                    ImGui.textDisabled("auto");
                    ImGui.endDisabled();
                });
                case RUNTIME -> FieldEditors.inspectorRow(name, () -> {
                    ImGui.beginDisabled();
                    ImGui.textDisabled("runtime");
                    ImGui.endDisabled();
                });
                case STATIC -> {
                    String currentValue = component.getVariables().getOrDefault(name, "");
                    varBuffer.set(currentValue);

                    FieldEditors.inspectorRow(name, () -> {
                        // Warning icon when value is empty
                        if (currentValue.isEmpty()) {
                            EditorColors.textColored(EditorColors.WARNING, MaterialIcons.Warning);
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip("Static variable has no value set");
                            }
                            ImGui.sameLine();
                        }

                        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                        if (ImGui.inputText("##staticVar", varBuffer)) {
                            component.getVariables().put(name, varBuffer.get());
                            markSceneDirty();
                        }
                    });
                }
            }

            ImGui.popID();
        }
    }

    // ========================================================================
    // F. PREVIEW
    // ========================================================================

    private void drawPreview() {
        if (!ImGui.collapsingHeader("Preview")) {
            return;
        }

        Dialogue dialogue = component.getDialogue();
        if (dialogue == null) {
            ImGui.textDisabled("No default dialogue set");
            return;
        }

        ImGui.textDisabled("Dialogue: " + dialogue.getName());
        ImGui.spacing();

        if (dialogue.getEntries() == null || dialogue.getEntries().isEmpty()) {
            ImGui.textDisabled("(empty)");
            return;
        }

        for (DialogueEntry entry : dialogue.getEntries()) {
            switch (entry) {
                case DialogueLine line -> {
                    ImGui.textWrapped(line.getText() != null ? line.getText() : "(empty line)");
                }
                case DialogueChoiceGroup choiceGroup -> {
                    if (choiceGroup.isHasChoices() && choiceGroup.getChoices() != null) {
                        ImGui.textDisabled("[Choices]");
                        for (Choice choice : choiceGroup.getChoices()) {
                            ImGui.text("  > " + (choice.getText() != null ? choice.getText() : "(empty)"));
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private List<String> loadEventNames() {
        try {
            DialogueEvents events = Assets.load(EVENTS_ASSET_PATH, DialogueEvents.class);
            if (events != null && events.getEvents() != null) {
                return events.getEvents();
            }
        } catch (Exception ignored) {
            // Asset may not exist yet
        }
        return Collections.emptyList();
    }

    private DialogueVariables loadVariablesAsset() {
        try {
            return Assets.load(VARIABLES_ASSET_PATH, DialogueVariables.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void markSceneDirty() {
        if (FieldEditorContext.getCurrentScene() != null) {
            FieldEditorContext.getCurrentScene().markDirty();
        }
    }
}
