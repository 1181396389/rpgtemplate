package com.rpgtemplate.templates;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.fx.Fx;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * 过渡品阶角色模板（N / R / SR / SSR）的公共工具方法。
 */
public final class TemplateHelper {
    private static final Set<Item> SWEET_ITEMS = Set.of(
        Items.f_42575_, // COOKIE
        Items.f_42694_, // PUMPKIN_PIE
        Items.f_42776_, // SWEET_BERRIES
        Items.f_151079_, // GLOW_BERRIES
        Items.f_42787_, // HONEY_BOTTLE
        Items.f_42499_  // APPLE
    );

    private TemplateHelper() {
    }

    /** 仅当玩家已激活系统且当前加载模板等于指定 templateId 时返回数据，严格隔离各模板效果。 */
    public static PlayerSystemData activeData(ServerPlayer player, String templateId) {
        if (player == null || !SystemDataManager.available(player)) {
            return null;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        return data.isActivated() && templateId.equals(data.loadedTemplateId()) ? data : null;
    }

    public static double maxStat(PlayerSystemData data, StatType stat) {
        return StatCalculator.maxStat(stat, data, TemplateRegistry.get(), CoreConfig.get());
    }

    /** 安全扣减系统生命（及少量原版生命反馈），最低保留 1 点生命，不直接误杀自身。 */
    public static void selfStrainDamage(ServerPlayer player, PlayerSystemData data, double ratioOfMaxHp) {
        double maxHp = maxStat(data, StatType.HP);
        double drain = Math.max(1.0, maxHp * ratioOfMaxHp);
        if (data.hp() > 1.0) {
            data.setHp(Math.max(1.0, data.hp() - drain));
        }
        if (player.m_21223_() > 2.0f) {
            player.m_21153_(Math.max(2.0f, player.m_21223_() - 1.5f));
        }
        SystemDataManager.markDirty(player);
    }

    /** 按百分比治疗系统与原版生命。 */
    public static void healRatio(ServerPlayer player, PlayerSystemData data, double ratio) {
        double maxHp = maxStat(data, StatType.HP);
        data.setHp(Math.min(maxHp, data.hp() + maxHp * ratio));
        player.m_5634_((float) (player.m_21233_() * ratio));
        SystemDataManager.markDirty(player);
    }

    /** 穿戴护甲件数（0～4）。 */
    public static int wornArmorCount(ServerPlayer player) {
        Inventory inv = player.m_150109_();
        int count = 0;
        for (int slot = 36; slot < 40; slot++) {
            if (!inv.m_8020_(slot).m_41619_()) {
                count++;
            }
        }
        return count;
    }

    /** 主手是否空手或持有剑类武器。 */
    public static boolean holdingSwordOrEmpty(ServerPlayer player) {
        ItemStack main = player.m_21205_();
        return main.m_41619_() || main.m_41720_() instanceof SwordItem;
    }

    /** 是否为甜食（用于宇智波鼬与冲田总司的甜食控癖好）。 */
    public static boolean isSweetFood(ItemStack stack) {
        if (stack == null || stack.m_41619_()) {
            return false;
        }
        if (SWEET_ITEMS.contains(stack.m_41720_())) {
            return true;
        }
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.m_41720_());
        if (key == null) {
            return false;
        }
        String path = key.m_135815_();
        return path.contains("sweet") || path.contains("cake") || path.contains("cookie")
            || path.contains("dango") || path.contains("honey") || path.contains("pie") || path.contains("sugar");
    }

    /** 沿水平视线冲刺推进。 */
    public static void dashForward(ServerPlayer player, double speed, double yBoost) {
        Vec3 look = player.m_20154_();
        double len = Math.hypot(look.f_82479_, look.f_82481_);
        double dx = len > 1e-4 ? look.f_82479_ / len : 0.0;
        double dz = len > 1e-4 ? look.f_82481_ / len : 0.0;
        player.m_5997_(dx * speed, yBoost, dz * speed);
        player.f_19864_ = true;
    }

    /** 在实体胸口绽放粒子特效。 */
    public static void burstOn(LivingEntity target, int shape, int rgb, float size, int life, int count) {
        if (target.m_9236_() instanceof ServerLevel sl) {
            Fx.burst(sl, shape, rgb, size, life, 0.18f,
                target.m_20185_(), target.m_20186_() + target.m_20206_() * 0.55, target.m_20189_(),
                count, 0.35, 0.35, 0.35);
        }
    }
}
