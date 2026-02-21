package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.AssetSelectionRequestEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetCreationInfo;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.panels.dialogue.DialogueChoicesEditor;
import com.pocket.rpg.editor.panels.dialogue.DialogueLinesEditor;
import com.pocket.rpg.editor.panels.dialogue.DialogueValidation;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.DialogueEventsLoader;
import com.pocket.rpg.resources.loaders.DialogueLoader;
import com.pocket.rpg.resources.loaders.DialogueVariablesLoader;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

/**
 * Content implementation for editing .dialogue.json assets in the unified AssetEditorPanel.
 * <p>
 * Renders the lines editor + choices editor. The hamburger sidebar handles
 * dialogue file selection; this content edits the currently loaded Dialogue.
 */
@EditorContentFor(com.pocket.rpg.dialogue.Dialogue.class)
public class DialogueEditorContent implements AssetEditorContent {

    private static final String VARIABLES_ASSET_PATH = "dialogues/variables.dialogue-vars.json";
    private static final String EVENTS_ASSET_PATH = "dialogues/events.dialogue-events.json";
    private static final AssetCreationInfo CREATION_INFO = new AssetCreationInfo("dialogues/", ".dialogue.json");

    // State
    private Dialogue editingDialogue;
    private String editingPath;
    private AssetEditorShell shell;

    // Sub-editors
    private DialogueLinesEditor linesEditor;
    private DialogueChoicesEditor choicesEditor;

    // Undo snapshot
    private Object pendingBeforeSnapshot = null;

    // New dialogue popup
    private boolean showNewDialoguePopup = false;
    private boolean focusNewDialogueInput = false;
    private final ImString newDialogueName = new ImString();

    // Delete confirmation
    private boolean showDeleteConfirmPopup = false;

    // Event subscription (for cleanup)
    private Consumer<AssetChangedEvent> assetChangedHandler;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        assetChangedHandler = event -> {
            if (event.path().endsWith(".dialogue.json")) {
                if (shell != null) shell.requestSidebarRefresh();
            }
        };
        EditorEventBus.get().subscribe(AssetChangedEvent.class, assetChangedHandler);
    }

    @Override
    public void destroy() {
        if (assetChangedHandler != null) {
            EditorEventBus.get().unsubscribe(AssetChangedEvent.class, assetChangedHandler);
            assetChangedHandler = null;
        }
    }

    @Override
    public void onAssetLoaded(String path, Object asset, AssetEditorShell shell) {
        this.editingPath = path;
        this.editingDialogue = (Dialogue) asset;
        this.shell = shell;
        this.pendingBeforeSnapshot = null;

        // Create sub-editors with callbacks
        linesEditor = new DialogueLinesEditor(
                this::captureUndoState,
                this::onSubEditorDirty,
                this::addLine,
                this::getValidVariableNames,
                this::getValidEventNames,
                this::getCustomEventNames,
                this::loadVariablesAsset
        );

        choicesEditor = new DialogueChoicesEditor(
                this::captureUndoState,
                this::onSubEditorDirty,
                this::getValidEventNames,
                this::getCustomEventNames,
                this::getDialogueOptions
        );
    }

    @Override
    public void onAssetUnloaded() {
        editingDialogue = null;
        editingPath = null;
        pendingBeforeSnapshot = null;
        linesEditor = null;
        choicesEditor = null;
    }

    @Override
    public void onAfterUndoRedo() {
        if (linesEditor != null) linesEditor.resetState();
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return CREATION_INFO;
    }

    @Override
    public Class<?> getAssetClass() {
        return Dialogue.class;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingDialogue == null || linesEditor == null) return;

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
    // TOOLBAR EXTRAS
    // ========================================================================

    @Override
    public void renderToolbarExtras() {
        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // New / Delete dialogue
        if (ImGui.button(MaterialIcons.Add + " New##newDlg")) {
            openNewDialog();
        }

        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.Delete + " Delete##delDlg")) {
            showDeleteConfirmPopup = true;
        }

        // Right-aligned: Variables, Events, Refresh
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
            shell.requestSidebarRefresh();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh dialogue list");
        }
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        renderNewDialoguePopup();
        renderDeleteConfirmPopup();
    }

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
            String name = editingPath != null ? extractDisplayName(editingPath) : "?";
            ImGui.text("Delete \"" + name + "\"?");
            ImGui.text("This action cannot be undone.");

            ImGui.spacing();

            if (ImGui.button("Delete", 100, 0)) {
                deleteCurrentDialogue();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                ImGui.closeCurrentPopup();
            }

            ImGui.endPopup();
        }
    }

    @Override
    public void onNewRequested() {
        openNewDialog();
    }

    private void openNewDialog() {
        if (shell != null) {
            shell.requestDirtyGuard(() -> {
                newDialogueName.set("");
                showNewDialoguePopup = true;
            });
        } else {
            newDialogueName.set("");
            showNewDialoguePopup = true;
        }
    }

    // ========================================================================
    // SHORTCUTS
    // ========================================================================

    @Override
    public List<ShortcutAction> provideExtraShortcuts(KeyboardLayout layout) {
        return List.of(
                ShortcutAction.builder()
                        .id("editor.dialogue.addLine")
                        .displayName("Add Dialogue Line")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Enter))
                        .allowInInput(true)
                        .handler(this::addLine)
                        .build()
        );
    }

    // ========================================================================
    // ASSET ANNOTATION (sidebar warning icons)
    // ========================================================================

    @Override
    public String getAssetAnnotation(String path) {
        if (!path.endsWith(".dialogue.json")) return null;
        try {
            Dialogue dialogue = Assets.load(path, Dialogue.class);
            if (dialogue != null) {
                Set<String> varNames = getValidVariableNames();
                Set<String> eventNames = getValidEventNames();
                if (DialogueValidation.hasDialogueWarnings(dialogue, varNames, eventNames)) {
                    return MaterialIcons.Warning;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ========================================================================
    // SAVE
    // ========================================================================

    @Override
    public boolean hasCustomSave() {
        return true;
    }

    @Override
    public void customSave(String path) {
        if (editingDialogue == null) return;

        Path filePath = Paths.get(Assets.getAssetRoot(), path);
        try {
            DialogueLoader loader = new DialogueLoader();
            loader.save(editingDialogue, filePath.toString());
            Assets.reload(path);
            shell.showStatus("Saved dialogue: " + extractDisplayName(path));
        } catch (IOException e) {
            shell.showStatus("Error saving dialogue: " + e.getMessage());
        }
    }

    // ========================================================================
    // UNDO SUPPORT
    // ========================================================================

    /** Deep snapshot for undo/redo. */
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

    private void captureUndoState() {
        if (editingDialogue == null) return;
        pendingBeforeSnapshot = DialogueSnapshot.capture(editingDialogue);
    }

    private void onSubEditorDirty() {
        shell.markDirty();
        flushPendingUndo();
    }

    private void flushPendingUndo() {
        if (pendingBeforeSnapshot != null && editingDialogue != null) {
            Object after = DialogueSnapshot.capture(editingDialogue);
            UndoManager um = UndoManager.getInstance();
            um.push(new SnapshotCommand<>(
                    editingDialogue, pendingBeforeSnapshot, after,
                    (target, snapshot) -> ((DialogueSnapshot) snapshot).restore(target),
                    "Edit Dialogue"
            ));
            pendingBeforeSnapshot = null;
        }
    }

    private void captureStructuralUndo(String description, Runnable mutation) {
        if (editingDialogue == null) return;
        UndoManager um = UndoManager.getInstance();
        um.push(SnapshotCommand.capture(
                editingDialogue,
                DialogueSnapshot::capture,
                (target, snapshot) -> ((DialogueSnapshot) snapshot).restore(target),
                mutation,
                description
        ));
        shell.markDirty();
    }

    private void addLine() {
        if (editingDialogue == null) return;
        captureStructuralUndo("Add Line", () -> {
            List<DialogueEntry> entries = editingDialogue.getEntries();
            int insertIndex = entries.size();
            if (!entries.isEmpty() && entries.getLast() instanceof DialogueChoiceGroup) {
                insertIndex = entries.size() - 1;
            }
            entries.add(insertIndex, new DialogueLine(""));
        });
    }

    // ========================================================================
    // OPERATIONS
    // ========================================================================

    private void createNewDialogue(String name) {
        Dialogue dialogue = new Dialogue(name);
        dialogue.getEntries().add(new DialogueLine(""));
        shell.createAsset(name, dialogue);
    }

    private void deleteCurrentDialogue() {
        if (editingPath == null) return;

        String path = editingPath;
        Path filePath = Paths.get(Assets.getAssetRoot(), path);
        try {
            Files.deleteIfExists(filePath);
            shell.showStatus("Deleted dialogue: " + extractDisplayName(path));
        } catch (IOException e) {
            System.err.println("[DialogueEditorContent] Failed to delete: " + e.getMessage());
            shell.showStatus("Error deleting dialogue: " + e.getMessage());
            return;
        }

        EditorEventBus.get().publish(new AssetChangedEvent(path, AssetChangedEvent.ChangeType.DELETED));
        shell.clearEditingAsset();
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
        List<String> paths = Assets.scanByType(Dialogue.class);
        return paths.stream()
                .map(p -> new DialogueChoicesEditor.DialogueOption(p, extractDisplayName(p)))
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
                shell.showStatus("Created " + relativePath);
            } catch (IOException e) {
                System.err.println("[DialogueEditorContent] Failed to create asset: " + relativePath);
            }
        }
    }

    @FunctionalInterface
    private interface IORunnable {
        void run() throws IOException;
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
}
