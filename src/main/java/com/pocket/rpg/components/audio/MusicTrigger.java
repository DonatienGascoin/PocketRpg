package com.pocket.rpg.components.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.HideInInspector;
import lombok.Getter;
import lombok.Setter;

/**
 * Event-based music trigger for combat, cutscenes, and other special moments.
 * <p>
 * MusicTrigger has the highest priority in the music system - it overrides
 * both MusicZone and scene-based music from MusicManager.
 * <p>
 * Usage from other components:
 * <pre>
 * // Reference the trigger in inspector
 * {@literal @}Getter @Setter
 * private MusicTrigger combatMusic;
 *
 * // In your combat system
 * public void onCombatStart() {
 *     combatMusic.trigger();
 * }
 *
 * public void onCombatEnd() {
 *     combatMusic.restore();
 * }
 * </pre>
 *
 * @see MusicManager
 * @see MusicZone
 */
@ComponentMeta(category = "Audio")
public class MusicTrigger extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * Music to play when triggered.
     */
    @Getter
    @Setter
    private AudioClip triggerMusic;

    /**
     * Whether to restore previous music when calling restore().
     * If false, restore() does nothing.
     */
    @Getter
    @Setter
    private boolean restoreOnEnd = true;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private boolean isActive = false;

    public MusicTrigger() {
        // Required for serialization
    }

    // ========================================================================
    // COMPONENT LIFECYCLE
    // ========================================================================

    @Override
    protected void onDisable() {
        // Restore music if we were active
        if (isActive) {
            restore();
        }
    }

    @Override
    protected void onDestroy() {
        if (isActive) {
            isActive = false;
            if (MusicManager.isInitialized()) {
                MusicManager.get().setTriggerMusic(null);
            }
        }
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Trigger this music. Overrides zone and scene music.
     */
    public void trigger() {
        if (isActive) {
            return; // Already active
        }

        if (triggerMusic == null) {
            System.out.println("MusicTrigger: No music assigned");
            return;
        }

        if (!MusicManager.isInitialized()) {
            System.out.println("MusicTrigger: MusicManager not initialized");
            return;
        }

        isActive = true;
        MusicManager.get().setTriggerMusic(triggerMusic);
        System.out.println("MusicTrigger: Triggered music");
    }

    /**
     * Restore previous music (zone or scene).
     * Only has effect if restoreOnEnd is true.
     */
    public void restore() {
        if (!isActive) {
            return; // Not active
        }

        isActive = false;

        if (!restoreOnEnd) {
            return;
        }

        if (!MusicManager.isInitialized()) {
            return;
        }

        MusicManager.get().setTriggerMusic(null);
        System.out.println("MusicTrigger: Restored previous music");
    }

    /**
     * Force stop this trigger without restoring.
     */
    public void forceStop() {
        if (isActive) {
            isActive = false;
            if (MusicManager.isInitialized()) {
                MusicManager.get().setTriggerMusic(null);
            }
        }
    }

    /**
     * @return true if this trigger is currently active
     */
    public boolean isActive() {
        return isActive;
    }
}
