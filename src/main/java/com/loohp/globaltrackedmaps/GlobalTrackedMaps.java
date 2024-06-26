package com.loohp.globaltrackedmaps;

import com.loohp.globaltrackedmaps.utils.MapUtils;
import com.loohp.globaltrackedmaps.utils.Scheduler;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalTrackedMaps extends JavaPlugin {
    private static final Map<MapView, MapUtils.GlobalTrackerRenderer> TRACKED_MAPS = new ConcurrentHashMap<>();

    private static final Set<MapView> SCHEDULED_REMOVAL_MAPS = ConcurrentHashMap.newKeySet();

    public static GlobalTrackedMaps plugin;

    public void onEnable() {
        plugin = this;
        Scheduler.runTaskTimerAsynchronously(this, this::tick, 0L, 1L);
        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "GlobalTrackedMaps has been enabled!");
    }

    public void onDisable() {
        for (Map.Entry<MapView, MapUtils.GlobalTrackerRenderer> entry : TRACKED_MAPS.entrySet())
            resetMapView(entry.getKey(), entry.getValue().getCraftBaseRenderer());
        getServer().getConsoleSender().sendMessage(ChatColor.RED + "GlobalTrackedMaps had been disabled!");
    }

    public void resetMapView(MapView mapView, MapRenderer craftBaseRenderer) {
        MapUtils.removeTrackedRenderers(mapView);
        List<MapRenderer> renderers = mapView.getRenderers();
        for (MapRenderer renderer : renderers)
            mapView.removeRenderer(renderer);
        mapView.addRenderer(craftBaseRenderer);
        for (MapRenderer renderer : renderers)
            mapView.addRenderer(renderer);
    }

    private void tick() {
        for (Map.Entry<MapView, MapUtils.GlobalTrackerRenderer> entry : TRACKED_MAPS.entrySet()) {
            MapView mapView = entry.getKey();
            if (!mapView.isTrackingPosition() || mapView.getWorld() == null || MapUtils.getTrackedPlayers(mapView).isEmpty()) {
                SCHEDULED_REMOVAL_MAPS.add(mapView);
                TRACKED_MAPS.remove(mapView);
                MapUtils.GlobalTrackerRenderer trackerRenderer = entry.getValue();
                Scheduler.runTask(this, () -> {
                    resetMapView(mapView, trackerRenderer.getCraftBaseRenderer());
                    SCHEDULED_REMOVAL_MAPS.remove(mapView);
                });
            }
        }
        Map<Player, Location> playerLocations = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerLocations.put(player, player.getLocation());
            for (ItemStack itemStack : player.getInventory()) {
                MapView mapView = MapUtils.getItemMapView(itemStack);
                if (mapView != null && !SCHEDULED_REMOVAL_MAPS.contains(mapView) && !TRACKED_MAPS.containsKey(mapView) && mapView.getWorld() != null && mapView.isTrackingPosition() && !MapUtils.getTrackedPlayers(mapView).isEmpty() && MapUtils.getCraftBaseRenderer(mapView) != null)
                    TRACKED_MAPS.put(mapView, MapUtils.createTrackedRenderer(mapView));
            }
        }
        for (Map.Entry<MapView, MapUtils.GlobalTrackerRenderer> entry : TRACKED_MAPS.entrySet())
            entry.getValue().setLocations(playerLocations);
    }
}
