---
name: rpg-template-creation
description: 设计与开发 rpgtemplate（RPG转生模板）角色模板、进阶/同源分支的标准参考技能规范。任何新建或修改角色模板的任务必须加载并严格遵循本规范中的【限制与弱点】三大铁律。
---

# RPG转生模板 · 角色模板制作参考技能（`rpg-template-creation`）

在 `rpgtemplate`（Minecraft Forge 1.20.1）中设计、编码或调整任何角色模板（`data/rpgtemplate/rpg_templates/*.json` 及服务端/客户端逻辑）时，**必须强制遵循以下核心规则**（注：非模板玩家专用的外部独立武器如 `nt_mimic_blade` 不受模板规则约束）。完整规范详见仓库 `docs/template-creation-reference.md`。

---

## 一、 限制与弱点（全模板强制核心规则）

- **使用限制**：威力强制锚定。无论模板原主强度如何，其实际效果（伤害、防御力、道具威力）完全取决于使用者当前的面板等级与属性。若使用者等级极低，使用高阶模板的大招可能会因能量不足无法发动或是威力不足。
- **反噬/副作用**：完美继承角色负面效果和弱点以及癖好。如抽取“身患重症的绝世天才”，使用者在载入该模板后会同步获得对应模板时期的相应的身体体质与病症以及才华。
- **克制关系**：机制级技能（如：必死、绝对催眠等）需经过等级/属性判定。判定结果由强至弱分为：1.完全生效；2.显著干扰；3.微弱牵制；4.判定失败。

---

## 二、 编码实现强制约束

### 1. 威力强制锚定（禁止硬编码数值）
- **技能与模板道具结算**：统一使用 `SkillContext.damage(coefficient)`、`SkillFormula.cost(baseCost, quality)` 与 `StatCalculator`，所有伤害、防御、回血、护盾及模板生成道具威力必须实时锚定使用者当前 `PlayerSystemData` 的等级与面板属性。
- **高阶消耗门槛**：合理配置 `base_cost` 配合 `Quality.costMultiplier`（`SSR=8x, UR=16x, EX=32x, EX+=64x, LR=128x, MR=256x`），确保低等级玩家因能量不足无法强开高阶大招，或仅能打出低等级面板威力。
- **成长被动封顶**：叠层增益被动必须设置与等级挂钩的上限与脱战衰减，禁止无限叠层。

### 2. 反噬/副作用同步继承与模板隔离
- **必填字段**：除基础模板外，每个角色模板 JSON 必须完整配置对应原作时期的 `weaknesses`（弱点）、`negatives`（负面体质/病症）与 `quirks`（角色癖好，条目字段为 `"type"`, `"name"`, `"description"`, `"params"`；属性加成为 `"bonus": {"hp", "mp", "sp", "def"}`）。
- **事件隔离（防跨模板污染）**：所有 Forge 事件监听器必须同时校验 `data.isActivated() && TEMPLATE_ID.equals(data.loadedTemplateId())`；模板卸载时立即清理临时 `AttributeModifier` 与状态。

### 3. 机制级技能四档克制判定
- 凡涉及必死/斩杀、绝对催眠/精神控制、概念破防/无视护甲、削生命上限、缴械夺取、越级绝对免疫等机制级能力，必须接入 `RuleJudge.evaluate(...)` 四档判定：
  1. **完全生效**（`rule.full`）
  2. **显著干扰**（`rule.major`）
  3. **微弱牵制**（`rule.minor`）
  4. **判定失败**（`rule.fail`）
