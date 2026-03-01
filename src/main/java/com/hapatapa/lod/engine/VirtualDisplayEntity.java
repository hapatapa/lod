package com.hapatapa.lod.engine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class VirtualDisplayEntity {

    private static final AtomicInteger ENTITY_ID_COUNTER = new AtomicInteger(2000000);

    private final int entityId;
    private final UUID uuid;
    private Location location;
    private BlockData blockData;
    private int biomeColor;
    private float thickness = 10.0f;
    private Matrix4f transformation;
    private int interpolationDuration = 0;
    private boolean firstUpdate = true;

    public VirtualDisplayEntity(Location location, BlockData blockData, int biomeColor) {
        this.entityId = ENTITY_ID_COUNTER.getAndIncrement();
        this.uuid = UUID.randomUUID();
        this.location = location;
        this.blockData = blockData;
        this.biomeColor = biomeColor;
        this.transformation = new Matrix4f();
    }

    private int getCMD(Material mat) {
        String name = mat.name();
        if (name.equals("GRASS_BLOCK"))
            return 1;
        if (name.equals("WATER")) {
            return (thickness > 12.0f) ? 11 : 2;
        }
        if (name.equals("SHORT_GRASS") || name.equals("GRASS"))
            return 3;
        if (name.equals("FERN"))
            return 4;
        if (name.equals("LARGE_FERN"))
            return 5;
        if (name.equals("TALL_GRASS"))
            return 7;
        if (name.contains("LEAVES"))
            return 9;
        if (name.equals("VINE"))
            return 10;
        return 0;
    }

    private static boolean needsCustomModel(Material mat) {
        String name = mat.name();
        return name.equals("GRASS_BLOCK") || name.equals("WATER") || name.equals("SHORT_GRASS") ||
                name.equals("GRASS") || name.equals("FERN") || name.equals("LARGE_FERN") ||
                name.equals("TALL_GRASS") || name.contains("LEAVES") || name.equals("VINE");
    }

    private boolean isPlayerReady(Player player) {
        return player != null && player.isOnline();
    }

    public void spawn(Player player) {
        if (!isPlayerReady(player))
            return;

        com.github.retrooper.packetevents.protocol.entity.type.EntityType type = needsCustomModel(
                blockData.getMaterial()) ? EntityTypes.ITEM_DISPLAY : EntityTypes.BLOCK_DISPLAY;

        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                entityId,
                Optional.of(uuid),
                type,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 0, Optional.empty());

        PacketEvents.getAPI().getPlayerManager().sendPacket(player, spawn);
        firstUpdate = true;
    }

    public void updateMetadata(Player player) {
        if (!isPlayerReady(player))
            return;

        List<EntityData<?>> data = new ArrayList<>();

        org.joml.Vector3f jomlTrans = new org.joml.Vector3f();
        transformation.getTranslation(jomlTrans);

        org.joml.Vector3f jomlScale = new org.joml.Vector3f();
        transformation.getScale(jomlScale);

        org.joml.Quaternionf jomlRot = new org.joml.Quaternionf();
        transformation.getUnnormalizedRotation(jomlRot);

        Vector3f peTranslation = new Vector3f(jomlTrans.x, jomlTrans.y, jomlTrans.z);
        Vector3f peScale = new Vector3f(jomlScale.x, jomlScale.y, jomlScale.z);
        Quaternion4f peRotation = new Quaternion4f(jomlRot.x, jomlRot.y, jomlRot.z, jomlRot.w);

        data.add(new EntityData(9, EntityDataTypes.INT, firstUpdate ? 0 : interpolationDuration));
        data.add(new EntityData(10, EntityDataTypes.INT, 0));
        data.add(new EntityData(11, EntityDataTypes.VECTOR3F, peTranslation));
        data.add(new EntityData(12, EntityDataTypes.VECTOR3F, peScale));
        data.add(new EntityData(13, EntityDataTypes.QUATERNION, peRotation));

        data.add(new EntityData(17, EntityDataTypes.FLOAT, 128f));
        data.add(new EntityData(18, EntityDataTypes.FLOAT, 0f));

        if (needsCustomModel(blockData.getMaterial())) {
            org.bukkit.inventory.ItemStack bukkitItem = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.LEATHER_HORSE_ARMOR);
            org.bukkit.inventory.meta.LeatherArmorMeta meta = (org.bukkit.inventory.meta.LeatherArmorMeta) bukkitItem
                    .getItemMeta();
            if (meta != null) {
                int cmd = getCMD(blockData.getMaterial());
                meta.setColor(org.bukkit.Color.fromRGB(biomeColor));
                meta.setCustomModelData(cmd);
                bukkitItem.setItemMeta(meta);
            }
            com.github.retrooper.packetevents.protocol.item.ItemStack peItem = SpigotConversionUtil
                    .fromBukkitItemStack(bukkitItem);
            data.add(new EntityData(23, EntityDataTypes.ITEMSTACK, peItem));
            data.add(new EntityData(24, EntityDataTypes.BYTE, (byte) 0)); // NONE transform
        } else {
            int blockId = SpigotConversionUtil.fromBukkitBlockData(blockData).getGlobalId();
            data.add(new EntityData(23, EntityDataTypes.BLOCK_STATE, blockId));
        }

        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata(entityId, data);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, metadata);

        firstUpdate = false;
    }

    public void remove(Player player) {
        if (player == null)
            return;
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(entityId);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, destroy);
    }

    public void setTransformation(Matrix4f transformation) {
        this.transformation = transformation;
    }

    public void setInterpolationDuration(int ticks) {
        this.interpolationDuration = ticks;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setBlockData(BlockData blockData, int biomeColor, float thickness) {
        boolean typeChanged = needsCustomModel(this.blockData.getMaterial()) != needsCustomModel(
                blockData.getMaterial());

        if (this.blockData != blockData || this.biomeColor != biomeColor || this.thickness != thickness
                || typeChanged) {
            this.blockData = blockData;
            this.biomeColor = biomeColor;
            this.thickness = thickness;
            this.firstUpdate = true;
        }
    }

    public boolean needsRespawn(BlockData newData) {
        return needsCustomModel(this.blockData.getMaterial()) != needsCustomModel(newData.getMaterial());
    }

    public boolean isItemDisplay() {
        return needsCustomModel(this.blockData.getMaterial());
    }
}
