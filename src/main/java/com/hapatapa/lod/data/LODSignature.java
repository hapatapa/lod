package com.hapatapa.lod.data;

import org.bukkit.block.data.BlockData;

public record LODSignature(BlockData[] blockData, int[] sampledHeights, int[] occlusionHeights, int[] biomeColors,
        float[] thicknesses, int subdivX, int subdivZ, int ratio, int cx, int cz) {
}
