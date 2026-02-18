package com.pocket.rpg.editor.panels.config;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.music.MusicConfig;
import com.pocket.rpg.audio.music.MusicConfig.SceneMusicEntry;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.AssetPickerPopup;
import com.pocket.rpg.editor.ui.fields.AudioClipFieldEditor;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.ConfigListCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.SceneData;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration tab for scene-to-music mappings.
 * Uses asset pickers for scene and audio clip selection.
 */
public class MusicConfigTab implements ConfigTab {

    private static final String SCENES_DIRECTORY = "gameData/scenes";

    private final Runnable markDirty;
    private MusicConfig config;

    private final AssetPickerPopup assetPicker = new AssetPickerPopup();

    // Scene list cache
    private List<String> cachedScenePaths = new ArrayList<>();
    private String[] cachedSceneNames = new String[0];
    private long lastSceneScanTime = 0;
    private static final long SCENE_SCAN_INTERVAL_MS = 2000;

    public MusicConfigTab(Runnable markDirty) {
        this.markDirty = markDirty;
        this.config = MusicConfig.load();
        refreshSceneList();
    }

    @Override
    public String getTabName() {
        return "Music";
    }

    @Override
    public void save() {
        config.save();
    }

    @Override
    public void revert() {
        config = MusicConfig.load();
    }

    @Override
    public void resetToDefaults() {
        config.setDefaultMusicPath(null);
        config.setSceneMappings(new ArrayList<>());
    }

    @Override
    public void renderContent() {
        ImGui.pushID("MusicTab");

        // Default Music section
        if (ImGui.collapsingHeader("Default Music", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            drawDefaultMusic();
            ImGui.unindent();
        }

        // Scene Mappings section
        if (ImGui.collapsingHeader("Scene Mappings", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.indent();
            drawSceneMappings();
            ImGui.unindent();
        }

        // Render asset picker popup
        assetPicker.render();

        ImGui.popID();
    }

    private void drawDefaultMusic() {
        String defaultPath = config.getDefaultMusicPath();
        String displayName = getAudioDisplayName(defaultPath);

        ImGui.text("Default Music");
        ImGui.sameLine();

        // Picker button (with undo support)
        if (ImGui.smallButton("...##defaultPicker")) {
            String oldPath = config.getDefaultMusicPath();
            assetPicker.open(AudioClip.class, defaultPath, asset -> {
                String newPath = asset != null ? Assets.getPathForResource(asset) : null;
                UndoManager.getInstance().execute(new SetterUndoCommand<>(
                        path -> { config.setDefaultMusicPath(path); markDirty.run(); },
                        oldPath,
                        newPath,
                        "Change default music"
                ));
            });
        }

        // Play button
        ImGui.sameLine();
        AudioClip defaultClip = loadAudioClipForPreview(defaultPath);
        AudioClipFieldEditor.drawPlayButton(defaultClip);

        // Display name (truncated to fit with clear button)
        ImGui.sameLine();
        if (defaultPath != null && !defaultPath.isEmpty()) {
            // Reserve space for clear button + spacing
            float buttonWidth = ImGui.calcTextSize(MaterialIcons.Clear).x + ImGui.getStyle().getFramePaddingX() * 2 + 8;
            String truncated = truncateForButton(displayName, buttonWidth);
            EditorColors.textColored(EditorColors.SUCCESS, truncated);
            if (!truncated.equals(displayName) && ImGui.isItemHovered()) {
                ImGui.setTooltip(displayName);
            }
        } else {
            ImGui.textDisabled("(none)");
        }

        // Clear button (with undo support)
        if (defaultPath != null && !defaultPath.isEmpty()) {
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Clear + "##clearDefault")) {
                String oldPath = config.getDefaultMusicPath();
                UndoManager.getInstance().execute(new SetterUndoCommand<>(
                        path -> { config.setDefaultMusicPath(path); markDirty.run(); },
                        oldPath,
                        null,
                        "Clear default music"
                ));
            }
        }

        ImGui.spacing();
        ImGui.textDisabled("Played when no scene-specific music is configured");
    }

    private void drawSceneMappings() {
        List<SceneMusicEntry> mappings = config.getSceneMappings();
        if (mappings == null) {
            mappings = new ArrayList<>();
            config.setSceneMappings(mappings);
        }

        // Draw each entry
        int indexToRemove = -1;
        for (int i = 0; i < mappings.size(); i++) {
            SceneMusicEntry entry = mappings.get(i);
            ImGui.pushID(i);

            // Scene header with remove button
            String sceneName = getSceneDisplayName(entry.getScenePath());
            boolean nodeOpen = ImGui.treeNodeEx("scene_node",
                    ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.AllowItemOverlap,
                    sceneName);

            // Remove button on same line
            ImGui.sameLine(ImGui.getContentRegionAvailX() - 25);
            EditorColors.pushDangerButton();
            if (ImGui.smallButton(MaterialIcons.Delete + "##removeEntry")) {
                indexToRemove = i;
            }
            EditorColors.popButtonColors();

            if (nodeOpen) {
                // Scene picker
                drawScenePicker(entry, i);

                // Track list
                drawTrackList(entry, i);

                ImGui.treePop();
            }

            ImGui.popID();
            ImGui.separator();
        }

        // Remove entry if requested (with undo support)
        if (indexToRemove >= 0) {
            UndoManager.getInstance().execute(
                    ConfigListCommand.remove(mappings, indexToRemove, "scene mapping", markDirty)
            );
        }

        // Add new entry button (with undo support)
        ImGui.spacing();
        if (ImGui.button(MaterialIcons.Add + " Add Scene Mapping")) {
            SceneMusicEntry newEntry = new SceneMusicEntry();
            newEntry.setTrackPaths(new ArrayList<>());
            UndoManager.getInstance().execute(
                    ConfigListCommand.add(mappings, newEntry, "scene mapping", markDirty)
            );
        }
    }

    private void drawScenePicker(SceneMusicEntry entry, int entryIndex) {
        // Refresh scene list periodically
        refreshSceneListIfNeeded();

        String scenePath = entry.getScenePath();

        ImGui.text("Scene:");
        ImGui.sameLine();

        // Get already used scenes (excluding current entry)
        List<String> usedScenes = getUsedScenePaths(entryIndex);

        // Build available scenes list (excluding already used)
        List<String> availablePaths = new ArrayList<>();
        List<String> availableNames = new ArrayList<>();

        for (int i = 0; i < cachedScenePaths.size(); i++) {
            String path = cachedScenePaths.get(i);
            if (!usedScenes.contains(path)) {
                availablePaths.add(path);
                availableNames.add(cachedSceneNames[i]);
            }
        }

        // Find current selection index in available list
        int currentIndex = 0; // 0 = "(select scene)"
        if (scenePath != null && !scenePath.isEmpty()) {
            for (int i = 0; i < availablePaths.size(); i++) {
                if (availablePaths.get(i).equals(scenePath)) {
                    currentIndex = i + 1; // +1 because of "(select scene)" at index 0
                    break;
                }
            }
            // If current scene not in available list, it might be already assigned
            // Keep showing it but mark as already used
            if (currentIndex == 0 && cachedScenePaths.contains(scenePath)) {
                // Scene is used elsewhere - add it back temporarily for display
                availablePaths.add(0, scenePath);
                availableNames.add(0, getSceneDisplayName(scenePath) + " (duplicate)");
                currentIndex = 1;
            }
        }

        // Build combo items: "(select scene)" + available scene names
        String[] comboItems = new String[availableNames.size() + 1];
        comboItems[0] = "(select scene)";
        for (int i = 0; i < availableNames.size(); i++) {
            comboItems[i + 1] = availableNames.get(i);
        }

        ImInt selectedIndex = new ImInt(currentIndex);
        ImGui.setNextItemWidth(200);
        if (ImGui.combo("##scenePicker" + entryIndex, selectedIndex, comboItems)) {
            String oldScenePath = entry.getScenePath();
            String newScenePath = selectedIndex.get() == 0 ? null : availablePaths.get(selectedIndex.get() - 1);
            UndoManager.getInstance().execute(new SetterUndoCommand<>(
                    path -> { entry.setScenePath(path); markDirty.run(); },
                    oldScenePath,
                    newScenePath,
                    "Change scene mapping"
            ));
        }
    }

    /**
     * Returns list of scene paths already used in mappings, excluding the given entry index.
     */
    private List<String> getUsedScenePaths(int excludeIndex) {
        List<String> used = new ArrayList<>();
        List<SceneMusicEntry> mappings = config.getSceneMappings();
        if (mappings == null) return used;

        for (int i = 0; i < mappings.size(); i++) {
            if (i == excludeIndex) continue;
            String path = mappings.get(i).getScenePath();
            if (path != null && !path.isEmpty()) {
                used.add(path);
            }
        }
        return used;
    }

    private void refreshSceneListIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastSceneScanTime > SCENE_SCAN_INTERVAL_MS) {
            refreshSceneList();
        }
    }

    private void refreshSceneList() {
        lastSceneScanTime = System.currentTimeMillis();
        cachedScenePaths = Assets.scanByType(SceneData.class, SCENES_DIRECTORY);
        cachedSceneNames = new String[cachedScenePaths.size()];

        for (int i = 0; i < cachedScenePaths.size(); i++) {
            cachedSceneNames[i] = getSceneDisplayName(cachedScenePaths.get(i));
        }
    }

    private void drawTrackList(SceneMusicEntry entry, int entryIndex) {
        List<String> tracks = entry.getTrackPaths();
        if (tracks == null) {
            tracks = new ArrayList<>();
            entry.setTrackPaths(tracks);
        }

        ImGui.text("Tracks:");
        ImGui.indent();

        // Draw each track
        int trackToRemove = -1;
        for (int t = 0; t < tracks.size(); t++) {
            String trackPath = tracks.get(t);
            String displayName = getAudioDisplayName(trackPath);

            ImGui.pushID(t);

            // Picker button
            final int trackIndex = t;
            if (ImGui.smallButton("...##trackPicker")) {
                assetPicker.open(AudioClip.class, trackPath, asset -> {
                    String newPath = asset != null ? Assets.getPathForResource(asset) : null;
                    if (newPath != null) {
                        entry.getTrackPaths().set(trackIndex, newPath);
                        markDirty.run();
                    }
                });
            }

            // Play button
            ImGui.sameLine();
            AudioClip clip = loadAudioClipForPreview(trackPath);
            AudioClipFieldEditor.drawPlayButton(clip);

            // Display name (truncated to fit with remove button)
            ImGui.sameLine();
            // Reserve space for remove button + spacing
            float buttonWidth = ImGui.calcTextSize(MaterialIcons.Clear).x + ImGui.getStyle().getFramePaddingX() * 2 + 8;
            if (trackPath != null && !trackPath.isEmpty()) {
                String truncated = truncateForButton(displayName, buttonWidth);
                EditorColors.textColored(EditorColors.SUCCESS, truncated);
                if (!truncated.equals(displayName) && ImGui.isItemHovered()) {
                    ImGui.setTooltip(displayName);
                }
            } else {
                ImGui.textDisabled("(select track)");
            }

            // Remove button
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.Clear + "##removeTrack")) {
                trackToRemove = t;
            }

            ImGui.popID();
        }

        // Remove track if requested (with undo support)
        if (trackToRemove >= 0) {
            UndoManager.getInstance().execute(
                    ConfigListCommand.remove(tracks, trackToRemove, "track", markDirty)
            );
        }

        // Add track button (with undo support)
        if (ImGui.smallButton(MaterialIcons.Add + " Add Track##" + entryIndex)) {
            assetPicker.open(AudioClip.class, null, asset -> {
                String newPath = asset != null ? Assets.getPathForResource(asset) : null;
                if (newPath != null) {
                    UndoManager.getInstance().execute(
                            ConfigListCommand.add(entry.getTrackPaths(), newPath, "track", markDirty)
                    );
                }
            });
        }

        ImGui.unindent();

        if (tracks.size() > 1) {
            ImGui.textDisabled("Multiple tracks: one chosen randomly");
        }
    }

    private AudioClip loadAudioClipForPreview(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return Assets.load(path, AudioClip.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String getAudioDisplayName(String path) {
        if (path == null || path.isEmpty()) {
            return "(none)";
        }
        // Extract filename
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String getSceneDisplayName(String path) {
        if (path == null || path.isEmpty()) {
            return "(no scene)";
        }
        // Extract filename without extension
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        int dotIndex = name.lastIndexOf('.');
        return dotIndex >= 0 ? name.substring(0, dotIndex) : name;
    }

    /**
     * Truncates a display name to fit available width, reserving space for a trailing button.
     * Adds "..." suffix if truncation is needed.
     *
     * @param name The display name to potentially truncate
     * @param reservedWidth Width to reserve for trailing elements (button + spacing)
     * @return The truncated name, or original if it fits
     */
    private String truncateForButton(String name, float reservedWidth) {
        if (name == null) return "(none)";

        float availWidth = ImGui.getContentRegionAvailX() - reservedWidth;
        if (availWidth <= 0) return "...";

        float textWidth = ImGui.calcTextSize(name).x;
        if (textWidth <= availWidth) {
            return name;
        }

        // Truncate with ellipsis
        String ellipsis = "...";
        float ellipsisWidth = ImGui.calcTextSize(ellipsis).x;

        // Find max characters that fit
        for (int i = name.length() - 1; i > 0; i--) {
            String truncated = name.substring(0, i) + ellipsis;
            if (ImGui.calcTextSize(truncated).x <= availWidth) {
                return truncated;
            }
        }

        return ellipsis;
    }
}
