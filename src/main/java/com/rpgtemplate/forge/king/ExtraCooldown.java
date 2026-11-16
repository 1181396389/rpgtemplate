package com.rpgtemplate.forge.king;

import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

public final class ExtraCooldown {
    private static int remaining(ItemCooldowns var0, Item var1) {
        if (!var0.isOnCooldown(var1)) {
            return 0;
        } else {
            int var2 = 0;
            int var3 = 1;

            while (var3 < 16777216 && var0.getCooldownPercent(var1, (float)var3) > 0.0F) {
                var3 *= 2;
            }

            while (var3 - var2 > 1) {
                int var4 = var2 + (var3 - var2) / 2;
                if (var0.getCooldownPercent(var1, (float)var4) > 0.0F) {
                    var2 = var4;
                } else {
                    var3 = var4;
                }
            }

            return var3;
        }
    }

    private static void apply(ItemCooldowns var0, Item var1, int var2) {
        if (var0.isOnCooldown(var1)) {
            var0.removeCooldown(var1);
        }

        if (var2 > 0) {
            var0.addCooldown(var1, var2);
        }
    }

    public static void begin(ServerPlayer var0, int var1, ItemStack var2) {
        if (!var2.isEmpty()) {
            CompoundTag var3 = ExtraHands.store(var0);
            Item var4 = var2.getItem();
            String var5 = BuiltInRegistries.ITEM.getKey(var4).toString();
            long var6 = var0.serverLevel().getGameTime();
            var3.putString("leaseCdItem", var5);
            var3.putDouble("leaseCdBaseUntil", (double)(var6 + (long)remaining(var0.getCooldowns(), var4)));
            CompoundTag var8 = var3.getCompound("cooldowns" + var1);
            apply(var0.getCooldowns(), var4, (int)Math.max(0.0, Math.min(1.6777216E7, var8.getDouble(var5) - (double)var6)));
        }
    }

    public static void end(ServerPlayer var0, int var1) {
        CompoundTag var2 = ExtraHands.store(var0);
        if (var2.contains("leaseCdItem")) {
            String var3 = var2.getString("leaseCdItem");
            ResourceLocation var4 = ResourceLocation.tryParse(var3);
            Item var5 = var4 == null ? null : (Item)BuiltInRegistries.ITEM.get(var4);
            if (var5 != null) {
                long var6 = var0.serverLevel().getGameTime();
                int var8 = remaining(var0.getCooldowns(), var5);
                CompoundTag var9 = var2.getCompound("cooldowns" + var1);

                for (String var11 : new HashSet<>(var9.getAllKeys())) {
                    if (var9.getDouble(var11) <= (double)var6) {
                        var9.remove(var11);
                    }
                }

                if (var8 > 0) {
                    var9.putDouble(var3, (double)(var6 + (long)var8));
                } else {
                    var9.remove(var3);
                }

                var2.put("cooldowns" + var1, var9);
                apply(var0.getCooldowns(), var5, (int)Math.max(0.0, Math.min(1.6777216E7, var2.getDouble("leaseCdBaseUntil") - (double)var6)));
            }

            var2.remove("leaseCdItem");
            var2.remove("leaseCdBaseUntil");
        }
    }
}
