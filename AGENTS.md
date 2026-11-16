# AGENTS.md — `rpgtemplate` 仓库开发与模板制作全局准则

本仓库为 Minecraft Forge 1.20.1 模组 **RPG转生模板（`rpgtemplate`）** 的源码、资源与设计文档树。

## 角色模板与模组开发最高铁律（限制、弱点与特效规范）

在本仓库中设计、新增或修改**任何角色模板、进阶/同源形态或模组技能特效**时，必须严格遵守以下四条核心铁律（注：前三条限制与弱点规则对非模板玩家使用的独立武器如 `nt_mimic_blade` 除外；第四条特效禁令对**全模组一切技能、被动、道具与实体特效无例外生效**；详见 `docs/template-creation-reference.md` 与 `skills/rpg-template-creation/SKILL.md`）：

- **使用限制**：威力强制锚定。无论模板原主强度如何，其实际效果（伤害、防御力、道具威力）完全取决于使用者当前的面板等级与属性。若使用者等级极低，使用高阶模板的大招可能会因能量不足无法发动或是威力不足。
- **反噬/副作用**：完美继承角色负面效果和弱点以及癖好。如抽取“身患重症的绝世天才”，使用者在载入该模板后会同步获得对应模板时期的相应的身体体质与病症以及才华。
- **克制关系**：机制级技能（如：必死、绝对催眠等）需经过等级/属性判定。判定结果由强至弱分为：1.完全生效；2.显著干扰；3.微弱牵制；4.判定失败。
- **特效禁令（严禁廉价粒子）**：**不许使用廉价粒子制作特效**。严禁使用 Minecraft 原版粒子系统（`ParticleTypes`、`DustParticleOptions`、`ParticleOptions`、`ServerLevel.sendParticles`、`ClientLevel.addParticle` 等）拼凑模组技能、被动、道具或实体视觉特效；所有模组特效必须通过自定义 3D 几何/着色器/模型渲染通道实现（如 `RenderLevelStageEvent` 独立几何渲染、自定义实体/图层渲染器、Shader 或 `com.rpgtemplate.forge.fx.Fx` 几何通道）。同时**严禁**通过 Mixin 拦截/替换原版 `ServerLevel`/`ClientLevel` 粒子方法去改写 Minecraft 原版特效——原版游戏自身的特效必须完整保持原样。

## 关键参考文档索引

- **角色模板制作总参考规范**（新增/调整角色模板数值、副作用与克制判定时必读）：`docs/template-creation-reference.md`
- **角色模板制作 Agent Skill**（任何新建或修改角色模板任务时加载；若未满足四大铁律须中止并修正）：`skills/rpg-template-creation/SKILL.md`
- **Forge 1.20.1 直编工程与 AI 协作 Skill**（编写/重构 SRG 源码、Mixin、渲染器、音效、贴图或执行交付前分层验收时加载；未查证的 SRG 签名须先用 `javap` 实证，严禁猜测）：`skills/mc-forge1201-engineering/SKILL.md`
- **`MC-AI-Coding-Assistant-Tool` 架构总结与本仓细考报告**（评估外部 MC AI 工具链、规划 `BuildMod.java` 资源审计升级或优化开发流程时按需查阅）：`docs/mc-ai-coding-assistant-tool-study.md`
- **一无所有（EX）设计文档**（维护/扩展 Nothing There 模板时查阅）：`nothing-there/design/template-nothing-there-ex.md`
- **殷红迷雾·原型拟态刀建模参考（独立非模板武器）**（维护 `nt_mimic_blade` 3D 模型与贴图时查阅）：`skills/nt-mimic-blade-modeling/SKILL.md`
