package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.dialogue.Dialogue;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.*;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.TrainerRegistryLoader;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Editor content for {@code .trainers.json} assets.
 * <p>
 * Two-column layout: left (searchable trainer list with tag filter) and
 * right (identity, battle settings, party editor with {@link MoveBrowserPopup}).
 */
@EditorContentFor(com.pocket.rpg.pokemon.TrainerRegistry.class)
public class TrainerRegistryEditorContent implements AssetEditorContent {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";

    // State
    private TrainerRegistry editingRegistry;
    private String editingPath;
    private AssetEditorShell shell;

    // Selection
    private TrainerDefinition selectedTrainer = null;
    private String selectedTrainerId = null;
    private int selectedTrainerIdx = -1;

    // Search & filter
    private final ImString searchFilter = new ImString();
    private String tagFilter = null; // null = all

    // Trainer ID editing (stable key pattern)
    private final ImString trainerIdBuffer = new ImString(256);
    private boolean trainerIdActive = false;

    // Popups
    private boolean showDeleteConfirmPopup = false;
    private final MoveBrowserPopup movePopup = new MoveBrowserPopup();

    // Event subscription
    private Consumer<AssetChangedEvent> assetChangedHandler;

    // Pokedex cache (for species dropdown + move popup)
    private Pokedex cachedPokedex;
    private String[] speciesOptions;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        assetChangedHandler = event -> {
            if (event.path().endsWith(".trainers.json")) {
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
        this.editingRegistry = (TrainerRegistry) asset;
        this.shell = shell;
        this.selectedTrainer = null;
        this.selectedTrainerId = null;
        this.searchFilter.set("");
        this.tagFilter = null;
        this.cachedPokedex = null;
        this.speciesOptions = null;
    }

    @Override
    public void onAssetUnloaded() {
        editingRegistry = null;
        editingPath = null;
        selectedTrainer = null;
        selectedTrainerId = null;
        cachedPokedex = null;
        speciesOptions = null;
    }

    @Override
    public void selectSubItem(String subItemId) {
        if (editingRegistry == null || subItemId == null) return;
        TrainerDefinition def = editingRegistry.getTrainer(subItemId);
        if (def != null) {
            selectedTrainer = def;
            selectedTrainerId = subItemId;
            // Find index in sorted list
            List<TrainerDefinition> sorted = getSortedTrainers();
            for (int i = 0; i < sorted.size(); i++) {
                if (subItemId.equals(sorted.get(i).getTrainerId())) {
                    selectedTrainerIdx = i;
                    break;
                }
            }
        }
    }

    @Override
    public void onAfterUndoRedo() {
        if (selectedTrainerId != null && editingRegistry != null) {
            selectedTrainer = editingRegistry.getTrainer(selectedTrainerId);
            if (selectedTrainer == null) {
                var all = getSortedTrainers();
                if (!all.isEmpty() && selectedTrainerIdx >= 0) {
                    selectedTrainer = all.get(Math.min(selectedTrainerIdx, all.size() - 1));
                    selectedTrainerId = selectedTrainer != null ? selectedTrainer.getTrainerId() : null;
                } else {
                    selectedTrainerId = null;
                }
            }
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return TrainerRegistry.class;
    }

    @Override
    public AssetCreationInfo getCreationInfo() {
        return new AssetCreationInfo("data/pokemon/", ".trainers.json");
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingRegistry == null) return;

        ensurePokedex();

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

        if (ImGui.beginChild("##trainerList", leftColumnWidth, -1, true)) {
            renderTrainerList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        if (ImGui.beginChild("##trainerEditor", 0, -1, true)) {
            renderTrainerEditor();
        }
        ImGui.endChild();

        movePopup.render();
    }

    // ========================================================================
    // LEFT COLUMN — TRAINER LIST
    // ========================================================================

    private void renderTrainerList() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);

        // Tag filter — dynamic from registry data
        renderTagFilter();

        ImGui.spacing();

        if (ImGui.button(MaterialIcons.Add + " New Trainer")) {
            addNewTrainer();
        }

        if (selectedTrainer != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                showDeleteConfirmPopup = true;
            }
        }

        ImGui.separator();

        String filter = searchFilter.get().toLowerCase();

        if (ImGui.beginChild("##trainerListScroll")) {
            List<TrainerDefinition> sorted = getSortedTrainers();

            for (int i = 0; i < sorted.size(); i++) {
                TrainerDefinition trainer = sorted.get(i);

                // Class filter
                if (tagFilter != null) {
                    String tc = trainer.getTag() != null ? trainer.getTag() : "";
                    if (!tagFilter.equals(tc)) continue;
                }

                // Text search
                if (!filter.isEmpty()) {
                    String id = trainer.getTrainerId() != null ? trainer.getTrainerId().toLowerCase() : "";
                    String name = trainer.getTrainerName() != null ? trainer.getTrainerName().toLowerCase() : "";
                    String cls = trainer.getTag() != null ? trainer.getTag().toLowerCase() : "";
                    if (!id.contains(filter) && !name.contains(filter) && !cls.contains(filter)) continue;
                }

                boolean isSelected = trainer == selectedTrainer;

                // Two-line display
                ImGui.pushID(i);
                String displayId = trainer.getTrainerId() != null ? trainer.getTrainerId() : "?";
                if (ImGui.selectable(displayId, isSelected)) {
                    selectedTrainer = trainer;
                    selectedTrainerId = trainer.getTrainerId();
                    selectedTrainerIdx = i;
                }
                String subtitle = formatSubtitle(trainer);
                if (!subtitle.isEmpty()) {
                    ImGui.indent(8);
                    ImGui.textDisabled(subtitle);
                    ImGui.unindent(8);
                }
                ImGui.popID();
            }
        }
        ImGui.endChild();
    }

    private void renderTagFilter() {
        // Build unique tag set dynamically
        Set<String> tags = new TreeSet<>();
        for (TrainerDefinition def : editingRegistry.getAllTrainers()) {
            String tag = def.getTag();
            if (tag != null && !tag.isEmpty()) tags.add(tag);
        }

        String[] options = new String[tags.size() + 1];
        options[0] = "All Tags";
        int idx = 1;
        int currentIdx = 0;
        for (String tag : tags) {
            if (tag.equals(tagFilter)) currentIdx = idx;
            options[idx++] = tag;
        }

        ImInt selected = new ImInt(currentIdx);
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##tagFilter", selected, options)) {
            tagFilter = selected.get() == 0 ? null : options[selected.get()];
        }
    }

    private String formatSubtitle(TrainerDefinition trainer) {
        String name = trainer.getTrainerName() != null && !trainer.getTrainerName().isEmpty()
                ? trainer.getTrainerName() : null;
        String cls = trainer.getTag() != null && !trainer.getTag().isEmpty()
                ? trainer.getTag() : null;

        if (name != null && cls != null) return name + " (" + cls + ")";
        if (name != null) return name;
        if (cls != null) return "(" + cls + ")";
        return "";
    }

    // ========================================================================
    // RIGHT COLUMN — TRAINER EDITOR
    // ========================================================================

    private void renderTrainerEditor() {
        if (selectedTrainer == null) {
            ImGui.textDisabled("Select a trainer to edit");
            return;
        }

        TrainerDefinition def = selectedTrainer;
        String tid = def.getTrainerId();

        if (ImGui.collapsingHeader(MaterialIcons.Badge + " Identity", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderIdentitySection(def, tid);
        }

        ImGui.separator();

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 0);
        if (ImGui.beginChild("##trainerFieldsScroll")) {
            if (ImGui.collapsingHeader(MaterialIcons.Settings + " Battle Settings", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderBattleSection(def, tid);
            }

            if (ImGui.collapsingHeader(MaterialIcons.Groups + " Party (" + def.getParty().size() + "/6)", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderPartySection(def, tid);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    // ========================================================================
    // IDENTITY SECTION
    // ========================================================================

    private void renderIdentitySection(TrainerDefinition def, String tid) {
        // Trainer ID — stable key, rename only on deactivation
        if (!trainerIdActive) {
            trainerIdBuffer.set(def.getTrainerId() != null ? def.getTrainerId() : "");
        }
        FieldEditorUtils.inspectorRow("Trainer ID", () -> {
            ImGui.inputText("##trainerId_edit", trainerIdBuffer);
        });
        trainerIdActive = ImGui.isItemActive();
        if (ImGui.isItemDeactivatedAfterEdit()) {
            String newId = trainerIdBuffer.get().trim();
            if (!newId.isEmpty() && !newId.equals(def.getTrainerId())
                    && editingRegistry.getTrainer(newId) == null) {
                captureStructuralUndo("Rename Trainer ID", () -> {
                    editingRegistry.removeTrainer(def.getTrainerId());
                    def.setTrainerId(newId);
                    editingRegistry.addTrainer(def);
                    selectedTrainerId = newId;
                });
            }
        }

        PrimitiveEditors.drawString("Name", "trainer." + tid + ".name",
                () -> def.getTrainerName() != null ? def.getTrainerName() : "",
                val -> { captureStructuralUndo("Edit Trainer Name", () -> def.setTrainerName(val)); });

        PrimitiveEditors.drawString("Tag", "trainer." + tid + ".tag",
                () -> def.getTag() != null ? def.getTag() : "",
                val -> { captureStructuralUndo("Edit Tag", () -> def.setTag(val)); });
    }

    // ========================================================================
    // BATTLE SETTINGS SECTION
    // ========================================================================

    private void renderBattleSection(TrainerDefinition def, String tid) {
        PrimitiveEditors.drawInt("Defeat Money", "trainer." + tid + ".defeatMoney",
                def::getDefeatMoney,
                val -> { captureStructuralUndo("Edit Defeat Money", () -> def.setDefeatMoney(Math.max(0, val))); });

        AssetEditor.drawAsset("Pre Dialogue", "trainer." + tid + ".preDialogue",
                def::getPreDialogue, val -> captureStructuralUndo("Edit Pre Dialogue", () -> def.setPreDialogue(val)),
                Dialogue.class);

        AssetEditor.drawAsset("Post Dialogue", "trainer." + tid + ".postDialogue",
                def::getPostDialogue, val -> captureStructuralUndo("Edit Post Dialogue", () -> def.setPostDialogue(val)),
                Dialogue.class);
    }

    // ========================================================================
    // PARTY SECTION
    // ========================================================================

    private void renderPartySection(TrainerDefinition def, String tid) {
        List<TrainerPokemonSpec> party = def.getParty();
        int removeIndex = -1;

        if (ImGui.beginChild("##partyList_" + tid, 0, 0, true)) {
            for (int i = 0; i < party.size(); i++) {
                TrainerPokemonSpec spec = party.get(i);
                if (spec == null) {
                    party.set(i, new TrainerPokemonSpec());
                    spec = party.get(i);
                    shell.markDirty();
                }

                ImGui.pushID(i);

                String nodeLabel = spec.getSpeciesId() != null && !spec.getSpeciesId().isEmpty()
                        ? spec.getSpeciesId() + " Lv." + spec.getLevel()
                        : "(empty) #" + (i + 1);

                boolean open = ImGui.treeNodeEx("##spec", ImGuiTreeNodeFlags.DefaultOpen
                        | ImGuiTreeNodeFlags.AllowOverlap, nodeLabel);

                ImGui.sameLine(ImGui.getContentRegionAvailX() - 20);
                if (ImGui.smallButton("x##rem")) {
                    removeIndex = i;
                }

                if (open) {
                    drawSpeciesCombo(spec, tid + ".spec" + i);
                    drawLevel(spec, tid + ".spec" + i);
                    drawMoveSlots(spec, def, i);
                    ImGui.treePop();
                }

                ImGui.popID();
            }

            if (party.isEmpty()) {
                ImGui.textDisabled("No Pokemon");
            }
        }
        ImGui.endChild();

        if (removeIndex >= 0) {
            int idx = removeIndex;
            captureStructuralUndo("Remove Party Pokemon", () -> party.remove(idx));
        }

        if (party.size() < 6) {
            if (ImGui.button("+ Add Pokemon")) {
                captureStructuralUndo("Add Party Pokemon", () -> party.add(new TrainerPokemonSpec()));
            }
        }
    }

    private void drawSpeciesCombo(TrainerPokemonSpec spec, String key) {
        if (speciesOptions == null || speciesOptions.length == 0) {
            FieldEditorUtils.inspectorRow("Species", () -> ImGui.textDisabled("No Pokedex loaded"));
            return;
        }

        String current = spec.getSpeciesId() != null ? spec.getSpeciesId() : "";
        String label = current.isEmpty() ? "Select..." : current;

        FieldEditorUtils.inspectorRow("Species", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##species", label)) {
                for (String species : speciesOptions) {
                    if (ImGui.selectable(species, species.equals(current))) {
                        captureStructuralUndo("Change Species", () -> spec.setSpeciesId(species));
                    }
                }
                ImGui.endCombo();
            }
        });
    }

    private void drawLevel(TrainerPokemonSpec spec, String key) {
        int[] levelBuf = {spec.getLevel()};
        FieldEditorUtils.inspectorRow("Level", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.dragInt("##level", levelBuf, 0.5f, 1, 100)) {
                int clamped = Math.max(1, Math.min(100, levelBuf[0]));
                captureStructuralUndo("Change Level", () -> spec.setLevel(clamped));
            }
        });
    }

    private void drawMoveSlots(TrainerPokemonSpec spec, TrainerDefinition def, int specIndex) {
        List<String> moves = spec.getMoves();
        int moveCount = moves != null ? moves.size() : 0;

        if (moveCount == 0) {
            FieldEditorUtils.inspectorRow("Moves", () -> ImGui.textDisabled("Auto (from learnset)"));
            if (ImGui.smallButton("+ Add Move##add_" + specIndex)) {
                openMovePopup(spec, def, specIndex, -1);
            }
            return;
        }

        ImGui.text("Moves");

        int[] removeHolder = {-1};

        int tableFlags = ImGuiTableFlags.Borders;
        if (ImGui.beginTable("##moves_" + specIndex, 2, tableFlags)) {
            ImGui.tableSetupColumn("##col0", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("##col1", ImGuiTableColumnFlags.WidthStretch);

            for (int slot = 0; slot < 4; slot++) {
                int col = slot % 2;
                if (col == 0) ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(col);

                if (slot < moveCount) {
                    String moveId = moves.get(slot);
                    int idx = slot;
                    ImGui.pushID(slot);

                    String btnLabel = moveId != null ? moveId : "?";
                    float[] typeColor = null;

                    if (moveId != null && cachedPokedex != null) {
                        Move move = cachedPokedex.getMove(moveId);
                        if (move != null) {
                            btnLabel = move.getName() != null ? move.getName() : moveId;
                            typeColor = PokemonTypeColors.get(move.getType());
                        }
                    }

                    // Type-colored cell background + selectable hover/active
                    if (typeColor != null) {
                        ImGui.tableSetBgColor(ImGuiTableBgTarget.CellBg,
                                ImGui.colorConvertFloat4ToU32(typeColor[0], typeColor[1], typeColor[2], 0.3f));
                        float[] hovered = darken(typeColor, 0.7f);
                        float[] active = darken(typeColor, 0.5f);
                        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, hovered[0], hovered[1], hovered[2], 0.5f);
                        ImGui.pushStyleColor(ImGuiCol.HeaderActive, active[0], active[1], active[2], 0.7f);
                    }

                    if (ImGui.selectable(btnLabel + "##moveBtn", false, ImGuiSelectableFlags.AllowItemOverlap)) {
                        openMovePopup(spec, def, specIndex, idx);
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                    }

                    if (typeColor != null) {
                        ImGui.popStyleColor(2);
                    }

                    // [x] button overlapping on the right
                    float xBtnWidth = ImGui.calcTextSize("x").x + ImGui.getStyle().getFramePaddingX() * 2;
                    ImGui.sameLine(ImGui.getContentRegionAvailX() - xBtnWidth);
                    if (ImGui.smallButton("x")) {
                        removeHolder[0] = idx;
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                    }

                    ImGui.popID();
                } else if (slot == moveCount && moveCount < 4) {
                    if (ImGui.smallButton("+ Add Move")) {
                        openMovePopup(spec, def, specIndex, -1);
                    }
                }
                // else: empty cell
            }

            ImGui.endTable();
        }

        if (removeHolder[0] >= 0) {
            int rmIdx = removeHolder[0];
            captureStructuralUndo("Remove Move", () -> {
                moves.remove(rmIdx);
                if (moves.isEmpty()) spec.setMoves(null);
            });
        }
    }

    private void openMovePopup(TrainerPokemonSpec spec, TrainerDefinition def, int specIndex, int slotIndex) {
        Set<String> selectedMoves = new HashSet<>();
        if (spec.getMoves() != null) {
            for (int i = 0; i < spec.getMoves().size(); i++) {
                String id = spec.getMoves().get(i);
                if (id != null && i != slotIndex) {
                    selectedMoves.add(id);
                }
            }
        }

        movePopup.open(spec.getSpeciesId(), selectedMoves, moveId -> {
            captureStructuralUndo("Set Move", () -> {
                if (slotIndex < 0) {
                    if (spec.getMoves() == null) spec.setMoves(new ArrayList<>());
                    spec.getMoves().add(moveId);
                } else {
                    spec.getMoves().set(slotIndex, moveId);
                }
            });
        });
    }

    private static float[] darken(float[] color, float factor) {
        return new float[]{color[0] * factor, color[1] * factor, color[2] * factor, color[3]};
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Confirmation##trainerReg");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Confirmation##trainerReg", ImGuiWindowFlags.AlwaysAutoResize)) {
            String name = selectedTrainer != null
                    ? selectedTrainer.getTrainerName() + " (" + selectedTrainer.getTrainerId() + ")"
                    : "?";
            ImGui.text("Are you sure you want to delete this trainer?");
            ImGui.text(name);
            ImGui.spacing();

            if (ImGui.button("Delete", 120, 0)) {
                performDelete();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void performDelete() {
        if (selectedTrainer == null || editingRegistry == null) return;
        String id = selectedTrainer.getTrainerId();
        captureStructuralUndo("Delete Trainer", () -> editingRegistry.removeTrainer(id));
        selectedTrainer = null;
        selectedTrainerId = null;
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
        if (editingRegistry == null) return;
        try {
            String fullPath = Assets.getAssetRoot() + "/" + path;
            TrainerRegistryLoader loader = new TrainerRegistryLoader();
            loader.save(editingRegistry, fullPath);
            Assets.reload(path);
            shell.showStatus("Saved: " + path);
        } catch (IOException e) {
            shell.showStatus("Save failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // UNDO
    // ========================================================================

    private static class TrainerRegistrySnapshot {
        private final List<TrainerDefinition> trainers;

        static TrainerRegistrySnapshot capture(TrainerRegistry registry) {
            TrainerRegistrySnapshot snap = new TrainerRegistrySnapshot();
            for (TrainerDefinition def : registry.getAllTrainers()) {
                snap.trainers.add(def.copy());
            }
            return snap;
        }

        private TrainerRegistrySnapshot() {
            this.trainers = new ArrayList<>();
        }

        void restore(TrainerRegistry registry) {
            TrainerRegistry temp = new TrainerRegistry();
            for (TrainerDefinition def : trainers) {
                temp.addTrainer(def.copy());
            }
            registry.copyFrom(temp);
        }
    }

    private void captureStructuralUndo(String description, Runnable mutation) {
        if (editingRegistry == null) return;
        UndoManager um = UndoManager.getInstance();
        um.push(SnapshotCommand.capture(
                editingRegistry,
                TrainerRegistrySnapshot::capture,
                (target, snapshot) -> ((TrainerRegistrySnapshot) snapshot).restore(target),
                mutation,
                description
        ));
        shell.markDirty();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void addNewTrainer() {
        String id = generateUniqueId();
        TrainerDefinition trainer = new TrainerDefinition(id);
        trainer.setTrainerName("New Trainer");
        captureStructuralUndo("Add Trainer", () -> editingRegistry.addTrainer(trainer));
        selectedTrainer = trainer;
        selectedTrainerId = id;
    }

    private String generateUniqueId() {
        int counter = 1;
        while (editingRegistry.getTrainer("trainer_" + counter) != null) {
            counter++;
        }
        return "trainer_" + counter;
    }

    private List<TrainerDefinition> getSortedTrainers() {
        List<TrainerDefinition> sorted = new ArrayList<>(editingRegistry.getAllTrainers());
        sorted.sort(Comparator.comparing(d -> d.getTrainerId() != null ? d.getTrainerId() : ""));
        return sorted;
    }

    private void ensurePokedex() {
        if (cachedPokedex == null) {
            cachedPokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
            if (cachedPokedex != null) {
                List<PokemonSpecies> allSpecies = new ArrayList<>(cachedPokedex.getAllSpecies());
                allSpecies.sort(Comparator.comparing(PokemonSpecies::getSpeciesId));
                speciesOptions = new String[allSpecies.size()];
                for (int i = 0; i < allSpecies.size(); i++) {
                    speciesOptions[i] = allSpecies.get(i).getSpeciesId();
                }
            } else {
                speciesOptions = new String[0];
            }
        }
    }
}
