package com.pocket.rpg.audio.music;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.AudioConfig;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.resources.AssetContext;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneLifecycleListener;
import com.pocket.rpg.scenes.SceneManager;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Random;

/**
 * Manages background music based on scene and zone context.
 * <p>
 * Implements {@link SceneLifecycleListener} to automatically change music
 * when scenes are loaded, using mappings from {@link MusicConfig}.
 * <p>
 * Priority system (highest to lowest):
 * <ol>
 *   <li>MusicTrigger - event-based overrides (combat, cutscenes)</li>
 *   <li>MusicZone - area-based overrides (boss rooms, danger zones)</li>
 *   <li>Scene mapping - from MusicConfig</li>
 *   <li>Default music - fallback from MusicConfig</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>
 * // Initialize once at game start
 * MusicManager.initialize(sceneManager, assetContext);
 *
 * // Music changes automatically on scene load
 * // Or manually control:
 * MusicManager.get().playSceneMusic("overworld");
 * </pre>
 */
public class MusicManager implements SceneLifecycleListener {

    private static MusicManager instance;

    @Getter
    private final MusicConfig config;

    @Getter
    private final AssetContext assets;

    private final Random random = new Random();

    // Current state tracking
    @Getter
    private AudioClip currentSceneMusic;

    @Getter
    private String currentSceneName;

    // Override tracking
    @Getter
    @Setter
    private AudioClip triggerOverride;

    @Getter
    @Setter
    private AudioClip zoneOverride;

    /**
     * Initialize the music manager.
     *
     * @param sceneManager Scene manager to listen to
     * @param assets       Asset context for loading audio clips
     */
    public static void initialize(SceneManager sceneManager, AssetContext assets) {
        if (instance != null) {
            System.out.println("MusicManager already initialized, reinitializing...");
        }

        instance = new MusicManager(assets);

        if (sceneManager != null) {
            sceneManager.addLifecycleListener(instance);
        }

        System.out.println("MusicManager initialized");
    }

    /**
     * Get the singleton instance.
     *
     * @return MusicManager instance, or null if not initialized
     */
    public static MusicManager get() {
        return instance;
    }

    /**
     * Check if initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    private MusicManager(AssetContext assets) {
        this.assets = assets;
        this.config = MusicConfig.load();
    }

    // ========================================================================
    // SCENE LIFECYCLE
    // ========================================================================

    @Override
    public void onSceneLoaded(Scene scene) {
        if (scene == null) return;

        currentSceneName = scene.getName();

        // Clear zone override on scene change
        zoneOverride = null;

        // Don't change music if trigger is active (e.g., combat continues across scenes)
        if (triggerOverride != null) {
            System.out.println("MusicManager: Trigger override active, keeping current music");
            return;
        }

        // Find music for this scene
        MusicConfig.SceneMusicEntry entry = config.findEntryForSceneName(currentSceneName);

        if (entry != null && entry.getTrackPaths() != null && !entry.getTrackPaths().isEmpty()) {
            // Pick a random track from the list
            List<String> tracks = entry.getTrackPaths();
            String trackPath = tracks.get(random.nextInt(tracks.size()));
            AudioClip clip = loadAudioClip(trackPath);

            if (clip != null) {
                currentSceneMusic = clip;
                crossfadeToMusic(clip);
                System.out.println("MusicManager: Playing scene music for '" + currentSceneName + "': " + trackPath);
                return;
            }
        }

        // Fallback to default music
        if (config.getDefaultMusicPath() != null && !config.getDefaultMusicPath().isEmpty()) {
            AudioClip defaultClip = loadAudioClip(config.getDefaultMusicPath());
            if (defaultClip != null) {
                currentSceneMusic = defaultClip;
                crossfadeToMusic(defaultClip);
                System.out.println("MusicManager: Playing default music for '" + currentSceneName + "'");
                return;
            }
        }

        System.out.println("MusicManager: No music configured for scene '" + currentSceneName + "'");
    }

    @Override
    public void onSceneUnloaded(Scene scene) {
        // Optional: could fade out here if desired
    }

    // ========================================================================
    // MANUAL CONTROL
    // ========================================================================

    /**
     * Manually play music for a scene by name.
     *
     * @param sceneName Scene name to find music for
     */
    public void playSceneMusic(String sceneName) {
        MusicConfig.SceneMusicEntry entry = config.findEntryForSceneName(sceneName);

        if (entry != null && entry.getTrackPaths() != null && !entry.getTrackPaths().isEmpty()) {
            List<String> tracks = entry.getTrackPaths();
            String trackPath = tracks.get(random.nextInt(tracks.size()));
            AudioClip clip = loadAudioClip(trackPath);

            if (clip != null) {
                currentSceneMusic = clip;
                crossfadeToMusic(clip);
            }
        }
    }

    /**
     * Set a trigger override (highest priority).
     * Call with null to clear the override and return to scene/zone music.
     *
     * @param music Music to play, or null to clear
     */
    public void setTriggerMusic(AudioClip music) {
        this.triggerOverride = music;

        if (music != null) {
            crossfadeToMusic(music);
        } else {
            // Return to zone or scene music
            restoreMusic();
        }
    }

    /**
     * Set a zone override (medium priority).
     * Call with null to clear the override.
     *
     * @param music Music to play, or null to clear
     */
    public void setZoneMusic(AudioClip music) {
        // Don't override if trigger is active
        if (triggerOverride != null) {
            this.zoneOverride = music;
            return;
        }

        this.zoneOverride = music;

        if (music != null) {
            crossfadeToMusic(music);
        } else {
            // Return to scene music
            restoreMusic();
        }
    }

    /**
     * Restore music based on current priority.
     * Trigger > Zone > Scene > Default
     */
    public void restoreMusic() {
        if (triggerOverride != null) {
            crossfadeToMusic(triggerOverride);
        } else if (zoneOverride != null) {
            crossfadeToMusic(zoneOverride);
        } else if (currentSceneMusic != null) {
            crossfadeToMusic(currentSceneMusic);
        } else if (config.getDefaultMusicPath() != null) {
            AudioClip defaultClip = loadAudioClip(config.getDefaultMusicPath());
            if (defaultClip != null) {
                crossfadeToMusic(defaultClip);
            }
        }
    }

    /**
     * Reload config from disk.
     */
    public void reloadConfig() {
        MusicConfig newConfig = MusicConfig.load();
        config.setDefaultMusicPath(newConfig.getDefaultMusicPath());
        config.setSceneMappings(newConfig.getSceneMappings());
    }

    // ========================================================================
    // INTERNAL
    // ========================================================================

    private void crossfadeToMusic(AudioClip clip) {
        if (!Audio.isInitialized()) return;

        MusicPlayer player = Audio.music();
        if (player == null) return;

        // Get crossfade duration from AudioConfig
        float duration = 2.0f;
        if (Audio.getMixer() != null && Audio.getMixer().getConfig() != null) {
            duration = Audio.getMixer().getConfig().getMusicCrossfadeDuration();
        }

        player.crossfadeTo(clip, duration);
    }

    private AudioClip loadAudioClip(String path) {
        if (path == null || path.isEmpty() || assets == null) {
            return null;
        }

        try {
            return assets.load(path, AudioClip.class);
        } catch (Exception e) {
            System.err.println("MusicManager: Failed to load audio clip: " + path + " - " + e.getMessage());
            return null;
        }
    }
}
