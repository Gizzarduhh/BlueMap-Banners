package me.Gizzarduhh.blueMapBanners.listener;

import me.Gizzarduhh.blueMapBanners.BlueMapBanners;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class BannerListener implements Listener {

    private final BlueMapBanners plugin;

    public BannerListener(BlueMapBanners plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBannerPlace(BlockPlaceEvent event) {
        if (
                event.getBlock().getState() instanceof Banner banner
                && event.getItemInHand().getItemMeta().hasCustomName()
        ) {
            String name = PlainTextComponentSerializer.plainText().serialize(event.getItemInHand().displayName());
            name = name.substring(1, name.length() - 1); // Strip brackets from each end
            plugin.addBannerMarker(banner, name, event.getPlayer());
        }
    }

    @EventHandler
    public void onBannerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if banner broke
        if (block.getState() instanceof Banner banner) {
            plugin.removeBannerMarker(banner, player);
        }

        // Check if block broke adjacent banners
        for (BlockFace face : BlockFace.values()) {
            Block relative = block.getRelative(face);
            if (relative.getState() instanceof Banner adjBanner) plugin.removeBannerMarker(adjBanner, player);
        }
    }

    @EventHandler
    public void onBannerExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            // Check if any banners broke
            if (block.getState() instanceof Banner banner) plugin.removeBannerMarker(banner, null);

            // Check if blocks broke adjacent banners
            for (BlockFace face : BlockFace.values()) {
                Block relative = block.getRelative(face);
                if (relative.getState() instanceof Banner adjBanner) plugin.removeBannerMarker(adjBanner, null);
            }
        }
    }
}
