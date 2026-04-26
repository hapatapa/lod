package com.hapatapa.lod.data;

import org.bukkit.block.data.BlockData;

public record LODSignature(BlockData[] blockData, BlockData[] bottomBlockData, int[] sampledHeights, int[] bottomHeights, int[] occlusionHeights, int[] biomeColors, int[] bottomBiomeColors,
        float[] thicknesses, int subdivX, int subdivZ, int ratio, int cx, int cz) {
}
