package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.skill.HeldState;
import com.rpgtemplate.forge.skill.Skill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.sounds.SoundSource;

/**
 * Holle：发射三根骨刺（HolleSpike，扇形），沿视线飞行，命中第一个目标后造成伤害；射程 32 格。
 * 总伤害平分到三根骨刺上（每根为总伤害的 1/3）。
 * 施法时播放 Holle 音效（NtSounds.HOLLE，MASTER 声道），并让施法者播放施法动作（NtHolleAnim，客户端）。
 */
public final class HolleSkill implements Skill {
    static final double BASE_COST = 0.05;
    static final int COOLDOWN = 40;       // 2 秒
    static final int SPIKE_COUNT = 3;     // 每次发射的骨刺数量（伤害平分）
    static final double SPREAD_DEG = 4.0; // 左右两根骨刺相对视线的偏转角（度）
    static final int CAST_TICKS = 24;     // 施法动作持续时间（与客户端 NtHolleAnim.DURATION 一致）

    @Override
    public void onPress(SkillContext ctx, HeldState held) {
        if (!ctx.consume(ctx.cost(BASE_COST))) {
            return;
        }
        ctx.cooldown(COOLDOWN);
        // JSON coefficient is 1.8; ctx.damage(1.0) applies it. Split evenly across the spikes.
        float damage = ctx.damage(1.0) / SPIKE_COUNT;
        // 施法时玩家身边的血雾（一次施法只生成一次）
        NtGore.castMist((net.minecraft.server.level.ServerLevel) ctx.player.m_9236_(), ctx.player);
        HolleSpike.launch(ctx.player, damage, -SPREAD_DEG);
        HolleSpike.launch(ctx.player, damage, 0.0);
        HolleSpike.launch(ctx.player, damage, SPREAD_DEG);
        ctx.player.m_9236_().m_6263_(null, ctx.player.m_20185_(), ctx.player.m_20186_(), ctx.player.m_20189_(),
            NtSounds.HOLLE, SoundSource.MASTER, 1.0f, 1.0f);
        // 施法动作：服务端写入截止时间，NtBody 把它同步给所有客户端。
        PlayerSystemData data = ctx.data;
        data.setValue(NtForms.KEY_HOLLE_UNTIL, (double) (ctx.now + CAST_TICKS));
        SystemDataManager.markDirty(ctx.player);
    }
}
