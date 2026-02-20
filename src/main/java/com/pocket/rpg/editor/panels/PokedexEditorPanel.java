package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.StatusMessageEvent;
import com.pocket.rpg.editor.shortcut.EditorShortcuts;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.ui.fields.EnumEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.loaders.PokedexLoader;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.*;

/**
 * Two-column editor for .pokedex.json assets.
 * Tab switch between Species and Moves views.
 * Left column: searchable list with add/delete.
 * Right column: field editors for the selected item.
 */
public class PokedexEditorPanel extends EditorPanel {

    private static final Logger LOG = Log.getLogger(PokedexEditorPanel.class);

    // ========================================================================
    // STATE
    // ========================================================================

    private enum Tab { SPECIES, MOVES }

    private Tab activeTab = Tab.SPECIES;

    // Pokedex list
    private final List<PokedexListEntry> pokedexList = new ArrayList<>();
    private PokedexListEntry selectedPokedexEntry = null;
    private Pokedex editingPokedex = null;
    private String editingPokedexPath = null;
    private boolean needsRefresh = true;

    // Species state
    private PokemonSpecies selectedSpecies = null;

    // Move state
    private Move selectedMove = null;

    // Search filter
    private final ImString searchFilter = new ImString();

    // Dirty tracking
    private boolean dirty = false;

    // Undo/redo
    private static final int MAX_UNDO_HISTORY = 50;
    private final Deque<PokedexSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<PokedexSnapshot> redoStack = new ArrayDeque<>();

    // Popups
    private boolean showDeleteConfirmPopup = false;
    private String deleteTarget = null;
    private boolean showUnsavedChangesPopup = false;
    private PokedexListEntry pendingSwitchEntry = null;

    // Learnset combo buffer
    private final ImInt learnsetMoveBuffer = new ImInt();

    // ========================================================================
    // INNER TYPES
    // ========================================================================

    private record PokedexListEntry(String path, String displayName) {
        boolean matches(PokedexListEntry other) {
            return other != null && path.equals(other.path);
        }
    }

    /** Deep snapshot of a Pokedex for undo/redo. */
    private static class PokedexSnapshot {
        private final List<PokemonSpecies> species;
        private final List<Move> moves;

        static PokedexSnapshot capture(Pokedex pokedex) {
            PokedexSnapshot snap = new PokedexSnapshot();
            for (PokemonSpecies sp : pokedex.getAllSpecies()) {
                snap.species.add(copySpecies(sp));
            }
            for (Move m : pokedex.getAllMoves()) {
                snap.moves.add(copyMove(m));
            }
            return snap;
        }

        private PokedexSnapshot() {
            this.species = new ArrayList<>();
            this.moves = new ArrayList<>();
        }

        void restore(Pokedex pokedex) {
            pokedex.getSpecies().clear();
            for (PokemonSpecies sp : species) {
                pokedex.addSpecies(copySpecies(sp));
            }
            pokedex.getMoves().clear();
            for (Move m : moves) {
                pokedex.addMove(copyMove(m));
            }
        }

        private static PokemonSpecies copySpecies(PokemonSpecies sp) {
            List<LearnedMove> learnsetCopy = new ArrayList<>();
            for (LearnedMove lm : sp.getLearnset()) {
                learnsetCopy.add(new LearnedMove(lm.getLevel(), lm.getMoveId()));
            }
            return new PokemonSpecies(
                    sp.getSpeciesId(), sp.getName(), sp.getType(), sp.getBaseStats(),
                    learnsetCopy, sp.getBaseExpYield(), sp.getCatchRate(),
                    sp.getGrowthRate(), sp.getSpriteId(),
                    sp.getEvolutionMethod(), sp.getEvolutionLevel(),
                    sp.getEvolutionItem(), sp.getEvolvesInto()
            );
        }

        private static Move copyMove(Move m) {
            return new Move(
                    m.getMoveId(), m.getName(), m.getType(), m.getCategory(),
                    m.getPower(), m.getAccuracy(), m.getPp(),
                    m.getEffect(), m.getEffectChance(), m.getPriority()
            );
        }
    }

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public PokedexEditorPanel() {
        super(EditorShortcuts.PanelIds.POKEDEX_EDITOR, false);
        EditorEventBus.get().subscribe(AssetChangedEvent.class, this::onAssetChanged);
    }

    private void onAssetChanged(AssetChangedEvent event) {
        if (event.path().endsWith(".pokedex.json")) {
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
                        .id("editor.pokedex.save")
                        .displayName("Save Pokedex")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.S))
                        .allowInInput(true)
                        .handler(() -> { if (dirty) saveCurrentPokedex(); })
                        .build(),
                panelShortcut()
                        .id("editor.pokedex.undo")
                        .displayName("Pokedex Undo")
                        .defaultBinding(undoBinding)
                        .allowInInput(true)
                        .handler(this::undo)
                        .build(),
                panelShortcut()
                        .id("editor.pokedex.redo")
                        .displayName("Pokedex Redo")
                        .defaultBinding(redoBinding)
                        .allowInInput(true)
                        .handler(this::redo)
                        .build(),
                panelShortcut()
                        .id("editor.pokedex.redoAlt")
                        .displayName("Pokedex Redo (Alt)")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
                        .allowInInput(true)
                        .handler(this::redo)
                        .build()
        );
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public void selectPokedexByPath(String path) {
        setOpen(true);
        refreshPokedexList();
        for (PokedexListEntry entry : pokedexList) {
            if (entry.path.equals(path)) {
                switchToPokedex(entry);
                return;
            }
        }
        LOG.warn("Pokedex not found: " + path);
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
                refreshPokedexList();
                needsRefresh = false;
            }

            float totalWidth = ImGui.getContentRegionAvailX();
            float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

            if (ImGui.beginChild("##pokedexList", leftColumnWidth, -1, true)) {
                renderLeftColumn();
            }
            ImGui.endChild();

            ImGui.sameLine();

            if (ImGui.beginChild("##pokedexEditor", 0, -1, true)) {
                renderRightColumn();
            }
            ImGui.endChild();
        }

        ImGui.end();

        renderDeleteConfirmPopup();
        renderUnsavedChangesPopup();
    }

    private String getWindowTitle() {
        String title = "Pokedex Editor";
        if (dirty && selectedPokedexEntry != null) {
            title += " *";
        }
        return title;
    }

    // ========================================================================
    // LEFT COLUMN
    // ========================================================================

    private void renderLeftColumn() {
        renderPokedexSelector();
        ImGui.separator();

        if (editingPokedex == null) {
            ImGui.textDisabled("Select a Pokedex file to edit");
            return;
        }

        if (ImGui.beginTabBar("##pokedexTabs")) {
            if (ImGui.beginTabItem("Species")) {
                activeTab = Tab.SPECIES;
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Moves")) {
                activeTab = Tab.MOVES;
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##search", "Search...", searchFilter);
        ImGui.spacing();

        if (activeTab == Tab.SPECIES) {
            renderSpeciesList();
        } else {
            renderMoveList();
        }
    }

    private void renderPokedexSelector() {
        String[] names = pokedexList.stream().map(e -> e.displayName).toArray(String[]::new);
        int currentIndex = selectedPokedexEntry != null ? pokedexList.indexOf(selectedPokedexEntry) : -1;
        ImInt selected = new ImInt(Math.max(0, currentIndex));

        FieldEditorUtils.inspectorRow("Pokedex", () -> {
            if (ImGui.combo("##pokedexSelect", selected, names)
                    && selected.get() >= 0 && selected.get() < pokedexList.size()) {
                PokedexListEntry newEntry = pokedexList.get(selected.get());
                if (selectedPokedexEntry == null || !selectedPokedexEntry.matches(newEntry)) {
                    if (dirty) {
                        pendingSwitchEntry = newEntry;
                        showUnsavedChangesPopup = true;
                    } else {
                        switchToPokedex(newEntry);
                    }
                }
            }
        });
    }

    // ========================================================================
    // SPECIES LIST
    // ========================================================================

    private void renderSpeciesList() {
        if (ImGui.button(MaterialIcons.Add + " New Species")) {
            captureUndoState();
            String id = generateUniqueSpeciesId();
            PokemonSpecies sp = new PokemonSpecies(
                    id, "New Species", PokemonType.NORMAL,
                    new Stats(50, 50, 50, 50, 50, 50),
                    new ArrayList<>(), 50, 45, GrowthRate.MEDIUM_FAST,
                    "", EvolutionMethod.NONE, 0, null, null
            );
            editingPokedex.addSpecies(sp);
            selectedSpecies = sp;
            dirty = true;
        }

        if (selectedSpecies != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                deleteTarget = "species:" + selectedSpecies.getSpeciesId();
                showDeleteConfirmPopup = true;
            }
        }

        ImGui.separator();

        String filter = searchFilter.get().toLowerCase();

        if (ImGui.beginChild("##speciesListScroll")) {
            List<PokemonSpecies> sorted = new ArrayList<>(editingPokedex.getAllSpecies());
            sorted.sort(Comparator.comparing(PokemonSpecies::getSpeciesId));

            for (PokemonSpecies sp : sorted) {
                if (!filter.isEmpty()
                        && !sp.getSpeciesId().toLowerCase().contains(filter)
                        && !sp.getName().toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = sp == selectedSpecies;
                String label = sp.getName() + " (" + sp.getSpeciesId() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    selectedSpecies = sp;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // MOVE LIST
    // ========================================================================

    private void renderMoveList() {
        if (ImGui.button(MaterialIcons.Add + " New Move")) {
            captureUndoState();
            String id = generateUniqueMoveId();
            Move move = new Move(
                    id, "New Move", PokemonType.NORMAL, MoveCategory.PHYSICAL,
                    40, 100, 35, "", 0, 0
            );
            editingPokedex.addMove(move);
            selectedMove = move;
            dirty = true;
        }

        if (selectedMove != null) {
            ImGui.sameLine();
            if (ImGui.button(MaterialIcons.Delete + " Delete")) {
                deleteTarget = "move:" + selectedMove.getMoveId();
                showDeleteConfirmPopup = true;
            }
        }

        ImGui.separator();

        String filter = searchFilter.get().toLowerCase();

        if (ImGui.beginChild("##moveListScroll")) {
            List<Move> sorted = new ArrayList<>(editingPokedex.getAllMoves());
            sorted.sort(Comparator.comparing(Move::getMoveId));

            for (Move m : sorted) {
                if (!filter.isEmpty()
                        && !m.getMoveId().toLowerCase().contains(filter)
                        && !m.getName().toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = m == selectedMove;
                String label = m.getName() + " (" + m.getMoveId() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    selectedMove = m;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN
    // ========================================================================

    private void renderRightColumn() {
        if (editingPokedex == null) {
            ImGui.textDisabled("No Pokedex loaded");
            return;
        }

        if (activeTab == Tab.SPECIES) {
            renderSpeciesEditor();
        } else {
            renderMoveEditor();
        }
    }

    // ========================================================================
    // SPECIES EDITOR
    // ========================================================================

    private void renderSpeciesEditor() {
        if (selectedSpecies == null) {
            ImGui.textDisabled("Select a species to edit");
            return;
        }

        PokemonSpecies sp = selectedSpecies;
        String sid = sp.getSpeciesId();

        ImGui.text("Species: " + sid);
        ImGui.separator();

        // Species ID — special handling for rename (re-key in map)
        PrimitiveEditors.drawString("Species ID", "species." + sid + ".speciesId",
                sp::getSpeciesId, newId -> {
                    String trimmed = newId.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(sp.getSpeciesId())
                            && editingPokedex.getSpecies(trimmed) == null) {
                        captureUndoState();
                        editingPokedex.removeSpecies(sp.getSpeciesId());
                        sp.setSpeciesId(trimmed);
                        editingPokedex.addSpecies(sp);
                        dirty = true;
                    }
                });

        if (PrimitiveEditors.drawString("Name", "species." + sid + ".name",
                sp::getName, val -> { captureUndoState(); sp.setName(val); dirty = true; })) {
            // handled by setter
        }

        if (EnumEditor.drawEnum("Type", "species." + sid + ".type",
                sp::getType, val -> { captureUndoState(); sp.setType(val); dirty = true; },
                PokemonType.class)) {
            // handled by setter
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Base Stats");
        ImGui.separator();

        renderBaseStats(sp);

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Attributes");
        ImGui.separator();

        if (PrimitiveEditors.drawInt("Base EXP", "species." + sid + ".baseExpYield",
                sp::getBaseExpYield, val -> { captureUndoState(); sp.setBaseExpYield(Math.max(0, val)); dirty = true; })) {
        }

        if (PrimitiveEditors.drawInt("Catch Rate", "species." + sid + ".catchRate",
                sp::getCatchRate, val -> { captureUndoState(); sp.setCatchRate(Math.max(0, Math.min(255, val))); dirty = true; })) {
        }

        if (EnumEditor.drawEnum("Growth Rate", "species." + sid + ".growthRate",
                sp::getGrowthRate, val -> { captureUndoState(); sp.setGrowthRate(val); dirty = true; },
                GrowthRate.class)) {
        }

        if (PrimitiveEditors.drawString("Sprite ID", "species." + sid + ".spriteId",
                () -> sp.getSpriteId() != null ? sp.getSpriteId() : "",
                val -> { captureUndoState(); sp.setSpriteId(val.isEmpty() ? null : val); dirty = true; })) {
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Evolution");
        ImGui.separator();

        if (EnumEditor.drawEnum("Method", "species." + sid + ".evoMethod",
                sp::getEvolutionMethod, val -> { captureUndoState(); sp.setEvolutionMethod(val); dirty = true; },
                EvolutionMethod.class)) {
        }

        if (sp.getEvolutionMethod() == EvolutionMethod.LEVEL) {
            if (PrimitiveEditors.drawInt("Level", "species." + sid + ".evoLevel",
                    sp::getEvolutionLevel, val -> { captureUndoState(); sp.setEvolutionLevel(Math.max(1, val)); dirty = true; })) {
            }
        }

        if (sp.getEvolutionMethod() == EvolutionMethod.ITEM) {
            if (PrimitiveEditors.drawString("Item", "species." + sid + ".evoItem",
                    () -> sp.getEvolutionItem() != null ? sp.getEvolutionItem() : "",
                    val -> { captureUndoState(); sp.setEvolutionItem(val.isEmpty() ? null : val); dirty = true; })) {
            }
        }

        if (sp.getEvolutionMethod() != EvolutionMethod.NONE) {
            renderEvolvesIntoCombo(sp);
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Learnset");
        ImGui.separator();

        renderLearnsetEditor(sp);
    }

    private void renderBaseStats(PokemonSpecies sp) {
        Stats stats = sp.getBaseStats();
        int[] hp = {stats.hp()};
        int[] atk = {stats.atk()};
        int[] def = {stats.def()};
        int[] spAtk = {stats.spAtk()};
        int[] spDef = {stats.spDef()};
        int[] spd = {stats.spd()};

        boolean changed = false;
        changed |= renderStatRow("HP", "hp", hp);
        changed |= renderStatRow("ATK", "atk", atk);
        changed |= renderStatRow("DEF", "def", def);
        changed |= renderStatRow("SP ATK", "spAtk", spAtk);
        changed |= renderStatRow("SP DEF", "spDef", spDef);
        changed |= renderStatRow("SPD", "spd", spd);

        if (changed) {
            captureUndoState();
            sp.setBaseStats(new Stats(hp[0], atk[0], def[0], spAtk[0], spDef[0], spd[0]));
            dirty = true;
        }

        int total = hp[0] + atk[0] + def[0] + spAtk[0] + spDef[0] + spd[0];
        ImGui.text("Total: " + total);
    }

    private boolean renderStatRow(String label, String id, int[] value) {
        ImInt buf = new ImInt(value[0]);
        boolean changed = false;
        FieldEditorUtils.inspectorRow(label, () -> {
            ImGui.inputInt("##stat_" + id, buf);
        });
        int clamped = Math.max(0, Math.min(255, buf.get()));
        if (clamped != value[0]) {
            value[0] = clamped;
            changed = true;
        }
        return changed;
    }

    private void renderEvolvesIntoCombo(PokemonSpecies sp) {
        List<PokemonSpecies> allSpecies = new ArrayList<>(editingPokedex.getAllSpecies());
        allSpecies.sort(Comparator.comparing(PokemonSpecies::getSpeciesId));

        String[] options = new String[allSpecies.size() + 1];
        options[0] = "(none)";
        int foundIdx = 0;
        for (int i = 0; i < allSpecies.size(); i++) {
            options[i + 1] = allSpecies.get(i).getSpeciesId();
            if (allSpecies.get(i).getSpeciesId().equals(sp.getEvolvesInto())) {
                foundIdx = i + 1;
            }
        }

        final int currentIdx = foundIdx;
        ImInt selected = new ImInt(currentIdx);
        FieldEditorUtils.inspectorRow("Evolves Into", () -> {
            if (ImGui.combo("##evolvesInto", selected, options)) {
                if (selected.get() != currentIdx) {
                    captureUndoState();
                    sp.setEvolvesInto(selected.get() == 0 ? null : options[selected.get()]);
                    dirty = true;
                }
            }
        });
    }

    // ========================================================================
    // LEARNSET EDITOR
    // ========================================================================

    private void renderLearnsetEditor(PokemonSpecies sp) {
        List<LearnedMove> learnset = sp.getLearnset();

        if (ImGui.button(MaterialIcons.Add + " Add Move")) {
            captureUndoState();
            learnset.add(new LearnedMove(1, ""));
            dirty = true;
        }

        if (learnset.isEmpty()) {
            ImGui.textDisabled("No moves in learnset");
            return;
        }

        learnset.sort(Comparator.comparingInt(LearnedMove::getLevel));

        List<Move> allMoves = new ArrayList<>(editingPokedex.getAllMoves());
        allMoves.sort(Comparator.comparing(Move::getMoveId));
        String[] moveOptions = allMoves.stream().map(Move::getMoveId).toArray(String[]::new);

        if (ImGui.beginTable("##learnsetTable", 3, ImGuiTableFlags.BordersInner | ImGuiTableFlags.RowBg)) {
            ImGui.tableSetupColumn("Level", ImGuiTableColumnFlags.WidthFixed, 60);
            ImGui.tableSetupColumn("Move", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("##del", ImGuiTableColumnFlags.WidthFixed, 30);
            ImGui.tableHeadersRow();

            int removeIndex = -1;

            for (int i = 0; i < learnset.size(); i++) {
                LearnedMove lm = learnset.get(i);
                ImGui.tableNextRow();
                ImGui.pushID(i);

                // Level
                ImGui.tableNextColumn();
                ImInt levelBuf = new ImInt(lm.getLevel());
                ImGui.setNextItemWidth(-1);
                if (ImGui.inputInt("##lvl", levelBuf)) {
                    int newLevel = Math.max(1, levelBuf.get());
                    if (newLevel != lm.getLevel()) {
                        captureUndoState();
                        lm.setLevel(newLevel);
                        dirty = true;
                    }
                }

                // Move combo
                ImGui.tableNextColumn();
                int moveIdx = 0;
                for (int j = 0; j < moveOptions.length; j++) {
                    if (moveOptions[j].equals(lm.getMoveId())) {
                        moveIdx = j;
                        break;
                    }
                }
                learnsetMoveBuffer.set(moveIdx);
                ImGui.setNextItemWidth(-1);
                if (ImGui.combo("##move", learnsetMoveBuffer, moveOptions) && moveOptions.length > 0) {
                    String newMoveId = moveOptions[learnsetMoveBuffer.get()];
                    if (!newMoveId.equals(lm.getMoveId())) {
                        captureUndoState();
                        lm.setMoveId(newMoveId);
                        dirty = true;
                    }
                }

                // Delete button
                ImGui.tableNextColumn();
                if (ImGui.smallButton(MaterialIcons.Delete + "##del")) {
                    removeIndex = i;
                }

                ImGui.popID();
            }

            ImGui.endTable();

            if (removeIndex >= 0) {
                captureUndoState();
                learnset.remove(removeIndex);
                dirty = true;
            }
        }
    }

    // ========================================================================
    // MOVE EDITOR
    // ========================================================================

    private void renderMoveEditor() {
        if (selectedMove == null) {
            ImGui.textDisabled("Select a move to edit");
            return;
        }

        Move m = selectedMove;
        String mid = m.getMoveId();

        ImGui.text("Move: " + mid);
        ImGui.separator();

        // Move ID — special handling for rename (re-key in map)
        PrimitiveEditors.drawString("Move ID", "move." + mid + ".moveId",
                m::getMoveId, newId -> {
                    String trimmed = newId.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(m.getMoveId())
                            && editingPokedex.getMove(trimmed) == null) {
                        captureUndoState();
                        editingPokedex.removeMove(m.getMoveId());
                        m.setMoveId(trimmed);
                        editingPokedex.addMove(m);
                        dirty = true;
                    }
                });

        if (PrimitiveEditors.drawString("Name", "move." + mid + ".name",
                m::getName, val -> { captureUndoState(); m.setName(val); dirty = true; })) {
        }

        if (EnumEditor.drawEnum("Type", "move." + mid + ".type",
                m::getType, val -> { captureUndoState(); m.setType(val); dirty = true; },
                PokemonType.class)) {
        }

        if (EnumEditor.drawEnum("Category", "move." + mid + ".category",
                m::getCategory, val -> { captureUndoState(); m.setCategory(val); dirty = true; },
                MoveCategory.class)) {
        }

        if (PrimitiveEditors.drawInt("Power", "move." + mid + ".power",
                m::getPower, val -> { captureUndoState(); m.setPower(Math.max(0, val)); dirty = true; })) {
        }

        if (PrimitiveEditors.drawInt("Accuracy", "move." + mid + ".accuracy",
                m::getAccuracy, val -> { captureUndoState(); m.setAccuracy(Math.max(0, Math.min(100, val))); dirty = true; })) {
        }

        if (PrimitiveEditors.drawInt("PP", "move." + mid + ".pp",
                m::getPp, val -> { captureUndoState(); m.setPp(Math.max(1, val)); dirty = true; })) {
        }

        if (PrimitiveEditors.drawString("Effect", "move." + mid + ".effect",
                () -> m.getEffect() != null ? m.getEffect() : "",
                val -> { captureUndoState(); m.setEffect(val); dirty = true; })) {
        }

        if (PrimitiveEditors.drawInt("Effect %", "move." + mid + ".effectChance",
                m::getEffectChance, val -> { captureUndoState(); m.setEffectChance(Math.max(0, Math.min(100, val))); dirty = true; })) {
        }

        if (PrimitiveEditors.drawInt("Priority", "move." + mid + ".priority",
                m::getPriority, val -> { captureUndoState(); m.setPriority(val); dirty = true; })) {
        }
    }

    // ========================================================================
    // POKEDEX MANAGEMENT
    // ========================================================================

    private void refreshPokedexList() {
        pokedexList.clear();
        List<String> paths = Assets.scanByType(Pokedex.class);
        for (String path : paths) {
            String displayName = path;
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                displayName = path.substring(lastSlash + 1);
            }
            displayName = displayName.replace(".pokedex.json", "");
            pokedexList.add(new PokedexListEntry(path, displayName));
        }

        if (selectedPokedexEntry != null) {
            boolean found = false;
            for (PokedexListEntry entry : pokedexList) {
                if (entry.matches(selectedPokedexEntry)) {
                    selectedPokedexEntry = entry;
                    found = true;
                    break;
                }
            }
            if (!found) {
                selectedPokedexEntry = null;
                editingPokedex = null;
                editingPokedexPath = null;
                selectedSpecies = null;
                selectedMove = null;
            }
        }
    }

    private void switchToPokedex(PokedexListEntry entry) {
        selectedPokedexEntry = entry;
        try {
            editingPokedex = Assets.load(entry.path, Pokedex.class);
            editingPokedexPath = entry.path;
            selectedSpecies = null;
            selectedMove = null;
            dirty = false;
            undoStack.clear();
            redoStack.clear();
        } catch (Exception e) {
            LOG.error("Failed to load pokedex: " + entry.path, e);
            showStatus("Failed to load: " + entry.path);
            editingPokedex = null;
            editingPokedexPath = null;
        }
    }

    // ========================================================================
    // SAVE
    // ========================================================================

    private void saveCurrentPokedex() {
        if (editingPokedex == null || editingPokedexPath == null) return;

        try {
            String fullPath = Assets.getAssetRoot() + "/" + editingPokedexPath;
            PokedexLoader loader = new PokedexLoader();
            loader.save(editingPokedex, fullPath);
            Assets.reload(editingPokedexPath);
            dirty = false;
            showStatus("Saved: " + editingPokedexPath);
        } catch (IOException e) {
            LOG.error("Failed to save pokedex: " + editingPokedexPath, e);
            showStatus("Save failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // UNDO / REDO
    // ========================================================================

    private void captureUndoState() {
        if (editingPokedex == null) return;
        undoStack.push(PokedexSnapshot.capture(editingPokedex));
        if (undoStack.size() > MAX_UNDO_HISTORY) {
            ((ArrayDeque<PokedexSnapshot>) undoStack).removeLast();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty() || editingPokedex == null) return;
        redoStack.push(PokedexSnapshot.capture(editingPokedex));
        PokedexSnapshot snapshot = undoStack.pop();
        snapshot.restore(editingPokedex);
        reSelectAfterRestore();
        dirty = true;
    }

    private void redo() {
        if (redoStack.isEmpty() || editingPokedex == null) return;
        undoStack.push(PokedexSnapshot.capture(editingPokedex));
        PokedexSnapshot snapshot = redoStack.pop();
        snapshot.restore(editingPokedex);
        reSelectAfterRestore();
        dirty = true;
    }

    private void reSelectAfterRestore() {
        if (selectedSpecies != null) {
            selectedSpecies = editingPokedex.getSpecies(selectedSpecies.getSpeciesId());
        }
        if (selectedMove != null) {
            selectedMove = editingPokedex.getMove(selectedMove.getMoveId());
        }
    }

    // ========================================================================
    // DELETE CONFIRM POPUP
    // ========================================================================

    private void renderDeleteConfirmPopup() {
        if (!showDeleteConfirmPopup) return;

        String popupId = "Delete Confirmation##pokedex";
        ImGui.openPopup(popupId);

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this?");
            ImGui.text(deleteTarget);
            ImGui.spacing();

            if (ImGui.button("Delete", 120, 0)) {
                performDelete();
                ImGui.closeCurrentPopup();
                showDeleteConfirmPopup = false;
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
                showDeleteConfirmPopup = false;
            }
            ImGui.endPopup();
        }
    }

    private void performDelete() {
        if (deleteTarget == null || editingPokedex == null) return;

        captureUndoState();

        if (deleteTarget.startsWith("species:")) {
            String id = deleteTarget.substring("species:".length());
            editingPokedex.removeSpecies(id);
            if (selectedSpecies != null && selectedSpecies.getSpeciesId().equals(id)) {
                selectedSpecies = null;
            }
        } else if (deleteTarget.startsWith("move:")) {
            String id = deleteTarget.substring("move:".length());
            editingPokedex.removeMove(id);
            if (selectedMove != null && selectedMove.getMoveId().equals(id)) {
                selectedMove = null;
            }
        }

        dirty = true;
        deleteTarget = null;
    }

    // ========================================================================
    // UNSAVED CHANGES POPUP
    // ========================================================================

    private void renderUnsavedChangesPopup() {
        if (!showUnsavedChangesPopup) return;

        String popupId = "Unsaved Changes##pokedex";
        ImGui.openPopup(popupId);

        if (ImGui.beginPopupModal(popupId, ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("You have unsaved changes. What would you like to do?");
            ImGui.spacing();

            if (ImGui.button("Save", 100, 0)) {
                saveCurrentPokedex();
                if (pendingSwitchEntry != null) {
                    switchToPokedex(pendingSwitchEntry);
                    pendingSwitchEntry = null;
                }
                ImGui.closeCurrentPopup();
                showUnsavedChangesPopup = false;
            }
            ImGui.sameLine();
            if (ImGui.button("Discard", 100, 0)) {
                dirty = false;
                if (pendingSwitchEntry != null) {
                    switchToPokedex(pendingSwitchEntry);
                    pendingSwitchEntry = null;
                }
                ImGui.closeCurrentPopup();
                showUnsavedChangesPopup = false;
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 100, 0)) {
                pendingSwitchEntry = null;
                ImGui.closeCurrentPopup();
                showUnsavedChangesPopup = false;
            }
            ImGui.endPopup();
        }
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private String generateUniqueSpeciesId() {
        int counter = 1;
        while (editingPokedex.getSpecies("new_species_" + counter) != null) {
            counter++;
        }
        return "new_species_" + counter;
    }

    private String generateUniqueMoveId() {
        int counter = 1;
        while (editingPokedex.getMove("new_move_" + counter) != null) {
            counter++;
        }
        return "new_move_" + counter;
    }

    private void showStatus(String message) {
        EditorEventBus.get().publish(new StatusMessageEvent(message));
    }
}
