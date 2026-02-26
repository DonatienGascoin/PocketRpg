package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.utils.FuzzyMatcher;
import com.pocket.rpg.pokemon.*;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImString;

import java.util.*;
import java.util.function.Consumer;

/**
 * Popup for browsing and selecting a Pokemon move.
 * <p>
 * Modeled after {@link ComponentBrowserPopup}: search bar with fuzzy matching,
 * filter by type / category / learnset, colored type labels.
 * <p>
 * Usage:
 * <pre>
 * popup.open("bulbasaur", selectedMoveId -> { ... });
 * // in render loop:
 * popup.render();
 * </pre>
 */
public class MoveBrowserPopup {

    private static final String POKEDEX_PATH = "data/pokemon/pokedex.pokedex.json";
    private static final String POPUP_ID = "SelectMovePopup";

    private boolean shouldOpen = false;
    private boolean focusSearchNextFrame = false;
    private ImString search = new ImString(64);
    private Consumer<String> onSelect;

    // Filter state
    private PokemonType filterType = null;       // null = all
    private MoveCategory filterCategory = null;   // null = all
    private boolean filterLearnsetOnly = false;

    // Data
    private List<MoveEntry> allMoves;
    private Set<String> learnsetMoveIds = Collections.emptySet();
    private Map<String, Integer> learnsetLevels = Collections.emptyMap();
    private Set<String> disabledMoveIds = Collections.emptySet();

    private record MoveEntry(String moveId, String name, PokemonType type, MoveCategory category) {}

    /**
     * Opens the popup for move selection.
     *
     * @param speciesId       the species whose learnset is highlighted (nullable)
     * @param disabledMoveIds move IDs to show as disabled (already selected on the same spec)
     * @param onSelect        callback receiving the selected moveId
     */
    public void open(String speciesId, Set<String> disabledMoveIds, Consumer<String> onSelect) {
        this.onSelect = onSelect;
        this.disabledMoveIds = disabledMoveIds != null ? disabledMoveIds : Collections.emptySet();
        this.shouldOpen = true;
        this.focusSearchNextFrame = true;
        this.search = new ImString(64);
        this.filterType = null;
        this.filterCategory = null;
        this.filterLearnsetOnly = false;
        buildMoveData(speciesId);
    }

    public void render() {
        if (shouldOpen) {
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
        }

        ImGui.setNextWindowSize(400, 480);
        if (ImGui.beginPopup(POPUP_ID)) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
            }

            renderSearchBar();
            renderFilters();
            ImGui.separator();
            renderMoveList();
            ImGui.endPopup();
        }
    }

    private void renderSearchBar() {
        ImGui.setNextItemWidth(-1);
        if (focusSearchNextFrame) {
            ImGui.setKeyboardFocusHere();
            focusSearchNextFrame = false;
        }
        ImGui.inputText("##MoveSearch", search, ImGuiInputTextFlags.AutoSelectAll);
    }

    private void renderFilters() {
        // Type filter
        ImGui.text("Type:");
        ImGui.sameLine();
        String typeLabel = filterType != null ? filterType.name() : "All";
        ImGui.setNextItemWidth(100);
        if (ImGui.beginCombo("##typeFilter", typeLabel)) {
            if (ImGui.selectable("All", filterType == null)) {
                filterType = null;
            }
            for (PokemonType t : PokemonType.values()) {
                float[] color = PokemonTypeColors.get(t);
                ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, color[0], color[1], color[2], color[3]);
                if (ImGui.selectable(t.name(), t == filterType)) {
                    filterType = t;
                }
                ImGui.popStyleColor();
            }
            ImGui.endCombo();
        }

        // Category filter
        ImGui.sameLine();
        ImGui.text("Cat:");
        ImGui.sameLine();
        String catLabel = filterCategory != null ? filterCategory.name() : "All";
        ImGui.setNextItemWidth(90);
        if (ImGui.beginCombo("##catFilter", catLabel)) {
            if (ImGui.selectable("All", filterCategory == null)) {
                filterCategory = null;
            }
            for (MoveCategory c : MoveCategory.values()) {
                if (ImGui.selectable(c.name(), c == filterCategory)) {
                    filterCategory = c;
                }
            }
            ImGui.endCombo();
        }

        // Learnset toggle
        ImGui.sameLine();
        if (ImGui.checkbox("Learnset", filterLearnsetOnly)) {
            filterLearnsetOnly = !filterLearnsetOnly;
        }
    }

    private void renderMoveList() {
        ImGui.beginChild("MoveList", 0, -1, false);

        if (allMoves == null) {
            ImGui.textDisabled("No Pokedex loaded");
            ImGui.endChild();
            return;
        }

        String filter = search.get().trim();
        boolean searching = !filter.isEmpty();

        // Learnset moves first, then others
        boolean drawnLearnsetHeader = false;
        boolean drawnOtherHeader = false;

        for (MoveEntry entry : allMoves) {
            // Apply filters
            if (filterType != null && entry.type() != filterType) continue;
            if (filterCategory != null && entry.category() != filterCategory) continue;
            if (filterLearnsetOnly && !learnsetMoveIds.contains(entry.moveId())) continue;
            if (searching && !FuzzyMatcher.matches(filter, entry.name())
                    && !FuzzyMatcher.matches(filter, entry.moveId())) continue;

            boolean isLearnset = learnsetMoveIds.contains(entry.moveId());

            // Section headers (only when not filtering learnset-only and not searching)
            if (!filterLearnsetOnly && !searching) {
                if (isLearnset && !drawnLearnsetHeader) {
                    ImGui.textDisabled("-- Learnset --");
                    drawnLearnsetHeader = true;
                }
                if (!isLearnset && !drawnOtherHeader) {
                    if (drawnLearnsetHeader) ImGui.separator();
                    ImGui.textDisabled("-- Other --");
                    drawnOtherHeader = true;
                }
            }

            renderMoveRow(entry, isLearnset);
        }

        ImGui.endChild();
    }

    private void renderMoveRow(MoveEntry entry, boolean isLearnset) {
        ImGui.pushID(entry.moveId());

        boolean disabled = disabledMoveIds.contains(entry.moveId());
        float[] typeColor = PokemonTypeColors.get(entry.type());

        // Type color dot (material icon)
        if (disabled) {
            ImGui.textDisabled(MaterialIcons.Circle);
        } else {
            ImGui.textColored(typeColor[0], typeColor[1], typeColor[2], typeColor[3],
                    MaterialIcons.Circle);
        }
        ImGui.sameLine();

        // Build label
        String displayName = entry.name() != null ? entry.name() : entry.moveId();
        String catStr = entry.category() != null ? entry.category().name() : "";
        String suffix = isLearnset && learnsetLevels.containsKey(entry.moveId())
                ? "  [Lv." + learnsetLevels.get(entry.moveId()) + "]" : "";
        String label = displayName + "  " + catStr + suffix;

        if (disabled) {
            ImGui.textDisabled(label);
        } else {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text,
                    typeColor[0], typeColor[1], typeColor[2], typeColor[3]);
            if (ImGui.selectable(label)) {
                if (onSelect != null) {
                    onSelect.accept(entry.moveId());
                }
                ImGui.closeCurrentPopup();
            }
            ImGui.popStyleColor();
        }

        ImGui.popID();
    }

    private void buildMoveData(String speciesId) {
        Pokedex pokedex = Assets.load(POKEDEX_PATH, Pokedex.class);
        if (pokedex == null) {
            allMoves = null;
            return;
        }

        // Build learnset lookup for the species
        if (speciesId != null && !speciesId.isEmpty()) {
            PokemonSpecies species = pokedex.getSpecies(speciesId);
            if (species != null && species.getLearnset() != null) {
                learnsetMoveIds = new LinkedHashSet<>();
                learnsetLevels = new LinkedHashMap<>();
                for (LearnedMove lm : species.getLearnset()) {
                    learnsetMoveIds.add(lm.getMoveId());
                    learnsetLevels.putIfAbsent(lm.getMoveId(), lm.getLevel());
                }
            } else {
                learnsetMoveIds = Collections.emptySet();
                learnsetLevels = Collections.emptyMap();
            }
        } else {
            learnsetMoveIds = Collections.emptySet();
            learnsetLevels = Collections.emptyMap();
        }

        // Build sorted move list: learnset first, then all others
        List<Move> learnsetMoves = new ArrayList<>();
        List<Move> otherMoves = new ArrayList<>();

        for (Move m : pokedex.getAllMoves()) {
            if (learnsetMoveIds.contains(m.getMoveId())) {
                learnsetMoves.add(m);
            } else {
                otherMoves.add(m);
            }
        }

        learnsetMoves.sort(Comparator.comparing(Move::getMoveId));
        otherMoves.sort(Comparator.comparing(Move::getMoveId));

        allMoves = new ArrayList<>(learnsetMoves.size() + otherMoves.size());
        for (Move m : learnsetMoves) {
            allMoves.add(new MoveEntry(m.getMoveId(), m.getName(), m.getType(), m.getCategory()));
        }
        for (Move m : otherMoves) {
            allMoves.add(new MoveEntry(m.getMoveId(), m.getName(), m.getType(), m.getCategory()));
        }
    }
}
