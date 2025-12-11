package com.pocket.rpg.resources;

/**
 * Builder for configuring the asset system.
 * Provides fluent API for setting configuration options.
 */
public class ConfigBuilder {
    
    private final AssetContext context;
    private String assetRoot;
    private Integer cacheSize;
    private ErrorMode errorMode;
    private Boolean enableStatistics;
    
    /**
     * Creates a config builder for the given context.
     * 
     * @param context Asset context to configure
     */
    public ConfigBuilder(AssetContext context) {
        this.context = context;
    }
    
    /**
     * Sets the asset root directory.
     * All relative paths will be resolved from this directory.
     * 
     * @param assetRoot Root directory for assets (e.g., "assets/")
     * @return This builder for chaining
     */
    public ConfigBuilder setAssetRoot(String assetRoot) {
        this.assetRoot = assetRoot;
        return this;
    }
    
    /**
     * Sets the maximum cache size.
     * 
     * @param cacheSize Maximum number of cached resources (0 = unlimited)
     * @return This builder for chaining
     */
    public ConfigBuilder setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
        return this;
    }
    
    /**
     * Sets the error handling mode.
     * 
     * @param errorMode Error mode (USE_PLACEHOLDER or THROW_EXCEPTION)
     * @return This builder for chaining
     */
    public ConfigBuilder setErrorMode(ErrorMode errorMode) {
        this.errorMode = errorMode;
        return this;
    }
    
    /**
     * Enables or disables statistics tracking.
     * 
     * @param enable true to enable statistics
     * @return This builder for chaining
     */
    public ConfigBuilder enableStatistics(boolean enable) {
        this.enableStatistics = enable;
        return this;
    }
    
    /**
     * Applies the configuration to the context.
     * Only non-null values are applied.
     */
    public void apply() {
        if (context instanceof AssetManager) {
            AssetManager manager = (AssetManager) context;
            
            if (assetRoot != null) {
                manager.setAssetRoot(assetRoot);
            }
            if (cacheSize != null) {
                manager.getCache().setMaxSize(cacheSize);
            }
            if (errorMode != null) {
                manager.setErrorMode(errorMode);
            }
            if (enableStatistics != null) {
                manager.setStatisticsEnabled(enableStatistics);
            }
        }
    }
}
