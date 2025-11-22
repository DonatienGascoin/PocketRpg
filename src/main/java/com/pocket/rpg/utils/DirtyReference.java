package com.pocket.rpg.utils;

import lombok.Getter;

public final class DirtyReference<T> {
    @Getter
    private T value;
    private boolean dirty = false;
    private final java.util.function.Consumer<T> onDirtyAction;

    public DirtyReference(T initialValue, java.util.function.Consumer<T> onDirtyAction) {
        this.value = initialValue;
        this.onDirtyAction = onDirtyAction;
    }

    public void markDirty() {
        dirty = true;
    }

    public void set(T newValue) {
        if (!java.util.Objects.equals(value, newValue)) {
            value = newValue;
            dirty = true;
        }
    }

    public void applyIfDirty() {
        if (dirty) {
            onDirtyAction.accept(value);
            dirty = false;
        }
    }
}