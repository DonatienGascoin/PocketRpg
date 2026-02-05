package com.pocket.rpg.editor.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RegistriesRefreshRequestEventTest {

    @BeforeEach
    void setUp() {
        EditorEventBus.reset();
    }

    @AfterEach
    void tearDown() {
        EditorEventBus.reset();
    }

    @Test
    void event_canBePublishedAndReceived() {
        AtomicBoolean received = new AtomicBoolean(false);
        EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class, event -> {
            received.set(true);
        });

        EditorEventBus.get().publish(new RegistriesRefreshRequestEvent());

        assertTrue(received.get());
    }

    @Test
    void event_isReceivedByMultipleSubscribers() {
        AtomicBoolean subscriber1 = new AtomicBoolean(false);
        AtomicBoolean subscriber2 = new AtomicBoolean(false);

        EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class, event -> subscriber1.set(true));
        EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class, event -> subscriber2.set(true));

        EditorEventBus.get().publish(new RegistriesRefreshRequestEvent());

        assertTrue(subscriber1.get());
        assertTrue(subscriber2.get());
    }

    @Test
    void event_implementsEditorEvent() {
        RegistriesRefreshRequestEvent event = new RegistriesRefreshRequestEvent();

        assertInstanceOf(EditorEvent.class, event);
    }

    @Test
    void event_subscriberReceivesCorrectEventInstance() {
        AtomicReference<RegistriesRefreshRequestEvent> received = new AtomicReference<>();
        EditorEventBus.get().subscribe(RegistriesRefreshRequestEvent.class, received::set);

        RegistriesRefreshRequestEvent published = new RegistriesRefreshRequestEvent();
        EditorEventBus.get().publish(published);

        assertSame(published, received.get());
    }
}
