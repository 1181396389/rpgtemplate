package com.rpgtemplate.forge.mask;

import com.rpgtemplate.forge.util.ItemNbt;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
 * 认知阻碍面具（NeoForge 1.21.1）。
 */
public final class PerceptionBlockingMaskItem extends Item implements Equipable {
    public static final String TAG_OWNER = "MaskOwner";
    public static final String TAG_TOKEN = "MaskToken";

    public PerceptionBlockingMaskItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, LivingEntity entity) {
        return armorType == EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public boolean isEnderMask(ItemStack stack, Player player, EnderMan enderman) {
        return PerceptionMaskRules.blocksMob(player, enderman);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.rpgtemplate.perception_blocking_mask.lore1")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.rpgtemplate.perception_blocking_mask.lore2")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rpgtemplate.perception_blocking_mask.lore3")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rpgtemplate.perception_blocking_mask.lore4")
            .withStyle(ChatFormatting.DARK_AQUA));
        UUID owner = ownerOf(stack);
        if (owner != null) {
            tooltip.add(Component.translatable("item.rpgtemplate.perception_blocking_mask.bound")
                .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    public static boolean isMask(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PerceptionBlockingMaskItem;
    }

    public static ItemStack getWornMask(LivingEntity entity) {
        if (entity == null) {
            return ItemStack.EMPTY;
        }
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        return isMask(head) ? head : ItemStack.EMPTY;
    }

    public static boolean isWearingMask(LivingEntity entity) {
        return !getWornMask(entity).isEmpty();
    }

    public static void stamp(ItemStack stack, UUID ownerId, long token) {
        ItemNbt.update(stack, tag -> {
            tag.putUUID(TAG_OWNER, ownerId);
            tag.putLong(TAG_TOKEN, token);
        });
    }

    @Nullable
    public static UUID ownerOf(ItemStack stack) {
        if (!isMask(stack)) {
            return null;
        }
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null && tag.hasUUID(TAG_OWNER)) {
            return tag.getUUID(TAG_OWNER);
        }
        return null;
    }

    public static long tokenOf(ItemStack stack) {
        if (!isMask(stack)) {
            return 0L;
        }
        CompoundTag tag = ItemNbt.getTag(stack);
        if (tag != null && tag.contains(TAG_TOKEN)) {
            return tag.getLong(TAG_TOKEN);
        }
        return 0L;
    }
}
