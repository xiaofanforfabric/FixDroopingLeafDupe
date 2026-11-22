package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.plugin.java.JavaPlugin;

public final class FixDroopingLeafDupe extends JavaPlugin {

    private MalformedDripleafCleaner cleaner;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        messages.load();

        cleaner = new MalformedDripleafCleaner(this);
        getServer().getPluginManager().registerEvents(new DripleafListener(this), this);
        cleaner.seedLoadedChunks();

        if (getConfig().getBoolean("auto-clean.enabled", true)) {
            long interval = Math.max(1L, getConfig().getLong("auto-clean.interval-ticks", 100L));
            SchedulerCompat.runGlobalTimer(this, cleaner::tick, interval);
        }

        getLogger().info("FixDroopingLeafDupe 已启用（垂滴叶复制防护，Folia="
                + SchedulerCompat.isFolia() + "，language=" + messages.getLanguage() + "）。");
    }

    @Override
    public void onDisable() {
        SchedulerCompat.cancelAll(this);
        getLogger().info("FixDroopingLeafDupe 已禁用。");
    }

    public MalformedDripleafCleaner getCleaner() {
        return cleaner;
    }

    /** 是否输出调试日志（-Dfixdroopingleafdupe.debug=true）。 */
    public boolean isDebug() {
        return Boolean.getBoolean("fixdroopingleafdupe.debug");
    }

    /** 读取并翻译（颜色代码）一条带前缀的多语言消息。 */
    public String message(String path) {
        return messages.get(path);
    }
}
