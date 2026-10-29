package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Shared state keys, form table and combat helpers for the 一无所有 template. */
public final class NtForms {
    /** Current form index: 0 锤, 1 手刀, 2 骨刺连射, 3 贪食之口, 4 脊骨鞭, 5 全收. */
    public static final String KEY_FORM = "nt.form";
    /** Bit mask of unlocked forms (bits 0 and 1 are implicit). */
    public static final String KEY_FORMS = "nt.forms";
    /** Consecutive hit stacks for 适应 (reset on form change). */
    public static final String KEY_ADAPT = "nt.adapt";
    /** Kill counters: nt.kill.monster / nt.kill.player / nt.kill.animal. */
    public static final String KEY_KILL = "nt.kill.";
    /** Death counters per killer UUID: nt.dm.<uuid>. */
    public static final String KEY_DM = "nt.dm.";
    /** Game tick until which the 茧 revive is on cooldown. */
    public static final String KEY_COCOON_UNTIL = "nt.cocoon_until";
    /** Game tick at which the cocoon hatches (0 = not in a cocoon). */
    public static final String KEY_COCOON_HATCH = "nt.cocoon_hatch";
    /** Goodbye: game tick of the pending strike (0 = none). */
    public static final String KEY_GB_STRIKE_AT = "nt.gb.strike_at";
    /** Goodbye: game tick until which the reduction/resistance window lasts. */
    public static final String KEY_GB_GUARD_UNTIL = "nt.gb.guard_until";
    /** Goodbye: skill slot to resolve the strike with. */
    public static final String KEY_GB_SLOT = "nt.gb.slot";
    /** Goodbye: 1 if the strike was started while airborne. */
    public static final String KEY_GB_AIRBORNE = "nt.gb.airborne";
    /** Holle: game tick until which the cast pose is shown (0 = not casting). */
    public static final String KEY_HOLLE_UNTIL = "nt.holle.until";
    /** oh man: game tick until which the hammer counts as charging (set on press, cleared on release; 0 = not). */
    public static final String KEY_OH_CHARGE_UNTIL = "nt.oh.charge_until";
    /** oh man: game tick until which the snap-down pose is shown after a slam (0 = not). */
    public static final String KEY_OH_SLAM_UNTIL = "nt.oh.slam_until";

    /** 减速 slowness（茧期间用 255 级）。SRG f_19597_ 已用 javap 核对。 */
    public static final MobEffect EFFECT_SLOW = MobEffects.f_19597_;
    /** 抗性提升 resistance（goodbye 用）。SRG f_19606_ 已用 javap 核对。 */
    public static final MobEffect EFFECT_RESIST = MobEffects.f_19606_;

    public static final String[] NAMES = {"锤", "手刀", "骨刺连射", "贪食之口", "脊骨鞭", "全收"};

    private NtForms() {
    }

    /** Template data of an active 一无所有 user, or null. */
    public static PlayerSystemData activeData(ServerPlayer player) {
        if (!SystemDataManager.available(player)) {
            return null;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        return data.isActivated() ? data : null;
    }

    public static int current(PlayerSystemData data) {
        int form = (int) data.value(KEY_FORM, 0.0);
        return form < 0 || form >= NAMES.length ? 0 : form;
    }

    public static boolean unlocked(PlayerSystemData data, int form) {
        if (form < 2) {
            return true;
        }
        long mask = (long) data.value(KEY_FORMS, 0.0);
        return (mask & (1L << form)) != 0L;
    }

    public static void unlock(PlayerSystemData data, int form) {
        long mask = (long) data.value(KEY_FORMS, 0.0);
        data.setValue(KEY_FORMS, (double) (mask | (1L << form)));
    }

    /** True while the player is inside a cocoon (no heal, no damage). */
    public static boolean inCocoon(ServerPlayer player, PlayerSystemData data) {
        return data.value(KEY_COCOON_HATCH, 0.0) > (double) player.m_284548_().m_46467_();
    }

    /** Heals the system HP by a fraction of max HP. */
    public static void healRatio(ServerPlayer player, PlayerSystemData data, double ratio) {
        double max = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        heal(player, data, max * ratio);
    }

    /** Heals the system HP by an absolute amount, capped at the current max. */
    public static void heal(ServerPlayer player, PlayerSystemData data, double amount) {
        if (amount <= 0.0 || inCocoon(player, data)) {
            return;
        }
        double max = StatCalculator.maxStat(StatType.HP, data, TemplateRegistry.get(), CoreConfig.get());
        data.setHp(Math.min(max, data.hp() + amount));
        SystemDataManager.markDirty((Player) player);
    }

    /**
     * Hits every valid target inside a forward arc. The arc is measured on the horizontal plane around
     * the player's look direction. Returns the entities that were hurt (the caller uses them for effects).
     */
    public static List<Entity> sweep(ServerPlayer player, PlayerSystemData data, double range, double arcDegrees,
                                     float damage, double knockback, double lifesteal) {
        List<Entity> hit = new ArrayList<>();
        Vec3 look = player.m_20154_();
        double lx = look.f_82479_;
        double lz = look.f_82481_;
        double len = Math.hypot(lx, lz);
        if (len < 1e-6) {
            return hit;
        }
        lx /= len;
        lz /= len;
        double cos = Math.cos(Math.toRadians(arcDegrees / 2.0));
        double px = player.m_20185_();
        double pz = player.m_20189_();
        List<Entity> found = player.m_9236_().m_6249_(player,
            player.m_20191_().m_82400_(range), e -> TargetUtil.valid(player, e));
        double total = 0.0;
        for (Entity entity : found) {
            double dx = entity.m_20185_() - px;
            double dz = entity.m_20189_() - pz;
            double d = Math.hypot(dx, dz);
            if (d < 1e-6 || d > range || (dx * lx + dz * lz) / d < cos) {
                continue;
            }
            if (!TargetUtil.hurt(entity, ModDamageTypes.technique(player), damage)) {
                continue;
            }
            total += damage;
            hit.add(entity);
            LivingEntity living = TargetUtil.living(entity);
            if (living != null && knockback > 0.0) {
                living.m_5997_(dx / d * knockback, 0.3, dz / d * knockback);
            }
        }
        if (lifesteal > 0.0) {
            heal(player, data, total * lifesteal);
        }
        return hit;
    }
}
