package com.pocket.rpg.editor;

import com.pocket.rpg.editor.events.EditorEventBus;
import com.pocket.rpg.editor.events.EditorModeChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorModeManagerTest {

    private EditorModeManager manager;

    @BeforeEach
    void setUp() {
        EditorEventBus.reset();
        manager = new EditorModeManager();
    }

    // ========================================================================
    // INITIAL STATE
    // ========================================================================

    @Nested
    class InitialState {

        @Test
        void defaultModeIsScene() {
            assertEquals(EditorMode.SCENE, manager.getCurrentMode());
        }

        @Test
        void isSceneMode_trueByDefault() {
            assertTrue(manager.isSceneMode());
        }

        @Test
        void isPlayMode_falseByDefault() {
            assertFalse(manager.isPlayMode());
        }

        @Test
        void isPrefabEditMode_falseByDefault() {
            assertFalse(manager.isPrefabEditMode());
        }
    }

    // ========================================================================
    // MODE TRANSITIONS
    // ========================================================================

    @Nested
    class ModeTransitions {

        @Test
        void setMode_changesToPlay() {
            manager.setMode(EditorMode.PLAY);

            assertEquals(EditorMode.PLAY, manager.getCurrentMode());
            assertTrue(manager.isPlayMode());
            assertFalse(manager.isSceneMode());
            assertFalse(manager.isPrefabEditMode());
        }

        @Test
        void setMode_changesToPrefabEdit() {
            manager.setMode(EditorMode.PREFAB_EDIT);

            assertEquals(EditorMode.PREFAB_EDIT, manager.getCurrentMode());
            assertTrue(manager.isPrefabEditMode());
            assertFalse(manager.isSceneMode());
            assertFalse(manager.isPlayMode());
        }

        @Test
        void setMode_backToScene() {
            manager.setMode(EditorMode.PLAY);
            manager.setMode(EditorMode.SCENE);

            assertEquals(EditorMode.SCENE, manager.getCurrentMode());
            assertTrue(manager.isSceneMode());
        }
    }

    // ========================================================================
    // EVENT FIRING
    // ========================================================================

    @Nested
    class EventFiring {

        @Test
        void setMode_firesEditorModeChangedEvent() {
            List<EditorModeChangedEvent> events = new ArrayList<>();
            EditorEventBus.get().subscribe(EditorModeChangedEvent.class, events::add);

            manager.setMode(EditorMode.PLAY);

            assertEquals(1, events.size());
            assertEquals(EditorMode.SCENE, events.get(0).previousMode());
            assertEquals(EditorMode.PLAY, events.get(0).newMode());
        }

        @Test
        void setMode_noEventWhenSameMode() {
            List<EditorModeChangedEvent> events = new ArrayList<>();
            EditorEventBus.get().subscribe(EditorModeChangedEvent.class, events::add);

            manager.setMode(EditorMode.SCENE); // already SCENE

            assertTrue(events.isEmpty());
        }

        @Test
        void setMode_firesMultipleEventsOnMultipleTransitions() {
            List<EditorModeChangedEvent> events = new ArrayList<>();
            EditorEventBus.get().subscribe(EditorModeChangedEvent.class, events::add);

            manager.setMode(EditorMode.PLAY);
            manager.setMode(EditorMode.SCENE);

            assertEquals(2, events.size());
            assertEquals(EditorMode.SCENE, events.get(0).previousMode());
            assertEquals(EditorMode.PLAY, events.get(0).newMode());
            assertEquals(EditorMode.PLAY, events.get(1).previousMode());
            assertEquals(EditorMode.SCENE, events.get(1).newMode());
        }
    }
}
