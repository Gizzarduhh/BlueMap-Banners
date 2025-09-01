package me.Gizzarduhh.blueMapBanners.listener;

import me.Gizzarduhh.blueMapBanners.BlueMapBanners;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BannerListener implements Listener {

    private final BlueMapBanners plugin;

    public BannerListener(BlueMapBanners plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        // Do nothing if player is sneaking
        if (event.getPlayer().isSneaking()) return;

        // Only handle Banners
        Block block = event.getBlockPlaced();
        if (!block.getType().name().endsWith("_BANNER")) return;

        // Add banners with custom name as marker
        String name = PlainTextComponentSerializer.plainText().serialize(event.getItemInHand().displayName());
        name = name.substring(1, name.length() - 1); // Strip brackets from each end
        if (event.getItemInHand().getItemMeta().hasCustomName())
            plugin.addBannerMarker(block, name, event.getPlayer());
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        // Only handle Banners
        Block block = event.getBlock();
        if (!block.getType().name().endsWith("_BANNER")) return;

        plugin.removeBannerMarker(block, event.getPlayer());
    }
}
