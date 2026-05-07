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
        return ((MapMeta) meta).getMapView();
    }

    public static void addCursor(MapView map, MapCursorCollection collection, MapCursor.Type type, double x, double z, double yaw) {
        byte b2;
        int i = 1 << map.getScale().getValue();
        float f = (float) (x - map.getCenterX()) / i;
        float f1 = (float) (z - map.getCenterZ()) / i;
        byte b0 = (byte) (int) ((f * 2.0F) + 0.5D);
        byte b1 = (byte) (int) ((f1 * 2.0F) + 0.5D);
        if (f >= -63.0F && f1 >= -63.0F && f <= 63.0F && f1 <= 63.0F) {
            yaw += (yaw < 0.0D) ? -8.0D : 8.0D;
            b2 = (byte) (int) (yaw * 16.0D / 360.0D);
            if (map.getWorld().getEnvironment() == World.Environment.NETHER) {
                int j = (int) (map.getWorld().getTime() / 10L);
                b2 = (byte) (j * j * 34187121 + j * 121 >> 15 & 0xF);
            }
        } else {
            if (type != MapCursor.Type.WHITE_POINTER)
                return;
            if (Math.abs(f) < 320.0F && Math.abs(f1) < 320.0F) {
                type = MapCursor.Type.WHITE_CIRCLE;
            } else {
                type = MapCursor.Type.SMALL_WHITE_CIRCLE;
            }
            b2 = 0;
            if (f <= -63.0F)
                b0 = Byte.MIN_VALUE;
            if (f1 <= -63.0F)
                b1 = Byte.MIN_VALUE;
            if (f >= 63.0F)
                b0 = Byte.MAX_VALUE;
            if (f1 >= 63.0F)
                b1 = Byte.MAX_VALUE;
        }
        if (b2 < 0)
            b2 = (byte) (b2 + 16);
        collection.addCursor(new MapCursor(b0, b1, b2, type, true));
    }

    public static MapRenderer getCraftBaseRenderer(MapView mapView) {
        for (MapRenderer mapRenderer : mapView.getRenderers()) {
            if (craftMapRendererClass.isInstance(mapRenderer))
                return mapRenderer;
        }
        return null;
    }

    public static void removeTrackedRenderers(MapView mapView) {
        for (MapRenderer mapRenderer : mapView.getRenderers()) {
            if (mapRenderer instanceof GlobalTrackerRenderer)
                mapView.removeRenderer(mapRenderer);
        }
    }

    public static GlobalTrackerRenderer createTrackedRenderer(MapView mapView) {
        MapRenderer craftRenderer = getCraftBaseRenderer(mapView);
        GlobalTrackerRenderer renderer = new GlobalTrackerRenderer(craftRenderer);
        Scheduler.runTask(GlobalTrackedMaps.plugin, () -> {
            removeTrackedRenderers(mapView);
            mapView.removeRenderer(craftRenderer);
            mapView.addRenderer(renderer);
        });
        return renderer;
    }

    public static class GlobalTrackerRenderer extends MapRenderer {
        private final MapRenderer craftBaseRenderer;

        private Map<Player, Location> locations;

        private GlobalTrackerRenderer(MapRenderer craftBaseRenderer) {
            super(true);
            this.craftBaseRenderer = craftBaseRenderer;
        }

        public void setLocations(Map<Player, Location> locations) {
            this.locations = locations;
        }

        public MapRenderer getCraftBaseRenderer() {
            return this.craftBaseRenderer;
        }

        public void render(MapView map, MapCanvas canvas, Player player) {
            this.craftBaseRenderer.render(map, canvas, player);
            World world = map.getWorld();
            if (world == null) {
                return;
            }
            MapCursorCollection collection = canvas.getCursors();
            for (int i = 0; i < collection.size(); ++i) {
                MapCursor cursor = collection.getCursor(i);
                MapCursor.Type type = cursor.getType();
                switch (type) {
                    case WHITE_POINTER:
                    case WHITE_CIRCLE:
                    case SMALL_WHITE_CIRCLE: {
                        collection.removeCursor(cursor);
                    }
                }
            }
            if (this.locations != null) {
                for (Map.Entry<Player, Location> entry : this.locations.entrySet()) {
                    Player p = entry.getKey();
                    Location location = entry.getValue();
                    if (!player.canSee(p) || p.getGameMode().equals(GameMode.SPECTATOR) || !world.equals(location.getWorld()) || !p.equals(player) && EntityUtils.isWearingMobHead(p))
                        continue;
                    MapUtils.addCursor(map, collection, MapCursor.Type.WHITE_POINTER, location.getX(), location.getZ(), location.getYaw());
                }
            }
            canvas.setCursors(collection);
        }
    }
}
