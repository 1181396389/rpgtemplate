package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.registry.ModCreativeTabs;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.templates.TemplateHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 假面骑士 Cronus 专属装备与武装物品注册：
 * <ul>
 *   <li>{@link #BUGGLE_DRIVER_II}：故障驱动器Ⅱ（可插入编年史转玩卡带进行手动变身 / 解除变身）。</li>
 *   <li>{@link #CHRONICLE_GASHAT}：假面骑士编年史转玩卡带（配合故障驱动器Ⅱ插入变身）。</li>
 *   <li>{@link #GASHACON_BUGVISOR_II}：Gashacon 缺陷护罩Ⅱ（变身后自动装备，蹲下右键切换光线枪 / 电锯模式，攻击加成 = 技能的 1/3）。</li>
 * </ul>
 */
public final class CronusItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, "rpgtemplate");

    public static final RegistryObject<BuggleDriverItem> BUGGLE_DRIVER_II =
        ITEMS.register("buggle_driver_ii", BuggleDriverItem::new);

    public static final RegistryObject<ChronicleGashatItem> CHRONICLE_GASHAT =
        ITEMS.register("chronicle_gashat", ChronicleGashatItem::new);

    public static final RegistryObject<GashaconBugvisorItem> GASHACON_BUGVISOR_II =
        ITEMS.register("gashacon_bugvisor_ii", GashaconBugvisorItem::new);

    public static final String TAG_OWNER = "cr.owner";
    public static final String TAG_TOKEN = "cr.token";
    public static final String TAG_GASHAT_LOADED = "cr.gashat_loaded";
    public static final String TAG_GASHAT_OWNER = "cr.gashat_owner";
    public static final String TAG_GASHAT_TOKEN = "cr.gashat_token";

    public static final String TAG_BUGVISOR_BOUND = "cr.bugvisor_bound";
    public static final String TAG_BUGVISOR_MODE = "cr.bugvisor_mode";
    public static final String TAG_BUGVISOR_BONUS = "cr.bugvisor_bonus";

    private CronusItems() {
    }

    public static void init() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        bus.addListener(CronusItems::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            event.accept(BUGGLE_DRIVER_II);
            event.accept(CHRONICLE_GASHAT);
            event.accept(GASHACON_BUGVISOR_II);
        }
    }

    // ==================== NBT 通用工具 ====================

    public static boolean isDriver(ItemStack stack) {
        return !stack.m_41619_() && stack.m_41720_() == BUGGLE_DRIVER_II.get();
    }

    public static boolean isGashat(ItemStack stack) {
        return !stack.m_41619_() && stack.m_41720_() == CHRONICLE_GASHAT.get();
    }

    public static boolean isBugvisor(ItemStack stack) {
        return !stack.m_41619_() && stack.m_41720_() == GASHACON_BUGVISOR_II.get();
    }

    /** 读取实体腰间（护腿槽位）装备的【故障驱动器Ⅱ】；未装备时返回空物品堆。 */
    public static ItemStack getWornDriver(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.f_41583_;
        }
        ItemStack legs = entity.m_6844_(EquipmentSlot.LEGS);
        return isDriver(legs) ? legs : ItemStack.f_41583_;
    }

    public static boolean isWearingDriver(LivingEntity entity) {
        return !getWornDriver(entity).m_41619_();
    }

    public static void stampOwned(ItemStack stack, UUID owner, long token) {
        CompoundTag tag = stack.m_41784_();
        if (owner != null) {
            tag.m_128362_(TAG_OWNER, owner);
        }
        tag.m_128356_(TAG_TOKEN, token);
    }

    public static UUID ownerOf(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null && tag.m_128403_(TAG_OWNER) ? tag.m_128342_(TAG_OWNER) : null;
    }

    public static long tokenOf(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null ? tag.m_128454_(TAG_TOKEN) : 0L;
    }

    public static boolean isDriverLoaded(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null && tag.m_128471_(TAG_GASHAT_LOADED);
    }

    public static UUID loadedGashatOwner(ItemStack driver) {
        CompoundTag tag = driver.m_41783_();
        return tag != null && tag.m_128403_(TAG_GASHAT_OWNER) ? tag.m_128342_(TAG_GASHAT_OWNER) : null;
    }

    public static long loadedGashatToken(ItemStack driver) {
        CompoundTag tag = driver.m_41783_();
        return tag != null ? tag.m_128454_(TAG_GASHAT_TOKEN) : 0L;
    }

    public static void setDriverLoaded(ItemStack driver, UUID gashatOwner, long gashatToken) {
        CompoundTag tag = driver.m_41784_();
        tag.m_128379_(TAG_GASHAT_LOADED, true);
        if (gashatOwner != null) {
            tag.m_128362_(TAG_GASHAT_OWNER, gashatOwner);
        } else {
            tag.m_128473_(TAG_GASHAT_OWNER);
        }
        tag.m_128356_(TAG_GASHAT_TOKEN, gashatToken);
    }

    public static void clearDriverLoaded(ItemStack driver) {
        CompoundTag tag = driver.m_41784_();
        tag.m_128379_(TAG_GASHAT_LOADED, false);
        tag.m_128473_(TAG_GASHAT_OWNER);
        tag.m_128473_(TAG_GASHAT_TOKEN);
    }

    public static int bugvisorMode(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null && tag.m_128451_(TAG_BUGVISOR_MODE) == CronusKeys.MODE_GUN
            ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW;
    }

    public static void setBugvisorMode(ItemStack stack, int mode) {
        stack.m_41784_().m_128405_(TAG_BUGVISOR_MODE, mode == CronusKeys.MODE_GUN ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW);
    }

    public static boolean isBugvisorBound(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null && tag.m_128471_(TAG_BUGVISOR_BOUND);
    }

    public static float cachedBugvisorBonus(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        return tag != null ? tag.m_128457_(TAG_BUGVISOR_BONUS) : 0.0f;
    }

    public static void updateBugvisorBonusTag(ItemStack stack, float bonus) {
        float rounded = Math.round(bonus * 10.0f) / 10.0f;
        if (Math.abs(cachedBugvisorBonus(stack) - rounded) > 0.05f) {
            stack.m_41784_().m_128350_(TAG_BUGVISOR_BONUS, rounded);
        }
    }

    // ==================== 1. 故障驱动器Ⅱ（可装备至腰间 / 护腿槽位） ====================

    public static final class BuggleDriverItem extends Item implements Equipable {
        public BuggleDriverItem() {
            super(new Item.Properties().m_41487_(1).m_41497_(Rarity.EPIC).m_41486_());
        }

        @Override
        public EquipmentSlot m_40402_() {
            return EquipmentSlot.LEGS;
        }

        @Override
        public SoundEvent m_150681_() {
            return SoundEvents.f_11673_; // ARMOR_EQUIP_IRON
        }

        @Override
        public EquipmentSlot getEquipmentSlot(ItemStack stack) {
            return EquipmentSlot.LEGS;
        }

        @Override
        public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
            return armorType == EquipmentSlot.LEGS;
        }

        @Override
        public boolean m_5812_(ItemStack stack) {
            return isDriverLoaded(stack);
        }

        @Override
        public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
            ItemStack driver = player.m_21120_(hand);
            // 兼容旧版：若手持旧存档中已插卡的驱动器且潜行右键，未变身时安全弹出旧卡带（变身期间禁止手动拔卡）
            if (player instanceof ServerPlayer sp && sp.m_6144_() && isDriverLoaded(driver)) {
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active != null && CronusKeys.isTransformed(active)) {
                    Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.gashat_skill_only_off")
                        .m_130940_(ChatFormatting.YELLOW));
                    return InteractionResultHolder.m_19098_(driver);
                }
                UUID gOwner = loadedGashatOwner(driver);
                long gToken = loadedGashatToken(driver);
                clearDriverLoaded(driver);
                if (gToken > 0L) {
                    ItemStack gashat = new ItemStack(CHRONICLE_GASHAT.get());
                    stampOwned(gashat, gOwner, gToken);
                    if (!sp.m_150109_().m_36054_(gashat)) {
                        sp.m_36176_(gashat, false);
                    }
                    CronusGearManager.syncGearStates(sp);
                    Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.gashat_ejected")
                        .m_130940_(ChatFormatting.AQUA));
                    return InteractionResultHolder.m_19096_(driver);
                }
            }

            // 右键一键将【故障驱动器Ⅱ】装备至腰间（护腿槽位），与已有护腿安全互换
            InteractionResultHolder<ItemStack> res = this.m_269277_(this, level, player, hand);
            if (player instanceof ServerPlayer sp && res.m_19089_().m_19077_()) {
                sp.m_9236_().m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(),
                    SoundEvents.f_11983_, SoundSource.PLAYERS, 0.70f, 1.25f);
                CronusGearManager.syncGearStates(sp);
                Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.driver_equipped")
                    .m_130940_(ChatFormatting.GREEN));
            }
            return res;
        }

        @Override
        public void m_7373_(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            boolean loaded = isDriverLoaded(stack);
            tips.add(Component.m_237115_(loaded
                ? "tooltip.rpgtemplate.buggle_driver_ii.state_loaded"
                : "tooltip.rpgtemplate.buggle_driver_ii.state_empty")
                .m_130940_(loaded ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            tips.add(Component.m_237115_("tooltip.rpgtemplate.buggle_driver_ii.usage")
                .m_130940_(ChatFormatting.DARK_AQUA));
            tips.add(Component.m_237115_("tooltip.rpgtemplate.buggle_driver_ii.eject")
                .m_130940_(ChatFormatting.DARK_GRAY));
        }
    }

    // ==================== 2. 假面骑士编年史转玩卡带（腰佩驱动器后右键快捷变身！） ====================

    public static final class ChronicleGashatItem extends Item {
        public ChronicleGashatItem() {
            super(new Item.Properties().m_41487_(1).m_41497_(Rarity.EPIC).m_41486_());
        }

        @Override
        public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
            ItemStack gashat = player.m_21120_(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.m_19090_(gashat);
            }

            // 核心玩法：腰间（护腿槽）已装备【故障驱动器Ⅱ】时，右键卡带将其直接插入腰带并触发快捷版变身（卡带消失，只能用技能解除并归还）！
            if (isWearingDriver(sp)) {
                if (!SystemDataManager.available(sp)) {
                    return InteractionResultHolder.m_19098_(gashat);
                }
                PlayerSystemData sys = SystemDataManager.get(sp);
                if (!CronusKeys.ownsCronusTemplate(sys)) {
                    Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.driver_need_template")
                        .m_130940_(ChatFormatting.RED));
                    return InteractionResultHolder.m_19098_(gashat);
                }
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active == null) {
                    Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.driver_need_load")
                        .m_130940_(ChatFormatting.YELLOW));
                    return InteractionResultHolder.m_19098_(gashat);
                }
                if (sp.m_36335_().m_41519_(this)) {
                    return InteractionResultHolder.m_19098_(gashat);
                }
                sp.m_36335_().m_41524_(this, 24);
                boolean started = CronusSkills.tryHenshinWithGashat(sp, active, gashat, sp.m_9236_().m_46467_());
                return started ? InteractionResultHolder.m_19096_(gashat) : InteractionResultHolder.m_19098_(gashat);
            }

            // 已处于变身态但未佩戴驱动器时也明确提示只能用技能解除变身
            PlayerSystemData activeNow = CronusKeys.activeData(sp);
            if (activeNow != null && CronusKeys.isTransformed(activeNow)) {
                Msg.actionBar(sp, Component.m_237115_("message.rpgtemplate.cronus.gashat_skill_only_off")
                    .m_130940_(ChatFormatting.YELLOW));
                return InteractionResultHolder.m_19098_(gashat);
            }

            // 未将故障驱动器Ⅱ装备至腰间：播放待机音效并提示先将驱动器装备至身上
            sp.m_9236_().m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(),
                SoundEvents.f_11983_, SoundSource.PLAYERS, 0.85f, 1.45f);
            boolean hasDriverInBag = hasDriverInInventory(sp);
            Msg.actionBar(sp, Component.m_237115_(hasDriverInBag
                    ? "message.rpgtemplate.cronus.gashat_wear_driver"
                    : "message.rpgtemplate.cronus.gashat_standby")
                .m_130940_(ChatFormatting.YELLOW));
            return InteractionResultHolder.m_19096_(gashat);
        }

        @Override
        public void m_7373_(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            tips.add(Component.m_237115_("tooltip.rpgtemplate.chronicle_gashat.desc")
                .m_130940_(ChatFormatting.GREEN));
            tips.add(Component.m_237115_("tooltip.rpgtemplate.chronicle_gashat.usage")
                .m_130940_(ChatFormatting.GRAY));
        }
    }

    // ==================== 3. Gashacon 缺陷护罩Ⅱ（电锯 / 光线枪双形态武装） ====================

    private static final class BugvisorTier implements Tier {
        static final BugvisorTier INSTANCE = new BugvisorTier();

        @Override
        public int m_6609_() {
            return 0; // 无限耐久
        }

        @Override
        public float m_6624_() {
            return Tiers.DIAMOND.m_6624_();
        }

        @Override
        public float m_6631_() {
            return 2.0f;
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
            return Ingredient.f_43901_;
        }
    }

    public static final class GashaconBugvisorItem extends SwordItem {
        public static final float BASE_WEAPON_DAMAGE = 6.0f;

        public GashaconBugvisorItem() {
            // 基础面板攻击 1 + 2 + 3 = 6.0，攻速 4 - 2.0 = 2.0；实际战斗额外叠加 Cronus 技能攻击力的 1/3！
            super(BugvisorTier.INSTANCE, 3, -2.0f, new Item.Properties().m_41487_(1).m_41499_(0).m_41497_(Rarity.EPIC).m_41486_());
        }

        @Override
        public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.m_21120_(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.m_19090_(stack);
            }

            // 蹲下右键：无消耗瞬切【光线枪模式】⇄【电锯模式】，并同步至 Cronus 全局武装状态（联动必杀技与 HUD）
            if (sp.m_6144_()) {
                int cur = bugvisorMode(stack);
                int next = (cur == CronusKeys.MODE_CHAINSAW) ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW;
                setBugvisorMode(stack, next);
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active != null) {
                    active.setValue(CronusKeys.KEY_WEAPON_MODE, (double) next);
                    SystemDataManager.markDirty((Player) sp);
                    SystemDataManager.sync(sp);
                }
                CronusSounds.playModeSwitch(sp);
                Msg.actionBar(sp, Component.m_237115_(next == CronusKeys.MODE_GUN
                        ? "message.rpgtemplate.cronus.mode_gun"
                        : "message.rpgtemplate.cronus.mode_chainsaw")
                    .m_130940_(ChatFormatting.AQUA));
                return InteractionResultHolder.m_19096_(stack);
            }

            if (sp.m_36335_().m_41519_(this)) {
                return InteractionResultHolder.m_19098_(stack);
            }

            PlayerSystemData active = CronusKeys.activeData(sp);
            float bonus = active != null ? CronusKeys.bugvisorAttackBonus(active) : 4.0f;
            updateBugvisorBonusTag(stack, bonus);
            float totalDmg = BASE_WEAPON_DAMAGE + bonus;

            int mode = bugvisorMode(stack);
            if (mode == CronusKeys.MODE_GUN) {
                // 光线枪模式右键：发射高速双管翡翠能源光束（24m 穿透 2 目标，伤害 = 基础 + 1/3 技能攻击加成）
                sp.m_36335_().m_41524_(this, 10);
                fireBeamGun(sp, hand, totalDmg);
            } else {
                // 电锯模式右键：发动近距离高速翡翠链锯弧斩（4.2m 扇形，伤害 = 基础 + 1/3 技能攻击加成）
                sp.m_36335_().m_41524_(this, 12);
                performChainsawSlash(sp, hand, totalDmg);
            }
            return InteractionResultHolder.m_19096_(stack);
        }

        private static void fireBeamGun(ServerPlayer sp, InteractionHand hand, float dmg) {
            sp.m_6674_(hand);
            // 发射真实物理激光弹射物实体（3D 翡翠双轨光矛，射程 36 格，最多穿透 2 名敌人）
            CronusLaserBoltEntity.launch(sp, hand, dmg);
            sp.m_9236_().m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(),
                SoundEvents.f_11736_, SoundSource.PLAYERS, 0.85f, 1.65f);
            sp.m_9236_().m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(),
                SoundEvents.f_11871_, SoundSource.PLAYERS, 0.45f, 1.95f);
        }

        private static void performChainsawSlash(ServerPlayer sp, InteractionHand hand, float dmg) {
            sp.m_6674_(hand);
            TemplateHelper.dashForward(sp, 0.32, 0.04);
            Vec3 eye = sp.m_146892_();
            Vec3 look = sp.m_20154_().m_82541_();
            double range = 4.3;
            AABB box = sp.m_20191_().m_82377_(range, 2.2, range);
            double rSq = range * range;
            List<LivingEntity> victims = new ArrayList<>();
            for (LivingEntity e : sp.m_9236_().m_45976_(LivingEntity.class, box)) {
                if (e == sp || !e.m_6084_() || e.m_20280_(sp) > rSq) {
                    continue;
                }
                Vec3 dir = e.m_20191_().m_82399_().m_82546_(eye).m_82541_();
                if (dir.m_82526_(look) >= 0.22) {
                    victims.add(e);
                }
            }
            // 若玩家正处于【电锯必杀技二·高能聚集】状态且本次弧斩命中敌人，则引爆全部附伤（首名全额，其余 65% 顺劈）
            PlayerSystemData data = CronusKeys.activeData(sp);
            long now = sp.m_9236_().m_46467_();
            float overchargeBonus = 0.0f;
            if (!victims.isEmpty() && data != null) {
                overchargeBonus = CronusCombatAndPassives.consumeChainsawOvercharge(sp, data, now);
            }
            for (int i = 0; i < victims.size(); i++) {
                LivingEntity v = victims.get(i);
                float hitDmg = dmg + (overchargeBonus > 0.0f ? (i == 0 ? overchargeBonus : overchargeBonus * 0.65f) : 0.0f);
                if (overchargeBonus > 0.0f) {
                    v.f_19802_ = 0;
                }
                TargetUtil.hurt(v, ModDamageTypes.technique(sp), hitDmg);
                if (overchargeBonus > 0.0f && i < 3) {
                    CronusCombatAndPassives.emitChainsawOverchargeBurst(sp, v);
                }
            }
            if (sp.m_9236_() instanceof ServerLevel sl) {
                Vec3 slashPos = eye.m_82549_(look.m_82490_(1.65));
                CronusNet.sendNear(sl, slashPos.f_82479_, slashPos.f_82480_, slashPos.f_82481_,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, sp.m_19879_(),
                        sp.m_20185_(), sp.m_20186_() + 0.08, sp.m_20189_(), overchargeBonus > 0.0f ? 3.8f : 2.1f, 0));
            }
            sp.m_9236_().m_6263_(null, sp.m_20185_(), sp.m_20186_(), sp.m_20189_(),
                SoundEvents.f_12317_, SoundSource.PLAYERS, 0.90f, overchargeBonus > 0.0f ? 0.65f : 0.92f);
        }

        @Override
        public void m_7373_(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag) {
            int mode = bugvisorMode(stack);
            tips.add(Component.m_237115_(mode == CronusKeys.MODE_GUN
                ? "tooltip.rpgtemplate.bugvisor.mode_gun"
                : "tooltip.rpgtemplate.bugvisor.mode_chainsaw")
                .m_130940_(mode == CronusKeys.MODE_GUN ? ChatFormatting.AQUA : ChatFormatting.GREEN));
            float bonus = cachedBugvisorBonus(stack);
            if (bonus > 0.05f) {
                tips.add(Component.m_237110_("tooltip.rpgtemplate.bugvisor.bonus_live",
                    String.format(Locale.ROOT, "%.1f", bonus))
                    .m_130940_(ChatFormatting.GOLD));
            } else {
                tips.add(Component.m_237115_("tooltip.rpgtemplate.bugvisor.bonus_static")
                    .m_130940_(ChatFormatting.GOLD));
            }
            tips.add(Component.m_237115_("tooltip.rpgtemplate.bugvisor.controls")
                .m_130940_(ChatFormatting.GRAY));
        }
    }

    // ==================== 内部检索辅助 ====================

    private static boolean hasDriverInInventory(ServerPlayer sp) {
        Inventory inv = sp.m_150109_();
        for (ItemStack s : inv.f_35976_) {
            if (isDriver(s)) return true;
        }
        for (ItemStack s : inv.f_35974_) {
            if (isDriver(s)) return true;
        }
        return false;
    }
}
