package com.pocket.rpg.editor.ui.fields;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.editor.EditorAudio;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Field editor for AudioClip assets with play/stop preview button.
 * Uses AssetEditor's shared popup for asset selection.
 */
public final class AudioClipFieldEditor {

    private AudioClipFieldEditor() {}

    /**
     * Draws an AudioClip field with play button and asset picker.
     */
    public static boolean drawAudioClip(String label, Component component, String fieldName,
                                         EditorGameObject entity) {
        AudioClip clip = (AudioClip) ComponentReflectionUtils.getFieldValue(component, fieldName);
        String display = FieldEditorUtils.getAssetDisplayName(clip);

        ImGui.pushID(fieldName);
        FieldEditorContext.pushOverrideStyle(fieldName);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                // Asset picker button first
                if (ImGui.smallButton("...")) {
                    Object oldValue = ComponentReflectionUtils.getFieldValue(component, fieldName);
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    AssetEditor.openPicker(AudioClip.class, currentPath, selectedAsset -> {
                        UndoManager.getInstance().execute(
                                new SetComponentFieldCommand(
                                        component,
                                        fieldName,
                                        oldValue,
                                        selectedAsset,
                                        FieldEditorContext.getEntity()
                                )
                        );
                        FieldEditorContext.markFieldOverridden(fieldName, selectedAsset);
                        var scene = FieldEditorContext.getCurrentScene();
                        if (scene != null) scene.markDirty();
                    });
                }

                // Play/Stop button
                ImGui.sameLine();
                drawPlayButton(clip);

                // Asset name (truncated if needed)
                ImGui.sameLine();
                String truncated = truncateAssetName(display);
                if (clip != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, truncated);
                } else {
                    ImGui.textDisabled(truncated);
                }
                // Tooltip with full name if truncated
                if (!truncated.equals(display) && ImGui.isItemHovered()) {
                    ImGui.setTooltip(display);
                }
            });

            FieldEditorContext.popOverrideStyle();
            FieldEditorUtils.drawResetButtonIfNeeded(component, fieldName);

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Draws an AudioClip field using getter/setter pattern.
     */
    public static boolean drawAudioClip(String label, String key,
                                         Supplier<AudioClip> getter, Consumer<AudioClip> setter) {
        AudioClip clip = getter.get();
        String display = FieldEditorUtils.getAssetDisplayName(clip);

        ImGui.pushID(key);

        try {
            FieldEditorUtils.inspectorRow(label, () -> {
                // Asset picker button first
                if (ImGui.smallButton("...")) {
                    AudioClip oldValue = getter.get();
                    String currentPath = oldValue != null ? Assets.getPathForResource(oldValue) : null;

                    AssetEditor.openPicker(AudioClip.class, currentPath, selectedAsset -> {
                        @SuppressWarnings("unchecked")
                        AudioClip typedAsset = (AudioClip) selectedAsset;

                        UndoManager.getInstance().execute(
                                new SetterUndoCommand<>(setter, oldValue, typedAsset, "Change " + label)
                        );
                        var scene = FieldEditorContext.getCurrentScene();
                        if (scene != null) scene.markDirty();
                    });
                }

                // Play/Stop button
                ImGui.sameLine();
                drawPlayButton(clip);

                // Asset name (truncated if needed)
                ImGui.sameLine();
                String truncated = truncateAssetName(display);
                if (clip != null) {
                    ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, truncated);
                } else {
                    ImGui.textDisabled(truncated);
                }
                // Tooltip with full name if truncated
                if (!truncated.equals(display) && ImGui.isItemHovered()) {
                    ImGui.setTooltip(display);
                }
            });

        } finally {
            ImGui.popID();
        }
        return false;
    }

    /**
     * Draws a play/stop button for the given clip.
     * Can be used standalone in custom layouts (e.g., MusicConfigTab track list).
     *
     * @param clip The audio clip (can be null, shows disabled button)
     */
    public static void drawPlayButton(AudioClip clip) {
        if (clip == null) {
            // Disabled play button when no clip
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.3f, 0.3f, 0.3f, 1.0f);
            ImGui.smallButton(MaterialIcons.PlayArrow);
            ImGui.popStyleColor(3);
            return;
        }

        boolean isPlaying = EditorAudio.isPreviewingClip(clip);

        if (isPlaying) {
            // Stop button (red)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.3f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.4f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.7f, 0.2f, 0.2f, 1.0f);

            if (ImGui.smallButton(MaterialIcons.Stop)) {
                EditorAudio.stopPreview(clip);
            }

            ImGui.popStyleColor(3);
        } else {
            // Play button (green)
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 0.3f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 0.4f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.5f, 0.2f, 1.0f);

            if (ImGui.smallButton(MaterialIcons.PlayArrow)) {
                EditorAudio.playPreview(clip);
            }

            ImGui.popStyleColor(3);
        }

        // Tooltip with clip info
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            if (isPlaying) {
                ImGui.text("Click to stop preview");
            } else {
                ImGui.text("Click to preview");
            }
            ImGui.separator();
            ImGui.textDisabled(String.format("Duration: %.2fs", clip.getDuration()));
            ImGui.textDisabled(String.format("Channels: %s", clip.isMono() ? "Mono" : "Stereo"));
            ImGui.textDisabled(String.format("Sample Rate: %d Hz", clip.getSampleRate()));
            ImGui.endTooltip();
        }
    }

    /**
     * Truncates asset name to fit in available space.
     */
    private static String truncateAssetName(String name) {
        if (name == null) return "(none)";

        // Calculate available width (approximate)
        float availWidth = ImGui.getContentRegionAvailX();
        float textWidth = ImGui.calcTextSize(name).x;

        if (textWidth <= availWidth) {
            return name;
        }

        // Truncate with ellipsis
        String ellipsis = "...";

        // Binary search for max chars that fit
        int maxChars = name.length();
        while (maxChars > 0) {
            String truncated = name.substring(0, maxChars) + ellipsis;
            if (ImGui.calcTextSize(truncated).x <= availWidth) {
                return truncated;
            }
            maxChars--;
        }

        return ellipsis;
    }
}
