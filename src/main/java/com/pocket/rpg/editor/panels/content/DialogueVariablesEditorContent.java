package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.dialogue.DialogueVariable;
import com.pocket.rpg.dialogue.DialogueVariables;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor content for DialogueVariables assets (.dialogue-vars.json).
 * <p>
 * Two-column layout: left (variable list with add/delete), right (name + type editor + info).
 */
@EditorContentFor(DialogueVariables.class)
public class DialogueVariablesEditorContent implements AssetEditorContent {

    private DialogueVariables editingVars;
    private AssetEditorShell shell;

    // Selection
    private int selectedIdx = -1;

    // Search
    private final ImString searchFilter = new ImString();

    // Name editing (deferred rename)
    private final ImString nameBuffer = new ImString(256);
    private boolean nameActive = false;

    // Delete confirmation
    private boolean showDeleteConfirmPopup = false;

    // Type combo options
    private static final DialogueVariable.Type[] TYPE_VALUES = DialogueVariable.Type.values();
    private static final String[] TYPE_LABELS;
    static {
        TYPE_LABELS = new String[TYPE_VALUES.length];
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            TYPE_LABELS[i] = TYPE_VALUES[i].name();
        }
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.editingVars = (DialogueVariables) asset;
        this.shell = shell;
        this.selectedIdx = -1;
        this.searchFilter.set("");
    }

    @Override
    public void onAssetUnloaded() {
        editingVars = null;
        selectedIdx = -1;
    }

    @Override
    public void onAfterUndoRedo() {
        if (editingVars == null) return;
        List<DialogueVariable> vars = editingVars.getVariables();
        if (selectedIdx >= vars.size()) {
            selectedIdx = vars.isEmpty() ? -1 : vars.size() - 1;
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return DialogueVariables.class;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingVars == null) return;

        // Info banner
        renderInfoBanner();

        ImGui.spacing();

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.35f);

        // Left column: variable list
        if (ImGui.beginChild("##varList", leftColumnWidth, -1, true)) {
            renderVariableList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: variable editor
        if (ImGui.beginChild("##varEditor", 0, -1, true)) {
            renderVariableEditor();
        }
        ImGui.endChild();
    }

    private void renderInfoBanner() {
        EditorColors.textColored(EditorColors.INFO, MaterialIcons.Info);
        ImGui.sameLine();
        ImGui.textWrapped("Variables are placeholders in dialogue text. Write [VAR_NAME] in a line " +
                "and it gets replaced at runtime. Insert with the \"+ Var\" button in the Dialogue Editor.");
    }

    // ========================================================================
    // LEFT COLUMN — VARIABLE LIST
    // ========================================================================

    private void renderVariableList() {
        // Search box
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        ImGui.spacing();

        // Add / Delete buttons
        if (ImGui.button(MaterialIcons.Add + " New Variable")) {
            addNewVariable();
        }

        if (selectedIdx >= 0) {
            ImGui.sameLine();
            EditorColors.pushDangerButton();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                showDeleteConfirmPopup = true;
            }
            EditorColors.popButtonColors();
        }

        ImGui.separator();

        // Variable list
        String filter = searchFilter.get().toLowerCase();
        List<DialogueVariable> vars = editingVars.getVariables();

        if (ImGui.beginChild("##varListScroll")) {
            for (int i = 0; i < vars.size(); i++) {
                DialogueVariable var = vars.get(i);
                String name = var.getName() != null ? var.getName() : "?";
                String type = var.getType() != null ? var.getType().name() : "?";

                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = (i == selectedIdx);
                String displayText = name + "  (" + type + ")";
                if (ImGui.selectable(displayText, isSelected)) {
                    selectedIdx = i;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN — VARIABLE EDITOR
    // ========================================================================

    private void renderVariableEditor() {
        List<DialogueVariable> vars = editingVars.getVariables();
        if (selectedIdx < 0 || selectedIdx >= vars.size()) {
            ImGui.textDisabled("Select a variable to edit");
            return;
        }

        DialogueVariable selected = vars.get(selectedIdx);

        // --- Name ---
        ImGui.text("Variable Name");
        ImGui.spacing();

        if (!nameActive) {
            nameBuffer.set(selected.getName() != null ? selected.getName() : "");
        }
        ImGui.setNextItemWidth(-1);
        ImGui.inputText("##varName", nameBuffer, ImGuiInputTextFlags.CallbackCharFilter);
        nameActive = ImGui.isItemActive();
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newName = sanitizeVarName(nameBuffer.get().trim());
            String oldName = selected.getName();
            if (!newName.isEmpty() && !newName.equals(oldName) && !nameExists(newName)) {
                int idx = selectedIdx;
                captureUndo("Rename Variable", () -> vars.get(idx).setName(newName));
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // --- Type ---
        ImGui.text("Type");
        ImGui.spacing();

        int currentTypeIdx = 0;
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            if (TYPE_VALUES[i] == selected.getType()) {
                currentTypeIdx = i;
                break;
            }
        }

        ImInt typeIdx = new ImInt(currentTypeIdx);
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##varType", typeIdx, TYPE_LABELS)) {
            DialogueVariable.Type newType = TYPE_VALUES[typeIdx.get()];
            int idx = selectedIdx;
            captureUndo("Change Variable Type", () -> vars.get(idx).setType(newType));
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // --- Type legend ---
        ImGui.textDisabled("Type reference:");
        ImGui.spacing();
        EditorColors.textColored(EditorColors.SUCCESS, "STATIC");
        ImGui.sameLine();
        ImGui.textWrapped("Set per NPC in the DialogueInteractable inspector.");
        EditorColors.textColored(EditorColors.INFO, "AUTO");
        ImGui.sameLine();
        ImGui.textWrapped("Filled automatically by the engine (e.g. player name).");
        EditorColors.textColored(EditorColors.WARNING, "RUNTIME");
        ImGui.sameLine();
        ImGui.textWrapped("Provided by game logic when starting a conversation.");

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Usage hint
        ImGui.textDisabled("How to use this variable:");
        ImGui.spacing();
        ImGui.bulletText("Write [" + (selected.getName() != null ? selected.getName() : "VAR_NAME") + "] in dialogue text");
        ImGui.bulletText("Insert via \"+ Var\" in the Dialogue Editor");
        if (selected.getType() == DialogueVariable.Type.STATIC) {
            ImGui.bulletText("Set value per NPC in the inspector");
        }
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Variable##varConfirm");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Variable##varConfirm", ImGuiWindowFlags.AlwaysAutoResize)) {
            List<DialogueVariable> vars = editingVars.getVariables();
            String name = (selectedIdx >= 0 && selectedIdx < vars.size())
                    ? vars.get(selectedIdx).getName() : "?";
            ImGui.text("Delete variable \"" + name + "\"?");
            ImGui.textDisabled("Any [" + name + "] references in dialogues will stop working.");
            ImGui.spacing();

            EditorColors.pushDangerButton();
            if (ImGui.button("Delete", 120, 0)) {
                performDelete();
                ImGui.closeCurrentPopup();
            }
            EditorColors.popButtonColors();

            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void performDelete() {
        List<DialogueVariable> vars = editingVars.getVariables();
        if (selectedIdx < 0 || selectedIdx >= vars.size()) return;

        int idx = selectedIdx;
        captureUndo("Delete Variable", () -> editingVars.getVariables().remove(idx));

        // Adjust selection
        if (selectedIdx >= editingVars.getVariables().size()) {
            selectedIdx = editingVars.getVariables().isEmpty() ? -1 : editingVars.getVariables().size() - 1;
        }
    }

    // ========================================================================
    // UNDO SUPPORT
    // ========================================================================

    private static List<DialogueVariable> snapshot(DialogueVariables vars) {
        List<DialogueVariable> copy = new ArrayList<>();
        for (DialogueVariable v : vars.getVariables()) {
            copy.add(new DialogueVariable(v.getName(), v.getType()));
        }
        return copy;
    }

    private static void restore(DialogueVariables target, Object snapshotObj) {
        @SuppressWarnings("unchecked")
        List<DialogueVariable> snapshot = (List<DialogueVariable>) snapshotObj;
        target.getVariables().clear();
        for (DialogueVariable v : snapshot) {
            target.getVariables().add(new DialogueVariable(v.getName(), v.getType()));
        }
    }

    private void captureUndo(String description, Runnable mutation) {
        if (editingVars == null) return;
        UndoManager.getInstance().push(SnapshotCommand.capture(
                editingVars,
                DialogueVariablesEditorContent::snapshot,
                DialogueVariablesEditorContent::restore,
                mutation,
                description
        ));
        shell.markDirty();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void addNewVariable() {
        String name = generateUniqueName();
        captureUndo("Add Variable", () ->
                editingVars.getVariables().add(new DialogueVariable(name, DialogueVariable.Type.STATIC)));
        selectedIdx = editingVars.getVariables().size() - 1;
    }

    private String generateUniqueName() {
        int counter = 1;
        while (nameExists("NEW_VARIABLE_" + counter)) {
            counter++;
        }
        return "NEW_VARIABLE_" + counter;
    }

    private boolean nameExists(String name) {
        for (DialogueVariable v : editingVars.getVariables()) {
            if (name.equals(v.getName())) return true;
        }
        return false;
    }

    private String sanitizeVarName(String input) {
        return input.toUpperCase()
                .replace(' ', '_')
                .replace('-', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }
}
