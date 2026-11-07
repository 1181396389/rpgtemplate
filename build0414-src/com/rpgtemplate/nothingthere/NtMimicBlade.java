package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 殷红迷雾·原型拟态刀（设计见 nt-sword/DESIGN.md 第 3 节）。
 *
 * <p>规则：
 * <ul>
 *   <li>来源：一无所有被其他玩家击败并真正死亡（茧未触发）时，在死亡位置掉落。</li>
 *   <li>唯一：世界中（掉落物或任一在线玩家背包）已存在时不再掉落。</li>
 *   <li>消失：未被拾取的剑使用 ItemEntity 默认生命周期（6000 tick，即 5 分钟）。</li>
 *   <li>绑定：第一个拾取者被写入 {@link #OWNER_KEY}；其他人不能再拾取。绑定者死亡时剑直接消失（不掉落）。</li>
 *   <li>持有者效果（持剑主手）：闪避概率随持有者的命中次数逐渐提升；招架为完全抵挡一次攻击。</li>
 * </ul>
 *
 * <p>占位数值（设计文档 3.2 第 2 项，待定，调这里即可）：
 * 闪避基础概率 {@link #DODGE_BASE}、每次命中增量 {@link #DODGE_PER_HIT}、上限 {@link #DODGE_CAP}；
 * 招架概率 {@link #PARRY_CHANCE}。招架动画未做。
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate")
public final class NtMimicBlade {
    public static final String ID = "nt_mimic_blade";
    /** 绑定者 UUID 写在物品 NBT 中的键（设计文档 nt.bound_owner）。 */
    public static final String OWNER_KEY = "nt.bound_owner";
    /** 持有者命中次数写在物品 NBT 中的键，用于闪避成长。 */
    public static final String HITS_KEY = "nt.hits";

    /** 闪避基础概率 5%（已确认）。 */
    public static final double DODGE_BASE = 0.05;
    /** 每次命中的闪避增量 +1%（已确认）。 */
    public static final double DODGE_PER_HIT = 0.01;
    /** 常态闪避上限 40%（已确认）。 */
    public static final double DODGE_CAP = 0.40;
    /** 常态招架概率 35%（已确认，完全抵挡一次攻击）。 */
    public static final double PARRY_CHANCE = 0.35;
    /** 解放后的闪避/招架概率（DESIGN 8.2）。 */
    static final double LIBERATED_DODGE_PARRY = NtMimicCombat.LIBERATED_DODGE_PARRY;

    /** 拟态刀自己的物品注册器（已随职业系统移除，不再依赖旧的物品注册器）。 */
    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(ForgeRegistries.ITEMS, "rpgtemplate");

    public static final RegistryObject<Item> ITEM =
            REGISTER.register(ID, Blade::new);

    /**
     * 拟态刀物品：基础武器数值（设计建议，待确认）：钻石剑级别，攻击伤害 40、攻速 2.0（已确认），不损耗（durability 0）。
     * 右键释放当前技能，潜行右键切换技能（DESIGN 8.3，按键待定）。
     */
    /**
     * 拟态刀的等级：攻击加成与钻石相同，但使用次数为 0。原版 Item.Properties.defaultDurability 会用等级的使用次数
     * 覆盖 durability(0)，所以不能直接传 Tiers.DIAMOND（否则刀会有耐久）。
     */
    static final class BladeTier implements Tier {
        static final BladeTier INSTANCE = new BladeTier();

        @Override
        public int m_6609_() {
            return 0;
        }

        @Override
        public float m_6624_() {
            return Tiers.DIAMOND.m_6624_();
        }

        @Override
        public float m_6631_() {
            return Tiers.DIAMOND.m_6631_();
        }

        @Override
        public int m_6604_() {
            return Tiers.DIAMOND.m_6604_();
        }

        @Override
        public int m_6601_() {
            return Tiers.DIAMOND.m_6601_();
        }

        @Override
        public Ingredient m_6282_() {
            return Tiers.DIAMOND.m_6282_();
        }
    }

    public static final class Blade extends SwordItem {
        public Blade() {
            // 显示攻击 = 1（基础）+ 3（钻石）+ 修正值：修正 36 → 40；攻速 = 4 + (-2.0) = 2.0
            super(BladeTier.INSTANCE, 36, -2.0f, new Item.Properties().m_41487_(1).m_41499_(0));
        }

        @Override
        public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.m_21120_(hand);
            if (player instanceof ServerPlayer sp) {
                NtMimicCombat.use(sp, player.m_6144_());
            }
            return InteractionResultHolder.m_19096_(stack); // success（与原版 Item.use 一致）
        }
    }

    private NtMimicBlade() {
    }

    /** 仅用于触发类加载，确保在 DeferredRegister 绑定总线之前完成注册。 */
    public static void init() {
    }

    public static boolean isBlade(ItemStack stack) {
        return !stack.m_41619_() && stack.m_41720_() == ITEM.get();
    }

    static boolean isNothingThere(Player player) {
        if (!SystemDataManager.available(player)) {
            return false;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        return data.isActivated() && NtBody.TEMPLATE_ID.equals(data.loadedTemplateId());
    }

    /** 唯一性：掉落物（当前维度附近全图）或任一在线玩家背包中已有这把剑则不再掉落。 */
    static boolean existsAnywhere(ServerLevel level) {
        AABB everywhere = new AABB(-3.0E7, -2048.0, -3.0E7, 3.0E7, 2048.0, 3.0E7);
        if (!level.m_6443_(ItemEntity.class, everywhere, e -> isBlade(e.m_32055_())).isEmpty()) {
            return true;
        }
        for (ServerPlayer p : level.m_7654_().m_6846_().m_11314_()) {
            Inventory inv = p.m_150109_();
            for (int i = 0; i < inv.m_6643_(); i++) {
                if (isBlade(inv.m_8020_(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 由 NothingThereDeath 调用：victim 是一无所有，hunter 是其他玩家，且茧未触发。 */
    public static void tryDrop(ServerPlayer victim, ServerPlayer hunter) {
        if (hunter == victim || !isNothingThere(victim)) {
            return;
        }
        if (!(victim.m_9236_() instanceof ServerLevel level)) {
            return;
        }
        if (existsAnywhere(level)) {
            return;
        }
        ItemEntity drop = new ItemEntity(level, victim.m_20185_(), victim.m_20186_(), victim.m_20189_(),
                new ItemStack(ITEM.get()));
        level.m_7967_(drop);
    }

    /** 拾取：第一个拾取者绑定；绑定后其他人不能拾取。 */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        ItemStack stack = event.getItem().m_32055_();
        if (!isBlade(stack) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag tag = stack.m_41784_();
        String me = player.m_20148_().toString();
        String owner = tag.m_128461_(OWNER_KEY);
        if (owner.isEmpty()) {
            tag.m_128359_(OWNER_KEY, me);
        } else if (!owner.equals(me)) {
            event.setCanceled(true);
        }
    }

    /** 绑定者死亡：绑定的剑不掉落，直接消失。 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        String me = victim.m_20148_().toString();
        event.getDrops().removeIf(d -> isBlade(d.m_32055_())
                && me.equals(d.m_32055_().m_41784_().m_128461_(OWNER_KEY)));
    }

    /** 持有者命中（流血结算除外）：累计命中次数，用于闪避成长。 */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (NtMimicCombat.ticking || !(event.getSource().m_7639_() instanceof ServerPlayer hunter)) {
            return;
        }
        ItemStack held = hunter.m_21120_(InteractionHand.MAIN_HAND);
        if (!isBlade(held)) {
            return;
        }
        CompoundTag tag = held.m_41784_();
        tag.m_128405_(HITS_KEY, tag.m_128451_(HITS_KEY) + 1);
    }

    /** 持剑主手：按概率闪避或招架（两者都完全抵挡这一次攻击）。解放后两者均为 60%。 */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        ItemStack held = victim.m_21120_(InteractionHand.MAIN_HAND);
        if (!isBlade(held)) {
            return;
        }
        boolean lib = NtMimicCombat.liberated(victim);
        int hits = held.m_41784_().m_128451_(HITS_KEY);
        double dodge = lib ? LIBERATED_DODGE_PARRY : Math.min(DODGE_CAP, DODGE_BASE + hits * DODGE_PER_HIT);
        double parry = lib ? LIBERATED_DODGE_PARRY : PARRY_CHANCE;
        // 闪避与招架各自独立掷骰；任一成功即完全抵挡这一次攻击。
        boolean dodged = victim.m_217043_().m_188500_() < dodge;
        boolean parried = victim.m_217043_().m_188500_() < parry;
        if (dodged || parried) {
            event.setCanceled(true);
        }
    }
}
