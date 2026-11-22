package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * 事件监听：
 * <ul>
 *     <li>{@link BlockFertilizeEvent}：拦截对畸形垂滴叶使用骨粉，并顺手清除该畸形方块；</li>
 *     <li>{@link ChunkLoadEvent} / {@link ChunkUnloadEvent}：维护已加载区块集合，
 *     并在区块加载时派发一次扫描（可配置）。</li>
 * </ul>
 */
public class DripleafListener implements Listener {

    private final FixDroopingLeafDupe plugin;

    public DripleafListener(FixDroopingLeafDupe plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (!plugin.getConfig().getBoolean("cancel-fertilize", true)) {
            return;
        }
        Block block = event.getBlock();
        if (!DripleafUtil.isMalformed(block)) {
            return;
        }

        event.setCancelled(true);
        DripleafUtil.clear(block);

        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(plugin.message("fertilizer-cancelled"));
        }
        if (plugin.isDebug()) {
            plugin.getLogger().info("已拦截对畸形垂滴叶的施肥: "
                    + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        plugin.getCleaner().track(chunk);

        if (!plugin.getConfig().getBoolean("auto-clean.enabled", true)
                || !plugin.getConfig().getBoolean("auto-clean.on-chunk-load", true)) {
            return;
        }
        // 派发到拥有该区块的区域线程（Folia）/ 主线程（普通服）执行
        SchedulerCompat.executeAtChunk(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(),
                () -> plugin.getCleaner().scanChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.getCleaner().untrack(event.getChunk());
    }
}
