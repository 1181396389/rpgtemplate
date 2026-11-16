package com.rpgtemplate.forge.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.data.TemplateInstance;
import com.rpgtemplate.core.skill.SkillFormula;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.CostType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateDefinition;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.combat.BlackFlash;
import com.rpgtemplate.forge.ext.HeianForm;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.RuntimeState;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class SkillContext {
    public final ServerPlayer player;
    public final PlayerSystemData data;
    public final SkillEntry entry;
    public final TemplateDefinition def;
    public final TemplateInstance inst;
    public final int slot;
    public final long now;
    private boolean heianResolved;
    private TraitEntry heianPassive;

    private SkillContext(ServerPlayer var1, PlayerSystemData var2, SkillEntry var3, TemplateDefinition var4, TemplateInstance var5, int var6) {
        this.player = var1;
        this.data = var2;
        this.entry = var3;
        this.def = var4;
        this.inst = var5;
        this.slot = var6;
        this.now = var1.serverLevel().getGameTime();
    }

    public static SkillContext create(ServerPlayer var0, int var1) {
        PlayerSystemData var2 = SystemDataManager.get(var0);
        if (var2.isActivated() && var2.loadedTemplateId() != null) {
            TemplateInstance var3 = var2.loadedTemplate().orElse(null);
            TemplateDefinition var4 = TemplateRegistry.get().find(var2.loadedTemplateId()).orElse(null);
            if (var3 != null && var4 != null) {
                List var5 = TemplateRegistry.get().effectiveSkills(var4.id());
                return var1 >= 0 && var1 < var5.size() ? new SkillContext(var0, var2, (SkillEntry)var5.get(var1), var4, var3, var1) : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public double cost(double var1) {
        return SkillFormula.cost(var1, this.def.quality());
    }

    public double cost() {
        return this.cost(this.entry.baseCost());
    }

    public boolean consume(double var1) {
        CostType var3 = this.entry.costType();
        if (var3 != CostType.NONE && !(var1 <= 0.0)) {
            double var4 = var3 == CostType.MP ? this.data.mp() : this.data.sp();
            if (var4 + 1.0E-9 < var1) {
                Msg.actionBar(this.player, Msg.tr(var3 == CostType.MP ? "skill.no_mp" : "skill.no_sp").withStyle(ChatFormatting.RED));
                return false;
            } else {
                if (var3 == CostType.MP) {
                    this.data.setMp(var4 - var1);
                } else {
                    this.data.setSp(var4 - var1);
                    RuntimeState.of(this.player.getUUID()).lastSpUseTick = this.now;
                }

                SystemDataManager.markDirty(this.player);
                return true;
            }
        } else {
            return true;
        }
    }

    private double statMax() {
        StatType var1 = this.entry.costType() == CostType.SP ? StatType.SP : StatType.MP;
        return StatCalculator.maxStat(var1, this.data, TemplateRegistry.get(), CoreConfig.get());
    }

    private int templateLevel() {
        return Math.max(1, this.inst.level());
    }

    public float damage(double var1, double var3) {
        double var5 = 1.0 + TraitLookup.techniqueDamageBonus(this.data);
        return (float)SkillFormula.attackPower(
            var1,
            this.statMax() * (this.entry.costType() == CostType.SP ? 0.1 : 1.0),
            this.templateLevel(),
            var3 * var5,
            (Double)ModConfigs.SERVER.skillDamageConversion.get()
        );
    }

    public float damage(double var1) {
        return this.damage(this.entry.coefficient(), var1);
    }

    public double heal(double var1) {
        return SkillFormula.power(var1, this.statMax(), this.templateLevel(), 1.0, (Double)ModConfigs.SERVER.skillHealConversion.get());
    }

    public void cooldown(int var1) {
        if (var1 > 0) {
            double var2 = BlackFlash.cooldownScale(this.data, this.now);
            int var4 = Math.max(1, (int)Math.round((double)var1 * var2));
            this.data.setValue("cd:" + this.entry.id(), (double)(this.now + (long)var4));
            SystemDataManager.markDirty(this.player);
        }
    }

    public int level() {
        return this.inst.level();
    }

    public int modeUnlockLevel(String var1) {
        return this.integer(var1 + ".unlock_level", 0);
    }

    public boolean modeUnlocked(String var1) {
        return this.param(var1) != null && this.inst.level() >= this.modeUnlockLevel(var1);
    }

    public boolean modeLocked(String var1) {
        return this.param(var1) != null && this.inst.level() < this.modeUnlockLevel(var1);
    }

    public void cooldown() {
        this.cooldown(this.entry.cooldownTicks());
    }

    public JsonElement param(String var1) {
        JsonElement var2 = this.entry.params();

        for (String var6 : var1.split("\\.")) {
            if (var2 == null || !var2.isJsonObject()) {
                return null;
            }

            JsonObject var7 = var2.getAsJsonObject();
            var2 = var7.has(var6) ? var7.get(var6) : null;
        }

        return (JsonElement)var2;
    }

    public double num(String var1, double var2) {
        JsonElement var4 = this.param(var1);

        try {
            return var4 != null && var4.isJsonPrimitive() ? HeianForm.adjust(this.heian(), var1, var4.getAsDouble()) : var2;
        } catch (RuntimeException var6) {
            return var2;
        }
    }

    public TraitEntry heian() {
        if (!this.heianResolved) {
            this.heianResolved = true;
            this.heianPassive = HeianForm.active(this.data).orElse(null);
        }

        return this.heianPassive;
    }

    public int integer(String var1, int var2) {
        return (int)Math.round(this.num(var1, (double)var2));
    }

    public boolean bool(String var1, boolean var2) {
        JsonElement var3 = this.param(var1);
        return var3 != null && var3.isJsonPrimitive() ? var3.getAsBoolean() : var2;
    }

    public String str(String var1, String var2) {
        JsonElement var3 = this.param(var1);
        return var3 != null && var3.isJsonPrimitive() ? var3.getAsString() : var2;
    }

    public JsonArray array(String var1) {
        JsonElement var2 = this.param(var1);
        return var2 != null && var2.isJsonArray() ? var2.getAsJsonArray() : new JsonArray();
    }

    public double arrayNum(String var1, int var2, double var3) {
        JsonArray var5 = this.array(var1);
        return var5.isEmpty() ? var3 : var5.get(Math.min(var2, var5.size() - 1)).getAsDouble();
    }
}
