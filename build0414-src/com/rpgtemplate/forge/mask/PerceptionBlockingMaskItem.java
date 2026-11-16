package com.rpgtemplate.forge.mask;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 认知阻碍面具（外观基于《废墟图书馆》罗兰持有的漆黑噤默同款面具）。
 * <ul>
 *   <li>佩戴于头盔槽位（支持手持右键一键佩戴/替换），装备后以纯黑面具覆盖佩戴者面部。</li>
 *   <li>规则等级生效内不会被任何生物察觉或产生敌意，且完全不触发任何红石装置与幽匿/探测机关。</li>
 *   <li>其他玩家视线中非准星正对时不可见；即使准星对准看见，面部仍呈现黑色遮盖与模糊形态。</li>
 *   <li>由穿越者面板唯一签发：持有者可随时存回面板，上一个遗失或存回后方可拿取下一个。</li>
 * </ul>
 */
public final class PerceptionBlockingMaskItem extends Item implements Equipable {
    public static final String TAG_OWNER = "MaskOwner";
    public static final String TAG_TOKEN = "MaskToken";

    public PerceptionBlockingMaskItem() {
        super(new Item.Properties().m_41487_(1).m_41497_(Rarity.EPIC));
    }

    @Override
    public EquipmentSlot m_40402_() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent m_150681_() {
        return SoundEvents.f_11675_; // ARMOR_EQUIP_LEATHER
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        return armorType == EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        return this.m_269277_(this, level, player, hand);
    }

    @Override
    public boolean isEnderMask(ItemStack stack, Player player, EnderMan enderman) {
        return PerceptionMaskRules.blocksMob(player, enderman);
    }

    @Override
    public void m_7373_(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.m_237115_("item.rpgtemplate.perception_blocking_mask.lore1")
            .m_130940_(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.m_237115_("item.rpgtemplate.perception_blocking_mask.lore2")
            .m_130940_(ChatFormatting.GRAY));
        tooltip.add(Component.m_237115_("item.rpgtemplate.perception_blocking_mask.lore3")
            .m_130940_(ChatFormatting.GRAY));
        tooltip.add(Component.m_237115_("item.rpgtemplate.perception_blocking_mask.lore4")
            .m_130940_(ChatFormatting.DARK_AQUA));
        UUID owner = ownerOf(stack);
        if (owner != null) {
            tooltip.add(Component.m_237115_("item.rpgtemplate.perception_blocking_mask.bound")
                .m_130940_(ChatFormatting.DARK_PURPLE));
        }
    }

    public static boolean isMask(ItemStack stack) {
        return stack != null && !stack.m_41619_() && stack.m_41720_() instanceof PerceptionBlockingMaskItem;
    }

    public static ItemStack getWornMask(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.f_41583_;
        }
        ItemStack head = entity.m_6844_(EquipmentSlot.HEAD);
        return isMask(head) ? head : ItemStack.f_41583_;
    }

    public static boolean isWearingMask(LivingEntity entity) {
        return !getWornMask(entity).m_41619_();
    }

    public static void stamp(ItemStack stack, UUID ownerId, long token) {
        CompoundTag tag = stack.m_41784_();
        tag.m_128362_(TAG_OWNER, ownerId);
        tag.m_128356_(TAG_TOKEN, token);
    }

    @Nullable
    public static UUID ownerOf(ItemStack stack) {
        if (!isMask(stack)) {
            return null;
        }
        CompoundTag tag = stack.m_41783_();
        if (tag != null && tag.m_128403_(TAG_OWNER)) {
            return tag.m_128342_(TAG_OWNER);
        }
        return null;
    }

    public static long tokenOf(ItemStack stack) {
        if (!isMask(stack)) {
            return 0L;
        }
        CompoundTag tag = stack.m_41783_();
        if (tag != null && tag.m_128441_(TAG_TOKEN)) {
            return tag.m_128454_(TAG_TOKEN);
        }
        return 0L;
    }
}
