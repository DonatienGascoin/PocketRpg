package com.pocket.rpg.editor.panels.content;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.events.AssetChangedEvent;
import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.panels.AssetEditorContent;
import com.pocket.rpg.editor.panels.AssetEditorShell;
import com.pocket.rpg.editor.ui.fields.AssetEditor;
import com.pocket.rpg.editor.ui.fields.EnumEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SnapshotCommand;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteReference;
import com.pocket.rpg.resources.loaders.PokedexLoader;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Content implementation for editing .pokedex.json assets in the unified AssetEditorPanel.
 * <p>
 * Two-column layout: left (species/moves tabs + lists) and right (field editors).
 * The hamburger sidebar handles pokedex file selection.
 */
@EditorContentFor(com.pocket.rpg.pokemon.Pokedex.class)
public class PokedexEditorContent implements AssetEditorContent {

    // State
    private Pokedex editingPokedex;
    private String editingPath;
    private AssetEditorShell shell;

    private enum Tab { SPECIES, MOVES }
    private Tab activeTab = Tab.SPECIES;

    // Selection state
    private PokemonSpecies selectedSpecies = null;
    private Move selectedMove = null;
    private int selectedSpeciesIdx = -1;
    private int selectedMoveIdx = -1;

    // Search filter
    private final ImString searchFilter = new ImString();

    // Popups
    private boolean showDeleteConfirmPopup = false;
    private String deleteTarget = null;

    // Learnset combo buffer
    private final ImInt learnsetMoveBuffer = new ImInt();

    // Drag/edit undo tracking (shared — only one widget active at a time)
    private PokedexSnapshot statDragBeforeSnapshot = null;
    private PokedexSnapshot attrEditBeforeSnapshot = null;

    // Event subscription
    private Consumer<AssetChangedEvent> assetChangedHandler;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void initialize() {
        assetChangedHandler = event -> {
            if (event.path().endsWith(".pokedex.json")) {
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
        this.editingPokedex = (Pokedex) asset;
        this.shell = shell;
        this.selectedSpecies = null;
        this.selectedMove = null;
        this.searchFilter.set("");
    }

    @Override
    public void onAssetUnloaded() {
        editingPokedex = null;
        editingPath = null;
        selectedSpecies = null;
        selectedMove = null;
    }

    @Override
    public void onAfterUndoRedo() {
        if (selectedSpecies != null && editingPokedex != null) {
            selectedSpecies = editingPokedex.getSpecies(selectedSpecies.getSpeciesId());
            if (selectedSpecies == null) {
                // ID changed (e.g., rename undo) — fall back to index
                var all = new ArrayList<>(editingPokedex.getAllSpecies());
                all.sort(Comparator.comparing(PokemonSpecies::getSpeciesId));
                if (!all.isEmpty() && selectedSpeciesIdx >= 0) {
                    selectedSpecies = all.get(Math.min(selectedSpeciesIdx, all.size() - 1));
                }
            }
        }
        if (selectedMove != null && editingPokedex != null) {
            selectedMove = editingPokedex.getMove(selectedMove.getMoveId());
            if (selectedMove == null) {
                // ID changed (e.g., rename undo) — fall back to index
                var all = new ArrayList<>(editingPokedex.getAllMoves());
                all.sort(Comparator.comparing(Move::getMoveId));
                if (!all.isEmpty() && selectedMoveIdx >= 0) {
                    selectedMove = all.get(Math.min(selectedMoveIdx, all.size() - 1));
                }
            }
        }
    }

    @Override
    public Class<?> getAssetClass() {
        return Pokedex.class;
    }

    // ========================================================================
    // RENDER
    // ========================================================================

    @Override
    public void render() {
        if (editingPokedex == null) return;

        float totalWidth = ImGui.getContentRegionAvailX();
        float leftColumnWidth = Math.max(200, totalWidth * 0.25f);

        // Left column: tabs + species/moves list
        if (ImGui.beginChild("##pokedexList", leftColumnWidth, -1, true)) {
            renderLeftColumn();
        }
        ImGui.endChild();

        ImGui.sameLine();

        // Right column: editors
        if (ImGui.beginChild("##pokedexEditor", 0, -1, true)) {
            renderRightColumn();
        }
        ImGui.endChild();
    }

    // ========================================================================
    // LEFT COLUMN
    // ========================================================================

    private void renderLeftColumn() {
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

    // ========================================================================
    // SPECIES LIST
    // ========================================================================

    private void renderSpeciesList() {
        if (ImGui.button(MaterialIcons.Add + " New Species")) {
            String id = generateUniqueSpeciesId();
            PokemonSpecies sp = new PokemonSpecies(
                    id, "New Species", PokemonType.NORMAL,
                    new Stats(50, 50, 50, 50, 50, 50),
                    new ArrayList<>(), 50, 45, GrowthRate.MEDIUM_FAST,
                    "", EvolutionMethod.NONE, 0, null, null
            );
            captureStructuralUndo("Add Species", () -> editingPokedex.addSpecies(sp));
            selectedSpecies = sp;
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

            for (int i = 0; i < sorted.size(); i++) {
                PokemonSpecies sp = sorted.get(i);
                if (!filter.isEmpty()
                        && !sp.getSpeciesId().toLowerCase().contains(filter)
                        && !sp.getName().toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = sp == selectedSpecies;
                String label = sp.getName() + " (" + sp.getSpeciesId() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    selectedSpecies = sp;
                    selectedSpeciesIdx = i;
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
            String id = generateUniqueMoveId();
            Move move = new Move(
                    id, "New Move", PokemonType.NORMAL, MoveCategory.PHYSICAL,
                    40, 100, 35, "", 0, 0
            );
            captureStructuralUndo("Add Move", () -> editingPokedex.addMove(move));
            selectedMove = move;
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

            for (int i = 0; i < sorted.size(); i++) {
                Move m = sorted.get(i);
                if (!filter.isEmpty()
                        && !m.getMoveId().toLowerCase().contains(filter)
                        && !m.getName().toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = m == selectedMove;
                String label = m.getName() + " (" + m.getMoveId() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    selectedMove = m;
                    selectedMoveIdx = i;
                }
            }
        }
        ImGui.endChild();
    }

    // ========================================================================
    // RIGHT COLUMN
    // ========================================================================

    private void renderRightColumn() {
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

        // --- Identity: always visible (pinned at top) ---
        if (ImGui.collapsingHeader(MaterialIcons.Badge + " Identity", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderIdentitySection(sp, sid);
        }

        ImGui.separator();

        // --- Scrollable area for remaining sections ---
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0, 0);
        ImGui.pushStyleVar(ImGuiStyleVar.IndentSpacing, 0);
        if (ImGui.beginChild("##speciesScroll")) {
            if (ImGui.collapsingHeader(MaterialIcons.BarChart + " Stats & Attributes", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderStatsAndAttributes(sp, sid);
            }

            if (ImGui.collapsingHeader(MaterialIcons.TrendingUp + " Evolution", ImGuiTreeNodeFlags.DefaultOpen)) {
                EnumEditor.drawEnum("Method", "species." + sid + ".evoMethod",
                        sp::getEvolutionMethod, val -> captureStructuralUndo("Edit Evolution Method", () -> sp.setEvolutionMethod(val)),
                        EvolutionMethod.class);

                if (sp.getEvolutionMethod() == EvolutionMethod.LEVEL) {
                    PrimitiveEditors.drawInt("Level", "species." + sid + ".evoLevel",
                            sp::getEvolutionLevel, val -> captureStructuralUndo("Edit Evolution Level", () -> sp.setEvolutionLevel(Math.max(1, val))));
                }

                if (sp.getEvolutionMethod() == EvolutionMethod.ITEM) {
                    PrimitiveEditors.drawString("Item", "species." + sid + ".evoItem",
                            () -> sp.getEvolutionItem() != null ? sp.getEvolutionItem() : "",
                            val -> captureStructuralUndo("Edit Evolution Item", () -> sp.setEvolutionItem(val.isEmpty() ? null : val)));
                }

                if (sp.getEvolutionMethod() != EvolutionMethod.NONE) {
                    renderEvolvesIntoCombo(sp);
                }
            }

            if (ImGui.collapsingHeader(MaterialIcons.School + " Learnset", ImGuiTreeNodeFlags.DefaultOpen)) {
                renderLearnsetEditor(sp);
            }
        }
        ImGui.endChild();
        ImGui.popStyleVar(2);
    }

    // ========================================================================
    // IDENTITY SECTION (sprite button left, fields right)
    // ========================================================================

    private void renderIdentitySection(PokemonSpecies sp, String sid) {
        Sprite resolvedSprite = resolveSprite(sp.getSpriteId());

        float spriteSize = 64;
        if (ImGui.beginTable("##identity_layout", 2, ImGuiTableFlags.None)) {
            ImGui.tableSetupColumn("sprite", ImGuiTableColumnFlags.WidthFixed, spriteSize + 16);
            ImGui.tableSetupColumn("fields", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableNextRow();

            // --- Left: Sprite button ---
            ImGui.tableNextColumn();
            renderSpriteButton(sp, resolvedSprite, spriteSize);

            // --- Right: Name, ID, Type ---
            ImGui.tableNextColumn();
            PrimitiveEditors.drawString("Species ID", "species." + sid + ".speciesId",
                    sp::getSpeciesId, newId -> {
                        String trimmed = newId.trim();
                        if (!trimmed.isEmpty() && !trimmed.equals(sp.getSpeciesId())
                                && editingPokedex.getSpecies(trimmed) == null) {
                            captureStructuralUndo("Rename Species", () -> {
                                editingPokedex.removeSpecies(sp.getSpeciesId());
                                sp.setSpeciesId(trimmed);
                                editingPokedex.addSpecies(sp);
                            });
                        }
                    });

            PrimitiveEditors.drawString("Name", "species." + sid + ".name",
                    sp::getName, val -> captureStructuralUndo("Edit Species Name", () -> sp.setName(val)));

            EnumEditor.drawEnum("Type", "species." + sid + ".type",
                    sp::getType, val -> captureStructuralUndo("Edit Species Type", () -> sp.setType(val)),
                    PokemonType.class);

            ImGui.endTable();
        }
    }

    private void renderSpriteButton(PokemonSpecies sp, Sprite resolvedSprite, float size) {
        ImGui.pushID("##spritePicker");

        boolean clicked;
        if (resolvedSprite != null && resolvedSprite.getTexture() != null) {
            int texId = resolvedSprite.getTexture().getTextureId();
            clicked = ImGui.imageButton("##sprBtn", texId, size, size,
                    resolvedSprite.getU0(), resolvedSprite.getV1(),
                    resolvedSprite.getU1(), resolvedSprite.getV0());
        } else {
            clicked = ImGui.button(MaterialIcons.Image, size, size);
        }

        if (clicked) {
            String currentPath = resolvedSprite != null
                    ? Assets.getPathForResource(resolvedSprite) : null;
            AssetEditor.openPicker(Sprite.class, currentPath, selectedAsset -> {
                Sprite picked = (Sprite) selectedAsset;
                String newPath = SpriteReference.toPath(picked);
                captureStructuralUndo("Change Sprite", () -> sp.setSpriteId(newPath));
            });
        }

        if (ImGui.isItemHovered()) {
            String spriteId = sp.getSpriteId();
            if (spriteId != null && !spriteId.isEmpty()) {
                ImGui.setTooltip(spriteId);
            } else {
                ImGui.setTooltip("Click to set sprite");
            }
        }

        ImGui.popID();
    }

    private Sprite resolveSprite(String spriteId) {
        if (spriteId == null || spriteId.isEmpty()) return null;
        try {
            return SpriteReference.fromPath(spriteId);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ========================================================================
    // STATS & ATTRIBUTES (side by side)
    // ========================================================================

    private void renderStatsAndAttributes(PokemonSpecies sp, String sid) {
        if (ImGui.beginTable("##statsAttrs", 2, ImGuiTableFlags.None)) {
            ImGui.tableSetupColumn("stats", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableSetupColumn("attrs", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableNextRow();

            // --- Left: Base Stats ---
            ImGui.tableNextColumn();
            renderBaseStats(sp);

            // --- Right: Attributes (stacked label-above-field for narrow column) ---
            ImGui.tableNextColumn();
            renderAttributeInt("Base EXP", "##baseExp_" + sid, sp.getBaseExpYield(),
                    val -> { sp.setBaseExpYield(Math.max(0, val)); shell.markDirty(); });
            renderAttributeInt("Catch Rate", "##catchRate_" + sid, sp.getCatchRate(),
                    val -> { sp.setCatchRate(Math.max(0, Math.min(255, val))); shell.markDirty(); });
            renderAttributeEnum(sp, sid);

            ImGui.endTable();
        }
    }

    private void renderBaseStats(PokemonSpecies sp) {
        Stats stats = sp.getBaseStats();
        int[] hp = {stats.hp()};
        int[] atk = {stats.atk()};
        int[] def = {stats.def()};
        int[] spAtk = {stats.spAtk()};
        int[] spDef = {stats.spDef()};
        int[] spd = {stats.spd()};

        boolean[] dragStarted = {false};
        boolean[] dragEnded = {false};
        boolean anyChanged = false;

        anyChanged |= renderStatBar("HP", "hp", hp, dragStarted, dragEnded);
        anyChanged |= renderStatBar("ATK", "atk", atk, dragStarted, dragEnded);
        anyChanged |= renderStatBar("DEF", "def", def, dragStarted, dragEnded);
        anyChanged |= renderStatBar("SP ATK", "spAtk", spAtk, dragStarted, dragEnded);
        anyChanged |= renderStatBar("SP DEF", "spDef", spDef, dragStarted, dragEnded);
        anyChanged |= renderStatBar("SPD", "spd", spd, dragStarted, dragEnded);

        // Capture snapshot at drag start
        if (dragStarted[0] && statDragBeforeSnapshot == null) {
            statDragBeforeSnapshot = PokedexSnapshot.capture(editingPokedex);
        }

        // Live-update stats during drag
        if (anyChanged) {
            sp.setBaseStats(new Stats(hp[0], atk[0], def[0], spAtk[0], spDef[0], spd[0]));
            shell.markDirty();
        }

        // Push undo when drag ends
        if (dragEnded[0] && statDragBeforeSnapshot != null) {
            PokedexSnapshot afterSnap = PokedexSnapshot.capture(editingPokedex);
            UndoManager.getInstance().push(new SnapshotCommand<>(
                    editingPokedex, statDragBeforeSnapshot, afterSnap,
                    (target, snapshot) -> ((PokedexSnapshot) snapshot).restore(target),
                    "Change Stats"
            ));
            statDragBeforeSnapshot = null;
        }

        int total = hp[0] + atk[0] + def[0] + spAtk[0] + spDef[0] + spd[0];
        ImGui.text("Total: " + total);
    }

    private boolean renderStatBar(String label, String id, int[] value,
                                  boolean[] dragStarted, boolean[] dragEnded) {
        int[] buf = {value[0]};
        FieldEditorUtils.inspectorRow(label, () -> {
            float availWidth = ImGui.getContentRegionAvailX();
            float height = ImGui.getFrameHeight();

            int capped = Math.max(0, Math.min(255, buf[0]));
            float fraction = capped / 255.0f;

            // Gradient color: red → yellow → green
            float t = Math.min(1.0f, capped / 200.0f);
            float cr = Math.min(1.0f, 2.0f * (1.0f - t));
            float cg = Math.min(1.0f, 2.0f * t);

            // Draw custom background + fill BEFORE the slider
            float startX = ImGui.getCursorScreenPosX();
            float startY = ImGui.getCursorScreenPosY();

            var drawList = ImGui.getWindowDrawList();
            drawList.addRectFilled(startX, startY, startX + availWidth, startY + height,
                    ImGui.getColorU32(0.12f, 0.12f, 0.15f, 1.0f));
            if (fraction > 0.001f) {
                drawList.addRectFilled(startX, startY, startX + availWidth * fraction, startY + height,
                        ImGui.getColorU32(cr, cg, 0.15f, 0.7f));
            }

            // Transparent slider on top for drag interaction + value overlay
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0, 0, 0, 0);
            ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 1, 1, 1, 0.06f);
            ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 1, 1, 1, 0.03f);
            ImGui.pushStyleColor(ImGuiCol.SliderGrab, 0, 0, 0, 0);
            ImGui.pushStyleColor(ImGuiCol.SliderGrabActive, 0, 0, 0, 0);
            ImGui.setNextItemWidth(availWidth);
            ImGui.sliderInt("##stat_" + id, buf, 0, 255);
            ImGui.popStyleColor(5);

            if (ImGui.isItemActivated()) dragStarted[0] = true;
            if (ImGui.isItemDeactivatedAfterEdit()) dragEnded[0] = true;
        });

        int clamped = Math.max(0, Math.min(255, buf[0]));
        if (clamped != value[0]) {
            value[0] = clamped;
            return true;
        }
        return false;
    }

    // ========================================================================
    // ATTRIBUTE FIELDS (stacked label-above-field, bypasses inspectorRow)
    // ========================================================================

    private void renderAttributeInt(String label, String imguiId, int currentValue,
                                    java.util.function.IntConsumer liveSetter) {
        ImGui.textDisabled(label);
        ImInt buf = new ImInt(currentValue);
        ImGui.setNextItemWidth(-1);
        if (ImGui.inputInt(imguiId, buf)) {
            liveSetter.accept(buf.get());
        }
        if (ImGui.isItemActivated() && attrEditBeforeSnapshot == null) {
            attrEditBeforeSnapshot = PokedexSnapshot.capture(editingPokedex);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && attrEditBeforeSnapshot != null) {
            PokedexSnapshot afterSnap = PokedexSnapshot.capture(editingPokedex);
            UndoManager.getInstance().push(new SnapshotCommand<>(
                    editingPokedex, attrEditBeforeSnapshot, afterSnap,
                    (target, snapshot) -> ((PokedexSnapshot) snapshot).restore(target),
                    "Edit " + label
            ));
            attrEditBeforeSnapshot = null;
        }
    }

    private void renderAttributeEnum(PokemonSpecies sp, String sid) {
        ImGui.textDisabled("Growth Rate");
        ImGui.setNextItemWidth(-1);

        GrowthRate[] values = GrowthRate.values();
        String[] names = new String[values.length];
        int currentIdx = 0;
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
            if (values[i] == sp.getGrowthRate()) currentIdx = i;
        }

        ImInt selected = new ImInt(currentIdx);
        if (ImGui.combo("##growthRate_" + sid, selected, names)) {
            GrowthRate newVal = values[selected.get()];
            if (newVal != sp.getGrowthRate()) {
                captureStructuralUndo("Edit Growth Rate", () -> sp.setGrowthRate(newVal));
            }
        }
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
                    String newValue = selected.get() == 0 ? null : options[selected.get()];
                    captureStructuralUndo("Change Evolution Target", () -> sp.setEvolvesInto(newValue));
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
            captureStructuralUndo("Add Learnset Entry", () -> learnset.add(new LearnedMove(1, "")));
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
                        captureStructuralUndo("Change Level", () -> lm.setLevel(newLevel));
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
                        captureStructuralUndo("Change Learnset Move", () -> lm.setMoveId(newMoveId));
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
                final int idx = removeIndex;
                captureStructuralUndo("Remove Learnset Entry", () -> learnset.remove(idx));
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

        // Move ID — special handling for rename
        PrimitiveEditors.drawString("Move ID", "move." + mid + ".moveId",
                m::getMoveId, newId -> {
                    String trimmed = newId.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(m.getMoveId())
                            && editingPokedex.getMove(trimmed) == null) {
                        captureStructuralUndo("Rename Move", () -> {
                            editingPokedex.removeMove(m.getMoveId());
                            m.setMoveId(trimmed);
                            editingPokedex.addMove(m);
                        });
                    }
                });

        if (PrimitiveEditors.drawString("Name", "move." + mid + ".name",
                m::getName, val -> captureStructuralUndo("Edit Move Name", () -> m.setName(val)))) {
        }

        if (EnumEditor.drawEnum("Type", "move." + mid + ".type",
                m::getType, val -> captureStructuralUndo("Edit Move Type", () -> m.setType(val)),
                PokemonType.class)) {
        }

        if (EnumEditor.drawEnum("Category", "move." + mid + ".category",
                m::getCategory, val -> captureStructuralUndo("Edit Move Category", () -> m.setCategory(val)),
                MoveCategory.class)) {
        }

        if (PrimitiveEditors.drawInt("Power", "move." + mid + ".power",
                m::getPower, val -> captureStructuralUndo("Edit Move Power", () -> m.setPower(Math.max(0, val))))) {
        }

        if (PrimitiveEditors.drawInt("Accuracy", "move." + mid + ".accuracy",
                m::getAccuracy, val -> captureStructuralUndo("Edit Move Accuracy", () -> m.setAccuracy(Math.max(0, Math.min(100, val)))))) {
        }

        if (PrimitiveEditors.drawInt("PP", "move." + mid + ".pp",
                m::getPp, val -> captureStructuralUndo("Edit Move PP", () -> m.setPp(Math.max(1, val))))) {
        }

        if (PrimitiveEditors.drawString("Effect", "move." + mid + ".effect",
                () -> m.getEffect() != null ? m.getEffect() : "",
                val -> captureStructuralUndo("Edit Move Effect", () -> m.setEffect(val)))) {
        }

        if (PrimitiveEditors.drawInt("Effect %", "move." + mid + ".effectChance",
                m::getEffectChance, val -> captureStructuralUndo("Edit Effect Chance", () -> m.setEffectChance(Math.max(0, Math.min(100, val)))))) {
        }

        if (PrimitiveEditors.drawInt("Priority", "move." + mid + ".priority",
                m::getPriority, val -> captureStructuralUndo("Edit Move Priority", () -> m.setPriority(val)))) {
        }
    }

    // ========================================================================
    // POPUPS
    // ========================================================================

    @Override
    public void renderPopups() {
        renderDeleteConfirmPopup();
    }

    private void renderDeleteConfirmPopup() {
        if (showDeleteConfirmPopup) {
            ImGui.openPopup("Delete Confirmation##pokedex");
            showDeleteConfirmPopup = false;
        }

        if (ImGui.beginPopupModal("Delete Confirmation##pokedex", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text("Are you sure you want to delete this?");
            ImGui.text(deleteTarget);
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
        if (deleteTarget == null || editingPokedex == null) return;

        if (deleteTarget.startsWith("species:")) {
            String id = deleteTarget.substring("species:".length());
            captureStructuralUndo("Delete Species", () -> editingPokedex.removeSpecies(id));
            if (selectedSpecies != null && selectedSpecies.getSpeciesId().equals(id)) {
                selectedSpecies = null;
            }
        } else if (deleteTarget.startsWith("move:")) {
            String id = deleteTarget.substring("move:".length());
            captureStructuralUndo("Delete Move", () -> editingPokedex.removeMove(id));
            if (selectedMove != null && selectedMove.getMoveId().equals(id)) {
                selectedMove = null;
            }
        }

        deleteTarget = null;
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
        if (editingPokedex == null) return;

        try {
            String fullPath = Assets.getAssetRoot() + "/" + path;
            PokedexLoader loader = new PokedexLoader();
            loader.save(editingPokedex, fullPath);
            Assets.reload(path);
            shell.showStatus("Saved: " + path);
        } catch (IOException e) {
            shell.showStatus("Save failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // UNDO SUPPORT
    // ========================================================================

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

    private void captureStructuralUndo(String description, Runnable mutation) {
        if (editingPokedex == null) return;
        UndoManager um = UndoManager.getInstance();
        um.push(SnapshotCommand.capture(
                editingPokedex,
                PokedexSnapshot::capture,
                (target, snapshot) -> ((PokedexSnapshot) snapshot).restore(target),
                mutation,
                description
        ));
        shell.markDirty();
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
}
