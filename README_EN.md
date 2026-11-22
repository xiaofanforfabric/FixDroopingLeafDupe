# FixDroopingLeafDupe

A Paper server plugin that fixes the **Dripleaf Universal Dupe** vulnerability.

[中文文档](README.md) | English

> Works with: Paper and its forks (including Folia), Minecraft **1.17 ~ 26.1.2** (the affected version range).

---

## How the exploit works

The dupe is triggered by a "malformed dripleaf + bone meal" and abuses a server implementation flaw (not vanilla behavior):

1. Using a **Wall Update Skipper**, the player creates a malformed small dripleaf with only its upper half and air below it.
2. The player applies bone meal to it. Before running the bone-meal logic, Spigot/Paper enables the
   `captureTreeGeneration` state flag (block changes are staged instead of being written to the world
   immediately), which is supposed to be disabled when the logic finishes.
3. Because the malformed structure makes the upper/lower-half check misbehave, the code that disables the flag
   is never executed, so the flag gets stuck.
4. While the flag is stuck, breaking a block drops the item **but the block does not disappear**. Then the player
   uses a non-placeable item to clear the abnormal state → breaks the same block again, and obtains the item again.

Paper 26.2 fixed this server-side by adding cleanup logic in a `finally` block. This plugin provides protection
for **older versions**.

---

## Fix strategy

- **Block fertilization on malformed dripleafs**: listen to `BlockFertilizeEvent`; if the target dripleaf has an
  incomplete upper/lower half structure, cancel the fertilization and remove the malformed block, preventing
  `captureTreeGeneration` from getting stuck at the source.
- **Automatically remove malformed dripleafs**: track loaded chunks, scan them in a rotating, budgeted manner, and
  remove malformed dripleafs; a chunk is also scanned immediately when it loads.

---

## Features

- Fertilization interception + malformed-structure validation
- Periodic or on-chunk-load automatic cleanup
- Highly configurable (toggle, scan interval, chunks per run, etc.)
- Internationalization (bundled `zh_cn`, `en_us`; customizable)
- Compatible with **1.17 ~ 26.1.2** and adapted to **Folia**'s region-thread model
- No commands, no permissions — works out of the box

---

## Compatibility

| Server | Support |
| --- | --- |
| Paper / Paper forks | 1.17 ~ 26.1.2 (affected versions) |
| Folia | Supported (`folia-supported: true`) |
| Spigot | Relies on `org.bukkit.event.block.BlockFertilizeEvent`; Paper-based servers are recommended |

> The plugin is compiled against the oldest `paper-api:1.17` and only uses APIs that remain stable across
> versions, so it runs on newer versions without recompiling.

---

## Installation

1. Place `FixDroopingLeafDupe-*.jar` into the server's `plugins/` directory.
2. Start the server; `config.yml` and the `lang/` files are generated automatically.

---

## Configuration

`plugins/FixDroopingLeafDupe/config.yml`:

```yaml
# Block bone meal on malformed dripleafs
cancel-fertilize: true

# Automatically remove existing malformed dripleafs
auto-clean:
  enabled: true
  # Scan interval (ticks, 20 ticks = 1 second)
  interval-ticks: 100
  # Number of loaded chunks processed per scan (higher = more CPU)
  chunks-per-run: 4
  # Scan a chunk immediately when it loads
  on-chunk-load: true

# Language: corresponds to lang/<language>.yml (bundled: zh_cn, en_us)
language: zh_cn
```

| Option | Description | Default |
| --- | --- | --- |
| `cancel-fertilize` | Block bone meal on malformed dripleafs | `true` |
| `auto-clean.enabled` | Enable automatic cleanup | `true` |
| `auto-clean.interval-ticks` | Scan interval in ticks | `100` |
| `auto-clean.chunks-per-run` | Chunks scanned per run | `4` |
| `auto-clean.on-chunk-load` | Scan chunk immediately on load | `true` |
| `language` | Language file name (without `.yml`) | `zh_cn` |

---

## Internationalization

Language files live in `plugins/FixDroopingLeafDupe/lang/<language>.yml`. Bundled:

- `lang/zh_cn.yml`
- `lang/en_us.yml`

File structure:

```yaml
prefix: "&8[&cDripleafFix&8] &r"
fertilizer-cancelled: "&cMalformed dripleaf detected; fertilization blocked and block removed."
```

Adding a custom language:

1. Create `xxx.yml` under `lang/` and fill in the keys.
2. Set `language` in `config.yml` to `xxx`.

> Note: bundled-language auto-extraction relies on `Messages.BUNDLED`; add the new language there if you want it
> bundled.

---

## Commands & Permissions

None. The plugin runs fully automatically and needs no commands or permissions.

---

## Debug logging

Add the JVM argument `-Dfixdroopingleafdupe.debug=true` to log every removal/interception.

---

## Building

Requirements: JDK 16+, Gradle 8.x.

```bash
./gradlew jar
# or with a local Gradle
gradle jar
```

Output: `build/libs/FixDroopingLeafDupe-<version>.jar`

---

## Project layout

```
src/main/java/org/cn/xiaofan/fixdroopingleafdupe/
├── FixDroopingLeafDupe.java        # Main class: enable/disable, scheduling
├── DripleafListener.java           # Event listeners (fertilization, chunk load/unload)
├── DripleafUtil.java               # Dripleaf structure validation & removal
├── MalformedDripleafCleaner.java   # Periodic / per-chunk automatic cleanup
├── SchedulerCompat.java            # Bukkit/Folia scheduler compatibility layer
└── Messages.java                   # i18n messages
src/main/resources/
├── plugin.yml
├── config.yml
└── lang/
    ├── zh_cn.yml
    └── en_us.yml
```

---

## License

This project is open source under the [MIT License](LICENSE).

## Disclaimer

This plugin is intended solely to fix a server vulnerability and to maintain server economy and fair play.
Do not use it for any purpose that violates server terms or harms others.
