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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 认知阻碍面具核心规则执行器（零廉价粒子）：
 * <ul>
 *   <li>规则等级生效内（{@link RuleJudge.Tier#FULL}）：任何生物无法看见佩戴者、无法锁定或产生敌意，并立刻清除已有仇恨。</li>
 *   <li>红石与感测绝对静默：配合 Mixin 阻断压力板、绊线、幽匿感测体/尖啸体、红石矿、探测铁轨、标靶与陷阱箱红石触发。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class PerceptionMaskRules {
    private PerceptionMaskRules() {
    }

    /** 实体当前是否正佩戴着有效的认知阻碍面具。 */
    public static boolean isActiveMaskWearer(Entity entity) {
        if (!(entity instanceof Player player) || !player.isAlive()) {
            return false;
        }
        ItemStack mask = PerceptionBlockingMaskItem.getWornMask(player);
        if (mask.isEmpty()) {
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
        if (entity instanceof Projectile proj && isActiveMaskWearer(proj.getOwner())) {
            return true;
        }
        if (isActiveMaskWearer(entity.getControllingPassenger())) {
            return true;
        }
        for (Entity passenger : entity.getPassengers()) {
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
        LivingEntity target = event.getNewAboutToBeSetTarget();
        LivingEntity attacker = event.getEntity();
        if (target instanceof Player && blocksMob(target, attacker)) {
            event.setNewAboutToBeSetTarget(null);
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
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now % 20L == 0L) {
            PerceptionMaskManager.tickPlayer(player);
        }
        if (now % 10L != 0L || !isActiveMaskWearer(player)) {
            return;
        }
        AABB scan = player.getBoundingBox().inflate(32.0);
        List<Mob> mobs = player.level().getEntitiesOfClass(Mob.class, scan);
        UUID pid = player.getUUID();
        for (Mob mob : mobs) {
            if (!mob.isAlive() || !blocksMob(player, mob)) {
                continue;
            }
            if (mob.getTarget() == player) {
                mob.setTarget(null);
                mob.setAggressive(false);
            }
            if (mob.getLastHurtByMob() == player) {
                mob.setLastHurtByMob(null);
            }
            if (mob instanceof NeutralMob neutral && pid.equals(neutral.getPersistentAngerTarget())) {
                neutral.stopBeingAngry();
            }
            if (mob instanceof Warden warden) {
                warden.clearAnger(player);
            }
            Brain<?> brain = mob.getBrain();
            if (brain != null) {
                if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)) {
                    Optional<LivingEntity> atk = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
                    if (atk.isPresent() && atk.get() == player) {
                        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    }
                }
                if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.VALUE_PRESENT)) {
                    Optional<UUID> angry = brain.getMemory(MemoryModuleType.ANGRY_AT);
                    if (angry.isPresent() && pid.equals(angry.get())) {
                        brain.eraseMemory(MemoryModuleType.ANGRY_AT);
                    }
                }
            }
        }
    }
}
