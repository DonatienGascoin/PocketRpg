package com.pocket.rpg.editor.panels;

import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.AssetSelectionRequestEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.StatusMessageEvent;
import com.pocket.rpg.editor.panels.dialogue.DialogueChoicesEditor;
import com.pocket.rpg.editor.panels.dialogue.DialogueLinesEditor;
import com.pocket.rpg.editor.panels.dialogue.DialogueValidation;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.DialogueEventsLoader;
import com.pocket.rpg.resources.loaders.DialogueLoader;
import com.pocket.rpg.resources.loaders.DialogueVariablesLoader;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Two-column editor for .dialogue.json assets.
 * Left column: dialogue list with search/filter, add/remove.
 * Right column: toolbar + line editor + choices editor.
 */
public class DialogueEditorPanel extends EditorPanel {

    private static final Logger LOG = Log.getLogger(DialogueEditorPanel.class);
    private static final String VARIABLES_ASSET_PATH = "dialogues/variables.dialogue-vars.json";
    private static final String EVENTS_ASSET_PATH = "dialogues/events.dialogue-events.json";

    // ========================================================================
    // STATE
    // ========================================================================

    private final List<DialogueListEntry> dialogueList = new ArrayList<>();
    private DialogueListEntry selectedEntry = null;
    private Dialogue editingDialogue = null;
    private boolean dirty = false;
    private boolean needsRefresh = true;

    // Search filter
    private final ImString searchFilter = new ImString();

    // New dialogue popup
    private boolean showNewDialoguePopup = false;
    private boolean focusNewDialogueInput = false;
    private final ImString newDialogueName = new ImString();

    // Delete confirmation
    private boolean showDeleteConfirmPopup = false;

    // Undo/redo
    private static final int MAX_UNDO_HISTORY = 50;
    private final Deque<DialogueSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<DialogueSnapshot> redoStack = new ArrayDeque<>();

    // Unsaved changes confirmation on dialogue switch
    private boolean showUnsavedChangesPopup = false;
    private DialogueListEntry pendingSwitchEntry = null;

    // Sub-editors
    private final DialogueLinesEditor linesEditor;
    private final DialogueChoicesEditor choicesEditor;

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    private record DialogueListEntry(String path, String displayName, Dialogue dialogue) {
        /** Match by path so selection survives list rebuild. */
        boolean matches(DialogueListEntry other) {
            return other != null && path.equals(other.path);
        }
    }

    /** Deep snapshot of a Dialogue for undo/redo. */
    private record DialogueSnapshot(String name, List<DialogueEntry> entries) {
        static DialogueSnapshot capture(Dialogue dialogue) {
            List<DialogueEntry> copy = new ArrayList<>();
            for (DialogueEntry entry : dialogue.getEntries()) {
                if (entry instanceof DialogueLine line) {
                    DialogueEventRef refCopy = copyEventRef(line.getOnCompleteEvent());
                    copy.add(new DialogueLine(line.getText(), refCopy));
                } else if (entry instanceof DialogueChoiceGroup cg) {
                    List<Choice> choicesCopy = new ArrayList<>();
                    for (Choice c : cg.getChoices()) {
                        ChoiceAction actionCopy = copyAction(c.getAction());
                        choicesCopy.add(new Choice(c.getText(), actionCopy));
                    }
                    copy.add(new DialogueChoiceGroup(cg.isHasChoices(), choicesCopy));
                }
            }
            return new DialogueSnapshot(dialogue.getName(), copy);
        }

        void restore(Dialogue dialogue) {
            dialogue.setName(name);
            dialogue.getEntries().clear();
            dialogue.getEntries().addAll(entries);
        }

        private static DialogueEventRef copyEventRef(DialogueEventRef ref) {
            if (ref == null) return null;
            if (ref.isBuiltIn()) return DialogueEventRef.builtIn(ref.getBuiltInEvent());
            if (ref.isCustom()) return DialogueEventRef.custom(ref.getCustomEvent());
            return null;
        }

        private static ChoiceAction copyAction(ChoiceAction action) {
            if (action == null) return new ChoiceAction();
            return switch (action.getType()) {
                case DIALOGUE -> ChoiceAction.dialogue(action.getDialoguePath());
                case BUILT_IN_EVENT -> ChoiceAction.builtInEvent(action.getBuiltInEvent());
                case CUSTOM_EVENT -> ChoiceAction.customEvent(action.getCustomEvent());
                case null -> new ChoiceAction();
            };
        }
    }

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public DialogueEditorPanel() {
        super(EditorShortcuts.PanelIds.DIALOGUE_EDITOR, false);
        EditorEventBus.get().subscribe(AssetChangedEvent.class, this::onAssetChanged);

        linesEditor = new DialogueLinesEditor(
                this::captureUndoState,
                () -> dirty = true,
                this::addLine,
                this::getValidVariableNames,
                this::getValidEventNames,
                this::getCustomEventNames,
                this::loadVariablesAsset
        );

        choicesEditor = new DialogueChoicesEditor(
                this::captureUndoState,
                () -> dirty = true,
                this::getValidEventNames,
                this::getCustomEventNames,
                this::getDialogueOptions
        );
    }

    private void onAssetChanged(AssetChangedEvent event) {
        if (event.path().endsWith(".dialogue.json")) {
            needsRefresh = true;
        }
    }

    // ========================================================================
    // SHORTCUTS
    // ========================================================================

    @Override
    public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
        ShortcutBinding undoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.W)
                : ShortcutBinding.ctrl(ImGuiKey.Z);
        ShortcutBinding redoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrlShift(ImGuiKey.W)
                : ShortcutBinding.ctrlShift(ImGuiKey.Z);

        return List.of(
                panelShortcut()
                        .id("editor.dialogue.save")
                        .displayName("Save Dialogue")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
                        .allowInInput(true)
                        .handler(() -> { if (dirty) saveCurrentDialogue(); })
                        .build(),
                panelShortcut()
                        .id("editor.dialogue.undo")
                        .displayName("Dialogue Undo")
                        .defaultBinding(undoBinding)
                        .allowInInput(true)
                        .handler(this::undo)
                        .build(),
                panelShortcut()
                        .id("editor.dialogue.redo")
                        .displayName("Dialogue Redo")
                        .defaultBinding(redoBinding)
                        .allowInInput(true)
                        .handler(this::redo)
                        .build(),
                panelShortcut()
                        .id("editor.dialogue.redoAlt")
                        .displayName("Dialogue Redo (Alt)")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
                        .allowInInput(true)
                        .handler(this::redo)
                        .build(),
                panelShortcut()
                        .id("editor.dialogue.addLine")
                        .displayName("Add Dialogue Line")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Enter))
                        .allowInInput(true)
                        .handler(this::addLine)
                        .build()
        );
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Opens the panel and selects a dialogue by its asset path.
     * Called by AssetBrowserPanel when double-clicking a .dialogue.json file.
     */
    public void selectDialogueByPath(String path) {
        setOpen(true);
        refreshDialogueList();
        for (DialogueListEntry entry : dialogueList) {
            if (entry.path.equals(path)) {
                selectDialogue(entry);
                return;
            }
        }
        LOG.warn("Dialogue not found: " + path);
    }

    // ========================================================================
    // RENDERING
    // ========================================================================

    @Override
    public void render() {
        if (!isOpen()) {
            setContentVisible(false);
            setFocused(false);
            return;
        }

        int flags = ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.NoScrollWithMouse;
        boolean visible = ImGui.begin(getWindowTitle(), flags);
        setContentVisible(visible);
        setFocused(ImGui.isWindowFocused(ImGuiFocusedFlags.ChildWindows));

        if (visible) {
            if (needsRefresh) {
                refreshDialogueList();
                needsRefresh = false;
            }

            float totalWidth = ImGui.getContentRegionAvailX();
            float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

            // Left column: dialogue list
            if (ImGui.beginChild("##dialogueList", leftColumnWidth, -1, true)) {
                renderLeftColumn();
            }
            ImGui.endChild();

            ImGui.sameLine();

            // Right column: editor
            if (ImGui.beginChild("##dialogueEditor", 0, -1, true)) {
                renderRightColumn();
            }
            ImGui.endChild();
        }

        ImGui.end();

        // Render popups outside main window
        renderNewDialoguePopup();
        renderDeleteConfirmPopup();
        renderUnsavedChangesPopup();
    }

    private String getWindowTitle() {
        String title = "Dialogue Editor";
        if (dirty && selectedEntry != null) {
            title += " *";
        }
        return title;
    }

    // ========================================================================
    // LEFT COLUMN — Dialogue List
    // ========================================================================

    private void renderLeftColumn() {
        // Search bar
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        ImGui.spacing();

        // Add/Remove buttons
        if (ImGui.button(MaterialIcons.Add + " Add")) {
            newDialogueName.set("");
            showNewDialoguePopup = true;
        }
        ImGui.sameLine();
        boolean canRemove = selectedEntry != null;
        if (!canRemove) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Delete + " Remove")) {
            showDeleteConfirmPopup = true;
        }
        if (!canRemove) ImGui.endDisabled();

        ImGui.separator();

        // Dialogue list
        Set<String> validVarNames = getValidVariableNames();
        Set<String> validEventNames = getValidEventNames();
        String filter = searchFilter.get().toLowerCase();
        for (DialogueListEntry entry : dialogueList) {
            if (!filter.isEmpty() && !entry.displayName.toLowerCase().contains(filter)) {
                continue;
            }

            boolean isSelected = entry.matches(selectedEntry);
            boolean hasWarnings = DialogueValidation.hasDialogueWarnings(entry.dialogue, validVarNames, validEventNames);
            String warning = hasWarnings ? " " + MaterialIcons.Warning : "";
            String label = MaterialIcons.ChatBubble + " " + entry.displayName + warning;

            if (hasWarnings) {
                ImGui.pushStyleColor(ImGuiCol.Text, 1f, 0.7f, 0.2f, 1f);
            }
            if (ImGui.selectable(label + "##" + entry.path, isSelected)) {
                if (!isSelected) {
                    selectDialogue(entry);
                }
            }
            if (hasWarnings) {
                ImGui.popStyleColor();
            }
        }
    }

    // ========================================================================
    // RIGHT COLUMN — Editor
    // ========================================================================

    private void renderRightColumn() {
        renderToolbar();
        ImGui.separator();

        if (editingDialogue == null) {
            // Centered placeholder
            float availW = ImGui.getContentRegionAvailX();
            float availH = ImGui.getContentRegionAvailY();
            String text = "Select a dialogue to start";
            float textW = ImGui.calcTextSize(text).x;
            ImGui.setCursorPos(
                    ImGui.getCursorPosX() + (availW - textW) * 0.5f,
                    ImGui.getCursorPosY() + availH * 0.4f
            );
            ImGui.textDisabled(text);
            return;
        }

        ImGui.spacing();

        // Scrollable content area
        if (ImGui.beginChild("##dialogueContent", 0, 0, false)) {
            linesEditor.render(editingDialogue);
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();
            choicesEditor.render(editingDialogue);
        }
        ImGui.endChild();
    }

    // ========================================================================
    // TOOLBAR
    // ========================================================================

    private void renderToolbar() {
        boolean hasDialogue = editingDialogue != null;

        // Dialogue name (read-only)
        if (hasDialogue) {
            ImGui.text(MaterialIcons.ChatBubble + " " + editingDialogue.getName());
            ImGui.sameLine();
        }

        // Save button — amber when dirty, disabled when clean (follows AnimatorEditorPanel pattern)
        boolean canSave = hasDialogue && dirty;
        if (!hasDialogue) ImGui.beginDisabled();
        else if (canSave) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.5f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.6f, 0.0f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.7f, 0.0f, 1.0f);
        } else {
            ImGui.beginDisabled();
        }
        if (ImGui.button(MaterialIcons.Save + " Save")) {
            saveCurrentDialogue();
        }
        if (!hasDialogue) ImGui.endDisabled();
        else if (canSave) {
            ImGui.popStyleColor(3);
        } else {
            ImGui.endDisabled();
        }

        // Undo/Redo buttons
        ImGui.sameLine();
        boolean canUndo = hasDialogue && !undoStack.isEmpty();
        if (!canUndo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Undo)) {
            undo();
        }
        if (!canUndo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Undo (Ctrl+Z)");
        }

        ImGui.sameLine();
        boolean canRedo = hasDialogue && !redoStack.isEmpty();
        if (!canRedo) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.Redo)) {
            redo();
        }
        if (!canRedo) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Redo (Ctrl+Y)");
        }

        // Right-aligned buttons: Variables, Events, Refresh
        float refreshWidth = ImGui.calcTextSize(MaterialIcons.Refresh).x + ImGui.getStyle().getFramePaddingX() * 2;
        float eventsWidth = ImGui.calcTextSize("Events " + MaterialIcons.OpenInNew).x + ImGui.getStyle().getFramePaddingX() * 2;
        float varsWidth = ImGui.calcTextSize("Variables " + MaterialIcons.OpenInNew).x + ImGui.getStyle().getFramePaddingX() * 2;
        float spacing = ImGui.getStyle().getItemSpacingX();
        float rightEdge = ImGui.getContentRegionAvailX() + ImGui.getCursorPosX();

        ImGui.sameLine(rightEdge - refreshWidth - spacing - eventsWidth - spacing - varsWidth);
        if (ImGui.button("Variables " + MaterialIcons.OpenInNew)) {
            openVariablesAsset();
        }
        ImGui.sameLine();
        if (ImGui.button("Events " + MaterialIcons.OpenInNew)) {
            openEventsAsset();
        }
        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.Refresh + "##refreshDialogues")) {
            refreshDialogueList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh dialogue list");
        }
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    private void renderNewDialoguePopup() {
        if (showNewDialoguePopup) {
            ImGui.openPopup("New Dialogue");
            showNewDialoguePopup = false;
            focusNewDialogueInput = true;
        }

        if (ImGui.beginPopupModal("New Dialogue", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Dialogue name:");
            if (focusNewDialogueInput) {
                ImGui.setKeyboardFocusHere();
                focusNewDialogueInput = false;
            }
            ImGui.setNextItemWidth(300);
            ImGui.inputText("##newName", newDialogueName);

            ImGui.spacing();

            String nameValue = newDialogueName.get().trim();
            boolean canCreate = !nameValue.isEmpty();
            if (!canCreate) ImGui.beginDisabled();
            if (ImGui.button("Create", 100, 0)) {
                createNewDialogue(nameValue);
                ImGui.closeCurrentPopup();
            }
            if (!canCreate) ImGui.endDisabled();

            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void renderDeleteConfirmPopup() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Dialogue?");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Dialogue?", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = selectedEntry != null ? selectedEntry.displayName : "?";
            ImGui.text("Delete \"" + name + "\"?");
            ImGui.text("This action cannot be undone.");

            ImGui.spacing();

            if (ImGui.button("Delete", 100, 0)) {
                deleteSelectedDialogue();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    private void renderUnsavedChangesPopup() {
        if (showUnsavedChangesPopup) {
            ImGui.openPopup("Unsaved Changes");
            showUnsavedChangesPopup = false;
        }

        if (ImGui.beginPopupModal("Unsaved Changes", new ImBoolean(true), ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes. What would you like to do?");
            ImGui.spacing();

            if (ImGui.button("Save & Switch", 120, 0)) {
                saveCurrentDialogue();
                if (pendingSwitchEntry != null) {
                    forceSelectDialogue(pendingSwitchEntry);
                    pendingSwitchEntry = null;
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Discard & Switch", 140, 0)) {
                // Reload current dialogue from disk to discard in-memory mutations
                if (selectedEntry != null) {
                    Assets.reload(selectedEntry.path);
                }
                if (pendingSwitchEntry != null) {
                    forceSelectDialogue(pendingSwitchEntry);
                    pendingSwitchEntry = null;
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                pendingSwitchEntry = null;
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    // ========================================================================
    // OPERATIONS
    // ========================================================================

    private void selectDialogue(DialogueListEntry entry) {
        // If unsaved changes, show confirmation popup instead of switching immediately
        if (dirty && selectedEntry != null && !entry.matches(selectedEntry)) {
            pendingSwitchEntry = entry;
            showUnsavedChangesPopup = true;
            return;
        }
        forceSelectDialogue(entry);
    }

    private void forceSelectDialogue(DialogueListEntry entry) {
        selectedEntry = entry;
        try {
            editingDialogue = Assets.load(entry.path, Dialogue.class);
        } catch (Exception e) {
            LOG.error("Failed to load dialogue: " + entry.path, e);
            editingDialogue = null;
        }
        dirty = false;
        linesEditor.resetState();
        undoStack.clear();
        redoStack.clear();
    }

    private void createNewDialogue(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        String relativePath = "dialogues/" + sanitized + ".dialogue.json";
        Path filePath = Paths.get(Assets.getAssetRoot(), relativePath);

        // Check for duplicate
        int counter = 1;
        String baseName = sanitized;
        while (Files.exists(filePath)) {
            sanitized = baseName + "_" + counter;
            relativePath = "dialogues/" + sanitized + ".dialogue.json";
            filePath = Paths.get(Assets.getAssetRoot(), relativePath);
            counter++;
        }

        // Create minimal dialogue
        Dialogue dialogue = new Dialogue(sanitized);
        dialogue.getEntries().add(new DialogueLine(""));

        try {
            DialogueLoader loader = new DialogueLoader();
            Files.createDirectories(filePath.getParent());
            loader.save(dialogue, filePath.toString());
            showStatus("Created dialogue: " + sanitized);

            // Refresh and select
            refreshDialogueList();
            for (DialogueListEntry entry : dialogueList) {
                if (entry.path.equals(relativePath)) {
                    selectDialogue(entry);
                    break;
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to create dialogue: " + e.getMessage(), e);
            showStatus("Error creating dialogue: " + e.getMessage());
        }
    }

    private void deleteSelectedDialogue() {
        if (selectedEntry == null) return;

        Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
        try {
            Files.deleteIfExists(filePath);
            showStatus("Deleted dialogue: " + selectedEntry.displayName);
        } catch (IOException e) {
            LOG.error("Failed to delete dialogue: " + e.getMessage(), e);
            showStatus("Error deleting dialogue: " + e.getMessage());
        }

        selectedEntry = null;
        editingDialogue = null;
        dirty = false;
        linesEditor.resetState();
        undoStack.clear();
        redoStack.clear();
        refreshDialogueList();
    }

    private void saveCurrentDialogue() {
        if (selectedEntry == null || editingDialogue == null) return;

        Path filePath = Paths.get(Assets.getAssetRoot(), selectedEntry.path);
        try {
            DialogueLoader loader = new DialogueLoader();
            loader.save(editingDialogue, filePath.toString());
            dirty = false;
            showStatus("Saved dialogue: " + selectedEntry.displayName);

            // Reload into asset cache
            Assets.reload(selectedEntry.path);
        } catch (IOException e) {
            LOG.error("Failed to save dialogue: " + e.getMessage(), e);
            showStatus("Error saving dialogue: " + e.getMessage());
        }
    }

    private void captureUndoState() {
        if (editingDialogue == null) return;
        redoStack.clear();
        undoStack.push(DialogueSnapshot.capture(editingDialogue));
        while (undoStack.size() > MAX_UNDO_HISTORY) {
            undoStack.removeLast();
        }
    }

    private void undo() {
        if (undoStack.isEmpty() || editingDialogue == null) return;
        redoStack.push(DialogueSnapshot.capture(editingDialogue));
        undoStack.pop().restore(editingDialogue);
        dirty = true;
        linesEditor.resetState();
    }

    private void redo() {
        if (redoStack.isEmpty() || editingDialogue == null) return;
        undoStack.push(DialogueSnapshot.capture(editingDialogue));
        redoStack.pop().restore(editingDialogue);
        dirty = true;
        linesEditor.resetState();
    }

    private void addLine() {
        if (editingDialogue == null) return;
        captureUndoState();
        List<DialogueEntry> entries = editingDialogue.getEntries();
        int insertIndex = entries.size();
        if (!entries.isEmpty() && entries.getLast() instanceof DialogueChoiceGroup) {
            insertIndex = entries.size() - 1;
        }
        entries.add(insertIndex, new DialogueLine(""));
        dirty = true;
    }

    // ========================================================================
    // DIALOGUE LIST MANAGEMENT
    // ========================================================================

    private void refreshDialogueList() {
        dialogueList.clear();

        List<String> paths = Assets.scanByType(Dialogue.class);
        for (String path : paths) {
            try {
                Dialogue dialogue = Assets.load(path, Dialogue.class);
                String displayName = extractDisplayName(path);
                dialogueList.add(new DialogueListEntry(path, displayName, dialogue));
            } catch (Exception e) {
                LOG.warn("Failed to load dialogue for list: " + path);
            }
        }

        // Sort alphabetically
        dialogueList.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        // Re-link selectedEntry to the new list so reference equality works
        if (selectedEntry != null) {
            String selectedPath = selectedEntry.path;
            selectedEntry = dialogueList.stream()
                    .filter(e -> e.path.equals(selectedPath))
                    .findFirst()
                    .orElse(null);
        }
    }

    // ========================================================================
    // ASSET LOADING HELPERS
    // ========================================================================

    private Set<String> getValidVariableNames() {
        DialogueVariables vars = loadVariablesAsset();
        if (vars == null || vars.getVariables() == null) return Set.of();
        Set<String> names = new HashSet<>();
        for (DialogueVariable v : vars.getVariables()) {
            if (v.getName() != null && !v.getName().isEmpty()) {
                names.add(v.getName());
            }
        }
        return names;
    }

    private Set<String> getValidEventNames() {
        DialogueEvents events = loadEventsAsset();
        if (events == null || events.getEvents() == null) return Set.of();
        return new HashSet<>(events.getEvents());
    }

    private List<String> getCustomEventNames() {
        DialogueEvents events = loadEventsAsset();
        if (events == null || events.getEvents() == null) return List.of();
        return events.getEvents();
    }

    private List<DialogueChoicesEditor.DialogueOption> getDialogueOptions() {
        return dialogueList.stream()
                .map(e -> new DialogueChoicesEditor.DialogueOption(e.path, e.displayName))
                .toList();
    }

    private DialogueVariables loadVariablesAsset() {
        try {
            return Assets.load(VARIABLES_ASSET_PATH, DialogueVariables.class);
        } catch (Exception e) {
            return null;
        }
    }

    private DialogueEvents loadEventsAsset() {
        try {
            return Assets.load(EVENTS_ASSET_PATH, DialogueEvents.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String extractDisplayName(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        if (fileName.endsWith(".dialogue.json")) {
            return fileName.substring(0, fileName.length() - ".dialogue.json".length());
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void openVariablesAsset() {
        ensureAssetExists(VARIABLES_ASSET_PATH, () -> {
            DialogueVariables empty = new DialogueVariables();
            new DialogueVariablesLoader().save(empty, Paths.get(Assets.getAssetRoot(), VARIABLES_ASSET_PATH).toString());
        });
        EditorEventBus.get().publish(new AssetSelectionRequestEvent(VARIABLES_ASSET_PATH, DialogueVariables.class));
    }

    private void openEventsAsset() {
        ensureAssetExists(EVENTS_ASSET_PATH, () -> {
            DialogueEvents empty = new DialogueEvents();
            new DialogueEventsLoader().save(empty, Paths.get(Assets.getAssetRoot(), EVENTS_ASSET_PATH).toString());
        });
        EditorEventBus.get().publish(new AssetSelectionRequestEvent(EVENTS_ASSET_PATH, DialogueEvents.class));
    }

    private void ensureAssetExists(String relativePath, IORunnable creator) {
        Path filePath = Paths.get(Assets.getAssetRoot(), relativePath);
        if (!Files.exists(filePath)) {
            try {
                Files.createDirectories(filePath.getParent());
                creator.run();
                showStatus("Created " + relativePath);
            } catch (IOException e) {
                LOG.error("Failed to create asset: " + relativePath, e);
            }
        }
    }

    @FunctionalInterface
    private interface IORunnable {
        void run() throws IOException;
    }

    private void showStatus(String message) {
        EditorEventBus.get().publish(new StatusMessageEvent(message));
    }
}
