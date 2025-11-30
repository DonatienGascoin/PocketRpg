package com.pocket.rpg.input;

import lombok.Getter;

public enum InputAxis {

    Horizontal(new AxisConfig(KeyCode.D, KeyCode.A)
            .withAltKeys(KeyCode.RIGHT, KeyCode.LEFT)),
    Vertical(new AxisConfig(KeyCode.W, KeyCode.S)
            .withAltKeys(KeyCode.UP, KeyCode.DOWN));

    @Getter
    private final AxisConfig axisConfig;

    InputAxis(AxisConfig axisConfig) {
        this.axisConfig = axisConfig;
    }
}
