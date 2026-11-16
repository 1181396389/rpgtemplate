package com.rpgtemplate.forge.item;

import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.system.TallyService;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public class TigerTallyItem extends Item {
    public TigerTallyItem(Properties var1) {
        super(var1);
    }

    public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
        ItemStack var4 = var2.getItemInHand(var3);
        if (!var1.isClientSide && var2 instanceof ServerPlayer var5) {
            TallyService.onUse(var5, var4);
        }

        return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
    }

    public boolean isFoil(ItemStack var1) {
        return TallyBinding.get(var1) != null || super.isFoil(var1);
    }

    public void appendHoverText(ItemStack var1, @Nullable Level var2, List<Component> var3, TooltipFlag var4) {
        TallyBinding var5 = TallyBinding.get(var1);
        if (var5 != null) {
            var3.add(Component.translatable("item.rpgtemplate.golden_leopard_tally.bound", new Object[]{var5.ownerName()}).withStyle(ChatFormatting.GOLD));
        } else {
            var3.add(
                Component.translatable("item.rpgtemplate.golden_leopard_tally.unbound")
                    .withStyle(new ChatFormatting[]{ChatFormatting.GRAY, ChatFormatting.ITALIC})
            );
        }

        var3.add(
            Component.translatable("item.rpgtemplate.golden_leopard_tally.lore")
                .withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC})
        );
    }

    public boolean onEntityItemUpdate(ItemStack var1, ItemEntity var2) {
        if (!var2.level().isClientSide && TallyBinding.get(var1) != null && (Boolean)ModConfigs.SERVER.protectBoundTally.get()) {
            if (!var2.isInvulnerable()) {
                var2.setInvulnerable(true);
            }

            var2.setUnlimitedLifetime();
        }

        return false;
    }
}
