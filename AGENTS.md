# AGENTS.md — `rpgtemplate` 仓库开发与模板制作全局准则

本仓库为 Minecraft Forge 1.20.1 模组 **RPG转生模板（`rpgtemplate`）** 的源码、资源与设计文档树。

## 角色模板制作最高铁律（限制与弱点）

在本仓库中设计、新增或修改**任何角色模板及进阶/同源形态**时，必须严格遵守以下三条核心规范（非模板玩家使用的独立武器如 `nt_mimic_blade` 除外；详见 `docs/template-creation-reference.md` 与 `skills/rpg-template-creation/SKILL.md`）：

- **使用限制**：威力强制锚定。无论模板原主强度如何，其实际效果（伤害、防御力、道具威力）完全取决于使用者当前的面板等级与属性。若使用者等级极低，使用高阶模板的大招可能会因能量不足无法发动或是威力不足。
- **反噬/副作用**：完美继承角色负面效果和弱点以及癖好。如抽取“身患重症的绝世天才”，使用者在载入该模板后会同步获得对应模板时期的相应的身体体质与病症以及才华。
- **克制关系**：机制级技能（如：必死、绝对催眠等）需经过等级/属性判定。判定结果由强至弱分为：1.完全生效；2.显著干扰；3.微弱牵制；4.判定失败。

## 关键参考文档索引

- **角色模板制作总参考规范**：`docs/template-creation-reference.md`
- **角色模板制作 Agent Skill**：`skills/rpg-template-creation/SKILL.md`
- **一无所有（EX）设计文档**：`nothing-there/design/template-nothing-there-ex.md`
- **殷红迷雾·原型拟态刀建模参考（独立非模板武器）**：`skills/nt-mimic-blade-modeling/SKILL.md`
