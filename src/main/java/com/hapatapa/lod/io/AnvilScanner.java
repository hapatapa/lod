package com.hapatapa.lod.io;

import com.hapatapa.lod.data.LODSignature;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AnvilScanner {

    public CompletableFuture<LODSignature> scanChunk(World world, int cx, int cz, int subdivX, int subdivZ,
            boolean allowGeneration) {
        if (Math.abs(cx) > 1875000 || Math.abs(cz) > 1875000) {
            return CompletableFuture.completedFuture(generateProcedural(cx, cz, subdivX, subdivZ));
        }

        return world.getChunkAtAsync(cx, cz, allowGeneration).thenApply(chunk -> {
            try {
                if (chunk == null) {
                    // If we can't get the chunk and generation is disabled, we return null to
                    // signal "nothing here"
                    // instead of falling back to procedural grass which causes "floating land" in
                    // the void.
                    return null;
                }

                int bx = cx << 4;
                int bz = cz << 4;

                int areaSizeX = 16 / subdivX;
                int areaSizeZ = 16 / subdivZ;
                int totalBlocks = subdivX * subdivZ;

                BlockData[] blocks = new BlockData[totalBlocks];
                int[] heights = new int[totalBlocks];
                int[] biomeColors = new int[totalBlocks];
                float[] thicknesses = new float[totalBlocks];

                for (int sx = 0; sx < subdivX; sx++) {
                    for (int sz = 0; sz < subdivZ; sz++) {
                        double totalHeight = 0;
                        Map<Material, Integer> materialCounts = new HashMap<>();
                        Map<Integer, Integer> heightCounts = new HashMap<>();
                        int samples = 0;
                        int minSubdivY = 320;

                        // Scan the area designated for this subdivision
                        for (int dx = 0; dx < areaSizeX; dx++) {
                            for (int dz = 0; dz < areaSizeZ; dz++) {
                                int x = sx * areaSizeX + dx;
                                int z = sz * areaSizeZ + dz;
                                Block b = world.getHighestBlockAt(bx + x, bz + z, HeightMap.WORLD_SURFACE);
                                int hy = b.getY();
                                totalHeight += hy;
                                minSubdivY = Math.min(minSubdivY, hy);
                                heightCounts.merge(hy, 1, Integer::sum);

                                Material m = b.getType();
                                if (!isExcluded(m)) {
                                    materialCounts.merge(m, 1, Integer::sum);
                                }
                                samples++;
                            }
                        }

                        int avgHeight = (int) Math.round(totalHeight / samples);
                        int surfaceY = heightCounts.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(avgHeight);

                        int idx = sx * subdivZ + sz;

                        if (avgHeight <= world.getMinHeight() + 5) {
                            blocks[idx] = getProceduralMaterial(cx, cz, avgHeight).createBlockData();
                            heights[idx] = avgHeight;
                            biomeColors[idx] = 0x3F76E4;
                            thicknesses[idx] = 10.0f;
                            continue;
                        }

                        Material bestMaterial = materialCounts.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(Material.GRASS_BLOCK);

                        float thickness = 10.0f;

                        // Polish: Water replacement and cleanup
                        if (isWaterLike(bestMaterial)) {
                            bestMaterial = Material.WATER;
                            avgHeight = surfaceY; // PRIORITIZE FLAT SURFACE
                            if (avgHeight < 62)
                                avgHeight = 62;

                            // GAP FILLING: If multiple heights exist, we scale down to min
                            if (avgHeight > minSubdivY) {
                                thickness = (avgHeight - minSubdivY) + 1.25f; // Extra padding
                            }
                        } else if (bestMaterial == Material.AIR || bestMaterial == Material.CAVE_AIR) {
                            bestMaterial = Material.GRASS_BLOCK;
                        }

                        blocks[idx] = bestMaterial.createBlockData();
                        heights[idx] = avgHeight;
                        thicknesses[idx] = thickness;
                        biomeColors[idx] = getBlendedBiomeColor(world, bx + (sx * areaSizeX) + (areaSizeX / 2),
                                avgHeight, bz + (sz * areaSizeZ) + (areaSizeZ / 2),
                                bestMaterial);
                    }
                }
                return new LODSignature(blocks, heights, biomeColors, thicknesses, subdivX, subdivZ);
            } catch (Exception e) {
                return null; // Return null instead of procedural to avoid weird void chunks
            }
        });
    }

    private boolean isExcluded(Material mat) {
        String name = mat.name();
        return name.contains("FLOWER") ||
                name.contains("MUSHROOM") ||
                mat == Material.AIR ||
                mat == Material.CAVE_AIR ||
                name.contains("SAPLING");
    }

    private boolean isWaterLike(Material mat) {
        return mat == Material.WATER || mat == Material.SEAGRASS || mat == Material.TALL_SEAGRASS ||
                mat == Material.KELP || mat == Material.KELP_PLANT || mat == Material.BUBBLE_COLUMN;
    }

    private LODSignature generateProcedural(int cx, int cz, int subdivX, int subdivZ) {
        int totalBlocks = subdivX * subdivZ;
        BlockData[] blocks = new BlockData[totalBlocks];
        int[] heights = new int[totalBlocks];
        int[] biomeColors = new int[totalBlocks];
        float[] thicknesses = new float[totalBlocks];

        int areaSizeX = 16 / subdivX;
        int areaSizeZ = 16 / subdivZ;

        for (int sx = 0; sx < subdivX; sx++) {
            for (int sz = 0; sz < subdivZ; sz++) {
                // Procedural generation uses the center of each subdivision for noise
                double px = cx * 16 + (sx * areaSizeX) + (16.0);
                double pz = cz * 16 + (sz * areaSizeZ) + (16.0);

                double noise = Math.sin(px * 0.05) * Math.cos(pz * 0.05);
                int y = 63 + (int) (noise * 5);

                int idx = sx * subdivZ + sz;
                blocks[idx] = getProceduralMaterial(cx, cz, y).createBlockData();
                heights[idx] = y;
                thicknesses[idx] = 10.0f;
                biomeColors[idx] = (y > 62) ? 0x79C05A : 0x3F76E4;
            }
        }

        return new LODSignature(blocks, heights, biomeColors, thicknesses, subdivX, subdivZ);
    }

    private int getBlendedBiomeColor(World world, int x, int y, int z, Material mat) {
        boolean isWater = isWaterLike(mat);
        int r = 0, g = 0, b = 0;
        int radius = 6; // 13x13 grid for intense blending
        int samples = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int color = getSingleBiomeColor(world, x + dx, y, z + dz, isWater);
                r += (color >> 16) & 0xFF;
                g += (color >> 8) & 0xFF;
                b += color & 0xFF;
                samples++;
            }
        }

        return ((r / samples) << 16) | ((g / samples) << 8) | (b / samples);
    }

    private int getSingleBiomeColor(World world, int x, int y, int z, boolean isWater) {
        String biome = world.getBiome(x, y, z).getKey().getKey().toUpperCase();
        if (isWater) {
            if (biome.contains("SWAMP"))
                return 0x617B64;
            if (biome.contains("WARM_OCEAN"))
                return 0x43D5EE;
            if (biome.contains("LUKEWARM_OCEAN"))
                return 0x45ADF2;
            if (biome.contains("COLD_OCEAN"))
                return 0x3D57D6;
            if (biome.contains("FROZEN_OCEAN"))
                return 0x3938C9;
            return 0x3F76E4; // Default water
        } else {
            if (biome.contains("JUNGLE"))
                return 0x59C93C;
            if (biome.contains("SWAMP"))
                return 0x6A7039;
            if (biome.contains("DESERT") || biome.contains("SAVANNA"))
                return 0xBFB755;
            if (biome.contains("BADLANDS"))
                return 0x90814D;
            if (biome.contains("DARK_FOREST"))
                return 0x507A32;
            if (biome.contains("TAIGA"))
                return 0x86B783;
            if (biome.contains("SNOWY") || biome.contains("ICE"))
                return 0x80B497;
            if (biome.contains("MUSHROOM"))
                return 0x55C93F;
            return 0x79C05A; // Default plains grass
        }
    }

    private Material getProceduralMaterial(int cx, int cz, int y) {
        return (y > 62) ? Material.GRASS_BLOCK : Material.WATER;
    }
}
