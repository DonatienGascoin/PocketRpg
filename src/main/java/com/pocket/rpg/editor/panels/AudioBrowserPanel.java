package com.pocket.rpg.editor.panels;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.audio.sources.AudioHandle;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.resources.Assets;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor panel for browsing and testing audio files.
 * Provides a Unity-style audio browser with playback controls.
 */
public class AudioBrowserPanel extends EditorPanel {

    private static final String PANEL_ID = "audioBrowser";

    private final List<String> audioFiles = new ArrayList<>();
    private final ImString searchFilter = new ImString(256);
    private final float[] previewVolume = {0.8f};

    private String selectedPath = null;
    private AudioClip selectedClip = null;
    private boolean needsRefresh = true;

    public AudioBrowserPanel() {
        super(PANEL_ID, false); // Not open by default
    }

    @Override
    public void render() {
        if (!isOpen()) return;

        ImBoolean open = new ImBoolean(true);
        ImGui.setNextWindowSize(350, 500, imgui.flag.ImGuiCond.FirstUseEver);

        if (ImGui.begin(MaterialIcons.AudioFile + " Audio Browser###" + getPanelId(), open, ImGuiWindowFlags.None)) {
            setContentVisible(true);

            renderToolbar();
            ImGui.separator();
            renderFileList();
            ImGui.separator();
            renderPreviewControls();

        } else {
            setContentVisible(false);
        }
        ImGui.end();

        if (!open.get()) {
            setOpen(false);
        }
    }

    private void renderToolbar() {
        // Refresh button
        if (ImGui.button(MaterialIcons.Refresh)) {
            refreshFileList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh file list");
        }

        ImGui.sameLine();

        // Search filter
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 30);
        if (ImGui.inputTextWithHint("##search", "Search...", searchFilter)) {
            // Filter applied automatically
        }

        ImGui.sameLine();
        if (ImGui.button(MaterialIcons.Clear + "##clearSearch")) {
            searchFilter.set("");
        }
    }

    private void renderFileList() {
        if (needsRefresh) {
            refreshFileList();
        }

        float listHeight = ImGui.getContentRegionAvailY() - 100; // Reserve space for preview controls
        if (ImGui.beginChild("AudioFileList", 0, listHeight, true)) {
            String filter = searchFilter.get().toLowerCase();

            for (String path : audioFiles) {
                if (!filter.isEmpty() && !path.toLowerCase().contains(filter)) {
                    continue;
                }

                boolean isSelected = path.equals(selectedPath);
                boolean isPlaying = selectedClip != null && EditorAudio.isPreviewingClip(selectedClip) && path.equals(selectedPath);

                // Icon based on state
                String icon = isPlaying ? MaterialIcons.VolumeUp : MaterialIcons.AudioFile;

                // Highlight playing items
                if (isPlaying) {
                    ImGui.pushStyleColor(ImGuiCol.Text, EditorColors.SUCCESS[0], EditorColors.SUCCESS[1], EditorColors.SUCCESS[2], EditorColors.SUCCESS[3]);
                }

                if (ImGui.selectable(icon + " " + getDisplayName(path), isSelected, ImGuiSelectableFlags.SpanAllColumns)) {
                    selectFile(path);
                }

                if (isPlaying) {
                    ImGui.popStyleColor();
                }

                // Double-click to play
                if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                    playSelected();
                }

                // Tooltip with full path
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(path);
                    if (selectedClip != null && path.equals(selectedPath)) {
                        ImGui.separator();
                        ImGui.text(String.format("Duration: %.2f s", selectedClip.getDuration()));
                        ImGui.text(String.format("Channels: %s", selectedClip.isMono() ? "Mono" : "Stereo"));
                        ImGui.text(String.format("Sample Rate: %d Hz", selectedClip.getSampleRate()));
                    }
                    ImGui.endTooltip();
                }
            }
        }
        ImGui.endChild();
    }

    private void renderPreviewControls() {
        // Preview section header
        ImGui.text(MaterialIcons.PlayCircle + " Preview");

        // Selected file info
        if (selectedClip != null) {
            EditorColors.textColored(EditorColors.SUCCESS, getDisplayName(selectedPath));

            boolean isPlaying = EditorAudio.isPreviewingClip(selectedClip);

            // Play/Stop buttons
            if (isPlaying) {
                EditorColors.pushDangerButton();

                if (ImGui.button(MaterialIcons.Stop + " Stop", 80, 0)) {
                    EditorAudio.stopPreview(selectedClip);
                }

                EditorColors.popButtonColors();
            } else {
                EditorColors.pushSuccessButton();

                if (ImGui.button(MaterialIcons.PlayArrow + " Play", 80, 0)) {
                    playSelected();
                }

                EditorColors.popButtonColors();
            }

            // Playback progress
            if (isPlaying) {
                ImGui.sameLine();
                AudioHandle handle = EditorAudio.getPreviewHandle(selectedClip);
                if (handle != null) {
                    float progress = handle.getPlaybackTime() / selectedClip.getDuration();
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    ImGui.progressBar(progress, -1, 0,
                            String.format("%.1f / %.1f s", handle.getPlaybackTime(), selectedClip.getDuration()));
                }
            }

            // Clip info
            ImGui.textDisabled(String.format("%.2fs | %s | %d Hz",
                    selectedClip.getDuration(),
                    selectedClip.isMono() ? "Mono" : "Stereo",
                    selectedClip.getSampleRate()));

        } else {
            ImGui.textDisabled("No audio selected");
        }

        ImGui.spacing();

        // Volume slider
        ImGui.text(MaterialIcons.VolumeUp + " Volume");
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.sliderFloat("##previewVolume", previewVolume, 0.0f, 1.0f, "%.0f%%")) {
            EditorAudio.setPreviewVolume(previewVolume[0]);
        }

        // Stop all button
        if (ImGui.button(MaterialIcons.StopCircle + " Stop All")) {
            EditorAudio.stopAllPreviews();
        }
    }

    private void selectFile(String path) {
        if (path.equals(selectedPath)) {
            return;
        }

        selectedPath = path;
        try {
            selectedClip = Assets.load(path, AudioClip.class);
        } catch (Exception e) {
            System.err.println("Failed to load audio: " + path + " - " + e.getMessage());
            selectedClip = null;
        }
    }

    private void playSelected() {
        if (selectedClip == null) {
            return;
        }

        if (EditorAudio.isPreviewingClip(selectedClip)) {
            EditorAudio.stopPreview(selectedClip);
        } else {
            EditorAudio.playPreview(selectedClip, previewVolume[0]);
        }
    }

    private void refreshFileList() {
        audioFiles.clear();
        audioFiles.addAll(Assets.scanByType(AudioClip.class));
        audioFiles.sort(String::compareToIgnoreCase);
        needsRefresh = false;
    }

    private String getDisplayName(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Request a refresh of the file list on next render.
     */
    public void requestRefresh() {
        needsRefresh = true;
    }
}
