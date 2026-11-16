package com.rpgtemplate.cronus;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.RpgTemplateMod;
import com.rpgtemplate.forge.combat.ModDamageTypes;
import com.rpgtemplate.forge.registry.ModCreativeTabs;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.util.ItemNbt;
import com.rpgtemplate.templates.TemplateHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 假面骑士 Cronus 专属装备与武装物品注册（NeoForge 1.21.1）。
 */
public final class CronusItems {
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, "rpgtemplate");

    public static final DeferredHolder<Item, BuggleDriverItem> BUGGLE_DRIVER_II =
        ITEMS.register("buggle_driver_ii", BuggleDriverItem::new);

    public static final DeferredHolder<Item, ChronicleGashatItem> CHRONICLE_GASHAT =
        ITEMS.register("chronicle_gashat", ChronicleGashatItem::new);

    public static final DeferredHolder<Item, GashaconBugvisorItem> GASHACON_BUGVISOR_II =
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
        init(RpgTemplateMod.MOD_BUS);
    }

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(CronusItems::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.MAIN.getKey())) {
            event.accept(BUGGLE_DRIVER_II.get());
            event.accept(CHRONICLE_GASHAT.get());
            event.accept(GASHACON_BUGVISOR_II.get());
        }
    }

    // ==================== NBT 通用工具（基于 1.21.1 DataComponents.CUSTOM_DATA） ====================

    public static boolean isDriver(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == BUGGLE_DRIVER_II.get();
    }

    public static boolean isGashat(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == CHRONICLE_GASHAT.get();
    }

    public static boolean isBugvisor(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == GASHACON_BUGVISOR_II.get();
    }

    public static ItemStack getWornDriver(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        return isDriver(legs) ? legs : ItemStack.EMPTY;
    }

    public static boolean isWearingDriver(LivingEntity entity) {
        return !getWornDriver(entity).isEmpty();
    }

    public static void stampOwned(ItemStack stack, UUID owner, long token) {
        ItemNbt.update(stack, tag -> {
            if (owner != null) {
                tag.putUUID(TAG_OWNER, owner);
            }
            tag.putLong(TAG_TOKEN, token);
        });
    }

    public static UUID ownerOf(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    public static long tokenOf(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null ? tag.getLong(TAG_TOKEN) : 0L;
    }

    public static boolean isDriverLoaded(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.getBoolean(TAG_GASHAT_LOADED);
    }

    public static UUID loadedGashatOwner(ItemStack driver) {
        CompoundTag tag = ItemNbt.getTag(driver);
        return tag != null && tag.hasUUID(TAG_GASHAT_OWNER) ? tag.getUUID(TAG_GASHAT_OWNER) : null;
    }

    public static long loadedGashatToken(ItemStack driver) {
        CompoundTag tag = ItemNbt.getTag(driver);
        return tag != null ? tag.getLong(TAG_GASHAT_TOKEN) : 0L;
    }

    public static void setDriverLoaded(ItemStack driver, UUID gashatOwner, long gashatToken) {
        ItemNbt.update(driver, tag -> {
            tag.putBoolean(TAG_GASHAT_LOADED, true);
            if (gashatOwner != null) {
                tag.putUUID(TAG_GASHAT_OWNER, gashatOwner);
            } else {
                tag.remove(TAG_GASHAT_OWNER);
            }
            tag.putLong(TAG_GASHAT_TOKEN, gashatToken);
        });
    }

    public static void clearDriverLoaded(ItemStack driver) {
        ItemNbt.update(driver, tag -> {
            tag.putBoolean(TAG_GASHAT_LOADED, false);
            tag.remove(TAG_GASHAT_OWNER);
            tag.remove(TAG_GASHAT_TOKEN);
        });
    }

    public static int bugvisorMode(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.getInt(TAG_BUGVISOR_MODE) == CronusKeys.MODE_GUN
            ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW;
    }

    public static void setBugvisorMode(ItemStack stack, int mode) {
        ItemNbt.putInt(stack, TAG_BUGVISOR_MODE, mode == CronusKeys.MODE_GUN ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW);
    }

    public static boolean isBugvisorBound(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null && tag.getBoolean(TAG_BUGVISOR_BOUND);
    }

    public static float cachedBugvisorBonus(ItemStack stack) {
        CompoundTag tag = ItemNbt.getTag(stack);
        return tag != null ? tag.getFloat(TAG_BUGVISOR_BONUS) : 0.0f;
    }

    public static void updateBugvisorBonusTag(ItemStack stack, float bonus) {
        float rounded = Math.round(bonus * 10.0f) / 10.0f;
        if (Math.abs(cachedBugvisorBonus(stack) - rounded) > 0.05f) {
            ItemNbt.putFloat(stack, TAG_BUGVISOR_BONUS, rounded);
        }
    }

    // ==================== 1. 故障驱动器Ⅱ ====================

    public static final class BuggleDriverItem extends Item implements Equipable {
        public BuggleDriverItem() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return EquipmentSlot.LEGS;
        }

        @Override
        public Holder<SoundEvent> getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_DIAMOND;
        }

        @Override
        public EquipmentSlot getEquipmentSlot(ItemStack stack) {
            return EquipmentSlot.LEGS;
        }

        @Override
        public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
            return armorType == EquipmentSlot.LEGS;
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return isDriverLoaded(stack);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack driver = player.getItemInHand(hand);
            if (player instanceof ServerPlayer sp && sp.isShiftKeyDown() && isDriverLoaded(driver)) {
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active != null && CronusKeys.isTransformed(active)) {
                    Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.gashat_skill_only_off")
                        .withStyle(ChatFormatting.YELLOW));
                    return InteractionResultHolder.pass(driver);
                }
                UUID gOwner = loadedGashatOwner(driver);
                long gToken = loadedGashatToken(driver);
                clearDriverLoaded(driver);
                if (gToken > 0L) {
                    ItemStack gashat = new ItemStack(CHRONICLE_GASHAT.get());
                    stampOwned(gashat, gOwner, gToken);
                    if (!sp.getInventory().add(gashat)) {
                        sp.drop(gashat, false);
                    }
                    CronusGearManager.syncGearStates(sp);
                    Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.gashat_ejected")
                        .withStyle(ChatFormatting.AQUA));
                    return InteractionResultHolder.consume(driver);
                }
            }

            InteractionResultHolder<ItemStack> res = this.swapWithEquipmentSlot(this, level, player, hand);
            if (player instanceof ServerPlayer sp && res.getResult().consumesAction()) {
                sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.70f, 1.25f);
                CronusGearManager.syncGearStates(sp);
                Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.driver_equipped")
                    .withStyle(ChatFormatting.GREEN));
            }
            return res;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
            boolean loaded = isDriverLoaded(stack);
            tips.add(Component.translatable(loaded
                ? "tooltip.rpgtemplate.buggle_driver_ii.state_loaded"
                : "tooltip.rpgtemplate.buggle_driver_ii.state_empty")
                .withStyle(loaded ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            tips.add(Component.translatable("tooltip.rpgtemplate.buggle_driver_ii.usage")
                .withStyle(ChatFormatting.DARK_AQUA));
            tips.add(Component.translatable("tooltip.rpgtemplate.buggle_driver_ii.eject")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ==================== 2. 假面骑士编年史转玩卡带 ====================

    public static final class ChronicleGashatItem extends Item {
        public ChronicleGashatItem() {
            super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack gashat = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(gashat);
            }

            if (isWearingDriver(sp)) {
                if (!SystemDataManager.available(sp)) {
                    return InteractionResultHolder.pass(gashat);
                }
                PlayerSystemData sys = SystemDataManager.get(sp);
                if (!CronusKeys.ownsCronusTemplate(sys)) {
                    Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.driver_need_template")
                        .withStyle(ChatFormatting.RED));
                    return InteractionResultHolder.pass(gashat);
                }
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active == null) {
                    Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.driver_need_load")
                        .withStyle(ChatFormatting.YELLOW));
                    return InteractionResultHolder.pass(gashat);
                }
                if (sp.getCooldowns().isOnCooldown(this)) {
                    return InteractionResultHolder.pass(gashat);
                }
                sp.getCooldowns().addCooldown(this, 24);
                boolean started = CronusSkills.tryHenshinWithGashat(sp, active, gashat, sp.level().getGameTime());
                return started ? InteractionResultHolder.consume(gashat) : InteractionResultHolder.pass(gashat);
            }

            PlayerSystemData activeNow = CronusKeys.activeData(sp);
            if (activeNow != null && CronusKeys.isTransformed(activeNow)) {
                Msg.actionBar(sp, Component.translatable("message.rpgtemplate.cronus.gashat_skill_only_off")
                    .withStyle(ChatFormatting.YELLOW));
                return InteractionResultHolder.pass(gashat);
            }

            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.85f, 1.45f);
            boolean hasDriverInBag = hasDriverInInventory(sp);
            Msg.actionBar(sp, Component.translatable(hasDriverInBag
                    ? "message.rpgtemplate.cronus.gashat_wear_driver"
                    : "message.rpgtemplate.cronus.gashat_standby")
                .withStyle(ChatFormatting.YELLOW));
            return InteractionResultHolder.consume(gashat);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
            tips.add(Component.translatable("tooltip.rpgtemplate.chronicle_gashat.desc")
                .withStyle(ChatFormatting.GREEN));
            tips.add(Component.translatable("tooltip.rpgtemplate.chronicle_gashat.usage")
                .withStyle(ChatFormatting.GRAY));
        }
    }

    // ==================== 3. Gashacon 缺陷护罩Ⅱ ====================

    private static final class BugvisorTier implements Tier {
        static final BugvisorTier INSTANCE = new BugvisorTier();

        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return Tiers.DIAMOND.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.0f;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return Tiers.DIAMOND.getEnchantmentValue();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }

    public static final class GashaconBugvisorItem extends SwordItem {
        public static final float BASE_WEAPON_DAMAGE = 6.0f;

        public GashaconBugvisorItem() {
            super(BugvisorTier.INSTANCE, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .fireResistant()
                .attributes(SwordItem.createAttributes(BugvisorTier.INSTANCE, 3, -2.0f)));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer sp)) {
                return InteractionResultHolder.success(stack);
            }

            if (sp.isShiftKeyDown()) {
                int cur = bugvisorMode(stack);
                int next = (cur == CronusKeys.MODE_CHAINSAW) ? CronusKeys.MODE_GUN : CronusKeys.MODE_CHAINSAW;
                setBugvisorMode(stack, next);
                PlayerSystemData active = CronusKeys.activeData(sp);
                if (active != null) {
                    active.setValue(CronusKeys.KEY_WEAPON_MODE, (double) next);
                    SystemDataManager.markDirty(sp);
                    SystemDataManager.sync(sp);
                }
                CronusSounds.playModeSwitch(sp);
                Msg.actionBar(sp, Component.translatable(next == CronusKeys.MODE_GUN
                        ? "message.rpgtemplate.cronus.mode_gun"
                        : "message.rpgtemplate.cronus.mode_chainsaw")
                    .withStyle(ChatFormatting.AQUA));
                return InteractionResultHolder.consume(stack);
            }

            if (sp.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.pass(stack);
            }

            PlayerSystemData active = CronusKeys.activeData(sp);
            float bonus = active != null ? CronusKeys.bugvisorAttackBonus(active) : 4.0f;
            updateBugvisorBonusTag(stack, bonus);
            float totalDmg = BASE_WEAPON_DAMAGE + bonus;

            int mode = bugvisorMode(stack);
            if (mode == CronusKeys.MODE_GUN) {
                sp.getCooldowns().addCooldown(this, 10);
                fireBeamGun(sp, hand, totalDmg);
            } else {
                sp.getCooldowns().addCooldown(this, 12);
                performChainsawSlash(sp, hand, totalDmg);
            }
            return InteractionResultHolder.consume(stack);
        }

        private static void fireBeamGun(ServerPlayer sp, InteractionHand hand, float dmg) {
            sp.swing(hand);
            CronusLaserBoltEntity.launch(sp, hand, dmg);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.85f, 1.65f);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45f, 1.95f);
        }

        private static void performChainsawSlash(ServerPlayer sp, InteractionHand hand, float dmg) {
            sp.swing(hand);
            TemplateHelper.dashForward(sp, 0.32, 0.04);
            Vec3 eye = sp.getEyePosition();
            Vec3 look = sp.getLookAngle().normalize();
            double range = 4.3;
            AABB box = sp.getBoundingBox().inflate(range, 2.2, range);
            double rSq = range * range;
            List<LivingEntity> victims = new ArrayList<>();
            for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, box)) {
                if (e == sp || !e.isAlive() || e.distanceToSqr(sp) > rSq) {
                    continue;
                }
                Vec3 dir = e.getBoundingBox().getCenter().subtract(eye).normalize();
                if (dir.dot(look) >= 0.22) {
                    victims.add(e);
                }
            }
            PlayerSystemData data = CronusKeys.activeData(sp);
            long now = sp.level().getGameTime();
            float overchargeBonus = 0.0f;
            if (!victims.isEmpty() && data != null) {
                overchargeBonus = CronusCombatAndPassives.consumeChainsawOvercharge(sp, data, now);
            }
            for (int i = 0; i < victims.size(); i++) {
                LivingEntity v = victims.get(i);
                float hitDmg = dmg + (overchargeBonus > 0.0f ? (i == 0 ? overchargeBonus : overchargeBonus * 0.65f) : 0.0f);
                if (overchargeBonus > 0.0f) {
                    v.invulnerableTime = 0;
                }
                TargetUtil.hurt(v, ModDamageTypes.technique(sp), hitDmg);
                if (overchargeBonus > 0.0f && i < 3) {
                    CronusCombatAndPassives.emitChainsawOverchargeBurst(sp, v);
                }
            }
            if (sp.level() instanceof ServerLevel sl) {
                Vec3 slashPos = eye.add(look.scale(1.65));
                CronusNet.sendNear(sl, slashPos.x, slashPos.y, slashPos.z,
                    new CronusFxMsg(CronusFxMsg.KIND_CLOCK_RIPPLE, sp.getId(),
                        sp.getX(), sp.getY() + 0.08, sp.getZ(), overchargeBonus > 0.0f ? 3.8f : 2.1f, 0));
            }
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.90f, overchargeBonus > 0.0f ? 0.65f : 0.92f);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tips, TooltipFlag flag) {
            int mode = bugvisorMode(stack);
            tips.add(Component.translatable(mode == CronusKeys.MODE_GUN
                ? "tooltip.rpgtemplate.bugvisor.mode_gun"
                : "tooltip.rpgtemplate.bugvisor.mode_chainsaw")
                .withStyle(mode == CronusKeys.MODE_GUN ? ChatFormatting.AQUA : ChatFormatting.GREEN));
            float bonus = cachedBugvisorBonus(stack);
            if (bonus > 0.05f) {
                tips.add(Component.translatable("tooltip.rpgtemplate.bugvisor.bonus_live",
                    String.format(Locale.ROOT, "%.1f", bonus))
                    .withStyle(ChatFormatting.GOLD));
            } else {
                tips.add(Component.translatable("tooltip.rpgtemplate.bugvisor.bonus_static")
                    .withStyle(ChatFormatting.GOLD));
            }
            tips.add(Component.translatable("tooltip.rpgtemplate.bugvisor.controls")
                .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean hasDriverInInventory(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        for (ItemStack s : inv.offhand) {
            if (isDriver(s)) return true;
        }
        for (ItemStack s : inv.items) {
            if (isDriver(s)) return true;
        }
        return false;
    }
}
