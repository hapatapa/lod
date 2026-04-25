package com.hapatapa.lod.engine;

public enum LODQuality {
    EXTREME(3, 3),
    MEDIUM(2, 2),
    LOW(1, 1);

    private final int subdivX;
    private final int subdivZ;

    LODQuality(int subdivX, int subdivZ) {
        this.subdivX = subdivX;
        this.subdivZ = subdivZ;
    }

    public int getSubdivX() {
        return subdivX;
    }

    public int getSubdivZ() {
        return subdivZ;
    }
}
