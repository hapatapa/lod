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
                // ### Update: Logic and Unit Fix
                // - **Unit Mismatch**: Corrected height-to-decimeter conversion (10.0 units = 1
                // block).
                // - **Stalactite Prevention**:
                // - Neighborhood checks now use **surface heights** (`heights[]`) instead of
                // floors (`mins[]`), which prevents a single hole from causing all surrounding
                // subdivisions to stretch down.
                // - Added a **12-block thickness clamp** for non-water materials. This ensures
                // that even on extreme cliffs or floating islands, the "skirt" doesn't become
                // an unnaturally long pillar.
                //
                // This combination provides a solid, weighted look for terrain while
                // maintaining visual sanity in caves and near drop-offs.

                int bx = cx << 4;
                int bz = cz << 4;

                int areaSizeX = 16 / subdivX;
                int areaSizeZ = 16 / subdivZ;
                int totalBlocks = subdivX * subdivZ;

                BlockData[] blocks = new BlockData[totalBlocks];
                int[] heights = new int[totalBlocks];
                int[] biomeColors = new int[totalBlocks];
                float[] thicknesses = new float[totalBlocks];
                int[] mins = new int[totalBlocks];

                // Pass 1: Gather raw data and determine surface blocks/heights
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
                            mins[idx] = avgHeight;
                            biomeColors[idx] = 0x3F76E4;
                            continue;
                        }

                        Material bestMaterial = materialCounts.entrySet().stream()
                                .max(Map.Entry.comparingByValue())
                                .map(Map.Entry::getKey)
                                .orElse(Material.GRASS_BLOCK);

                        // Polish: Water replacement and cleanup
                        if (isWaterLike(bestMaterial)) {
                            bestMaterial = Material.WATER;
                            surfaceY = Math.max(surfaceY, 62); // Water level normalization
                        } else if (bestMaterial == Material.AIR || bestMaterial == Material.CAVE_AIR) {
                            bestMaterial = Material.GRASS_BLOCK;
                        }

                        blocks[idx] = bestMaterial.createBlockData();
                        heights[idx] = surfaceY; // Use dominant height for top of LOD
                        mins[idx] = minSubdivY;
                        biomeColors[idx] = getBlendedBiomeColor(world, bx + (sx * areaSizeX) + (areaSizeX / 2),
                                surfaceY, bz + (sz * areaSizeZ) + (areaSizeZ / 2),
                                bestMaterial);
                    }
                }

                // Pass 2: Generalized Gap-Filling across subdivisions and chunk boundaries
                for (int sx = 0; sx < subdivX; sx++) {
                    for (int sz = 0; sz < subdivZ; sz++) {
                        int idx = sx * subdivZ + sz;
                        int h = heights[idx];
                        int minForThis = mins[idx];

                        // Internal Gap Check (intra-chunk neighbors)
                        // Use surface heights (heights[]) not floor heights (mins[]) to prevent deep
                        // gaps from spreading
                        if (sx > 0)
                            minForThis = Math.min(minForThis, heights[(sx - 1) * subdivZ + sz]);
                        if (sx < subdivX - 1)
                            minForThis = Math.min(minForThis, heights[(sx + 1) * subdivZ + sz]);
                        if (sz > 0)
                            minForThis = Math.min(minForThis, heights[sx * subdivZ + (sz - 1)]);
                        if (sz < subdivZ - 1)
                            minForThis = Math.min(minForThis, heights[sx * subdivZ + (sz + 1)]);

                        // Real Chunk Boundary Check (inter-chunk)
                        if (sx == 0 && world.isChunkLoaded(cx - 1, cz)) {
                            minForThis = Math.min(minForThis,
                                    scanLowestAtBoundary(world, bx - 1, bz + (sz * areaSizeZ), 1, areaSizeZ));
                        }
                        if (sx == subdivX - 1 && world.isChunkLoaded(cx + 1, cz)) {
                            minForThis = Math.min(minForThis,
                                    scanLowestAtBoundary(world, bx + 16, bz + (sz * areaSizeZ), 1, areaSizeZ));
                        }
                        if (sz == 0 && world.isChunkLoaded(cx, cz - 1)) {
                            minForThis = Math.min(minForThis,
                                    scanLowestAtBoundary(world, bx + (sx * areaSizeX), bz - 1, areaSizeX, 1));
                        }
                        if (sz == subdivZ - 1 && world.isChunkLoaded(cx, cz + 1)) {
                            minForThis = Math.min(minForThis,
                                    scanLowestAtBoundary(world, bx + (sx * areaSizeX), bz + 16, areaSizeX, 1));
                        }

                        // Protect against "stalactites": For non-water blocks, clamp the gap-filling
                        // skirt to 3 blocks
                        if (!isWaterLike(blocks[idx].getMaterial())) {
                            minForThis = Math.max(minForThis, h - 3);
                        } else {
                            // Water can go deeper for waterfalls, but still clamp to something sane
                            minForThis = Math.max(minForThis, h - 32);
                        }

                        // Units: 10.0f = 1 block. (h - minForThis) is in blocks.
                        thicknesses[idx] = Math.max(10.0f, ((h - minForThis) * 10.0f) + 1.25f);
                    }
                }

                return new LODSignature(blocks, heights, biomeColors, thicknesses, subdivX, subdivZ);
            } catch (Exception e) {
                return null; // Return null instead of procedural to avoid weird void chunks
            }
        });
    }

    private int scanLowestAtBoundary(World world, int startX, int startZ, int sizeX, int sizeZ) {
        int min = 320;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                min = Math.min(min, world.getHighestBlockAt(startX + x, startZ + z, HeightMap.WORLD_SURFACE).getY());
            }
        }
        return min;
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
