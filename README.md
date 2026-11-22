# FixDroopingLeafDupe

中文 | [English](README_EN.md)

一个用于修复 **小型垂滴叶复制（Dripleaf Universal Dupe）** 漏洞的 Paper 服务端插件。

> 适用：Paper 及其分支（含 Folia），Minecraft **1.17 ~ 26.1.2**（漏洞受影响版本区间）。

---

## 漏洞原理

该漏洞由「畸形垂滴叶 + 骨粉」触发，利用的是服务端实现缺陷（非原版特性）：

1. 利用 **Wall Update Skipper（更新跳过器）** 造出一个只有上半部分、下方为空气的畸形小型垂滴叶。
2. 玩家对其使用骨粉。Spigot/Paper 在执行骨粉逻辑前会开启 `captureTreeGeneration` 状态标记
   （使方块变化暂存而不立即写入世界），本应在结束时关闭。
3. 由于畸形结构导致上下半判断异常、关闭该标记的代码未被执行，标记卡死。
4. 标记卡死时破坏方块 → **物品掉落但方块不消失**；再用不可放置物品右键解除异常 → 再次破坏同一方块，
   即可重复获得物品，实现复制。

Paper 26.2 已在服务端源码的 `finally` 块中补上清理逻辑修复此问题；本插件用于为**更早版本**提供防护。

---

## 修复策略

- **拦截畸形叶施肥**：监听 `BlockFertilizeEvent`，若目标垂滴叶上下半结构不完整，
  取消施肥并清除该畸形方块，从根源上避免 `captureTreeGeneration` 被卡死。
- **自动清除畸形叶**：维护已加载区块集合，定时轮转扫描并清除畸形垂滴叶；区块加载时也会立即扫描。

---

## 功能特性

- 施肥拦截 + 畸形结构校验
- 定时 / 区块加载自动清理
- 高度可配（开关、扫描间隔、每轮扫描区块数等）
- 多语言（内置 `zh_cn`、`en_us`，可自定义）
- 兼容 **1.17 ~ 26.1.2**，并适配 **Folia** 区域线程模型
- 无需命令、无权限节点，开箱即用

---

## 兼容性

| 服务端 | 支持情况 |
| --- | --- |
| Paper / Paper 分支 | 1.17 ~ 26.1.2（受漏洞影响版本） |
| Folia | 支持（`folia-supported: true`） |
| Spigot | 依赖 `org.bukkit.event.block.BlockFertilizeEvent`，建议使用 Paper 系列 |

> 插件以最老版本 `paper-api:1.17` 编译，仅使用向后稳定存在的 API，运行于更新版本无需重新编译。

---

## 安装

1. 将 `FixDroopingLeafDupe-*.jar` 放入服务端 `plugins/` 目录。
2. 启动服务器，插件会自动生成 `config.yml` 与 `lang/` 语言文件。

---

## 配置

`plugins/FixDroopingLeafDupe/config.yml`：

```yaml
# 拦截对「畸形垂滴叶」使用骨粉
cancel-fertilize: true

# 自动清除已经存在的畸形垂滴叶
auto-clean:
  enabled: true
  # 定时扫描间隔（tick，20 tick = 1 秒）
  interval-ticks: 100
  # 每次定时扫描处理的已加载区块数量（越大越耗性能）
  chunks-per-run: 4
  # 区块加载时是否立即扫描一次该区块
  on-chunk-load: true

# 语言：对应插件目录下 lang/<language>.yml（内置 zh_cn、en_us）
language: zh_cn
```

| 配置项 | 说明 | 默认 |
| --- | --- | --- |
| `cancel-fertilize` | 是否拦截畸形叶施肥 | `true` |
| `auto-clean.enabled` | 是否启用自动清理 | `true` |
| `auto-clean.interval-ticks` | 定时扫描间隔（tick） | `100` |
| `auto-clean.chunks-per-run` | 每轮扫描区块数 | `4` |
| `auto-clean.on-chunk-load` | 区块加载时立即扫描 | `true` |
| `language` | 语言文件名（不含 `.yml`） | `zh_cn` |

---

## 多语言

语言文件位于 `plugins/FixDroopingLeafDupe/lang/<language>.yml`，内置：

- `lang/zh_cn.yml`
- `lang/en_us.yml`

文件结构：

```yaml
prefix: "&8[&c垂滴叶修复&8] &r"
fertilizer-cancelled: "&c检测到畸形垂滴叶，已拦截施肥并清除。"
```

自定义语言步骤：

1. 在 `lang/` 下新建 `xxx.yml` 并填写键值。
2. 将 `config.yml` 的 `language` 设为 `xxx`。

> 注意：内置语言的自动释放依赖 `Messages.BUNDLED`，新增内置语言时需同步该列表。

---

## 命令与权限

无。插件全自动运行，不需要任何命令或权限节点。

---

## 开发者日志

启动时加 JVM 参数 `-Dfixdroopingleafdupe.debug=true` 可输出每次清理/拦截的调试日志。

---

## 构建

环境：JDK 16+、Gradle 8.x。

```bash
./gradlew jar
# 或使用本地 Gradle
gradle jar
```

产物：`build/libs/FixDroopingLeafDupe-<version>.jar`

---

## 项目结构

```
src/main/java/org/cn/xiaofan/fixdroopingleafdupe/
├── FixDroopingLeafDupe.java        # 主类：启用/禁用、调度
├── DripleafListener.java           # 事件监听（施肥拦截、区块加载/卸载）
├── DripleafUtil.java               # 垂滴叶结构判定与清除
├── MalformedDripleafCleaner.java   # 定时/按区块自动清理
├── SchedulerCompat.java            # Bukkit/Folia 调度兼容层
└── Messages.java                   # 多语言消息
src/main/resources/
├── plugin.yml
├── config.yml
└── lang/
    ├── zh_cn.yml
    └── en_us.yml
```

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

## 免责声明

本插件仅用于修复服务端漏洞、维护服务器经济与游戏公平性。
请勿将其用于任何违反服务端条款或侵害他人权益的用途。
