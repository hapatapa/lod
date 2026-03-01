package com.hapatapa.lod.engine;

public enum LODDistance {
    HIGH_FIDELITY(32, 1, 5), // Max 32 chunks to stay vibrant
    BALANCED(24, 1, 10), // Very tight for high FPS
    PERFORMANCE(16, 2, 20);

    private final int radius;
    private final int ratio;
    private final int updateInterval;

    LODDistance(int radius, int ratio, int updateInterval) {
        this.radius = radius;
        this.ratio = ratio;
        this.updateInterval = updateInterval;
    }

    public int getRadius() {
        return radius;
    }

    public int getRatio() {
        return ratio;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }
}
