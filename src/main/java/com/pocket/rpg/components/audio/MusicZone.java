package com.pocket.rpg.components.audio;

import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.audio.music.MusicManager;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Area-based music override that changes background music when the listener enters.
 * <p>
 * When the AudioListener enters this zone's radius, the music crossfades to the
 * zone's music. When exiting, it returns to the previous music (scene default
 * or lower priority zone).
 * <p>
 * Priority system:
 * <ul>
 *   <li>Higher priority zones override lower priority ones</li>
 *   <li>When overlapping zones have equal priority, the most recently entered wins</li>
 *   <li>MusicTrigger always overrides MusicZone</li>
 * </ul>
 *
 * @see MusicManager
 * @see MusicTrigger
 * @see AmbientZone
 */
@ComponentMeta(category = "Audio")
public class MusicZone extends Component {

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /**
     * Music to play when inside this zone.
     */
    @Getter
    @Setter
    private AudioClip zoneMusic;

    /**
     * Zone radius in world units.
     */
    @Getter
    @Setter
    private float radius = 10.0f;

    /**
     * Priority for overlapping zones (higher wins).
     */
    @Getter
    @Setter
    private int priority = 0;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private boolean isListenerInside = false;

    @HideInInspector
    private static MusicZone activeZone = null;

    public MusicZone() {
        // Required for serialization
    }

    // ========================================================================
    // COMPONENT LIFECYCLE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!MusicManager.isInitialized()) {
            return;
        }

        boolean wasInside = isListenerInside;
        isListenerInside = isListenerInZone();

        if (isListenerInside && !wasInside) {
            onEnterZone();
        } else if (!isListenerInside && wasInside) {
            onExitZone();
        }
    }

    @Override
    protected void onDisable() {
        // Clean up if we were the active zone
        if (activeZone == this) {
            onExitZone();
        }
    }

    @Override
    protected void onDestroy() {
        if (activeZone == this) {
            activeZone = null;
            MusicManager.get().setZoneMusic(null);
        }
    }

    // ========================================================================
    // ZONE LOGIC
    // ========================================================================

    private void onEnterZone() {
        // Only override if higher priority or no active zone
        if (activeZone == null || priority >= activeZone.priority) {
            activeZone = this;
            MusicManager.get().setZoneMusic(zoneMusic);
            System.out.println("MusicZone: Entered zone, playing: " +
                    (zoneMusic != null ? zoneMusic.toString() : "null"));
        }
    }

    private void onExitZone() {
        if (activeZone == this) {
            activeZone = null;
            MusicManager.get().setZoneMusic(null);
            System.out.println("MusicZone: Exited zone, restoring previous music");
        }
    }

    private boolean isListenerInZone() {
        Vector3f listenerPos = AudioListener.getListenerPosition();
        Vector3f zonePos = getTransform().getWorldPosition();

        float dx = listenerPos.x - zonePos.x;
        float dy = listenerPos.y - zonePos.y;
        float distSq = dx * dx + dy * dy;

        return distSq <= radius * radius;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * @return true if this zone is currently active (listener inside and highest priority)
     */
    public boolean isActive() {
        return activeZone == this;
    }

    /**
     * @return true if listener is inside this zone's radius
     */
    public boolean isListenerInside() {
        return isListenerInside;
    }

    /**
     * @return the currently active MusicZone, or null
     */
    public static MusicZone getActiveZone() {
        return activeZone;
    }

    // ========================================================================
    // GIZMOS
    // ========================================================================

    @Override
    public void onDrawGizmosSelected(GizmoContext ctx) {
        Transform transform = ctx.getTransform();
        if (transform == null) return;

        Vector3f pos = transform.getWorldPosition();

        // Draw radius circle (different color from AmbientZone)
        ctx.setColor(GizmoColors.MUSIC_ZONE);
        ctx.setThickness(2.0f);
        ctx.drawCircle(pos.x, pos.y, radius);

        // Draw center point
        ctx.setColor(GizmoColors.MUSIC_ZONE);
        float markerSize = ctx.getHandleSize(10);
        ctx.drawDiamondFilled(pos.x, pos.y, markerSize);

        // Draw music note icon indicator
        ctx.setColor(GizmoColors.MUSIC_ZONE);
        ctx.drawCircleFilled(pos.x, pos.y, markerSize * 0.5f);
    }
}
