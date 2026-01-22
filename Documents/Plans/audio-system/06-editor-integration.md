# 6. Editor Integration

This document covers audio functionality specific to the scene editor, including:
- Testing audio clips directly from the inspector
- Preview playback without entering Play Mode
- Audio asset browser panel
- Integration with existing editor patterns

## EditorAudio Static Facade

Parallel to `Audio` for editor-specific operations:

```java
public final class EditorAudio {
    private static EditorAudioContext context;

    private EditorAudio() {}

    public static void initialize(AudioBackend backend) {
        context = new EditorAudioContext(backend);
    }

    public static void destroy() {
        if (context != null) {
            context.destroy();
            context = null;
        }
    }

    // ========== Preview Playback ==========

    /**
     * Play a clip for preview. Doesn't use game channels.
     */
    public static AudioHandle playPreview(AudioClip clip) {
        return context.playPreview(clip, 1.0f);
    }

    public static AudioHandle playPreview(AudioClip clip, float volume) {
        return context.playPreview(clip, volume);
    }

    /**
     * Preview an AudioSource with its configured settings.
     */
    public static void previewSource(AudioSource source) {
        context.previewSource(source);
    }

    public static void stopSourcePreview(AudioSource source) {
        context.stopSourcePreview(source);
    }

    public static boolean isPreviewingSource(AudioSource source) {
        return context.isPreviewingSource(source);
    }

    public static float getSourcePreviewTime(AudioSource source) {
        return context.getSourcePreviewTime(source);
    }

    // ========== Global Control ==========

    public static void stopAllPreviews() {
        context.stopAllPreviews();
    }

    public static void setPreviewVolume(float volume) {
        context.setPreviewVolume(volume);
    }

    public static float getPreviewVolume() {
        return context.getPreviewVolume();
    }

    // ========== Lifecycle Hooks ==========

    /**
     * Called when entering Play Mode - stops all previews.
     */
    public static void onEnterPlayMode() {
        stopAllPreviews();
    }

    /**
     * Called when exiting Play Mode - restores editor audio.
     */
    public static void onExitPlayMode() {
        // Context is already valid, nothing special needed
    }

    // ========== Update ==========

    public static void update(float deltaTime) {
        if (context != null) {
            context.update(deltaTime);
        }
    }
}
```

## EditorAudioContext

Editor-specific context that wraps the audio backend:

```java
public class EditorAudioContext {
    private final AudioBackend backend;
    private final Map<AudioClip, AudioHandle> clipPreviews = new HashMap<>();
    private final Map<AudioSource, AudioHandle> sourcePreviews = new IdentityHashMap<>();

    private float previewVolume = 0.8f;

    public EditorAudioContext(AudioBackend backend) {
        this.backend = backend;
    }

    // ========== Clip Preview ==========

    public AudioHandle playPreview(AudioClip clip, float volume) {
        // Stop existing preview of this clip
        stopPreview(clip);

        float finalVolume = volume * previewVolume;
        AudioHandle handle = backend.play(clip, finalVolume, false);
        clipPreviews.put(clip, handle);
        return handle;
    }

    public void stopPreview(AudioClip clip) {
        AudioHandle handle = clipPreviews.remove(clip);
        if (handle != null && handle.isPlaying()) {
            handle.stop();
        }
    }

    public boolean isPreviewingClip(AudioClip clip) {
        AudioHandle handle = clipPreviews.get(clip);
        return handle != null && handle.isPlaying();
    }

    // ========== AudioSource Preview ==========

    public void previewSource(AudioSource source) {
        stopSourcePreview(source);

        AudioClip clip = source.getClip();
        if (clip == null) return;

        float volume = source.getVolume() * previewVolume;
        float pitch = source.getPitch();
        boolean loop = source.isLoop();

        AudioHandle handle = backend.play(clip, volume, pitch, loop);
        sourcePreviews.put(source, handle);
    }

    public void stopSourcePreview(AudioSource source) {
        AudioHandle handle = sourcePreviews.remove(source);
        if (handle != null && handle.isPlaying()) {
            handle.stop();
        }
    }

    public boolean isPreviewingSource(AudioSource source) {
        AudioHandle handle = sourcePreviews.get(source);
        return handle != null && handle.isPlaying();
    }

    public float getSourcePreviewTime(AudioSource source) {
        AudioHandle handle = sourcePreviews.get(source);
        return handle != null ? handle.getPlaybackTime() : 0f;
    }

    // ========== Global ==========

    public void stopAllPreviews() {
        for (AudioHandle handle : clipPreviews.values()) {
            if (handle.isPlaying()) handle.stop();
        }
        clipPreviews.clear();

        for (AudioHandle handle : sourcePreviews.values()) {
            if (handle.isPlaying()) handle.stop();
        }
        sourcePreviews.clear();
    }

    public void setPreviewVolume(float volume) {
        this.previewVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getPreviewVolume() {
        return previewVolume;
    }

    public void update(float deltaTime) {
        // Clean up finished previews
        clipPreviews.entrySet().removeIf(e -> !e.getValue().isValid());
        sourcePreviews.entrySet().removeIf(e -> !e.getValue().isValid());
    }

    public void destroy() {
        stopAllPreviews();
    }
}
```

## AudioClip Field Editor

Custom field editor for AudioClip fields with play/stop button:

```java
// New file: editor/ui/fields/AudioClipFieldEditor.java
public class AudioClipFieldEditor {

    /**
     * Render an AudioClip field with preview controls.
     * Use this for AudioClip fields on components.
     */
    public static void drawAudioClip(String label, Supplier<AudioClip> getter,
                                      Consumer<AudioClip> setter) {
        AudioClip clip = getter.get();

        // Add play/stop button before the asset picker
        FieldEditorUtils.setNextMiddleContent(() -> {
            renderPreviewButton(clip);
        });

        // Standard asset picker (handles undo, picker popup, etc.)
        AssetEditor.drawAsset(label, "audioClip", getter, setter, AudioClip.class);
    }

    /**
     * Reflection-based variant for component fields.
     */
    public static void drawAudioClip(String label, Component component,
                                      String fieldName, EditorGameObject entity) {
        // Get current value via reflection
        AudioClip clip = (AudioClip) ReflectionUtils.getFieldValue(component, fieldName);

        // Add play/stop button
        FieldEditorUtils.setNextMiddleContent(() -> {
            renderPreviewButton(clip);
        });

        // Standard asset picker with undo
        AssetEditor.drawAsset(label, component, fieldName, AudioClip.class, entity);
    }

    private static void renderPreviewButton(AudioClip clip) {
        if (clip == null) {
            // Disabled button when no clip
            ImGui.beginDisabled();
            ImGui.smallButton(Icons.PLAY);
            ImGui.endDisabled();
            return;
        }

        boolean isPlaying = EditorAudio.isPreviewingClip(clip);
        String icon = isPlaying ? Icons.STOP : Icons.PLAY;
        String tooltip = isPlaying ? "Stop preview" : "Play preview";

        if (ImGui.smallButton(icon + "##audio_preview_" + clip.hashCode())) {
            if (isPlaying) {
                EditorAudio.stopPreview(clip);
            } else {
                EditorAudio.playPreview(clip);
            }
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
    }
}
```

## Integration with FieldEditors Facade

Add AudioClip support to the main facade:

```java
// In FieldEditors.java - add method
public static void drawAudioClip(String label, Supplier<AudioClip> getter,
                                  Consumer<AudioClip> setter) {
    AudioClipFieldEditor.drawAudioClip(label, getter, setter);
}

public static void drawAudioClip(String label, Component component,
                                  String fieldName, EditorGameObject entity) {
    AudioClipFieldEditor.drawAudioClip(label, component, fieldName, entity);
}
```

Update `ReflectionFieldEditor` to use it:

```java
// In ReflectionFieldEditor.drawField()
if (fieldType == AudioClip.class) {
    FieldEditors.drawAudioClip(displayName, component, fieldName, entity);
    return true;
}
```

## AudioSource Inspector Extension

Custom inspector section for AudioSource component with test button:

```java
// New file: editor/ui/inspector/AudioSourceInspector.java
public class AudioSourceInspector {

    /**
     * Render extra controls after standard component fields.
     * Called from ComponentFieldEditor when component is AudioSource.
     */
    public static void renderExtras(AudioSource source, EditorGameObject entity) {
        ImGui.separator();
        ImGui.spacing();

        // Preview section header
        ImGui.textColored(0.6f, 0.8f, 1.0f, 1.0f, "Preview");
        ImGui.sameLine();

        AudioClip clip = source.getClip();
        if (clip == null) {
            ImGui.textDisabled("(No clip assigned)");
            return;
        }

        boolean isPlaying = EditorAudio.isPreviewingSource(source);

        // Play/Stop button
        if (isPlaying) {
            if (ImGui.button(Icons.STOP + " Stop##source_preview")) {
                EditorAudio.stopSourcePreview(source);
            }
        } else {
            if (ImGui.button(Icons.PLAY + " Play##source_preview")) {
                EditorAudio.previewSource(source);
            }
        }

        // Progress bar when playing
        if (isPlaying) {
            ImGui.sameLine();
            float time = EditorAudio.getSourcePreviewTime(source);
            float duration = clip.getDuration();
            float progress = duration > 0 ? time / duration : 0f;

            ImGui.pushItemWidth(-1);
            ImGui.progressBar(progress, -1, 16,
                String.format("%.1f / %.1fs", time, duration));
            ImGui.popItemWidth();
        }

        // Clip info
        ImGui.spacing();
        ImGui.textDisabled("Duration: %.2fs | %s | %d Hz",
            clip.getDuration(),
            clip.isMono() ? "Mono" : "Stereo",
            clip.getSampleRate());
    }
}
```

Register in `ComponentFieldEditor`:

```java
// After rendering standard fields
if (component instanceof AudioSource audioSource) {
    AudioSourceInspector.renderExtras(audioSource, entity);
}
```

## AudioClip Preview Renderer

For asset picker and detail panels:

```java
// New file: editor/assets/AudioClipPreviewRenderer.java
public class AudioClipPreviewRenderer implements AssetPreviewRenderer<AudioClip> {

    @Override
    public Class<AudioClip> getAssetType() {
        return AudioClip.class;
    }

    @Override
    public void render(AudioClip clip, float maxSize) {
        if (clip == null) {
            ImGui.textDisabled("No audio clip");
            return;
        }

        ImGui.beginGroup();

        // Metadata
        ImGui.text(clip.getName());
        ImGui.textDisabled("%.2f seconds", clip.getDuration());
        ImGui.textDisabled("%s | %d Hz",
            clip.isMono() ? "Mono" : "Stereo",
            clip.getSampleRate());

        ImGui.spacing();

        // Playback progress / waveform placeholder
        boolean isPlaying = EditorAudio.isPreviewingClip(clip);
        if (isPlaying) {
            AudioHandle handle = EditorAudio.getPreviewHandle(clip);
            float progress = handle != null
                ? handle.getPlaybackTime() / clip.getDuration()
                : 0f;
            ImGui.progressBar(progress, maxSize, 20, "");
        } else {
            // Static waveform visualization placeholder
            ImVec2 pos = ImGui.getCursorScreenPos();
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.addRectFilled(pos.x, pos.y, pos.x + maxSize, pos.y + 20,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.2f, 0.2f, 1f));
            drawList.addRect(pos.x, pos.y, pos.x + maxSize, pos.y + 20,
                ImGui.colorConvertFloat4ToU32(0.4f, 0.4f, 0.4f, 1f));
            ImGui.dummy(maxSize, 20);
        }

        ImGui.spacing();

        // Play/Stop button
        float buttonWidth = maxSize;
        if (isPlaying) {
            if (ImGui.button(Icons.STOP + " Stop", buttonWidth, 0)) {
                EditorAudio.stopPreview(clip);
            }
        } else {
            if (ImGui.button(Icons.PLAY + " Play", buttonWidth, 0)) {
                EditorAudio.playPreview(clip);
            }
        }

        ImGui.endGroup();
    }
}
```

Register in `AssetPreviewRegistry`:

```java
// In AssetPreviewRegistry static initializer
static {
    register(new SpritePreviewRenderer());
    register(new AnimationPreviewRenderer());
    register(new AudioClipPreviewRenderer());  // NEW
}
```

## Audio Browser Panel

Dedicated panel for browsing and testing audio assets:

```java
// New file: editor/panels/AudioBrowserPanel.java
public class AudioBrowserPanel extends EditorPanel {
    private List<AudioClip> allClips = new ArrayList<>();
    private AudioClip selectedClip;
    private String searchFilter = "";
    private ImString searchBuffer = new ImString(256);

    // Preview state
    private AudioHandle previewHandle;
    private float previewVolume = 0.8f;

    public AudioBrowserPanel(EditorContext context) {
        super("Audio Browser", context);
    }

    @Override
    public void onOpen() {
        refreshClipList();
    }

    private void refreshClipList() {
        allClips = Assets.findAll(AudioClip.class);
        allClips.sort(Comparator.comparing(AudioClip::getName));
    }

    @Override
    public void render() {
        // Toolbar
        renderToolbar();

        ImGui.separator();

        // Split: list on left, preview on right
        float availWidth = ImGui.getContentRegionAvailX();
        float listWidth = availWidth * 0.5f;

        // Clip list
        ImGui.beginChild("clip_list", listWidth, 0, true);
        renderClipList();
        ImGui.endChild();

        ImGui.sameLine();

        // Preview panel
        ImGui.beginChild("clip_preview", 0, 0, true);
        renderPreviewPanel();
        ImGui.endChild();
    }

    private void renderToolbar() {
        // Search
        ImGui.setNextItemWidth(200);
        if (ImGui.inputTextWithHint("##search", "Search...", searchBuffer)) {
            searchFilter = searchBuffer.get().toLowerCase();
        }

        ImGui.sameLine();

        // Refresh button
        if (ImGui.button(Icons.REFRESH + "##refresh")) {
            refreshClipList();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Refresh audio list");
        }

        ImGui.sameLine();

        // Volume slider
        ImGui.setNextItemWidth(100);
        float[] vol = {previewVolume};
        if (ImGui.sliderFloat("##volume", vol, 0f, 1f, "Vol: %.0f%%")) {
            previewVolume = vol[0];
            EditorAudio.setPreviewVolume(previewVolume);
        }
    }

    private void renderClipList() {
        for (AudioClip clip : allClips) {
            // Filter
            if (!searchFilter.isEmpty() &&
                !clip.getName().toLowerCase().contains(searchFilter)) {
                continue;
            }

            boolean isSelected = clip == selectedClip;
            boolean isPlaying = previewHandle != null &&
                                previewHandle.isPlaying() &&
                                selectedClip == clip;

            // Playing indicator
            if (isPlaying) {
                ImGui.textColored(0.4f, 1f, 0.4f, 1f, Icons.VOLUME_HIGH);
            } else {
                ImGui.textDisabled("  ");
            }
            ImGui.sameLine();

            // Selectable
            if (ImGui.selectable(clip.getName(), isSelected)) {
                selectedClip = clip;
            }

            // Double-click to play
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                playPreview(clip);
            }

            // Context menu
            if (ImGui.beginPopupContextItem()) {
                if (ImGui.menuItem(Icons.PLAY + " Play")) {
                    playPreview(clip);
                }
                if (ImGui.menuItem(Icons.STOP + " Stop")) {
                    stopPreview();
                }
                ImGui.separator();
                if (ImGui.menuItem(Icons.FOLDER + " Show in Explorer")) {
                    FileUtils.showInExplorer(clip.getPath());
                }
                ImGui.endPopup();
            }
        }
    }

    private void renderPreviewPanel() {
        if (selectedClip == null) {
            ImGui.textDisabled("Select an audio clip to preview");
            return;
        }

        // Clip name
        ImGui.textColored(1f, 1f, 0.6f, 1f, selectedClip.getName());
        ImGui.separator();

        // Metadata
        ImGui.text("Duration: %.2f seconds", selectedClip.getDuration());
        ImGui.text("Channels: %s", selectedClip.isMono() ? "Mono (3D Ready)" : "Stereo");
        ImGui.text("Sample Rate: %d Hz", selectedClip.getSampleRate());
        ImGui.text("Path: %s", selectedClip.getPath());

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Playback controls
        boolean isPlaying = previewHandle != null && previewHandle.isPlaying();

        if (isPlaying) {
            // Progress bar
            float time = previewHandle.getPlaybackTime();
            float duration = selectedClip.getDuration();
            float progress = duration > 0 ? time / duration : 0f;

            ImGui.progressBar(progress, -1, 20,
                String.format("%.1f / %.1f s", time, duration));

            ImGui.spacing();

            // Stop button
            if (ImGui.button(Icons.STOP + " Stop", -1, 30)) {
                stopPreview();
            }
        } else {
            // Play button
            if (ImGui.button(Icons.PLAY + " Play", -1, 30)) {
                playPreview(selectedClip);
            }
        }
    }

    private void playPreview(AudioClip clip) {
        stopPreview();
        selectedClip = clip;
        previewHandle = EditorAudio.playPreview(clip, previewVolume);
    }

    private void stopPreview() {
        if (previewHandle != null) {
            previewHandle.stop();
            previewHandle = null;
        }
    }

    @Override
    public void onClose() {
        stopPreview();
    }
}
```

## Play Mode Integration

Update `PlayModeController` to handle audio lifecycle:

```java
// In PlayModeController.enterPlayMode()
public void enterPlayMode() {
    // Stop all editor audio previews
    EditorAudio.onEnterPlayMode();

    // ... existing play mode setup ...

    // Initialize game audio context
    Audio.initialize(new DefaultAudioContext(audioBackend, audioConfig));
}

// In PlayModeController.exitPlayMode()
public void exitPlayMode() {
    // Stop all game audio
    Audio.stopAll();
    Audio.destroy();

    // ... existing exit setup ...

    // Editor audio is already available (never destroyed)
    EditorAudio.onExitPlayMode();
}
```

## Editor Initialization

In `EditorApplication` startup:

```java
// During editor initialization
AudioBackend audioBackend = platformFactory.createAudioBackend();
EditorAudio.initialize(audioBackend);

// In editor main loop
while (running) {
    float deltaTime = ...;

    // Update editor audio
    EditorAudio.update(deltaTime);

    // ... rest of editor loop
}

// On shutdown
EditorAudio.destroy();
```

## Editor Audio Settings

Add to editor preferences:

```java
public class EditorAudioSettings {
    private float previewVolume = 0.8f;
    private boolean muteOnDefocus = true;      // Mute when editor loses focus
    private boolean stopOnSelectionChange = false;  // Stop preview when selecting different object

    // Persisted in editor preferences JSON
}
```

Window focus handling:

```java
// In EditorApplication window callbacks
glfwSetWindowFocusCallback(window, (win, focused) -> {
    if (editorAudioSettings.isMuteOnDefocus()) {
        if (focused) {
            EditorAudio.setPreviewVolume(editorAudioSettings.getPreviewVolume());
        } else {
            EditorAudio.setPreviewVolume(0f);
        }
    }
});
```

## Package Structure

```
com.pocket.rpg.audio.editor/
├── EditorAudio.java              # Static facade
├── EditorAudioContext.java       # Editor-specific context
└── EditorAudioSettings.java      # Preferences

com.pocket.rpg.editor.ui.fields/
└── AudioClipFieldEditor.java     # Inspector field editor

com.pocket.rpg.editor.ui.inspector/
└── AudioSourceInspector.java     # AudioSource extras

com.pocket.rpg.editor.assets/
└── AudioClipPreviewRenderer.java # Asset preview

com.pocket.rpg.editor.panels/
└── AudioBrowserPanel.java        # Audio browser panel
```

## Summary: Inspector Audio Testing

After implementation, testing audio from the inspector works as follows:

1. **AudioClip fields**: Show a play button next to the asset picker
2. **AudioSource component**: Shows a "Preview" section with Play/Stop and progress
3. **Asset picker**: Displays audio metadata and playback controls in preview
4. **Audio Browser panel**: Dedicated panel to browse and test all audio assets

All preview functionality:
- Works without entering Play Mode
- Uses separate `EditorAudioContext` that doesn't affect game state
- Automatically stops when entering Play Mode
- Respects editor volume settings
