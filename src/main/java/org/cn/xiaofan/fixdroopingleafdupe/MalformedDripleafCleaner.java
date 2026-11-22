package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自动清除畸形垂滴叶。
 *
 * <p>通过区块加载/卸载事件维护「已加载区块」集合，定时任务从集合中轮转挑选有限数量
 * 的区块进行扫描（{@code auto-clean.chunks-per-run}），并把每个区块的扫描派发到其
 * 所属区域线程执行，从而兼容 Folia 的线程模型（普通服则回到主线程执行）。</p>
 */
public class MalformedDripleafCleaner {

    private final FixDroopingLeafDupe plugin;
    /** 已加载区块集合：世界 UUID -> 打包后的区块坐标集合。 */
    private final Map<UUID, Set<Long>> trackedChunks = new ConcurrentHashMap<>();
    /** 轮转游标（仅在单一调度线程上访问）。 */
    private int cursor = 0;

    public MalformedDripleafCleaner(FixDroopingLeafDupe plugin) {
        this.plugin = plugin;
    }

    private static long key(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }

    /** 记住一个已加载区块。 */
    public void track(Chunk chunk) {
        trackedChunks.computeIfAbsent(chunk.getWorld().getUID(), k -> ConcurrentHashMap.newKeySet())
                .add(key(chunk.getX(), chunk.getZ()));
    }

    /** 忘记一个已卸载区块。 */
    public void untrack(Chunk chunk) {
        Set<Long> set = trackedChunks.get(chunk.getWorld().getUID());
        if (set != null) {
            set.remove(key(chunk.getX(), chunk.getZ()));
            if (set.isEmpty()) {
                trackedChunks.remove(chunk.getWorld().getUID(), set);
            }
        }
    }

    /**
     * 记录启动时已经加载的区块。Folia 下枚举已加载区块可能受限，异常会被忽略。
     */
    public void seedLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            try {
                for (Chunk chunk : world.getLoadedChunks()) {
                    track(chunk);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("无法枚举已加载区块(" + world.getName() + "): " + t);
            }
        }
    }

    /**
     * 定时任务入口：轮转挑选一批区块，并派发到各自区域线程扫描。
     */
    public void tick() {
        int budget = plugin.getConfig().getInt("auto-clean.chunks-per-run", 4);
        if (budget <= 0) {
            return;
        }
        List<ChunkRef> all = snapshot();
        if (all.isEmpty()) {
            cursor = 0;
            return;
        }
        int start = cursor % all.size();
        int count = Math.min(budget, all.size());
        for (int i = 0; i < count; i++) {
            ChunkRef ref = all.get((start + i) % all.size());
            SchedulerCompat.executeAtChunk(plugin, ref.world, ref.chunkX, ref.chunkZ,
                    () -> scanChunk(ref.world, ref.chunkX, ref.chunkZ));
        }
        cursor = (start + count) % all.size();
    }

    private List<ChunkRef> snapshot() {
        List<ChunkRef> list = new ArrayList<>();
        for (Map.Entry<UUID, Set<Long>> entry : trackedChunks.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            for (Long k : entry.getValue()) {
                list.add(new ChunkRef(world, keyX(k), keyZ(k)));
            }
        }
        return list;
    }

    /** 供区块加载事件直接调用（应在拥有该区块的区域线程上执行）。 */
    public void scanChunk(Chunk chunk) {
        scanChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    /**
     * 扫描指定区块并清除其中的畸形垂滴叶。必须在拥有该区块的区域线程上调用。
     */
    public void scanChunk(World world, int chunkX, int chunkZ) {
        Chunk chunk;
        try {
            chunk = world.getChunkAt(chunkX, chunkZ);
        } catch (Throwable t) {
            return;
        }
        int[] range = heightRange(world);
        int minY = range[0];
        int maxY = range[1];

        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Material type = snapshot.getBlockType(x, y, z);
                    if (!DripleafUtil.isDripleaf(type)) {
                        continue;
                    }
                    BlockData data = snapshot.getBlockData(x, y, z);
                    if (!(data instanceof Bisected self)) {
                        continue;
                    }

                    boolean malformed;
                    if (self.getHalf() == Bisected.Half.TOP) {
                        if (y - 1 < minY) {
                            malformed = true;
                        } else {
                            Material belowType = snapshot.getBlockType(x, y - 1, z);
                            BlockData belowData = snapshot.getBlockData(x, y - 1, z);
                            malformed = belowType != type
                                    || !(belowData instanceof Bisected below)
                                    || below.getHalf() != Bisected.Half.BOTTOM;
                        }
                    } else {
                        if (y + 1 > maxY) {
                            malformed = true;
                        } else {
                            Material aboveType = snapshot.getBlockType(x, y + 1, z);
                            BlockData aboveData = snapshot.getBlockData(x, y + 1, z);
                            malformed = aboveType != type || !(aboveData instanceof Bisected);
                        }
                    }

                    if (malformed) {
                        Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                        DripleafUtil.clear(block);
                        if (plugin.isDebug()) {
                            plugin.getLogger().info("已清除畸形垂滴叶: "
                                    + world.getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ());
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取世界的建筑高度范围。高版本（1.18+）存在 getMinHeight/getMaxHeight，
     * 低版本（1.17）回退为 0~255。
     */
    private static int[] heightRange(World world) {
        try {
            Method min = World.class.getMethod("getMinHeight");
            Method max = World.class.getMethod("getMaxHeight");
            return new int[]{(int) min.invoke(world), (int) max.invoke(world)};
        } catch (Exception e) {
            return new int[]{0, 255};
        }
    }

    /** 一个待扫描区块的轻量引用。 */
    private static final class ChunkRef {
        private final World world;
        private final int chunkX;
        private final int chunkZ;

        private ChunkRef(World world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }
}
