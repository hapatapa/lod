package com.hapatapa.lod.engine;

import com.hapatapa.lod.data.LODSignature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.*;

public class PlayerSession {

    private static final boolean USE_TELEPORTATION = true; // Toggle for teleportation vs remove/respawn
    private double cullingCosine = Math.cos(Math.toRadians(160.0 / 2.0));

    private final Player player;
    private final LODManager manager;
    private LODDistance distance = LODDistance.HIGH_FIDELITY;
    private LODQuality quality = LODQuality.LOW;
    private float fov = 70.0f;

    private final Map<Long, VirtualDisplayEntity[]> activeEntities = new HashMap<>();
    private final Map<Long, Location[]> entityAnchors = new HashMap<>();
    private final Map<Long, int[]> entityHeights = new HashMap<>();
    private final Queue<VirtualDisplayEntity> entityPool = new LinkedList<>();

    private int tickCounter = 0;
    private final List<Long> scanQueue = new ArrayList<>();
    private int scanIndex = 0;

    private final List<Long> anchorRefreshQueue = new ArrayList<>();
    private int anchorRefreshIndex = 0;

    private int lastCX = Integer.MAX_VALUE;
    private int lastCZ = Integer.MAX_VALUE;
    private float lastYaw = 0f;
    private float lastPitch = 0f;

    public PlayerSession(Player player, LODManager manager) {
        this.player = player;
        this.manager = manager;
    }

    public void update() {
        if (!player.isOnline())
            return;

        tickCounter++;
        Location pLoc = player.getLocation();
        int pCX = pLoc.getBlockX() >> 4;
        int pCZ = pLoc.getBlockZ() >> 4;
        float yaw = pLoc.getYaw();
        float pitch = pLoc.getPitch();

        // More frequent rotation-based updates (0.5 degree threshold)
        boolean rotated = Math.abs(yaw - lastYaw) > 0.5f || Math.abs(pitch - lastPitch) > 0.5f;

        if (pCX != lastCX || pCZ != lastCZ || rotated || (tickCounter & 7) == 0) {
            recalculateDesiredChunks(pLoc, pCX, pCZ);
            lastCX = pCX;
            lastCZ = pCZ;
            lastYaw = yaw;
            lastPitch = pitch;
        }

        // Aggressive Budgeting for visual stability:
        // 1. Give massive priority to existing LODs that need an anchor refresh (up to
        // 500 chunks).
        // 2. Use a high total budget for new scans (up to 1000 total processed chunks).
        int totalBudget = 1000;
        processCleanupQueue(200);

        // Sync the refresh queue with active entities if it becomes significantly
        // disparate or empty
        if (anchorRefreshQueue.size() != activeEntities.size() || tickCounter % 100 == 0) {
            anchorRefreshQueue.clear();
            anchorRefreshQueue.addAll(activeEntities.keySet());
            // We do NOT reset anchorRefreshIndex here to maintain fair rotation
        }

        int refreshed = processAnchorRefresh(500); // 500 chunks per tick for anchors
        int remainingBudget = totalBudget - refreshed;

        if (remainingBudget > 0) {
            processScanQueue(remainingBudget);
        }
    }

    private final List<Long> cleanupQueue = new ArrayList<>();

    private void recalculateDesiredChunks(Location pLoc, int pCX, int pCZ) {
        World world = player.getWorld();
        int radius = distance.getRadius();
        int radiusSq = radius * radius;

        int serverVD = Bukkit.getViewDistance();
        int worldVD = world.getViewDistance();
        int clientVD = player.getClientViewDistance();
        if (clientVD == 0)
            clientVD = 10;

        float renderDist = (float) Math.min(clientVD, Math.min(serverVD, worldVD));
        double renderDistThresholdSq = (renderDist * 16.0) * (renderDist * 16.0);

        Set<Long> nextDesired = new HashSet<>();
        List<Long> newTargets = new ArrayList<>();

        double px = pLoc.getX();
        double py = pLoc.getY();
        double pz = pLoc.getZ();

        Vector viewDir = pLoc.getDirection();
        double vx = viewDir.getX();
        double vy = viewDir.getY();
        double vz = viewDir.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > radiusSq)
                    continue;

                int ratio;
                if (distSq < 484) // 22 * 22
                    ratio = 1;
                else if (distSq < 1764) // 42 * 42
                    ratio = 2;
                else
                    ratio = 4;

                int shift = ratio >> 1; // 1->0, 2->1, 4->2
                int alignedCX = ((pCX + dx) >> shift) << shift;
                int alignedCZ = ((pCZ + dz) >> shift) << shift;

                int blockSize = ratio << 4;
                double minX = (alignedCX << 4);
                double maxX = minX + blockSize;
                double minZ = (alignedCZ << 4);
                double maxZ = minZ + blockSize;

                double centerX = minX + (blockSize * 0.5);
                double centerZ = minZ + (blockSize * 0.5);
                // Chunk spanning bedrock to sky
                double centerY = 128.0;
                double halfY = 192.0;
                double halfX = blockSize * 0.5;
                double halfZ = blockSize * 0.5;

                // 3D Sphere-Frustum Culling (Conservative AABB check replacement)
                double dx_chunk = centerX - px;
                double dy_chunk = centerY - py;
                double dz_chunk = centerZ - pz;

                double chunkDistSq = dx_chunk * dx_chunk + dy_chunk * dy_chunk + dz_chunk * dz_chunk;
                double chunkDist = Math.sqrt(chunkDistSq);

                // Max radius of the chunk's AABB
                double chunkRadius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);

                if (chunkDist > chunkRadius) {
                    double dot = vx * dx_chunk + vy * dy_chunk + vz * dz_chunk;
                    // Standard sphere-frustum check: dot >= dist * cos(halfAngle) - radius
                    if (dot < (chunkDist * cullingCosine - chunkRadius)) {
                        continue;
                    }
                }

                double closestX = Math.max(minX, Math.min(px, maxX));
                double closestZ = Math.max(minZ, Math.min(pz, maxZ));

                double diffX = px - closestX;
                double diffZ = pz - closestZ;
                double nearestDistSq = diffX * diffX + diffZ * diffZ;

                boolean shouldShow = (nearestDistSq >= renderDistThresholdSq)
                        || !world.isChunkLoaded(pCX + dx, pCZ + dz);

                if (shouldShow) {
                    long key = LODManager.getChunkKey(world, alignedCX, alignedCZ, ratio);
                    if (nextDesired.add(key) && !activeEntities.containsKey(key)) {
                        newTargets.add(key);
                    }
                }
            }
        }

        cleanupQueue.clear();
        for (long key : activeEntities.keySet()) {
            if (!nextDesired.contains(key))
                cleanupQueue.add(key);
        }

        newTargets.sort(Comparator.comparingDouble(key -> {
            double ddx = LODManager.unpackX(key) - pCX;
            double ddz = LODManager.unpackZ(key) - pCZ;
            return ddx * ddx + ddz * ddz;
        }));

        scanQueue.clear();
        scanQueue.addAll(newTargets);
        scanIndex = 0;

        // CRITICAL FIX: Do NOT clear anchorRefreshQueue or reset index here.
        // The queue is synced periodically in update() to maintain rotation.
    }

    private void processCleanupQueue(int max) {
        int count = 0;
        Iterator<Long> it = cleanupQueue.iterator();
        while (it.hasNext() && count < max) {
            long key = it.next();
            VirtualDisplayEntity[] entities = activeEntities.remove(key);
            if (entities != null) {
                for (VirtualDisplayEntity entity : entities) {
                    if (entity != null) {
                        entity.remove(player);
                        if (entityPool.size() < 4000)
                            entityPool.offer(entity);
                    }
                }
            }
            entityAnchors.remove(key);
            entityHeights.remove(key);
            it.remove();
            count++;
        }
    }

    private int processScanQueue(int count) {
        if (scanQueue.isEmpty())
            return 0;
        World world = player.getWorld();
        int processed = 0;
        while (scanIndex < scanQueue.size() && processed < count) {
            long key = scanQueue.get(scanIndex++);
            if (activeEntities.containsKey(key))
                continue;

            int cx = LODManager.unpackX(key);
            int cz = LODManager.unpackZ(key);
            int ratio = LODManager.unpackY(key);

            LODSignature sig = manager.getSignature(world, cx, cz, quality.getSubdivX(), quality.getSubdivZ());
            if (sig != null) {
                spawnLOD(key, cx, cz, ratio, sig);
                processed++;
            } else {
                manager.requestScan(world, cx, cz, quality.getSubdivX(), quality.getSubdivZ());
            }
        }
        return processed;
    }

    private int processAnchorRefresh(int max) {
        if (anchorRefreshQueue.isEmpty())
            return 0;
        Location pLoc = player.getLocation();
        int count = 0;
        int size = anchorRefreshQueue.size();
        for (int i = 0; i < size && count < max; i++) {
            if (anchorRefreshIndex >= size)
                anchorRefreshIndex = 0;
            long key = anchorRefreshQueue.get(anchorRefreshIndex++);

            VirtualDisplayEntity[] entities = activeEntities.get(key);
            if (entities == null)
                continue;

            Location[] anchors = entityAnchors.get(key);
            if (anchors == null || anchors.length == 0 || anchors[0] == null)
                continue;

            // Use 3600 (60 blocks) instead of 4096 (64 blocks) as a safety margin for
            // client culling
            if (pLoc.distanceSquared(anchors[0]) > 3600) {
                int ratio = LODManager.unpackY(key);
                int[] heights = entityHeights.get(key);
                if (heights == null)
                    continue;

                int subdivX = quality.getSubdivX();
                int subdivZ = quality.getSubdivZ();

                LODSignature sig = manager.getSignature(player.getWorld(), LODManager.unpackX(key),
                        LODManager.unpackZ(key), subdivX, subdivZ);
                if (sig == null)
                    continue;

                for (int idx = 0; idx < entities.length; idx++) {
                    VirtualDisplayEntity entity = entities[idx];
                    if (entity == null)
                        continue;

                    int sx = idx / subdivZ;
                    int sz = idx % subdivZ;
                    int h = heights[idx];
                    Location newAnchor = calculateAnchor(pLoc, LODManager.unpackX(key), LODManager.unpackZ(key), h);

                    float thickness = (idx < sig.thicknesses().length) ? sig.thicknesses()[idx] : 10.0f;
                    updateMatrix(entity, key, newAnchor, ratio, sx, sz, subdivX, subdivZ, h, thickness);

                    if (USE_TELEPORTATION) {
                        entity.teleport(player, newAnchor);
                        anchors[idx] = newAnchor;
                        entity.updateMetadata(player);
                    } else {
                        entity.remove(player);
                        entity.setLocation(newAnchor);
                        anchors[idx] = newAnchor;
                        entity.spawn(player);
                        entity.updateMetadata(player);
                    }
                }
                count++;
            }
        }
        return count;
    }

    private void spawnLOD(long key, int cx, int cz, int ratio, LODSignature sig) {
        Location pLoc = player.getLocation();
        int subdivX = sig.subdivX();
        int subdivZ = sig.subdivZ();
        int totalEntities = subdivX * subdivZ;

        VirtualDisplayEntity[] entities = new VirtualDisplayEntity[totalEntities];
        Location[] anchors = new Location[totalEntities];
        int[] heights = new int[totalEntities];

        for (int sx = 0; sx < subdivX; sx++) {
            for (int sz = 0; sz < subdivZ; sz++) {
                int idx = sx * subdivZ + sz;
                int y = sig.sampledHeights()[idx];
                Location anchor = calculateAnchor(pLoc, cx, cz, y);

                VirtualDisplayEntity entity = entityPool.poll();
                if (entity == null) {
                    entity = new VirtualDisplayEntity(anchor.clone(), sig.blockData()[idx], sig.biomeColors()[idx]);
                } else {
                    entity.setBlockData(sig.blockData()[idx], sig.biomeColors()[idx], sig.thicknesses()[idx]);
                    entity.setLocation(anchor.clone());
                }

                entities[idx] = entity;
                anchors[idx] = anchor;
                heights[idx] = y;

                float thickness = (idx < sig.thicknesses().length) ? sig.thicknesses()[idx] : 10.0f;
                updateMatrix(entity, key, anchor, ratio, sx, sz, subdivX, subdivZ, y, thickness);
                entity.spawn(player);
                entity.updateMetadata(player);
            }
        }

        entityHeights.put(key, heights);
        entityAnchors.put(key, anchors);
        activeEntities.put(key, entities);
    }

    private void updateMatrix(VirtualDisplayEntity entity, long key, Location anchor, int ratio, int sx, int sz,
            int subdivX, int subdivZ, int h, float thickness) {
        int cx = LODManager.unpackX(key);
        int cz = LODManager.unpackZ(key);
        float wy = (float) h;

        float sideX = (16.0f * ratio) / subdivX;
        float sideZ = (16.0f * ratio) / subdivZ;
        float overlap = 0.1f;
        float scaleX = sideX + overlap;
        float scaleZ = sideZ + overlap;

        float wx = (cx << 4) + (sx * sideX);
        float wz = (cz << 4) + (sz * sideZ);

        float ax = (float) anchor.getX();
        float ay = (float) anchor.getY();
        float az = (float) anchor.getZ();

        float transX = wx - ax;
        float transY = wy - ay - thickness + 0.05f;
        float transZ = wz - az;

        if (entity.isItemDisplay()) {
            transX += scaleX / 2.0f;
            transY += thickness / 2.0f;
            transZ += scaleZ / 2.0f;
        }

        Matrix4f matrix = new Matrix4f().translate(transX, transY, transZ);
        if (entity.isItemDisplay())
            matrix.rotateY((float) Math.PI);
        matrix.scale(scaleX, thickness, scaleZ);
        entity.setTransformation(matrix);
    }

    private Location calculateAnchor(Location pLoc, int cx, int cz, int y) {
        double tx = (cx << 4) + 8.0;
        double tz = (cz << 4) + 8.0;
        Vector dir = new Vector(tx - pLoc.getX(), 0, tz - pLoc.getZ());
        if (dir.lengthSquared() > 0) {
            dir.normalize().multiply(12.0);
        }
        return pLoc.clone().add(dir).add(0, -2, 0);
    }

    public void clear() {
        activeEntities.values().forEach(entities -> {
            for (VirtualDisplayEntity e : entities)
                if (e != null)
                    e.remove(player);
        });
        activeEntities.clear();
        entityAnchors.clear();
        entityHeights.clear();
        entityPool.clear();
        scanQueue.clear();
        cleanupQueue.clear();
        anchorRefreshQueue.clear();
        lastCX = Integer.MAX_VALUE;
    }

    public void setDistance(LODDistance distance) {
        this.distance = distance;
        clear();
    }

    public void setQuality(LODQuality quality) {
        this.quality = quality;
        clear();
    }

    public int getActiveEntityCount() {
        int count = 0;
        for (VirtualDisplayEntity[] entities : activeEntities.values())
            count += entities.length;
        return count;
    }

    public LODDistance getDistance() {
        return distance;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
        this.cullingCosine = Math.cos(Math.toRadians((fov + 20.0) / 2.0));
        clear();
    }

    public LODQuality getQuality() {
        return quality;
    }
}
