# rpgtemplate (万象模板) — Minecraft 1.20.1 Forge

面向 Minecraft `1.20.1` / Forge `47.1.0+` 的 RPG 跨次元模板模组源码与直接构建仓库。

## 直接构建可运行 `.jar`

环境要求：**仅需安装 JDK 17+**（无需预先安装 Gradle、Maven 或手动解压基础包）。

### Linux / macOS / Git Bash

```bash
./build.sh
```

### Windows（CMD / PowerShell）

```bat
build.bat
```

构建完成后，可直接放入客户端/服务端 `mods/` 目录的成品位于：

```text
dist/rpgtemplate-0.4.14.jar
```

### 常用可选参数

```bash
# 指定输出版本号（默认读取 build.properties 中的 mod_version）
./build.sh --version 0.4.15

# 指定其他基础 JAR 路径（默认使用 base/rpgtemplate-0.4.14.jar）
./build.sh --base-jar /path/to/rpgtemplate-0.4.14.jar

# 指定 Forge/Minecraft SDK 缓存目录（默认 .build-cache）
./build.sh --cache-dir /path/to/cache

# 清理 build/ 后重新编译
./build.sh --clean
```

首次运行会自动下载并缓存官方 Forge `1.20.1-47.3.0` SRG 运行环境与 Minecraft `1.20.1` 依赖库到 `.build-cache/`（约 20 秒）；后续增量构建仅需数秒。

### GitHub Actions 自动构建

每次推送到 `main` 分支、提交 Pull Request 或在仓库 **Actions → Build Mod JAR** 手动点击 **Run workflow** 时，GitHub Actions 都会自动完成编译、打包与字节码校验，并在构建详情页的 **Artifacts** 中提供可直接下载的 `rpgtemplate-jar`。推送 `v*` 标签（如 `v0.4.15`）会自动创建对应的 GitHub Release 并附带成品 `.jar`。

## 构建流水线说明

`tools/BuildMod.java` 会自动完成以下步骤：

1. **拉取官方 Forge 1.20.1 SRG 编译环境**：使用官方 Forge Installer 生成真实 `forge-client.jar` / `forge-universal.jar` / `client-srg.jar`，并拉取 Minecraft `1.20.1` 依赖库。
2. **全量编译覆盖源码**：使用 JDK 17 (`--release 17 -encoding UTF-8`) 编译 `build0414-src/` 下全部 Java 源码。
3. **自动清理与打包**：
   - 自动剔除基础包中已废弃的 `production/`（职业/Build 系统）旧类、旧配方、旧战利品表、旧贴图/模型、`rpgtemplate.build.client.mixins.json` 以及曾劫持原版粒子的 `rpgtemplate.fx.mixins.json` / `ServerLevelParticleMixin` / `ClientLevelParticleMixin`（保留兼容桩 `CraftMath`）。
   - 自动剔除被覆盖外部类对应的旧匿名/内部类（`Outer$*.class`），防止残留字节码冲突。
   - 自动将 `build0414-resources/*_sounds_fragment.json` 合并写入 `assets/rpgtemplate/sounds.json`，且不会泄露碎片文件到 JAR 根目录。
   - 自动重写 `META-INF/MANIFEST.MF`（仅保留有效 Mixin 配置 `rpgtemplate.nothingthere.mixins.json`）与 `META-INF/mods.toml` 版本号。
4. **内置打包后自检**：校验 `MANIFEST.MF`、`mods.toml`、`RpgTemplateMod -> NtBootstrap`、合并后的 `sounds.json`、双语 `lang/*.json`、全部模板 JSON、全 JAR 内部类字节码链接完整性，并强制校验 `build0414-src/` 不引用原版廉价粒子且不劫持原版粒子系统。

## 目录结构

- `build0414-src/`：模组 Java 17 源码（SRG 命名），包含 Cronus、过渡模板、战术双层技能栏、拟态（Nothing There）、渲染特效、规则判定与模组入口 `RpgTemplateMod.java`。
- `build0414-resources/`：模板配置 JSON、中英文语言文件、Mixin 配置与音效片段。
- `base/rpgtemplate-0.4.14.jar`：构建所需的未覆盖基础类与模型/音效/着色器资源包。
- `tools/BuildMod.java`、`build.sh`、`build.bat`、`build.properties`：零外部依赖的跨平台直接构建工具。
- `docs/`、`skills/`、`AGENTS.md`：模板设计与开发规范文档。
