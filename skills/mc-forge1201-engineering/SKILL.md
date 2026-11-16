---
name: mc-forge1201-engineering
description: rpgtemplate（Minecraft Forge 1.20.1 SRG 直编模组）的工程开发、验证与 AI 协作纪律技能。当在本仓库新增/重构角色模板、武器、实体、渲染特效、Mixin、音效、贴图，或执行代码评审与多步骤功能拆解时激活；若涉及角色数值限制、副作用继承、四档克制判定或特效禁令，须同时遵守 skills/rpg-template-creation/SKILL.md。不适用于纯文案修改或与 Minecraft 模组无关的任务。
---

# Forge 1.20.1 直编工程与 AI 协作开发技能（`mc-forge1201-engineering`）

本技能吸收了 Minecraft AI 编程工具链的工程实践（详见 `docs/mc-ai-coding-assistant-tool-study.md`），并专门针对 `rpgtemplate` 的 **Forge 1.20.1 + Java 17 + SRG 源码直编（`tools/BuildMod.java`）** 架构定制。

---

## 一、开发前对齐与竖切片拆解

### 1. 模糊需求先对齐（避免大面积返工）
遇到全新角色模板、复杂多形态武器或大型视觉/音效系统且需求未完全定型时，先拆分三类分支并向用户确认：
- **创意枝（只能用户定）**：技能手感、数值强度定位、原作副作用与癖好、音效/视频素材出处、视觉美术风格。
- **技术枝（按 Forge 1.20.1 直编边界定）**：独立网络包协议、自定义 3D 渲染通道（`RenderLevelStageEvent` / `EntityRenderer` / `RenderLayer` / `RenderGuiOverlayEvent`）、Mixin 注入点与 `MANIFEST.MF` 登记。
- **代劳枝（可直接定，须交代）**：内部持久化 Key、缓存结构、内部工具类拆分——选定后简述理由即可，不琐碎打扰。

**完成判据**：所有创意枝均已有明确答案，不再依赖猜测假设才开工。

### 2. 按竖切片推进，不按横层堆砌
- **竖切片** = 每一片完整打通「注册/配置 → 服务端逻辑 → 网络同步 → 客户端渲染/音效 → 构建验证」，产出可独立验证的玩家可见行为。
- 禁止先写完 20 个空壳类或全部贴图却迟迟不编译；第一片先跑通最小闭环并过 `./build.sh --clean`。

---

## 二、SRG 零幻觉与出处实证铁律

`rpgtemplate` 的 `build0414-src/` 直接面向 Forge 1.20.1 官方 SRG 字节码编译，**绝不允许凭记忆猜测 SRG 方法名、字段名、Mixin 描述符或原版资源路径**：

1. **SRG 方法/字段先查后写**：不确定任何 Minecraft/Forge 类的方法名（`m_xxxxx_`）、字段名（`f_xxxxx_`）或参数类型时，必须先用 `javap -p` 查询构建缓存中的真实字节码（`.build-cache/` 或 `/tmp/rpg-build-cache/` 下的 `forge-universal.jar` / `client-srg.jar`）或检索仓库已有用法；未查证的符号必须标 `// TODO(未核实)` 并补查，不得硬猜。
2. **Mixin 精准锚定**：
   - 修改或新增 Mixin 类前，用 `javap -s` 核对目标方法的精确 JVM 描述符；
   - 严禁在 `@Inject` 中向构造函数修改 `final` 字段，严禁实例化 Mixin 类；
   - 新增独立 Mixin 配置文件时，必须同步检查 `tools/BuildMod.java` 的 `CLEAN_MIXIN_CONFIGS` 与输出 JAR 的 `META-INF/MANIFEST.MF`；
   - **绝对禁止**任何拦截/改写 `ServerLevel`/`ClientLevel` 原版粒子系统的 Mixin。
3. **资源路径实证**：代码中引用的任何 `new ResourceLocation("rpgtemplate", ...)` 贴图或音效，必须先确认对应 `.png` / `.ogg` 真实存在于 `build0414-resources/` 或 `base/rpgtemplate-0.4.14.jar` 内。纯白几何渲染纹理统一使用已验证存在的 `rpgtemplate:textures/fx/cronus_white.png`，**严禁**引用不存在的 `textures/misc/white.png`。

---

## 三、Forge 1.20.1 核心技术防坑清单

### 1. 物理端分离与线程安全
- **客户端代码严格隔离**：所有 `Minecraft`、`GuiGraphics`、`PoseStack`、`RenderLevelStageEvent`、`EntityRenderer`、`RenderLayer`、`KeyMapping` 相关代码必须位于 `client/` 子包，且注解 `@Mod.EventBusSubscriber(..., value = Dist.CLIENT)`。
- **通用/服务端包调用客户端**：网络消息处理或通用类中若需触发客户端行为，必须通过 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ...)` 隔离，杜绝专用服务端启动时因加载客户端类而抛出 `ClassNotFoundException`。
- **网络包线程切换**：`SimpleChannel` 回调必须用 `ctx.get().enqueueWork(() -> { ... })` 切回主线程，并调用 `ctx.get().setPacketHandled(true)`；高频状态（如持续激光/蓄力进度）合并字段打包，禁止逐字段刷包。

### 2. 渲染与实体规范
- **无廉价粒子铁律**：严禁引入 `net.minecraft.core.particles.*`；全部技能、领域、爆炸、光束与斩击必须走自定义 3D 几何渲染、Shader、实体渲染器或 HUD 叠加层。
- **渲染器无实例状态**：`EntityRenderer` / `RenderLayer` 实例在所有同类型实体间共享，禁止把单个实体的动画计时器存在 Renderer 字段中；应存于实体对象本身或按 `entityId` 映射的客户端状态表。
- **VertexFormat 一致性**：使用 `CronusRenderTypes.additive` / `translucent` / `pixelTranslucent` 时，确保顶点提交顺序严格匹配 `POSITION_COLOR_TEX`（`m_252986_` → `m_85950_` → `m_7421_` → `m_5752_`）。
- **高频 Tick 节流**：`PlayerTickEvent` / `ServerTickEvent` 中避免每 tick 全图遍历实体，非实时逻辑用 `now % 20L == 0L` 等节流，或改由事件触发。

### 3. 音效规范（`sounds.json` & OGG Vorbis）
- 注册统一使用 `SoundEvent.m_262824_(id)`（对应 `createVariableRangeEvent`），禁止使用废弃构造函数。
- 新音效片段放入 `build0414-resources/*_sounds_fragment.json`（构建时自动合并进 `assets/rpgtemplate/sounds.json`，不会泄露碎片文件到 JAR 根目录）。
- 每个 `sounds.json` 条目的 `sounds` 字段必须是数组，所有引用的 `rpgtemplate:<name>` 必须对应真实存在的 44.1 kHz 单声道 OGG Vorbis 文件（无缺失文件警告）。
- 音频流式策略：超过 ~10 秒的背景音乐/长演出音频建议评估 `"stream": true` 降低常驻内存；短促高频战斗/必杀/瞬移音效保持 `"stream": false` 保证零延迟瞬播。

### 4. 像素贴图与特效纹理规范
- **1× 原尺寸判据**：16×16 / 32×32 物品与图标贴图完成后必须按真实 1× 尺寸审视剪影与辨识度，不可只看放大图。
- **分层绘制顺序**：`1. 清晰剪影 → 2. 统一左上光源打光（暗部偏冷、亮部偏暖）→ 3. 内部细节 → 4. 深色同系描边 → 5. 高光/发光标记`。
- **纹理动画**：采用参数化生成竖排图集（总高度必须为单帧高度整数倍）+ 同名 `.png.mcmeta`，避免手画逐帧抖动。

---

## 四、分层验收阶梯与交付前双轴评审

每次代码或资源改动交付前，**由低到高逐层验收，低层未过禁止宣称完成**：

| 层级 | 检查内容 | 本仓库执行方式 |
|---|---|---|
| **L0 构建编译** | 全量 Java 17 编译与 JAR 组装无报错 | `JAVA_HOME=/tmp/jdk17 ./build.sh --clean --cache-dir /tmp/rpg-build-cache`（或本机 `./build.sh --clean`） |
| **L1 静态与字节码** | 460+ 类内部断链 = 0、无粒子引用、Mixin 配置存在、新类/新方法字节码核验 | `BuildMod.java` 自动验证 + `javap -cp dist/rpgtemplate-*.jar <变更类>` |
| **L2 资源与数据闭环** | `sounds.json` 全部引用 OGG 存在且为有效 Vorbis；新增纹理 PNG 尺寸/RGBA 正确；`zh_cn.json` 与 `en_us.json` 键 100% 一致；模板 JSON 合法 | 打包后校验脚本检查 JAR 内 `sounds.json` / `.ogg` / `.png` / `lang/*.json` / `rpg_templates/*.json` |
| **L3 规格对照（双轴评审）** | **标准轴**：对照 `AGENTS.md` 四大铁律与本技能第 3 节反模式逐条过筛；**规格轴**：逐条核对用户需求清单，确认无漏项、无擅自改语义 | 列出具体改动文件与核验事实；若存在仅能在真机客户端/专服确认的视觉手感或多人同步项（L4–L6），如实标明「待实机验证」 |

---

## 五、文档维护边界（防常驻上下文膨胀）

- 根 `AGENTS.md` 只放每轮必读铁律与三要素指针句（目标路径 + 触发条件 + 边界说明），**不堆实现流水账**；
- 详细模板数值/设计文档归属 `docs/`；
- 可复用操作规程归属 `skills/<skill-name>/SKILL.md`；
- 历史变更记录交给 `git log`，不重复抄写进 Skill 正文。
