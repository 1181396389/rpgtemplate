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
        Items.COOKIE, // COOKIE
        Items.PUMPKIN_PIE, // PUMPKIN_PIE
        Items.SWEET_BERRIES, // SWEET_BERRIES
        Items.GLOW_BERRIES, // GLOW_BERRIES
        Items.HONEY_BOTTLE, // HONEY_BOTTLE
        Items.APPLE  // APPLE
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
        if (player.getHealth() > 2.0f) {
            player.setHealth(Math.max(2.0f, player.getHealth() - 1.5f));
        }
        SystemDataManager.markDirty(player);
    }

    /** 按百分比治疗系统与原版生命。 */
    public static void healRatio(ServerPlayer player, PlayerSystemData data, double ratio) {
        double maxHp = maxStat(data, StatType.HP);
        data.setHp(Math.min(maxHp, data.hp() + maxHp * ratio));
        player.heal((float) (player.getMaxHealth() * ratio));
        SystemDataManager.markDirty(player);
    }

    /** 穿戴护甲件数（0～4）。 */
    public static int wornArmorCount(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int count = 0;
        for (int slot = 36; slot < 40; slot++) {
            if (!inv.getItem(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** 主手是否空手或持有剑类武器。 */
    public static boolean holdingSwordOrEmpty(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.isEmpty() || main.getItem() instanceof SwordItem;
    }

    /** 是否为甜食（用于角色甜食控癖好）。 */
    public static boolean isSweetFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (SWEET_ITEMS.contains(stack.getItem())) {
            return true;
        }
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.contains("sweet") || path.contains("cake") || path.contains("cookie")
            || path.contains("dango") || path.contains("honey") || path.contains("pie") || path.contains("sugar");
    }

    /** 沿水平视线冲刺推进。 */
    public static void dashForward(ServerPlayer player, double speed, double yBoost) {
        Vec3 look = player.getLookAngle();
        double len = Math.hypot(look.x, look.z);
        double dx = len > 1e-4 ? look.x / len : 0.0;
        double dz = len > 1e-4 ? look.z / len : 0.0;
        player.push(dx * speed, yBoost, dz * speed);
        player.hurtMarked = true;
    }

    /** 在实体胸口绽放粒子特效。 */
    public static void burstOn(LivingEntity target, int shape, int rgb, float size, int life, int count) {
        if (target.level() instanceof ServerLevel sl) {
            Fx.burst(sl, shape, rgb, size, life, 0.18f,
                target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                count, 0.35, 0.35, 0.35);
        }
    }
}
