package com.rpgtemplate.forge.mask;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.skill.RuleJudge;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 认知阻碍面具核心规则执行器（零廉价粒子）：
 * <ul>
 *   <li>规则等级生效内（{@link RuleJudge.Tier#FULL}）：任何生物无法看见佩戴者、无法锁定或产生敌意，并立刻清除已有仇恨。</li>
 *   <li>红石与感测绝对静默：配合 Mixin 阻断压力板、绊线、幽匿感测体/尖啸体、红石矿、探测铁轨、标靶与陷阱箱红石触发。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class PerceptionMaskRules {
    private PerceptionMaskRules() {
    }

    /** 实体当前是否正佩戴着有效的认知阻碍面具。 */
    public static boolean isActiveMaskWearer(Entity entity) {
        if (!(entity instanceof Player player) || !player.m_6084_()) {
            return false;
        }
        ItemStack mask = PerceptionBlockingMaskItem.getWornMask(player);
        if (mask.m_41619_()) {
            return false;
        }
        if (player instanceof ServerPlayer sp) {
            return PerceptionMaskManager.isStackValidOnServer(sp, mask);
        }
        return true;
    }

    /**
     * 判断实体（玩家本人、面具佩戴者乘坐的载具/矿车、或佩戴者射出的投射物）是否应免疫红石/振动触发。
     */
    public static boolean shouldIgnoreRedstone(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (isActiveMaskWearer(entity)) {
            return true;
        }
        if (entity instanceof Projectile proj && isActiveMaskWearer(proj.m_19749_())) {
            return true;
        }
        if (isActiveMaskWearer(entity.m_6688_())) {
            return true;
        }
        for (Entity passenger : entity.m_20197_()) {
            if (isActiveMaskWearer(passenger)) {
                return true;
            }
        }
        return false;
    }

    /** 使用模组统一四档机制判定 {@link RuleJudge} 评估面具对该生物的认知阻隔档位。 */
    public static RuleJudge.Tier evaluateTier(Player wearer, LivingEntity observer) {
        if (!isActiveMaskWearer(wearer)) {
            return RuleJudge.Tier.FAIL;
        }
        if (observer == null || observer == wearer) {
            return RuleJudge.Tier.FULL;
        }
        if (wearer instanceof ServerPlayer sp) {
            PlayerSystemData data = SystemDataManager.available(sp) ? SystemDataManager.get(sp) : null;
            return RuleJudge.evaluate(sp, data, observer, null).tier();
        }
        return RuleJudge.Tier.FULL;
    }

    /** 判断指定生物在规则等级生效内是否完全无法看到/锁定/敌视佩戴者。 */
    public static boolean blocksMob(LivingEntity wearer, LivingEntity mob) {
        if (!(wearer instanceof Player player) || mob == null || mob instanceof Player) {
            return false;
        }
        if (!isActiveMaskWearer(player)) {
            return false;
        }
        return evaluateTier(player, mob) == RuleJudge.Tier.FULL;
    }

    /** 降低/清零生物对佩戴者的视觉发现倍率。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isActiveMaskWearer(player)) {
            return;
        }
        Entity looking = event.getLookingEntity();
        if (looking instanceof Player) {
            return;
        }
        LivingEntity obs = looking instanceof LivingEntity le ? le : null;
        RuleJudge.Tier tier = evaluateTier(player, obs);
        switch (tier) {
            case FULL -> event.modifyVisibility(0.0);
            case MAJOR -> event.modifyVisibility(0.15);
            case MINOR -> event.modifyVisibility(0.50);
            case FAIL -> {
            }
        }
    }

    /** 拦截生物产生敌意/切换攻击目标到佩戴者。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        LivingEntity attacker = event.getEntity();
        if (target instanceof Player && blocksMob(target, attacker)) {
            event.setNewTarget(null);
            event.setCanceled(true);
        }
    }

    /** 拦截佩戴者（及其坐骑/投射物）产生的一切游戏振动事件，彻底静默幽匿感测体/校频感测体/尖啸体与监守者。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onVanillaGameEvent(VanillaGameEvent event) {
        if (shouldIgnoreRedstone(event.getCause())) {
            event.setCanceled(true);
        }
    }

    /** 服务端周期同步：维护面板面具状态，并立刻清除周边已处于追击状态的生物仇恨。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        long now = player.m_9236_().m_46467_();
        if (now % 20L == 0L) {
            PerceptionMaskManager.tickPlayer(player);
        }
        if (now % 10L != 0L || !isActiveMaskWearer(player)) {
            return;
        }
        AABB scan = player.m_20191_().m_82400_(32.0);
        List<Mob> mobs = player.m_9236_().m_45976_(Mob.class, scan);
        UUID pid = player.m_20148_();
        for (Mob mob : mobs) {
            if (!mob.m_6084_() || !blocksMob(player, mob)) {
                continue;
            }
            if (mob.m_5448_() == player) {
                mob.m_6710_(null);
                mob.m_21561_(false);
            }
            if (mob.m_21188_() == player) {
                mob.m_6703_(null);
            }
            if (mob instanceof NeutralMob neutral && pid.equals(neutral.m_6120_())) {
                neutral.m_21662_();
            }
            if (mob instanceof Warden warden) {
                warden.m_219428_(player);
            }
            Brain<?> brain = mob.m_6274_();
            if (brain != null) {
                if (brain.m_21876_(MemoryModuleType.f_26372_, MemoryStatus.VALUE_PRESENT)) {
                    Optional<LivingEntity> atk = brain.m_21952_(MemoryModuleType.f_26372_);
                    if (atk.isPresent() && atk.get() == player) {
                        brain.m_21936_(MemoryModuleType.f_26372_);
                    }
                }
                if (brain.m_21876_(MemoryModuleType.f_26334_, MemoryStatus.VALUE_PRESENT)) {
                    Optional<UUID> angry = brain.m_21952_(MemoryModuleType.f_26334_);
                    if (angry.isPresent() && pid.equals(angry.get())) {
                        brain.m_21936_(MemoryModuleType.f_26334_);
                    }
                }
            }
        }
    }
}
