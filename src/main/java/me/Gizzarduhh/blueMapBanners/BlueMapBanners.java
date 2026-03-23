package me.Gizzarduhh.blueMapBanners;

import com.google.gson.Gson;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.gson.MarkerGson;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import me.Gizzarduhh.blueMapBanners.listener.BannerListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Banner;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.*;
import java.io.*;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;


public final class BlueMapBanners extends JavaPlugin {

    public static final Gson MARKER_GSON = MarkerGson.INSTANCE;
    private final NamespacedKey markerIdKey = new NamespacedKey(this,"markerId");
    private final String markerSetId = "bm-banners";
    private HashMap<BlueMapWorld, MarkerSet> worldMarkerSets;
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
            worldMarkerSets = new HashMap<>();

            reloadConfig();
            config = getConfig();

            loadBannerMarkers(api);
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

    public void addBannerMarker(Banner banner, String name, Player player) {
        BlueMapAPI.getInstance().ifPresent(api -> {
            // Get BlueMap map banner was placed within
            Optional<BlueMapWorld> blueMapWorld = api.getWorld(banner.getWorld());
            if (blueMapWorld.isEmpty()) {
                getLogger().severe("Error adding banner, " + name + ": BlueMap world '" + banner.getWorld().getName() + "' not found.");
                return;
            }

            // Create UUID and store it in banner data container
            String markerId = "bm-banner-" + UUID.randomUUID();
            banner.getPersistentDataContainer().set(markerIdKey, PersistentDataType.STRING, markerId);
            banner.update();

            // Write asset to "global" webapp storage if not already there
            String asset = "banners/" + banner.getType().name()
                    .toLowerCase()
                    .replace("_wall","") + ".png";
            try (InputStream in = getResource(asset)) {
                final Path toPath = api.getWebApp().getWebRoot().resolve("assets/" + asset);
                if (in != null && !Files.exists(toPath)) {
                    Files.createDirectories(toPath.getParent());
                    try (OutputStream out = Files.newOutputStream(toPath)) {
                        in.transferTo(out);
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
                    .position(banner.getX()+0.5, banner.getY()+0.5, banner.getZ()+0.5) // +0.5 to center the marker in the block
                    .icon("assets/" + asset,12,16)
                    .maxDistance(1000)
                    .build();

            // Get existing markerSet from HashMap or create new
            MarkerSet markerSet = worldMarkerSets.computeIfAbsent(blueMapWorld.get(), bmWorld -> {
                MarkerSet newMarkerSet = MarkerSet.builder()
                        .defaultHidden(config.getBoolean("markers.hidden"))
                        .label("Banners")
                        .build();
                // Add this new MarkerSet to all maps of this world
                bmWorld.getMaps().forEach(bmMap -> bmMap.getMarkerSets().put(markerSetId, newMarkerSet));
                return newMarkerSet;
            });

            // Add bannerMarker to markerSet
            markerSet.getMarkers()
                    .put(markerId, bannerMarker);
        });

        if (config.getBoolean("messages.enabled"))
            player.sendMessage(Component
                    .text(config.getString("messages.+marker", "%banner% added to BlueMap!")
                            .replace("%banner%", name))
                    .color(NamedTextColor.GRAY));
    }

    public void removeBannerMarker(Banner banner, Player player) {
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
            PersistentDataContainer pdc = banner.getPersistentDataContainer();
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
        });
    }

    public void saveBannerMarkers() {
        getLogger().info("Saving markers...");

        // Create banners save directory
        File folder = new File(getDataFolder(), "banners");
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().severe("Failed to create banners directory!");
            return;
        }

        // For each BlueMap map, save markers to a json file.
        worldMarkerSets.forEach((blueMapWorld, markerSet) -> {
            if (markerSet.getMarkers().isEmpty()) return;

            File markerFile = new File(folder, blueMapWorld.getId() + ".json");
            try (FileWriter writer = new FileWriter(markerFile)) {
                MARKER_GSON.toJson(markerSet, writer);
            } catch (IOException ex) {
                // handle io-exception
                getLogger().severe("Unable to save markers: " + ex.getMessage());
            }
        });
    }

    public void loadBannerMarkers(BlueMapAPI api) {
        getLogger().info("Loading markers...");

        // Get file of each map
        File bannerDir = new File(getDataFolder(), "banners");
        if (!bannerDir.exists()) return;
        File[] markerFiles = bannerDir.listFiles();
        if (markerFiles == null) return;
        for (File markerFile : markerFiles) {
            if (!markerFile.getName().endsWith(".json")) return;

            // Get marker set from file
            try (FileReader reader = new FileReader(markerFile)) {
                MarkerSet markerSet = MARKER_GSON.fromJson(reader, MarkerSet.class);
                if (markerSet == null) return;

                // Apply config
                markerSet.setDefaultHidden(config.getBoolean("markers.hidden"));

                // Apply marker set to the world
                String worldId = markerFile.getName().replaceAll("\\.json$", "");
                Optional<BlueMapWorld> oBlueMapWorld = api.getWorld(worldId);
                oBlueMapWorld.ifPresent(blueMapWorld -> {
                    worldMarkerSets.put(blueMapWorld, markerSet);
                    // Add this loaded MarkerSet to all maps of this world
                    blueMapWorld.getMaps().forEach(blueMapMap -> blueMapMap.getMarkerSets().put(markerSetId, markerSet));
                });
            } catch (IOException ex) {
                // handle io-exception
                getLogger().severe("Unable to load markers: " + ex.getMessage());
            }
        }
    }
}
