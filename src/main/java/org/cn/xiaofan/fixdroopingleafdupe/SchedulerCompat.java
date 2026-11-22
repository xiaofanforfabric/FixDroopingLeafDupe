package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * 调度器兼容层：同时兼容 Bukkit/Paper 的经典调度器与 Folia 的区域调度器。
 *
 * <p>Folia 的 {@code io.papermc.paper.threadedregions.scheduler.*} 类在 1.17 的
 * paper-api 上并不存在，因此为避免提高编译期依赖，这里统一通过反射调用。</p>
 */
public final class SchedulerCompat {

    private static final boolean FOLIA = detectFolia();

    private SchedulerCompat() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * 在拥有 {@code (world, chunkX, chunkZ)} 的区域线程上执行任务；普通服在主线程执行。
     */
    public static void executeAtChunk(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Method execute = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler")
                    .getMethod("execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
            execute.invoke(regionScheduler, plugin, world, chunkX, chunkZ, task);
        } catch (Throwable t) {
            plugin.getLogger().warning("Folia 区域调度失败: " + t);
        }
    }

    /**
     * 注册一个按固定周期执行的全局任务（Folia 下使用全局区域调度器）。
     */
    public static void runGlobalTimer(Plugin plugin, Runnable task, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, period, period);
            return;
        }
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method runAtFixedRate = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler")
                    .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Consumer<Object> consumer = ignored -> task.run();
            runAtFixedRate.invoke(globalScheduler, plugin, consumer, 1L, period);
        } catch (Throwable t) {
            plugin.getLogger().warning("Folia 全局调度失败: " + t);
        }
    }

    /**
     * 取消本插件的所有调度任务。
     */
    public static void cancelAll(Plugin plugin) {
        try {
            if (!FOLIA) {
                Bukkit.getScheduler().cancelTasks(plugin);
            } else {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method cancel = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler")
                        .getMethod("cancelTasks", Plugin.class);
                cancel.invoke(globalScheduler, plugin);
            }
        } catch (Throwable ignored) {
            // 忽略
        }
    }
}
