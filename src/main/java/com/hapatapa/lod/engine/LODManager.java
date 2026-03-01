package com.hapatapa.lod.engine;

import com.hapatapa.lod.LODPlugin;
import com.hapatapa.lod.data.LODSignature;
import com.hapatapa.lod.io.AnvilScanner;
import com.hapatapa.lod.util.SettingsManager;
import com.hapatapa.packhost.PackHostAPI;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LODManager implements Listener {

    private final LODPlugin plugin;
    private final AnvilScanner scanner;
    private final SettingsManager settingsManager;

    // LRU Cache for signatures to prevent memory leaks in long sessions
    private final Map<Long, LODSignature> staticLODCache = new LinkedHashMap<Long, LODSignature>(1000, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, LODSignature> eldest) {
            return size() > 50000; // Limit to 50k chunks (~20MB memory)
        }
    };

    // Thread-safe wrapper for the LRU
    private final Object cacheLock = new Object();

    // Transient Cache: Stores the last ~1000 scan results regardless of global
    // cache settings.
    // This allows active sessions to retrieve results that were just scanned.
    private final Map<Long, LODSignature> transientCache = new LinkedHashMap<Long, LODSignature>(200, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, LODSignature> eldest) {
            return size() > 2000;
        }
    };

    private final Map<UUID, PlayerSession> activeSessions = new ConcurrentHashMap<>();
    private final Queue<PendingScan> scanQueue = new ConcurrentLinkedQueue<>();
    private final Set<Long> pendingScans = ConcurrentHashMap.newKeySet();

    private record PendingScan(World world, int cx, int cz, int subdivX, int subdivZ, long key) {
    }

    public LODManager(LODPlugin plugin) {
        this.plugin = plugin;
        this.scanner = new AnvilScanner();
        this.settingsManager = new SettingsManager(plugin);
    }

    public void init() {
        settingsManager.load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, this::processScanQueue, 1, 1);

        // Periodic diagnostic log
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int totalEntities = 0;
            for (PlayerSession session : activeSessions.values()) {
                totalEntities += session.getActiveEntityCount();
            }
            if (activeSessions.size() > 0) {
                plugin.getLogger()
                        .info(String.format("[LOD] Performance: %d sessions, %d total entities, %d cached chunks",
                                activeSessions.size(), totalEntities, getCacheSize()));
            }
        }, 200, 200); // Every 10 seconds
    }

    private int getCacheSize() {
        synchronized (cacheLock) {
            return staticLODCache.size();
        }
    }

    public void shutdown() {
        activeSessions.values().forEach(PlayerSession::clear);
        activeSessions.clear();
    }

    private void tick() {
        for (PlayerSession session : activeSessions.values()) {
            session.update();
        }
    }

    private void processScanQueue() {
        int processed = 0;
        int maxPerTick = 30; // Throttled slightly to protect Chunk API

        boolean useGlobalCache = settingsManager.isCacheEnabled();
        boolean allowGeneration = settingsManager.isChunkGenerationEnabled();

        while (!scanQueue.isEmpty() && processed < maxPerTick) {
            PendingScan pending = scanQueue.poll();
            if (pending == null)
                continue;

            scanner.scanChunk(pending.world, pending.cx, pending.cz, pending.subdivX, pending.subdivZ, allowGeneration)
                    .thenAccept(sig -> {
                        if (sig != null) {
                            synchronized (cacheLock) {
                                transientCache.put(pending.key, sig);
                                if (useGlobalCache) {
                                    staticLODCache.put(pending.key, sig);
                                }
                            }
                        }
                        pendingScans.remove(pending.key);
                    });

            processed++;
        }
    }

    public void requestScan(World world, int cx, int cz, int subdivX, int subdivZ) {
        if (!settingsManager.isChunkGenerationEnabled()) {
            if (!world.isChunkGenerated(cx, cz)) {
                return;
            }
        }

        long key = getChunkKey(world, cx, cz, subdivX);
        if (settingsManager.isCacheEnabled()) {
            synchronized (cacheLock) {
                if (staticLODCache.containsKey(key))
                    return;
            }
        }

        if (pendingScans.add(key)) {
            scanQueue.add(new PendingScan(world, cx, cz, subdivX, subdivZ, key));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = new PlayerSession(player, this);

        // Load settings
        session.setDistance(settingsManager.getPlayerDistance(player.getUniqueId()));
        session.setQuality(settingsManager.getPlayerQuality(player.getUniqueId()));

        activeSessions.put(player.getUniqueId(), session);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            settingsManager.savePlayerSettings(player.getUniqueId(), session.getDistance(), session.getQuality());
            session.clear();
        }
    }

    public LODSignature getSignature(World world, int cx, int cz, int subdivX, int subdivZ) {
        long key = getChunkKey(world, cx, cz, subdivX);
        synchronized (cacheLock) {
            LODSignature sig = transientCache.get(key);
            if (sig != null)
                return sig;

            if (settingsManager.isCacheEnabled()) {
                return staticLODCache.get(key);
            }
        }
        return null;
    }

    public static long getChunkKey(World world, int x, int z, int meta) {
        long worldPart = ((long) world.getUID().getMostSignificantBits() & 0xFFFF) << 48;
        long xPart = ((long) x & 0x3FFFFFL) << 26;
        long zPart = ((long) z & 0x3FFFFFL) << 4;
        long metaPart = (long) (meta & 0xF);
        return worldPart | xPart | zPart | metaPart;
    }

    public static int unpackX(long key) {
        long xEnc = (key >> 26) & 0x3FFFFFL;
        if ((xEnc & 0x200000L) != 0)
            xEnc |= 0xFFFFFFFFFFC00000L;
        return (int) xEnc;
    }

    public static int unpackZ(long key) {
        long zEnc = (key >> 4) & 0x3FFFFFL;
        if ((zEnc & 0x200000L) != 0)
            zEnc |= 0xFFFFFFFFFFC00000L;
        return (int) zEnc;
    }

    public static int unpackY(long key) {
        return (int) (key & 0xF);
    }

    public void clearCache() {
        synchronized (cacheLock) {
            staticLODCache.clear();
            transientCache.clear();
        }
        scanQueue.clear();
        pendingScans.clear();
        for (PlayerSession session : activeSessions.values()) {
            session.clear();
        }
    }

    public void onResourcePackReady() {
        // Edge case: player(s) joined during the tiny window between plugin enable and
        // pack hashing completing. Resend all early packs to currently online players.
        ResourcePackRequest request = PackHostAPI.buildEarlyRequest();
        if (request != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendResourcePacks(request);
                }
            });
        }
    }

    public PlayerSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public LODPlugin getPlugin() {
        return plugin;
    }
}
