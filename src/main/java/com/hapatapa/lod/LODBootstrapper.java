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
                                                                                        .build())
                                                                        .type(DialogType.multiAction(List.of(
                                                                                        createDistanceButton(
                                                                                                        "Distance: High",
                                                                                                        LODDistance.HIGH_FIDELITY,
                                                                                                        NamedTextColor.GREEN),
                                                                                        createDistanceButton(
                                                                                                        "Distance: Balanced",
                                                                                                        LODDistance.BALANCED,
                                                                                                        NamedTextColor.YELLOW),
                                                                                        createDistanceButton(
                                                                                                        "Distance: Perf",
                                                                                                        LODDistance.PERFORMANCE,
                                                                                                        NamedTextColor.RED),
                                                                                        createQualityButton(
                                                                                                        "Quality: Extreme",
                                                                                                        LODQuality.EXTREME,
                                                                                                        NamedTextColor.DARK_RED),
                                                                                        createQualityButton(
                                                                                                        "Quality: Medium",
                                                                                                        LODQuality.MEDIUM,
                                                                                                        NamedTextColor.YELLOW),
                                                                                        createQualityButton(
                                                                                                        "Quality: Low",
                                                                                                        LODQuality.LOW,
                                                                                                        NamedTextColor.RED),

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

        private ActionButton createDistanceButton(String label, LODDistance distance, NamedTextColor color) {
                return ActionButton.builder(Component.text(label, color))
                                .action(DialogAction.customClick((view, audience) -> {
                                        if (audience instanceof Player p) {
                                                LODPlugin plugin = LODPlugin.getInstance();
                                                if (plugin != null && plugin.getLodManager() != null) {
                                                        plugin.getLodManager().getSession(p).setDistance(distance);
                                                        p.sendMessage(Component
                                                                        .text("LOD Distance set to: ",
                                                                                        NamedTextColor.GRAY)
                                                                        .append(Component.text(distance.name(),
                                                                                        color)));
                                                }
                                        }
                                }, ClickCallback.Options.builder().uses(100).build()))
                                .build();
        }

        private ActionButton createQualityButton(String label, LODQuality quality, NamedTextColor color) {
                if (quality == LODQuality.EXTREME) {
                        return ActionButton.builder(Component.text(label, color))
                                        .action(DialogAction.customClick((view, audience) -> {
                                                if (audience instanceof Player p) {
                                                        io.papermc.paper.dialog.Dialog confirmMenu = io.papermc.paper.registry.RegistryAccess
                                                                        .registryAccess()
                                                                        .getRegistry(io.papermc.paper.registry.RegistryKey.DIALOG)
                                                                        .get(Key.key("lod:quality_extreme_confirm"));
                                                        if (confirmMenu != null) {
                                                                p.showDialog(confirmMenu);
                                                        }
                                                }
                                        }, ClickCallback.Options.builder().uses(100).build()))
                                        .build();
                }

                return ActionButton.builder(Component.text(label, color))
                                .action(DialogAction.customClick((view, audience) -> {
                                        if (audience instanceof Player p) {
                                                LODPlugin plugin = LODPlugin.getInstance();
                                                if (plugin != null && plugin.getLodManager() != null) {
                                                        plugin.getLodManager().getSession(p).setQuality(quality);
                                                        p.sendMessage(Component
                                                                        .text("LOD Quality set to: ",
                                                                                        NamedTextColor.GRAY)
                                                                        .append(Component.text(quality.name(), color)));
                                                }
                                        }
                                }, ClickCallback.Options.builder().uses(100).build()))
                                .build();
        }

}
