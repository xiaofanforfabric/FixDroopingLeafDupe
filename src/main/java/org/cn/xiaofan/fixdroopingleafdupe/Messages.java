package org.cn.xiaofan.fixdroopingleafdupe;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 多语言消息管理。
 *
 * <p>语言文件位于插件目录 {@code lang/<language>.yml}，内置 {@code zh_cn} 与
 * {@code en_us}。首次运行时会把内置语言文件释放到插件目录，方便服主自定义。</p>
 */
public class Messages {

    private static final String[] BUNDLED = {"zh_cn", "en_us"};
    private static final String DEFAULT_LANGUAGE = "zh_cn";

    private final FixDroopingLeafDupe plugin;
    private final Map<String, String> messages = new HashMap<>();
    private String prefix = "";
    private String language = DEFAULT_LANGUAGE;

    public Messages(FixDroopingLeafDupe plugin) {
        this.plugin = plugin;
    }

    /**
     * 释放内置语言文件并加载配置指定的语言。
     */
    public void load() {
        for (String name : BUNDLED) {
            try {
                plugin.saveResource("lang/" + name + ".yml", false);
            } catch (IllegalArgumentException ignored) {
                // 资源缺失时忽略
            }
        }

        String configured = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
        File file = langFile(configured);
        if (file == null || !file.exists()) {
            plugin.getLogger().warning("未找到语言文件 lang/" + configured + ".yml，回退到 " + DEFAULT_LANGUAGE);
            file = langFile(DEFAULT_LANGUAGE);
        }

        messages.clear();
        prefix = "";
        if (file != null && file.exists()) {
            language = file.getName().replaceFirst("\\.yml$", "");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            prefix = yaml.getString("prefix", "");
            for (String key : yaml.getKeys(false)) {
                if ("prefix".equals(key)) {
                    continue;
                }
                String value = yaml.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        } else {
            plugin.getLogger().warning("无法加载任何语言文件，将使用空消息。");
        }
    }

    private File langFile(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        // 防目录穿越
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return null;
        }
        return new File(new File(plugin.getDataFolder(), "lang"), name + ".yml");
    }

    public String getLanguage() {
        return language;
    }

    /**
     * 读取一条带前缀、已翻译颜色代码的消息。缺失的键会原样返回键名，便于发现。
     */
    public String get(String key) {
        String raw = messages.getOrDefault(key, key);
        return ChatColor.translateAlternateColorCodes('&', prefix + raw);
    }
}
