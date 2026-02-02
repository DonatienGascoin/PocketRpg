package com.pocket.rpg.editor.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneWillChangeEventTest {

    @Test
    void notCancelledByDefault() {
        SceneWillChangeEvent event = new SceneWillChangeEvent();
        assertFalse(event.isCancelled());
    }

    @Test
    void cancelMakesIsCancelledTrue() {
        SceneWillChangeEvent event = new SceneWillChangeEvent();
        event.cancel();
        assertTrue(event.isCancelled());
    }

    @Test
    void subscriberCanCancelViaEventBus() {
        EditorEventBus.reset();
        EditorEventBus.get().subscribe(SceneWillChangeEvent.class, SceneWillChangeEvent::cancel);

        SceneWillChangeEvent event = new SceneWillChangeEvent();
        EditorEventBus.get().publish(event);

        assertTrue(event.isCancelled());
    }
}
