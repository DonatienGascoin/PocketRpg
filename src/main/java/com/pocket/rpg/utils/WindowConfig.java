package com.pocket.rpg.utils;

import lombok.Getter;

@Getter
public class WindowConfig {

    private String title = "Pocket Rpg";
    private int initialWidth = 640;
    private int initialHeight = 480;
    private boolean fullscreen = false;
    private boolean vsync = false;
    private ICallback callback = new DefaultCallback();
}
