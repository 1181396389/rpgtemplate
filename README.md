# rpgtemplate (万象模板) — Minecraft 1.21.1 NeoForge

面向 Minecraft `1.21.1` / NeoForge `21.1.x` 的 RPG 跨次元模板模组完整 Mojmap 独立源码仓库（分支 `neoforge`；Forge 1.20.1 版本请见 `main` 分支）。

## 直接构建可运行 `.jar`

环境要求：**仅需安装 JDK 21+**（无需预先安装 Gradle、Maven 或基础二进制 JAR；全量 290 个 Java 源码文件直接从零编译）。

### Linux / macOS / Git Bash

```bash
./build.sh
```

### Windows（CMD / PowerShell）

```bat
build.bat
```

构建完成后，可直接放入 NeoForge `1.21.1` 客户端/服务端 `mods/` 目录的成品位于：

```text
dist/rpgtemplate-0.4.14-neoforge-1.21.1.jar
```

同时仓库已提供标准 ModDevGradle `2.0.78` 工程文件（`build.gradle` / `settings.gradle` / `gradle.properties`），可直接使用已安装的 Gradle 8.8+ / JDK 21 执行 `gradle build` 或在 IDE 中导入开发。

### 常用可选参数

```bash
# 指定输出版本号（默认读取 build.properties 中的 mod_version）
./build.sh --version 0.4.15

# 指定 NeoForge 1.21.1 SDK 缓存目录（默认 .build-cache）
./build.sh --cache-dir /path/to/cache

# 清理 build/ 后重新编译
./build.sh --clean
```

首次运行会自动下载并缓存官方 NeoForge `1.21.1-21.1.266` Mojmap 编译环境与 Minecraft `1.21.1` 依赖库到 `.build-cache/`；后续增量构建仅需数秒。

### GitHub Actions 自动构建

每次推送到 `neoforge` 分支、提交 Pull Request 或在仓库 **Actions → Build Mod JAR (NeoForge 1.21.1)** 手动点击 **Run workflow** 时，GitHub Actions 会使用 JDK 21 自动执行 `./build.sh --clean`，并在 **Artifacts** 中提供可直接下载的 `rpgtemplate-neoforge-1.21.1-jar`。推送 `v*-neoforge*` 标签（如 `v0.4.15-neoforge-1.21.1`）会自动创建对应 GitHub Release 并附带成品 `.jar`。

## 构建流水线与 1.21.1 迁移要点

- **彻底脱离 Forge 1.20.1 `base/` 二进制依赖**：全部 `290` 个 Java 源码已完整恢复至 Mojmap 官方命名并全量迁移至 NeoForge `21.1` / Minecraft `1.21.1` API（`CustomPacketPayload` + `StreamCodec`、`AttachmentType`、`DataComponents.CUSTOM_DATA`、`ResourceLocation` 属性修饰符、全新渲染管线与 `JAVA_21` Mixin）。
- **完整内容覆盖**：包含假面骑士 Cronus（EX，含客户端时空定格 Mixin 与优化版图集）、一无所有（Nothing There / EX）、宿傩 / 诅咒之王（含四手层、神龛与灶火着色器）、认知阻碍面具、过渡模板（宇智波鼬 / 佐藤和真 / 灶门炭治郎 / 旅人）、灵魂夺舍与双层战术技能栏。
- **内置打包后自检 (`tools/BuildNeoForgeMod.java`)**：校验 `META-INF/neoforge.mods.toml`、全部 3 份 `JAVA_21` Mixin 配置、全部 `463` 个编译类的跨类字节码链接完整性与零 `net.minecraftforge.*` 残留、全部 11 份模板 JSON、28 处 `sounds.json` 音效 OGG 引用、13 处物品模型贴图引用及 719 键双语语言一致性。

## 目录结构

- `src/main/java/`：全量 290 个 NeoForge 1.21.1 / JDK 21 Mojmap 源码文件。
- `src/main/resources/`：完整模组资源（`META-INF/neoforge.mods.toml`、`pack.mcmeta` (format 34/48)、`assets/` 贴图/模型/音效/着色器/语言、`data/` 1.21.1 单数化标签与模板数据、3 份 `JAVA_21` Mixin JSON）。
- `build.gradle`、`settings.gradle`、`gradle.properties`：标准 NeoForge ModDevGradle 工程配置。
- `tools/BuildNeoForgeMod.java`、`build.sh`、`build.bat`、`build.properties`：零外部依赖的跨平台 JDK 21 直接构建工具。
- `docs/`、`skills/`、`AGENTS.md`：模板设计与开发规范文档。
