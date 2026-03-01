package com.hapatapa.lod.data;

import org.bukkit.block.data.BlockData;

public record LODSignature(BlockData[] blockData, int[] sampledHeights, int[] biomeColors, float[] thicknesses,
        int subdivX, int subdivZ) {
}
