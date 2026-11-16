package com.rpgtemplate.forge.possession;
import com.rpgtemplate.forge.util.ItemNbt;

import com.rpgtemplate.forge.registry.ModItems;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

public class SukunaFingerItem extends Item {
    public SukunaFingerItem(Properties var1) {
        super(var1);
    }

    public static ItemStack create(UUID var0, ServerPlayer var1) {
        ItemStack var2 = new ItemStack((ItemLike)ModItems.SUKUNA_FINGER.get());
        ItemNbt.update(var2, var3 -> {
            var3.putUUID("FingerId", var0);
            var3.putUUID("Owner", var1.getUUID());
            var3.putString("OwnerName", var1.getGameProfile().getName());
        });
        return var2;
    }

    public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
        var2.startUsingItem(var3);
        return InteractionResultHolder.consume(var2.getItemInHand(var3));
    }

    public UseAnim getUseAnimation(ItemStack var1) {
        return UseAnim.EAT;
    }

    public int getUseDuration(ItemStack var1) {
        return 24;
    }

    public ItemStack finishUsingItem(ItemStack var1, Level var2, LivingEntity var3) {
        if (!var2.isClientSide && var3 instanceof ServerPlayer var4) {
            var2.playSound(null, var4.getX(), var4.getY(), var4.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 0.6F);
            if (Possession.eat(var4, var1, null)) {
                var1.shrink(1);
            }
        }

        return var1;
    }

    public InteractionResult interactLivingEntity(ItemStack var1, Player var2, LivingEntity var3, InteractionHand var4) {
        if (!(var3 instanceof Player)) {
            return InteractionResult.PASS;
        } else {
            if (!var2.level().isClientSide && var3 instanceof ServerPlayer var5 && var2 instanceof ServerPlayer var6) {
                ItemStack var7 = var6.getItemInHand(var4);
                if (Possession.eat(var5, var7, var6)) {
                    var5.level().playSound(null, var5.getX(), var5.getY(), var5.getZ(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0F, 0.5F);
                    var7.shrink(1);
                }
            }

            return InteractionResult.sidedSuccess(var2.level().isClientSide);
        }
    }

    public void inventoryTick(ItemStack var1, Level var2, Entity var3, int var4, boolean var5) {
        if (!var2.isClientSide) {
            if (!Possession.live(var1)) {
                var1.setCount(0);
                ((ServerLevel)var2).sendParticles(ParticleTypes.ASH, var3.getX(), var3.getY() + 1.0, var3.getZ(), 20, 0.3, 0.4, 0.3, 0.01);
            } else {
                Possession.sighted(var1, var3);
            }
        }
    }

    public boolean onEntityItemUpdate(ItemStack var1, ItemEntity var2) {
        Level var3 = var2.level();
        if (var3.isClientSide) {
            if (var2.tickCount % 6 == 0) {
                var3.addParticle(ParticleTypes.SOUL, var2.getX(), var2.getY() + 0.3, var2.getZ(), 0.0, 0.03, 0.0);
            }

            return false;
        } else if (!Possession.live(var1)) {
            ((ServerLevel)var3).sendParticles(ParticleTypes.ASH, var2.getX(), var2.getY() + 0.2, var2.getZ(), 20, 0.2, 0.2, 0.2, 0.01);
            var2.discard();
            return true;
        } else {
            Possession.sighted(var1, var2);
            if (var2.getY() < (double)(var3.getMinBuildHeight() - 32)) {
                Possession.destroyed(var1);
            }

            return false;
        }
    }

    public void onDestroyed(ItemEntity var1, DamageSource var2) {
        Possession.destroyed(var1.getItem());
    }

    public int getEntityLifespan(ItemStack var1, Level var2) {
        return Integer.MAX_VALUE;
    }

    public boolean isFoil(ItemStack var1) {
        return true;
    }

    public void appendHoverText(ItemStack var1, Item.TooltipContext var2, List<Component> var3, TooltipFlag var4) {
        CompoundTag var5 = ItemNbt.getTag(var1);
        String var6 = var5 != null ? var5.getString("OwnerName") : "";
        var3.add(Component.translatable("item.rpgtemplate.sukuna_finger.tip1", new Object[]{var6}).withStyle(ChatFormatting.DARK_RED));
        var3.add(Component.translatable("item.rpgtemplate.sukuna_finger.tip2").withStyle(ChatFormatting.GRAY));
        var3.add(Component.translatable("item.rpgtemplate.sukuna_finger.tip3").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Properties props() {
        return new Properties().stacksTo(1).rarity(Rarity.EPIC);
    }
}
