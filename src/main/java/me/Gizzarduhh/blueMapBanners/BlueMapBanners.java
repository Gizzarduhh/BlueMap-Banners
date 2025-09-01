package me.Gizzarduhh.blueMapBanners;

import de.bluecolored.bluemap.api.AssetStorage;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import me.Gizzarduhh.blueMapBanners.listener.BannerListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;


public final class BlueMapBanners extends JavaPlugin {

    private final NamespacedKey markerIdKey = new NamespacedKey(this,"markerId");
    private final String markerSetId = "bm-banners";
    private Collection<BlueMapMap> blueMapMaps;
    private Configuration config;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();

        // Listener(s)
        getServer().getPluginManager().registerEvents(new BannerListener(this), this);

        // After BlueMap Enable
        BlueMapAPI.onEnable(api -> {
            blueMapMaps = api.getMaps();

            reloadConfig();
            config = getConfig();

            loadBannerMarkers();
        });

        // Before BlueMap Disable
        BlueMapAPI.onDisable(api -> {
            saveBannerMarkers();
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void addBannerMarker(Block banner, String name, Player player) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Get BlueMap map banner was placed within
            Optional<BlueMapMap> blueMapMap = api.getMap(banner.getWorld().getName());
            if (blueMapMap.isEmpty()) {
                getLogger().severe("Error adding banner, " + name + ": BlueMap world not found.");
                return;
            }

            // Create UUID and store it on banner state data container
            String markerId = "bm-banner-" + UUID.randomUUID();
            if (banner.getState() instanceof Banner bannerState) {
                bannerState.getPersistentDataContainer().set(markerIdKey, PersistentDataType.STRING, markerId);
                bannerState.update();
            } else {
                getLogger().severe("Error adding banner, failed to get banners block state.");
                return;
            }

            // Write asset to storage if not in storage
            AssetStorage assetStorage = blueMapMap.get().getAssetStorage();
            String asset = "banners/" + banner.getType().name()
                    .toLowerCase()
                    .replace("_wall","") + ".png";
            try (InputStream in = getResource(asset)) {
                if (in != null && !assetStorage.assetExists(asset)) {
                    try (OutputStream out = assetStorage.writeAsset(asset)) {
                        ImageIO.write(ImageIO.read(in), "png", out);
                    }
                }
            } catch (IOException ex) {
                // handle io-exception
                getLogger().severe("Unable to write asset: " + ex.getMessage());
            }

            // Create bannerMarker
            POIMarker bannerMarker = POIMarker.builder()
                    .label(config.getString("markers.label", "%banner%")
                            .replace("%banner%",name)
                            .replace("%player%",player.getName()))
                    .detail(config.getString("markers.detail", "%banner%\n - %player%")
                            .replace("%banner%",name)
                            .replace("%player%",player.getName()))
                    .position(banner.getX(), banner.getY(), (double)banner.getZ())
                    .icon(blueMapMap.get().getAssetStorage().getAssetUrl(asset),12,16)
                    .maxDistance(1000)
                    .build();

            // Get existing markerSet from map or create new
            MarkerSet markerSet = blueMapMap.get().getMarkerSets().get(markerSetId);
            if (markerSet == null) {
                markerSet = MarkerSet.builder()
                        .defaultHidden(config.getBoolean("markers.hidden"))
                        .label("Banners")
                        .build();
            }

            // Add bannerMarker to markerSet
            markerSet.getMarkers()
                    .put(markerId, bannerMarker);

            // Add markerSet to map
            blueMapMap.get().getMarkerSets().put(markerSetId, markerSet);
        });

        if (config.getBoolean("messages.enabled"))
            player.sendMessage(Component
                    .text(config.getString("messages.+marker", "%banner% added to BlueMap!")
                            .replace("%banner%", name))
                    .color(NamedTextColor.GRAY));
    }

    public void removeBannerMarker(Block banner, Player player) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Get BlueMap map
            Optional<BlueMapMap> blueMapMap = api.getMap(banner.getWorld().getName());
            if (blueMapMap.isEmpty()) {
                getLogger().severe("Error removing banner: BlueMap world not found.");
                return;
            }

            // Get markerSet from map
            MarkerSet markerSet = blueMapMap.get().getMarkerSets().get(markerSetId);
            if (markerSet == null) return;

            // Check if banner has marker id
            if (banner.getState() instanceof Banner bannerState) {
                PersistentDataContainer pdc = bannerState.getPersistentDataContainer();
                if (!pdc.has(markerIdKey)) return;

                // Remove marker if it exists
                String markerId = pdc.get(markerIdKey, PersistentDataType.STRING);
                if (markerSet.get(markerId) != null){
                    String name = '"' + markerSet.get(markerId).getLabel() + '"';
                    markerSet.remove(markerId);

                    if (!config.getBoolean("messages.enabled")) return;

                    if (player != null) {
                        player.sendMessage(Component
                                .text(config.getString("messages.-marker", "%banner% removed from BlueMap!")
                                        .replace("%banner%", name))
                                .color(NamedTextColor.GRAY));
                    } else {
                        getServer().broadcast(Component
                                .text(config.getString("messages.explode", "The %banner% banner exploded!")
                                        .replace("%banner%", name))
                                .color(NamedTextColor.GRAY));
                    }
                }
            }
        });
    }

    public void saveBannerMarkers() {
        getLogger().info("Saving markers...");

        // Create banners save directory
        File folder = new File(getDataFolder(), "banners");
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                getLogger().severe("Failed to create banners directory!");
                return;
            }
        }

        BlueMapAPI.getInstance().ifPresent(api -> {
            // For each BlueMap map, save markers to a json file.
            for (BlueMapMap map : blueMapMaps) {
                File markerFile = new File(folder, map.getId() + ".json");
                MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                if (markerSet == null) continue;

                try (FileWriter writer = new FileWriter(markerFile)) {
                    MarkerGson.INSTANCE.toJson(markerSet, writer);
                } catch (IOException ex) {
                    // handle io-exception
                    getLogger().severe("Unable to save markers: " + ex.getMessage());
                }
            }
        });
    }

    public void loadBannerMarkers() {
        getLogger().info("Loading markers...");

        BlueMapAPI.getInstance().ifPresent(api -> {
            // For each BlueMap map, retrieve the marker set file if it exists then write to map marker set.
            for (BlueMapMap map : blueMapMaps) {
                File markerFile = new File(getDataFolder(), "banners/" + map.getId() + ".json");
                if (!markerFile.exists()) continue;

                try (FileReader reader = new FileReader(markerFile)) {
                    MarkerSet markerSet = MarkerGson.INSTANCE.fromJson(reader, MarkerSet.class);
                    if (markerSet == null) continue;    // Skip empty marker sets

                    markerSet.setDefaultHidden(config.getBoolean("markers.hidden"));
                    map.getMarkerSets().put(markerSetId, markerSet);
                } catch (IOException ex) {
                    // handle io-exception
                    getLogger().severe("Unable to load markers: " + ex.getMessage());
                }
            }
        });
    }
}
