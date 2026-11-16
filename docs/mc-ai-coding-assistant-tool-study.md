# `MC-AI-Coding-Assistant-Tool` 深度总结与 `rpgtemplate` 可借鉴细考报告

> **研究对象**：[`guguzea/MC-AI-Coding-Assistant-Tool`](https://github.com/guguzea/MC-AI-Coding-Assistant-Tool)（Minecraft 模组 AI 编程助手完整工具包，涵盖 8 大加载器 + 服务端面、89 个 MCP/CLI 工具、14 个跨平台编排 Skill、Forge 1.20.1 规则树与 35 个专精 Skill）
> **许可合规说明**：上游仓库采用 `PolyForm Noncommercial License 1.0.0` 及强 Copyleft 附加条款（其文档语料与 `pixel-art-upstream` 等随各自上游许可）。本文与配套 `skills/mc-forge1201-engineering/SKILL.md` **仅为原创架构分析与方法论吸收总结**，不复制或搬运上游任何代码、语料库、规则原文或受限素材。

---

## 一、项目全景总结：它到底解决了什么问题？

`MC-AI-Coding-Assistant-Tool` 的核心目标，是系统性消除 AI 编程助手开发 Minecraft 模组时的四大结构性障碍：**知识陈旧、API/版本跨档混淆、多套映射（Mojmap / SRG / MCP / Yarn / Parchment）错拼、以及“只改代码不做端到端验证”**。

### 1. 三层解耦架构（知识库 × 控制代码 × 游戏连接器）

| 层级 | 仓库对应组成 | 核心职责与设计亮点 |
|---|---|---|
| **知识库（只读真相面）** | `forge/`、`fabric/`、`neoforge/`、`common_skill/`、`community_knowledge/`、`knowledge/`、`data/` | 存放官方文档离线语料、社区实务短文与反模式、按「平台 × 精确版本」严格隔离的规则包（`00`–`10`）、35+ 专精 Skill 以及库模组 API 摘要。**只被查、不执行动作**。 |
| **知识库控制器 & 工程控制器** | `mcp-server/`（89 个 MCP / CLI 双入口工具） | AI 不靠训练记忆猜 API，而是经由工具实时检索官方文档页、查 Vanilla/Loader 方法签名、互转映射名、反编译第三方 JAR、审计资源引用、校验 Mixin/AT/数据包 JSON、生成代码骨架。**所有落盘、跑构建、复制 JAR、发布动作默认 `dryRun`，必须人在环确认**。 |
| **游戏连接器（真机自测面）** | `playtest-bridge` / `playtest-intent` / `playtest-evidence` | 按需求向工程注入临时 Playtest Driver，让 AI 进游戏执行冒烟或场景脚本（走位、扫描、断言、截图、产出 `state.json`/`exit-code.txt`/`qa.log` 证据三件套），用结构化证据判定改动红绿。 |

### 2. 四大核心支柱深度拆解

#### 支柱 A：按「平台 × 精确版本」硬隔离的规则树（以 `forge/1.20.1/` 为例）
- **绝不模糊凑合**：每个加载器的每个小版本独立成档（如 `forge/1.20.1/`），缺档直接返回 `PACK_NOT_FOUND`，严禁悄悄拿 `1.20.4` 或 `1.19.4` 的规则顶替。
- **分层按需注入（`00`–`10` 规则文件）**：
  - 常驻底座仅注入 `00-project-setup`、`01-registry`、`09-anti-patterns` 三份高频核心规则；
  - 其余 `02-block`、`03-item`、`04-entity`、`05-events`、`06-networking`、`07-datagen`、`08-client-server`、`10-gui` 以及 35 个专精 Skill（如 `mc-renderer`、`mc-mixin`、`mc-sound`、`mc-entity`、`mc-gui` 等）按任务主题按需加载，控制上下文负载。
- **反模式库（`09-anti-patterns.mdc`）**：每条严格按「错误代码 → 错误症状 → 正确方案」三段式书写（例如：禁止在 `Entity` 构造函数修改未就绪属性、禁止在网络包处理器直接改世界而不用 `ctx.enqueueWork`、禁止在 `TickEvent` 全图无节流遍历、禁止在 `BlockEntity.load()` 访问未就绪的 `level`、禁止在客户端/服务端混放 `@OnlyIn(Dist.CLIENT)`）。

#### 支柱 B：14 个跨平台编排型 Skill（`common_skill/`）
这是该仓库最容易被忽略、但对大型复杂模组（尤其是多形态角色模板）价值极高的设计——把“写代码之前的对齐”与“写完之后的验收”也工程化为标准 Skill：

| 分类 | 代表 Skill | 核心机制与价值 |
|---|---|---|
| **需求对齐与规划** | `mc-grill`、`mc-spec`、`mc-breakdown`、`mc-prototype` | 动手前用 `mc-grill` 按设计树 frontier 逐轮拷问（区分创意枝/技术枝/代劳枝，每题附带立场鲜明的推荐答案），清空默认假设并产出共识清单后才允许开工；`mc-breakdown` 强迫按**竖切片（端到端可验收功能）**而非横层（先做完所有贴图再做所有逻辑）拆解任务，首片用曳光弹打通全链路，并在文档尾部维护 TAB 分隔机读台账防多会话漂移。 |
| **事实调查与评审** | `mc-research`、`mc-review`、`mc-glossary` | `mc-review` 强制**双轴独立评审**：标准轴按当前版本规则/反模式查技术合规，规格轴对照 spec/共识清单查漏做或擅自加戏；采用**行级落点闸**（`file:line` 必须锚定真实 diff 行号，否则作废降级）与**复审短路**（无新提交不重跑）。 |
| **协作与收口** | `mc-handoff`、`mc-retro`、`mc-router`、`mc-wizard`、`mc-waitwhat` | 解决长会话上下文断裂、人工操作步骤打包、沟通术语对齐与每轮摩擦复盘问题。 |
| **像素美术专精** | `mc-texture` + `pixel-art-upstream` | 提出「**1× 原尺寸认得出**」唯一硬判据、5 层严格绘制顺序（平涂剪影 → 单一光源打光 → 内部细节 → 深色同系描边 → 五官/标记）、材质游戏逻辑（如铜的四级锈蚀过程而非随机涂绿）、**反向指认测试**（遮住标题让独立视角盲认），以及**参数化生成竖排图集 + `.png.mcmeta`** 制作纹理动画。 |

#### 支柱 C：给 Agent 写文档的工程方法论（`WRITING-FOR-AGENTS.md`）
该仓库将“面向 AI 的文档编写”上升为一门可自动化校验（`assert-authoring-lint.mjs`）的工程学科：
1. **双预算模型**：区分**上下文负载**（常驻 `AGENTS.md` 每轮必花 token，每行必须通过“每轮都相关”测试）与**认知负载**（按需展开的 Skill/Docs 不占常驻预算，核心看指针质量）。
2. **分叉测试**：每个分支都要用的留在原地；只有特定任务会用到的，一律推到**三要素指针句**之后（① 精确路径/工具名；② 触发条件；③ 失败/缺件时的停止读数）。
3. **正向默认，安全负向留职**：纯风格约束改成正向指令；但对**防幻觉与防破坏性操作**（如“严禁凭记忆编造 SRG/API 名称”“严禁使用原版粒子”），明确保留强负向原形，并以“正向做法 + 安全禁令”配对书写。
4. **出处形状（防幻觉铁底线）**：Agent 文档中出现的每个 API 名、版本口径与数量统计，必须附带可核查出处（`javap` 构件、官方文档页 ID 或源码路径行号），核不到必须显式标注 `// TODO(未核实)`，绝不静默伪造。
5. **一史一处（防常驻面膨胀）**：把「现行规则/口径」与「修改历史/台账状态」彻底拆离，历史流水账不得污染根 `AGENTS.md` 或 Skill 正文。
6. **派发纪律**：编排者派发子任务时必须**事实预采随任务书走**（避免子代理靠猜测定位）、机读输出用**两遍式校验**、对象未变更时**零产出短路**。

#### 支柱 D：L0–L7 分层游玩测试阶梯（`doc/playtest-ladder.md`）
把模组验证拆解为严格递增的 8 个层级，**低层红不许跳高层**：
- **L0 构建**（编译出 JAR）→ **L1 结构与静态**（元数据、Mixin、AT/AW、资源/贴图/音效引用审计）→ **L2 数据面**（JSON 配方/战利品/模板/双语 `lang` 键校验）→ **L3 自动化功能**（GameTest）→ **L4 真机冒烟**（进世界不崩、基础交互）→ **L5 场景验收**（本次改动玩家可见行为）→ **L6 专服与多人**（服务端启动 + 双端状态同步）→ **L7 回归与多 Mod 共存**。

---

## 二、细考：对照 `rpgtemplate` 现状，哪些最值得立刻学习与吸收？

`rpgtemplate` 是一套非常独特的 **Minecraft Forge 1.20.1 零 Gradle 直接编译 SRG 源码** 工程（`tools/BuildMod.java` + `build0414-src/` + `build0414-resources/` + `base/rpgtemplate-0.4.14.jar`）。把 `MC-AI-Coding-Assistant-Tool` 的理念逐项映射到本仓库真实架构与历史踩坑记录，以下 **7 个方向** 含金量最高：

### 1. 【最高优先 · 立竿见影】把「静态资源引用审计（L1）」升级进 `tools/BuildMod.java`
- **现状痛点**：`tools/BuildMod.java` 已经具备极强的 L0/L2 校验（461 类字节码内部断链检查、粒子禁令检查、`MANIFEST.MF` Mixin 校验、`zh_cn`/`en_us` 键一致性校验），但**尚未校验资源引用闭环**：
  - 历史上本仓曾因代码引用了不存在的 `textures/misc/white.png` 导致客户端紫黑丢失贴图（后统一修复为 `textures/fx/cronus_white.png`）；
  - `sounds.json` 若登记了不存在的 `.ogg` 文件名，或物品模型 JSON 指向了不存在的贴图，仅靠 `JsonCodec.parse` 无法在构建期拦住。
- **直接学习点**：吸收 `audit_resources` 的静态审计思路，未来可在 `BuildMod.verifyJar()` 中增加 3 条轻量自动化检查（零外部依赖）：
  1. **声音文件闭环校验**：解析合并后的 `assets/rpgtemplate/sounds.json`，断言每条 `rpgtemplate:<path>` 均对应 JAR 内真实存在的 `assets/rpgtemplate/sounds/<path>.ogg`；
  2. **模型 → 贴图闭环校验**：扫描 `assets/rpgtemplate/models/**/*.json` 中所有 `textures` 值，断言 `rpgtemplate:<tex>` 均存在对应 `assets/rpgtemplate/textures/<tex>.png`；
  3. **Java 常量池贴图路径预警**：扫描 `build0414-src/` 中字面量 `textures/...png`（非 `minecraft:` 已知原版路径），校验其存在于输出 JAR 中，彻底杜绝运行时紫黑方块。

### 2. 【最高优先】建立「SRG 字节码实证律」，彻底根除 SRG 名称幻觉
- **现状痛点**：`rpgtemplate` 直接在源码中使用 Forge 1.20.1 SRG 方法/字段名（如 `m_280411_`、`f_252403_`、`m_269285_`），一旦记忆偏差就会导致编译失败或 Mixin 运行时脱靶（例如 `CronusArmorHideMixin` 或 `FxShader` 曾遇到的 SRG 签名细节）。
- **直接学习点**：吸收 `MC-AI-Coding-Assistant-Tool` 的 **出处形状（§7）** 与 `mc-mixin` 的 **工具实证替代手写** 原则：
  - 严禁凭记忆编写任何陌生 SRG 方法/字段名或 Mixin `@At(target=...)` 描述符；
  - 必须直接对本地缓存 `.build-cache/`（或 `/tmp/rpg-build-cache/`）中的真实 `forge-universal.jar` / `client-srg.jar` 运行 `javap -p` 实证方法签名；
  - 对 Mixin 配置（`rpgtemplate.*.mixins.json`），修改目标方法前必须通过 `javap -s` 核对精确 JVM 描述符，并确认 `BuildMod.CLEAN_MIXIN_CONFIGS` 与 `MANIFEST.MF` 同步登记。

### 3. 【高优先】将复杂角色模板开发流程升级为「拷问对齐 → 竖切片 → 双轴评审」
- **现状痛点**：像假面骑士 Cronus 这样的 EX 级模板，同时涉及 8 项技能、多件专属装备、3D 护甲与腰带建模、自定义投射物、16 段 OGG 音效以及 HUD 对角切入 VFX。如果直接按“大包大揽一次性写完”推进，容易出现需求细节返工（例如变身音效先导延时、腰带卡带收纳归还规则、必杀技形态区分等后期多次微调）。
- **直接学习点**：吸收 `mc-grill` + `mc-breakdown` + `mc-review`：
  - **新模板立项先做轻量化需求对齐**：明确拆出「创意枝（技能数值/副作用/音效来源/视觉风格，由用户定）」「技术枝（SRG 渲染通道/网络同步包/Mixin 边界）」「代劳枝（内部键名与实现细节，记明理由）」；
  - **按竖切片推进**：例如「切片 1：模板 JSON + 变身骨架 + 1 项核心技能打通构建与加载」→「切片 2：核心武装与投射物」→「切片 3：3D 外观模型」→「切片 4：原声音效与必杀渲染特效」，每片独立过 `./build.sh --clean` + 资源验证；
  - **交付前双轴自审**：标准轴核对 `AGENTS.md` 四大铁律（威力锚定 / 副作用继承 / 四档克制 / 严禁廉价粒子）+ Forge 1.20.1 物理端与线程规范；规格轴逐条对照用户原始图文要求核对无漏项。

### 4. 【高优先】Forge 1.20.1 物理端分离、音频流式策略与反模式加固
对照 `forge/1.20.1/` 的 `06-networking`、`08-client-server`、`09-anti-patterns`、`mc-sound`、`mc-renderer`，直接对应到 `rpgtemplate` 的可改进检查项：
- **物理端隔离（专服安全）**：任何服务端/通用包（如 `com.rpgtemplate.cronus`）中调用客户端类（`CronusClientFx`、`CronusHudOverlay`、`Minecraft`）必须经过 `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` 或放入独立 `client/` 子包，且绝不在非 `client/` 包中引用客户端类，确保独立服务器（L6）零 `ClassNotFoundException`。
- **网络包线程安全**：所有 `SimpleChannel` 消息处理器必须严格包裹在 `ctx.get().enqueueWork(...)` 内并调用 `ctx.get().setPacketHandled(true)`；高频同步（如持续激光或状态更新）合并字段打包发送，避免逐字段刷包。
- **音频流式加载策略（`mc-sound`）**：`sounds.json` 中 `"stream": true` 控制 OpenAL 流式读盘而非整段解压进内存。像 `cronus_henshin.ogg`（`20.52s`）、`cronus_henshin_fast.ogg`（`11.52s`）以及 `nt_ego_bgm` 等超过 10 秒的长音频，适合评估开启 `"stream": true` 以降低客户端内存常驻峰值；而 `0.4s`–`9s` 的高频战斗/必杀/瞬移音效保持 `"stream": false` 保证零延迟瞬播。
- **渲染器状态隔离（`mc-renderer`）**：自定义渲染器与 Layer（如 `CronusSuitLayer`、各投射物 Renderer）必须是**无每实例可变状态**的；实体独立动画进度与计时一律存放在实体或按 `entityId` 索引的客户端状态表（如 `CronusSuitAppearance`），防止多人同屏时互相串帧。

### 5. 【中高优先】像素贴图与程序化资源制作升级（吸收 `mc-texture`）
- **现状关联**：`rpgtemplate` 的武器物品图标（`buggle_driver_ii`、`chronicle_gashat`、`gashacon_bugvisor_ii`）、像素能量道具硬币（`cronus_energy_items.png`）与特效贴图均由 Python/Pillow 程序化生成。
- **直接学习点**：
  1. **1× 原尺寸可读性硬检查**：生成 16×16 / 32×32 图标后，不只看放大预览图，必须同步检查真实 1× 缩放与黑底/物品栏背景下的剪影清晰度；
  2. **5 步分层绘制法**：先定不对称清晰剪影 → 统一左上光源打光（暗部偏冷、亮部偏暖）→ 添加内部机械/纹理细节 → 深色同系描边（不用死纯黑）→ 最后点亮高光与标识；
  3. **参数化竖排帧动画**：需要为装备或能量核心制作流光动画时，直接采用「固定底图 + 参数化逐帧偏移」生成竖排图集 + `.png.mcmeta`（并用 `frames: [0,1,2,1]` 省帧循环），避免帧间跳动。

### 6. 【中高优先】Agent 文档治理：守住根 `AGENTS.md` 的上下文预算
- **直接学习点**：遵循 `WRITING-FOR-AGENTS.md` 的核心法则：
  - 根 `AGENTS.md` **只保留每轮必用铁律 + 三要素指针索引**，绝不把开发日志、历史 bug 修复流水账或长篇教程塞进 `AGENTS.md`；
  - 将可复用的 Forge 1.20.1 工程纪律沉淀到按需加载的 `skills/mc-forge1201-engineering/SKILL.md`；
  - 将架构调研与设计演化分析沉淀到 `docs/` 下的专题文档。

### 7. 【中长期可选】将 `MC-AI-Coding-Assistant-Tool` 作为本地辅助查询工具接入（但不耦合进模组工程）
- 在个人开发机（Cursor / Claude Code 等宿主）中，可将 `MC-AI-Coding-Assistant-Tool` 安装到独立目录作为外部 MCP/CLI 辅助工具，用于：
  - 离线检索 Forge 1.20.1 官方文档与社区库模组避坑指南（如未来接入 Curios、GeckoLib、JEI 联动）；
  - 使用 `convert_mapping` 辅助查询 Mojmap ↔ SRG 映射、用 `crash_analyze` 快速分诊玩家提交的崩溃报告、用 `localize_mod` 抽查中英文漏翻键；
  - **边界注意**：`rpgtemplate` 采用直编 SRG 流水线而非 ForgeGradle MDK，且受 `AGENTS.md` 「严禁廉价粒子」铁律约束——使用外部工具生成的骨架代码时，必须人工/代理按 `rpgtemplate` 的 SRG 命名与自定义渲染通道做二次适配，绝不直接套用其 Gradle 脚手架或原版粒子示例。

---

## 三、落地路线图（按投入产出比排序）

| 优先级 | 落地动作 | 状态 / 落点 |
|---|---|---|
| **P0（本轮已落地）** | 编写本深度细考报告，并提炼专属 `rpgtemplate` 的 Forge 1.20.1 工程协作 Skill，在 `AGENTS.md` 用三要素指针接入 | ✅ `docs/mc-ai-coding-assistant-tool-study.md`、`skills/mc-forge1201-engineering/SKILL.md`、`AGENTS.md` |
| **P1（下次构建增强）** | 在 `tools/BuildMod.java` 的 `verifyJar()` 中补齐 L1 静态资源审计（`sounds.json -> .ogg` 闭环、`models -> textures` 闭环、贴图路径检查） | 规划中（`tools/BuildMod.java`） |
| **P2（音频与专服打磨）** | 评估将超过 10 秒的长音频（如 `cronus_henshin.ogg`、`nt_ego_bgm`）切为 `"stream": true`，并做一次 L6 专服启动冒烟测试 | 规划中（`build0414-resources/*_sounds_fragment.json`） |
| **P3（后续新模板开发）** | 开发下一位高阶角色模板时，启用 `skills/mc-forge1201-engineering/SKILL.md` 的「需求对齐 → 竖切片 → SRG `javap` 实证 → 双轴评审」全流程 | 随时可启用 |
