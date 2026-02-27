package com.pocket.rpg.editor.panels;

import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.utils.FuzzyMatcher;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A floating search popup for quickly finding and opening assets by name.
 * Activated via Ctrl+P. Supports fuzzy matching and keyboard navigation.
 */
public class AssetQuickSearchPopup {

    private static final String POPUP_ID = "##assetQuickSearch";
    private static final int MAX_RESULTS = 15;

    private final ImString searchText = new ImString(256);
    private boolean shouldOpen;
    private boolean focusSearchNextFrame;
    private List<String> allAssetPaths;
    private List<SearchResult> filteredResults = new ArrayList<>();
    private int favoritesEndIndex = -1; // index after last favorite (-1 = no favorites)
    private int selectedIndex;
    private Consumer<String> onSelect;
    private List<String> favoritePaths = List.of();
    private Set<String> favoritePathsSet = Set.of();
    private List<String> recentPaths = List.of();

    private record SearchResult(String path, Class<?> type, int score) {}

    public void open(Consumer<String> onSelect) {
        open(onSelect, List.of(), List.of());
    }

    public void open(Consumer<String> onSelect, List<String> recentPaths) {
        open(onSelect, recentPaths, List.of());
    }

    public void open(Consumer<String> onSelect, List<String> recentPaths, List<String> favoritePaths) {
        this.onSelect = onSelect;
        this.recentPaths = recentPaths != null ? recentPaths : List.of();
        this.favoritePaths = favoritePaths != null ? favoritePaths : List.of();
        this.favoritePathsSet = new HashSet<>(this.favoritePaths);
        shouldOpen = true;
        searchText.set("");
        selectedIndex = 0;
        refreshAssetList();
        filterResults();
    }

    public void render() {
        if (shouldOpen) {
            float centerX = ImGui.getIO().getDisplaySizeX() / 2;
            float centerY = ImGui.getIO().getDisplaySizeY() / 3;
            ImGui.setNextWindowPos(centerX, centerY, ImGuiCond.Appearing, 0.5f, 0.5f);
            ImGui.setNextWindowSize(450, 350);
            ImGui.openPopup(POPUP_ID);
            shouldOpen = false;
            focusSearchNextFrame = true;
        }

        if (ImGui.beginPopup(POPUP_ID)) {
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                ImGui.closeCurrentPopup();
                ImGui.endPopup();
                return;
            }

            // Search input
            if (focusSearchNextFrame) {
                ImGui.setKeyboardFocusHere();
                focusSearchNextFrame = false;
            }
            ImGui.setNextItemWidth(-1);
            if (ImGui.inputTextWithHint("##quickSearch",
                    MaterialIcons.Search + " Type to search assets...", searchText)) {
                filterResults();
                selectedIndex = 0;
            }

            // Keyboard navigation
            if (ImGui.isKeyPressed(ImGuiKey.DownArrow) && !filteredResults.isEmpty()) {
                selectedIndex = Math.min(selectedIndex + 1, filteredResults.size() - 1);
            }
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
                selectedIndex = Math.max(selectedIndex - 1, 0);
            }
            if (ImGui.isKeyPressed(ImGuiKey.Enter) && !filteredResults.isEmpty()) {
                int idx = Math.min(selectedIndex, filteredResults.size() - 1);
                confirmSelection(filteredResults.get(idx));
                ImGui.closeCurrentPopup();
            }

            ImGui.separator();

            // Results list
            if (ImGui.beginChild("##quickSearchResults")) {
                for (int i = 0; i < filteredResults.size(); i++) {
                    if (i == favoritesEndIndex && favoritesEndIndex > 0) {
                        ImGui.separator();
                    }
                    SearchResult r = filteredResults.get(i);
                    boolean isSelected = (i == selectedIndex);
                    String icon = r.type() != null
                            ? Assets.getIconCodepoint(r.type())
                            : MaterialIcons.InsertDriveFile;
                    String star = favoritePathsSet.contains(r.path()) ? MaterialIcons.Star + " " : "";
                    String display = icon + " " + star + r.path();

                    if (ImGui.selectable(display + "##qsr_" + i, isSelected)) {
                        confirmSelection(r);
                        ImGui.closeCurrentPopup();
                    }
                    if (isSelected) {
                        ImGui.setScrollHereY();
                    }
                }

                if (filteredResults.isEmpty()) {
                    String query = searchText.get().trim();
                    if (query.isEmpty()) {
                        ImGui.textDisabled("No assets found. Try refreshing the sidebar.");
                    } else {
                        ImGui.textDisabled("No matches for \"" + query + "\"");
                    }
                }
            }
            ImGui.endChild();
            ImGui.endPopup();
        }
    }

    private void refreshAssetList() {
        allAssetPaths = Assets.scanAll();
    }

    private void filterResults() {
        String query = searchText.get().trim();
        if (query.isEmpty()) {
            // Show favorites first, then recent assets, then remaining assets
            List<SearchResult> results = new ArrayList<>();
            Set<String> added = new HashSet<>();
            for (String p : favoritePaths) {
                if (added.add(p) && results.size() < MAX_RESULTS) {
                    results.add(new SearchResult(p, Assets.getTypeForPath(p), 0));
                }
            }
            favoritesEndIndex = !results.isEmpty() ? results.size() : -1;
            for (String p : recentPaths) {
                if (added.add(p) && results.size() < MAX_RESULTS) {
                    results.add(new SearchResult(p, Assets.getTypeForPath(p), 0));
                }
            }
            for (String p : allAssetPaths) {
                if (added.add(p) && results.size() < MAX_RESULTS) {
                    results.add(new SearchResult(p, Assets.getTypeForPath(p), 0));
                }
            }
            filteredResults = results;
            return;
        }

        favoritesEndIndex = -1;
        filteredResults = allAssetPaths.stream()
                .map(path -> {
                    int s = FuzzyMatcher.score(query, path);
                    return s > 0 ? new SearchResult(path, Assets.getTypeForPath(path), s) : null;
                })
                .filter(r -> r != null)
                .sorted(Comparator.comparingInt(SearchResult::score).reversed())
                .limit(MAX_RESULTS)
                .toList();
    }

    private void confirmSelection(SearchResult result) {
        if (onSelect != null) {
            onSelect.accept(result.path());
        }
    }
}
