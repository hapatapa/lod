package com.hapatapa.lod.ui;

import com.hapatapa.lod.LODPlugin;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.function.Consumer;

public class LODDialogManager implements Listener {

    private final LODPlugin plugin;

    public LODDialogManager(LODPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer()
                .sendMessage(Component.text("LOD System active. You can find settings in your ", NamedTextColor.GRAY)
                        .append(Component.text("Pause Menu (ESC) > Server Options", NamedTextColor.AQUA))
                        .append(Component.text(".", NamedTextColor.GRAY)));
    }

    public void openSettingsMenu(Player player) {
        Dialog settingsMenu = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.DIALOG)
                .get(Key.key("lod:settings"));

        if (settingsMenu != null) {
            player.showDialog(settingsMenu);
        } else {
            // Fallback for development if registry is not ready
            player.sendMessage(Component.text("LOD Settings dialog not registered!", NamedTextColor.RED));
        }
    }

    public void openAdminMenu(Player player) {
        Dialog adminMenu = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("LOD Admin Settings", NamedTextColor.RED))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(List.of(
                        createAdminToggleButton("Chunk Generation", "chunkGenerationEnabled"),
                        createAdminToggleButton("LOD Cache", "cacheEnabled"),
                        ActionButton.builder(Component.text("Back to Settings", NamedTextColor.GRAY))
                                .action(DialogAction.customClick((view, audience) -> {
                                    if (audience instanceof Player p) {
                                        openSettingsMenu(p);
                                    }
                                }, ClickCallback.Options.builder().uses(100).build()))
                                .build()))
                        .build()));

        player.showDialog(adminMenu);
    }

    private ActionButton createAdminToggleButton(String label, String setting) {
        com.hapatapa.lod.util.SettingsManager sm = plugin.getLodManager().getSettingsManager();
        boolean current = setting.equals("chunkGenerationEnabled") ? sm.isChunkGenerationEnabled()
                : sm.isCacheEnabled();

        NamedTextColor color = current ? NamedTextColor.GREEN : NamedTextColor.RED;
        String status = current ? "ENABLED" : "DISABLED";

        return ActionButton.builder(Component.text(label + ": " + status, color))
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        if (setting.equals("chunkGenerationEnabled")) {
                            sm.setChunkGenerationEnabled(!sm.isChunkGenerationEnabled());
                        } else {
                            sm.setCacheEnabled(!sm.isCacheEnabled());
                        }

                        // Re-open admin menu to show update
                        openAdminMenu(p);

                        p.sendMessage(
                                Component.text("Global LOD setting '" + label + "' updated.", NamedTextColor.YELLOW));
                    }
                }, ClickCallback.Options.builder().uses(100).build()))
                .build();
    }
}
