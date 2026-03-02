package com.hapatapa.lod.util;

import com.hapatapa.lod.LODPlugin;
import com.hapatapa.lod.engine.LODDistance;
import com.hapatapa.lod.engine.LODQuality;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

public class SettingsManager {

    private final LODPlugin plugin;
    private boolean chunkGenerationEnabled = true;
    private boolean cacheEnabled = true;

    public SettingsManager(LODPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        this.chunkGenerationEnabled = config.getBoolean("admin.chunk-generation-enabled", true);
        this.cacheEnabled = config.getBoolean("admin.cache-enabled", true);
    }

    public void saveGlobal() {
        FileConfiguration config = plugin.getConfig();
        config.set("admin.chunk-generation-enabled", chunkGenerationEnabled);
        config.set("admin.cache-enabled", cacheEnabled);
        plugin.saveConfig();
    }

    public void savePlayerSettings(UUID uuid, LODDistance distance, LODQuality quality, float fov) {
        FileConfiguration config = plugin.getConfig();
        String path = "players." + uuid.toString();
        config.set(path + ".distance", distance.name());
        config.set(path + ".quality", quality.name());
        config.set(path + ".fov", fov);
        plugin.saveConfig();
    }

    public LODDistance getPlayerDistance(UUID uuid) {
        String name = plugin.getConfig().getString("players." + uuid.toString() + ".distance");
        if (name == null)
            return LODDistance.HIGH_FIDELITY;
        try {
            return LODDistance.valueOf(name);
        } catch (IllegalArgumentException e) {
            return LODDistance.HIGH_FIDELITY;
        }
    }

    public LODQuality getPlayerQuality(UUID uuid) {
        String name = plugin.getConfig().getString("players." + uuid.toString() + ".quality");
        if (name == null)
            return LODQuality.LOW;
        try {
            return LODQuality.valueOf(name);
        } catch (IllegalArgumentException e) {
            return LODQuality.LOW;
        }
    }

    public float getPlayerFOV(UUID uuid) {
        return (float) plugin.getConfig().getDouble("players." + uuid.toString() + ".fov", 70.0);
    }

    public boolean isChunkGenerationEnabled() {
        return chunkGenerationEnabled;
    }

    public void setChunkGenerationEnabled(boolean chunkGenerationEnabled) {
        this.chunkGenerationEnabled = chunkGenerationEnabled;
        saveGlobal();
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        saveGlobal();
    }
}
