package com.pocket.rpg.resources;

import lombok.Getter;

@Getter
public class LoadOptions {
    public static final LoadOptions DEFAULT_OPTIONS = new LoadOptions();
    private boolean useAssetRoot = true;
    private boolean useCache = true;

    public static LoadOptions defaults() {
        return DEFAULT_OPTIONS;
    }

    public static LoadOptions raw() {
        return new LoadOptions().withoutAssetRoot();
    }

    public static LoadOptions uncached() {
        return new LoadOptions().withoutCache();
    }

    public static LoadOptions rawUncached() {
        return new LoadOptions().withoutAssetRoot().withoutCache();
    }

    public LoadOptions withoutAssetRoot() {
        this.useAssetRoot = false;
        return this;
    }

    public LoadOptions withoutCache() {
        this.useCache = false;
        return this;
    }

}