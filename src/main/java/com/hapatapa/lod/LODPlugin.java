package com.hapatapa.lod;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.hapatapa.lod.engine.LODManager;
import com.hapatapa.packhost.PackHostAPI;
import org.bukkit.plugin.java.JavaPlugin;

public class LODPlugin extends JavaPlugin {

    private static LODPlugin instance;
    private LODManager lodManager;
    private com.hapatapa.lod.ui.LODDialogManager dialogManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        PacketEvents.getAPI().init();
        this.lodManager = new LODManager(this);
        this.lodManager.init();

        PackHostAPI.registerPack(this, "/resource_pack", true, entry -> {
            if (lodManager != null)
                lodManager.onResourcePackReady();
        });

        this.dialogManager = new com.hapatapa.lod.ui.LODDialogManager(this);
        getServer().getPluginManager().registerEvents(this.dialogManager, this);

        registerCommand("lod", new LODCommand(this));

        getLogger().info("LODSystem enabled successfully.");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        if (lodManager != null) {
            lodManager.shutdown();
        }
    }

    public static LODPlugin getInstance() {
        return instance;
    }

    public LODManager getLodManager() {
        return lodManager;
    }

    public com.hapatapa.lod.ui.LODDialogManager getDialogManager() {
        return dialogManager;
    }
}
