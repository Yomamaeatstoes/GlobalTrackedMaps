package com.loohp.globaltrackedmaps.utils;

import com.google.common.collect.Collections2;
import com.loohp.globaltrackedmaps.GlobalTrackedMaps;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class MapUtils {
    private static Class<?> craftMapRendererClass;

    private static Class<?> craftMapViewClass;

    private static Field craftMapViewWorldMapField;

    private static Class<?> nmsWorldMapClass;

    private static Field nmsWorldMapHumansField;

    private static Class<?> nmsEntityHumanClass;

    private static Method nmsEntityHumanGetBukkitEntityMethod;

    private static boolean warnedMissingTrackingField;

    static {
        try {
            craftMapRendererClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.map.CraftMapRenderer");
            craftMapViewClass = NMSUtils.getNMSClass("org.bukkit.craftbukkit.%s.map.CraftMapView");
            craftMapViewWorldMapField = craftMapViewClass.getDeclaredField("worldMap");
            nmsWorldMapClass = NMSUtils.getNMSClass("net.minecraft.server.%s.WorldMap", "net.minecraft.world.level.saveddata.maps.WorldMap", "net.minecraft.world.level.saveddata.maps.MapItemSavedData");
            nmsWorldMapHumansField = NMSUtils.reflectiveLookup(Field.class,
                    () -> nmsWorldMapClass.getDeclaredField("carriedByPlayers"),
                    () -> nmsWorldMapClass.getDeclaredField("humans"),
                    () -> nmsWorldMapClass.getDeclaredField("o")
            );
            nmsEntityHumanClass = NMSUtils.getNMSClass("net.minecraft.server.%s.EntityHuman", "net.minecraft.world.entity.player.EntityHuman", "net.minecraft.world.entity.player.Player");
            nmsEntityHumanGetBukkitEntityMethod = nmsEntityHumanClass.getMethod("getBukkitEntity");
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    public static Collection<Player> getTrackedPlayers(MapView mapView) {
        if (craftMapViewWorldMapField == null || nmsWorldMapHumansField == null || nmsEntityHumanGetBukkitEntityMethod == null) {
            warnMissingTrackingField();
            return Collections.emptySet();
        }
        craftMapViewWorldMapField.setAccessible(true);
        nmsWorldMapHumansField.setAccessible(true);
        try {
            Object nmsWorldMap = craftMapViewWorldMapField.get(mapView);
            if (nmsWorldMap == null) {
                return Collections.emptySet();
            }
            Map<?, ?> nmsEntityHumanMap = (Map<?, ?>) nmsWorldMapHumansField.get(nmsWorldMap);
            if (nmsEntityHumanMap == null) {
                return Collections.emptySet();
            }
            return Collections2.transform(nmsEntityHumanMap.keySet(), nmsEntityHuman -> {
                try {
                    return (Player) nmsEntityHumanGetBukkitEntityMethod.invoke(nmsEntityHuman, new Object[0]);
                } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    private static void warnMissingTrackingField() {
        if (warnedMissingTrackingField) {
            return;
        }
        warnedMissingTrackingField = true;
        if (GlobalTrackedMaps.plugin != null) {
            GlobalTrackedMaps.plugin.getLogger().warning("Unable to access Minecraft's map tracking field for this server version. Global map tracking will be disabled instead of throwing repeated errors.");
        }
    }

    public static MapView getItemMapView(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR) || !itemStack.hasItemMeta())
            return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof MapMeta))
            return null;
