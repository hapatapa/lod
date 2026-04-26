package com.hapatapa.lod;

import com.hapatapa.lod.engine.LODDistance;
import com.hapatapa.lod.engine.LODQuality;
import com.hapatapa.lod.util.SettingsManager;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.keys.DialogKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LODBootstrapper implements PluginBootstrap {

        @Override
        public void bootstrap(@NotNull BootstrapContext context) {
                context.getLifecycleManager().registerEventHandler(
                                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.DATAPACK_DISCOVERY
                                                .newHandler(event -> {
                                                        try {
                                                                java.net.URL url = getClass()
                                                                                .getResource("/lod_datapack");
                                                                if (url != null) {
                                                                        // Register the bundled datapack for custom
                                                                        // dialog tagging
                                                                        event.registrar().discoverPack(url.toURI(),
                                                                                        "lod_datapack");
                                                                }
                                                        } catch (Exception e) {
                                                                e.printStackTrace();
                                                        }
                                                }));

                context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose()
                                .newHandler(event -> {
                                        event.registry().register(DialogKeys.create(Key.key("lod:settings")),
                                                        builder -> builder
                                                                        .base(DialogBase.builder(Component.text(
                                                                                        "LOD Settings",
                                                                                        NamedTextColor.GOLD))
                                                                                        .canCloseWithEscape(true)
                                                                                        .inputs(List.of(
                                                                                                        io.papermc.paper.registry.data.dialog.input.DialogInput
                                                                                                                        .numberRange("fov",
                                                                                                                                        Component.text("FOV (Culling Buffer: +20)",
                                                                                                                                                        NamedTextColor.GREEN),
                                                                                                                                        30f,
                                                                                                                                        110f)
                                                                                                                        .step(1f)
                                                                                                                        .initial(80f)
                                                                                                                        .width(300)
                                                                                                                        .build(),
                                                                                                        io.papermc.paper.registry.data.dialog.input.DialogInput.singleOption(
                                                                                                                        "water_depth",
                                                                                                                        Component.text("Water Depth", NamedTextColor.AQUA),
                                                                                                                        List.of(
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("true", Component.text("ON", NamedTextColor.GREEN), true),
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("false", Component.text("OFF", NamedTextColor.RED), false)
                                                                                                                        )
                                                                                                        ).build(),
                                                                                                        io.papermc.paper.registry.data.dialog.input.DialogInput.singleOption(
                                                                                                                        "distance",
                                                                                                                        Component.text("LOD Distance", NamedTextColor.YELLOW),
                                                                                                                        List.of(
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("HIGH_FIDELITY", Component.text("High Fidelity", NamedTextColor.GREEN), true),
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("BALANCED", Component.text("Balanced", NamedTextColor.YELLOW), false),
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("PERFORMANCE", Component.text("Performance", NamedTextColor.RED), false)
                                                                                                                        )
                                                                                                        ).build(),
                                                                                                        io.papermc.paper.registry.data.dialog.input.DialogInput.singleOption(
                                                                                                                        "quality",
                                                                                                                        Component.text("LOD Quality", NamedTextColor.GOLD),
                                                                                                                        List.of(
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("EXTREME", Component.text("Extreme", NamedTextColor.DARK_RED), false),
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("MEDIUM", Component.text("Medium", NamedTextColor.YELLOW), false),
                                                                                                                                        io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry.create("LOW", Component.text("Low", NamedTextColor.GREEN), true)
                                                                                                                        )
                                                                                                        ).build()
                                                                                        ))
                                                                                        .build())
                                                                        .type(DialogType.multiAction(List.of(
                                                                                        createSaveButton(),

                                                                                        ActionButton.builder(Component
                                                                                                        .text("Clear Cache",
                                                                                                                        NamedTextColor.WHITE))
                                                                                                        .action(DialogAction
                                                                                                                        .customClick((view,
                                                                                                                                        audience) -> {
                                                                                                                                if (audience instanceof Player p) {
                                                                                                                                        LODPlugin.getInstance()
                                                                                                                                                        .getLodManager()
                                                                                                                                                        .getSession(p)
                                                                                                                                                        .clear();
                                                                                                                                        p.sendMessage(
                                                                                                                                                        Component.text("LOD Cache Cleared.",
                                                                                                                                                                        NamedTextColor.GREEN));
                                                                                                                                }
                                                                                                                        }, ClickCallback.Options
                                                                                                                                        .builder()
                                                                                                                                        .uses(100)
                                                                                                                                        .build()))
                                                                                                        .build()))
                                                                                        .build()));

                                        // Register the confirmation dialog
                                        event.registry()
                                                        .register(DialogKeys
                                                                        .create(Key.key("lod:quality_extreme_confirm")),
                                                                        builder -> builder
                                                                                        .base(DialogBase.builder(
                                                                                                        Component.text(
                                                                                                                        "WARNING: Extreme Quality will ruin performance!",
                                                                                                                        NamedTextColor.DARK_RED))
                                                                                                        .canCloseWithEscape(
                                                                                                                        true)
                                                                                                        .build())
                                                                                        .type(DialogType.multiAction(
                                                                                                        List.of(
                                                                                                                        ActionButton.builder(
                                                                                                                                        Component.text("Yes, ruin my performance!",
                                                                                                                                                        NamedTextColor.RED))
                                                                                                                                        .action(DialogAction
                                                                                                                                                        .customClick((view,
                                                                                                                                                                        audience) -> {
                                                                                                                                                                if (audience instanceof Player p) {
                                                                                                                                                                        LODPlugin plugin = LODPlugin
                                                                                                                                                                                        .getInstance();
                                                                                                                                                                        if (plugin != null
                                                                                                                                                                                        && plugin.getLodManager() != null) {
                                                                                                                                                                                plugin.getLodManager()
                                                                                                                                                                                                .getSession(p)
                                                                                                                                                                                                .setQuality(
                                                                                                                                                                                                                LODQuality.EXTREME);
                                                                                                                                                                                p.sendMessage(Component
                                                                                                                                                                                                .text(
                                                                                                                                                                                                                "LOD Quality set to: EXTREME",
                                                                                                                                                                                                                NamedTextColor.RED));
                                                                                                                                                                                // Close
                                                                                                                                                                                // or
                                                                                                                                                                                // reopen
                                                                                                                                                                                io.papermc.paper.dialog.Dialog settingsMenu = io.papermc.paper.registry.RegistryAccess
                                                                                                                                                                                                .registryAccess()
                                                                                                                                                                                                .getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG)
                                                                                                                                                                                                .get(Key.key("lod:settings"));
                                                                                                                                                                                if (settingsMenu != null)
                                                                                                                                                                                        p.showDialog(
                                                                                                                                                                                                        settingsMenu);
                                                                                                                                                                        }
                                                                                                                                                                }
                                                                                                                                                        }, ClickCallback.Options
                                                                                                                                                                        .builder()
                                                                                                                                                                        .uses(100)
                                                                                                                                                                        .build()))
                                                                                                                                        .build(),
                                                                                                                        ActionButton.builder(
                                                                                                                                        Component
                                                                                                                                                        .text("Cancel",
                                                                                                                                                                        NamedTextColor.GREEN))
                                                                                                                                        .action(DialogAction
                                                                                                                                                        .customClick((view,
                                                                                                                                                                        audience) -> {
                                                                                                                                                                if (audience instanceof Player p) {
                                                                                                                                                                        io.papermc.paper.dialog.Dialog settingsMenu = io.papermc.paper.registry.RegistryAccess
                                                                                                                                                                                        .registryAccess()
                                                                                                                                                                                        .getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG)
                                                                                                                                                                                        .get(Key.key("lod:settings"));
                                                                                                                                                                        if (settingsMenu != null)
                                                                                                                                                                                p.showDialog(
                                                                                                                                                                                                settingsMenu);
                                                                                                                                                                }
                                                                                                                                                        }, ClickCallback.Options
                                                                                                                                                                        .builder()
                                                                                                                                                                        .uses(100)
                                                                                                                                                                        .build()))
                                                                                                                                        .build()))
                                                                                                        .build()));

                                        // No longer registering lod:admin_settings here as it's built dynamically in
                                        // LODDialogManager
                                        // to avoid NPE and allow state-reflecting labels.
                                }));
        }

        private ActionButton createSaveButton() {
                return ActionButton.builder(Component.text("Save Inputs & Apply", NamedTextColor.AQUA))
                                .action(DialogAction.customClick((view, audience) -> {
                                        if (audience instanceof Player p) {
                                                LODPlugin plugin = LODPlugin.getInstance();
                                                if (plugin != null && plugin.getLodManager() != null) {
                                                        Float val = view.getFloat("fov");
                                                        if (val != null) {
                                                                plugin.getLodManager().getSession(p).setFov(val);
                                                                p.sendMessage(Component.text(
                                                                                "FOV set to " + val.intValue() + " (Culling at "
                                                                                                + (val.intValue() + 20) + "°)",
                                                                                NamedTextColor.GREEN));
                                                        }
                                                        String wdStr = view.getText("water_depth");
                                                        if (wdStr != null) {
                                                                boolean enabled = "true".equals(wdStr);
                                                                plugin.getLodManager().getSession(p).setWaterDepthEnabled(enabled);
                                                                p.sendMessage(Component
                                                                                .text("LOD Water Depth set to: ",
                                                                                                NamedTextColor.GRAY)
                                                                                .append(Component.text(enabled ? "ON" : "OFF", enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
                                                        }
                                                        String distStr = view.getText("distance");
                                                        if (distStr != null) {
                                                                LODDistance d = LODDistance.valueOf(distStr);
                                                                plugin.getLodManager().getSession(p).setDistance(d);
                                                                p.sendMessage(Component
                                                                                .text("LOD Distance set to: ",
                                                                                                NamedTextColor.GRAY)
                                                                                .append(Component.text(d.name(),
                                                                                                NamedTextColor.YELLOW)));
                                                        }
                                                        String qualStr = view.getText("quality");
                                                        if (qualStr != null) {
                                                                LODQuality q = LODQuality.valueOf(qualStr);
                                                                if (q == LODQuality.EXTREME) {
                                                                        io.papermc.paper.dialog.Dialog confirmMenu = io.papermc.paper.registry.RegistryAccess
                                                                                        .registryAccess()
                                                                                        .getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG)
                                                                                        .get(Key.key("lod:quality_extreme_confirm"));
                                                                        if (confirmMenu != null) {
                                                                                p.showDialog(confirmMenu);
                                                                                return; // Stop and let confirm menu handle reopening settings
                                                                        }
                                                                } else {
                                                                        plugin.getLodManager().getSession(p).setQuality(q);
                                                                        p.sendMessage(Component
                                                                                        .text("LOD Quality set to: ",
                                                                                                        NamedTextColor.GRAY)
                                                                                        .append(Component.text(q.name(), NamedTextColor.GOLD)));
                                                                }
                                                        }
                                                        // Attempt to reopen the dialog (so they see it updated if
                                                        // needed)
                                                        io.papermc.paper.dialog.Dialog settingsMenu = io.papermc.paper.registry.RegistryAccess
                                                                        .registryAccess()
                                                                        .getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG)
                                                                        .get(Key.key("lod:settings"));
                                                        if (settingsMenu != null)
                                                                p.showDialog(settingsMenu);
                                                }
                                        }
                                }, ClickCallback.Options.builder().uses(100).build()))
                                .build();
        }

}
