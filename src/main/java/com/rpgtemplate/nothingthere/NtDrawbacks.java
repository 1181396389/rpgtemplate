package com.rpgtemplate.nothingthere;
import net.minecraft.resources.ResourceLocation;

import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.system.DrawAttunementUnlock;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

/**
 * 一无所有（O-06-20）继承的负面体质、弱点与异想体癖好（反噬/副作用）：
 * <ul>
 *   <li>弱点 {@code nt_white_pale_weakness}：惧怕精神/灵魂侵蚀（魂伤、魔法、龙息、凋零、音爆额外 +35% 伤害，压制回血 4 秒）。</li>
 *   <li>弱点 {@code nt_fire_aversion}：厌恶火焰净化（火焰/熔岩额外 +20% 伤害，每次流失 2 层适应）。</li>
 *   <li>负面体质 {@code nt_grotesque_shell}：拙劣拟态躯壳无法使用人类盾牌与弓弩；穿戴超过 2 件人类护甲减速 15% 且 SP 消耗 +25%。</li>
 *   <li>负面状态 {@code nt_shell_decay}：连续 90 秒未摄取血肉/击杀猎物时人皮腐朽，每秒流失 1.5% 最大 SP 且伤害衰减 15%。</li>
 *   <li>癖好 {@code nt_mimic_voice}：破碎模仿音令村民恐慌拒绝交易；近身猎杀活体可重塑人皮稳定度并额外获得模板契合经验。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "rpgtemplate")
public final class NtDrawbacks {
    static final long SHELL_DECAY_TICKS = 1800L;    // 90 秒不进食血肉则人皮腐朽
    static final long MENTAL_WOUND_TICKS = 80L;     // 4 秒灵魂/精神创伤压制回血
    static final double WHITE_PALE_EXTRA = 0.35;
    static final double FIRE_EXTRA = 0.20;
    static final double ARMOR_COST_MULT = 1.25;
    static final double SHELL_DECAY_DAMAGE_MULT = 0.85;

    private static final ResourceLocation SHELL_BIND_SPEED_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_shell_armor_bind");
    private static final Set<String> MENTAL_MSG_IDS = Set.of(
        "magic", "indirectMagic", "wither", "dragonBreath", "sonic_boom", "indirectSonicBoom");
    private static final Set<String> FIRE_MSG_IDS = Set.of(
        "inFire", "onFire", "lava", "hotFloor", "fireball", "unattributed_fireball");

    private NtDrawbacks() {
    }

    /** 穿戴超过 2 件人类护甲时，SP 技能消耗提高 25%。 */
    public static double costMultiplier(ServerPlayer player) {
        return countWornArmor(player) > 2 ? ARMOR_COST_MULT : 1.0;
    }

    /** 人皮腐朽期间输出伤害 ×0.85。 */
    public static double outputMultiplier(PlayerSystemData data) {
        return data != null && data.value(NtForms.KEY_SHELL_DECAYED, 0.0) > 0.5 ? SHELL_DECAY_DAMAGE_MULT : 1.0;
    }

    /** 摄取血肉（咬击或击杀猎物）后刷新人皮稳定度。 */
    public static void markFed(ServerPlayer player, PlayerSystemData data) {
        if (player == null || data == null) {
            return;
        }
        long now = player.level().getGameTime();
        boolean wasDecayed = data.value(NtForms.KEY_SHELL_DECAYED, 0.0) > 0.5;
        data.setValue(NtForms.KEY_LAST_FEED, (double) now);
        data.setValue(NtForms.KEY_SHELL_DECAYED, 0.0);
        SystemDataManager.markDirty((Player) player);
        if (wasDecayed) {
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.nt.shell_restored")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** 弱点判定：精神/灵魂易伤 & 火焰厌恶。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onVictimHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0f) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null || NtForms.inCocoon(player, data)) {
            return;
        }
        DamageSource source = event.getSource();
        String msgId = source.getMsgId();
        long now = player.level().getGameTime();
        if (ModDamageTypes.isSoulDamage(source) || MENTAL_MSG_IDS.contains(msgId)) {
            event.setAmount((float) (event.getAmount() * (1.0 + WHITE_PALE_EXTRA)));
            data.setValue(NtForms.KEY_MENTAL_WOUND_UNTIL, (double) (now + MENTAL_WOUND_TICKS));
            SystemDataManager.markDirty((Player) player);
        } else if (FIRE_MSG_IDS.contains(msgId)) {
            event.setAmount((float) (event.getAmount() * (1.0 + FIRE_EXTRA)));
            double adapt = data.value(NtForms.KEY_ADAPT, 0.0);
            if (adapt > 0.0) {
                data.setValue(NtForms.KEY_ADAPT, Math.max(0.0, adapt - 2.0));
                SystemDataManager.markDirty((Player) player);
            }
        }
    }

    /** 拙劣拟态躯壳：无法使用人类盾牌与精密弓弩。 */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        Item item = event.getItem().getItem();
        if (item instanceof ShieldItem || item instanceof ProjectileWeaponItem) {
            event.setCanceled(true);
            Msg.actionBar(player, Component.translatable("message.rpgtemplate.nt.clumsy_limbs")
                .withStyle(ChatFormatting.RED));
        }
    }

    /** 破碎模仿音：村民听到扭曲模仿语音会恐慌推开，拒绝交易。 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractVillager villager)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            return;
        }
        event.setCanceled(true);
        double dx = villager.getX() - player.getX();
        double dz = villager.getZ() - player.getZ();
        double len = Math.max(0.01, Math.hypot(dx, dz));
        villager.push(dx / len * 0.45, 0.15, dz / len * 0.45);
        Msg.actionBar(player, Component.translatable("message.rpgtemplate.nt.villager_fear")
            .withStyle(ChatFormatting.DARK_RED));
    }

    /** 近身猎杀活体：刷新人皮稳定度 + 触发异想体狩猎癖好经验加成。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKillPrey(LivingDeathEvent event) {
        Entity src = event.getSource().getEntity();
        LivingEntity victim = event.getEntity();
        if (!(src instanceof ServerPlayer hunter) || hunter == victim) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(hunter);
        if (data == null) {
            return;
        }
        markFed(hunter, data);
        if (hunter.distanceTo(victim) <= 6.5f) {
            DrawAttunementUnlock.grantQuirkXp(hunter, data, 6.0, "披皮拟态");
        }
    }

    /** 每秒结算：人类护甲束缚减速修饰符同步 + 人皮腐朽计时与 SP 流失。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.level().getGameTime();
        if (now % 20L != 0L) {
            return;
        }
        PlayerSystemData data = NtForms.activeData(player);
        if (data == null) {
            applyArmorBind(player, false);
            return;
        }
        applyArmorBind(player, countWornArmor(player) > 2);

        double lastFeed = data.value(NtForms.KEY_LAST_FEED, -1.0);
        if (lastFeed < 0.0 || now < lastFeed) {
            data.setValue(NtForms.KEY_LAST_FEED, (double) now);
            data.setValue(NtForms.KEY_SHELL_DECAYED, 0.0);
            SystemDataManager.markDirty((Player) player);
            return;
        }
        boolean decayed = (now - lastFeed) >= SHELL_DECAY_TICKS;
        double prev = data.value(NtForms.KEY_SHELL_DECAYED, 0.0);
        if (decayed) {
            if (prev < 0.5) {
                data.setValue(NtForms.KEY_SHELL_DECAYED, 1.0);
                Msg.actionBar(player, Component.translatable("message.rpgtemplate.nt.shell_decay_warn")
                    .withStyle(ChatFormatting.RED));
            }
            double maxSp = StatCalculator.maxStat(StatType.SP, data, TemplateRegistry.get(), CoreConfig.get());
            if (data.sp() > 0.0 && maxSp > 0.0) {
                data.setSp(Math.max(0.0, data.sp() - maxSp * 0.015));
            }
            SystemDataManager.markDirty((Player) player);
        } else if (prev > 0.5) {
            data.setValue(NtForms.KEY_SHELL_DECAYED, 0.0);
            SystemDataManager.markDirty((Player) player);
        }
    }

    private static int countWornArmor(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        Inventory inv = player.getInventory();
        int worn = 0;
        for (int slot = 36; slot < 40; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()) {
                worn++;
            }
        }
        return worn;
    }

    private static void applyArmorBind(ServerPlayer player, boolean bind) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier existing = speed.getModifier(SHELL_BIND_SPEED_ID);
        if (bind && existing == null) {
            speed.addTransientModifier(new AttributeModifier(SHELL_BIND_SPEED_ID, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!bind && existing != null) {
            speed.removeModifier(SHELL_BIND_SPEED_ID);
        }
    }
}
