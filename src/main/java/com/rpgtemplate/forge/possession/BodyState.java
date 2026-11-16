package com.rpgtemplate.forge.possession;
import net.minecraft.nbt.NbtAccounter;

import com.rpgtemplate.forge.king.ExtraHands;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.storage.LevelResource;

public final class BodyState {
    private BodyState() {
    }

    public static CompoundTag capture(ServerPlayer var0, boolean var1) {
        ExtraHands.release(var0);
        CompoundTag var2 = new CompoundTag();
        var2.put("Inv", var0.getInventory().save(new ListTag()));
        var2.putInt("Sel", var0.getInventory().selected);
        var2.putFloat("Health", var0.getHealth());
        var2.putFloat("Abs", var0.getAbsorptionAmount());
        var2.putInt("Food", var0.getFoodData().getFoodLevel());
        var2.putFloat("Sat", var0.getFoodData().getSaturationLevel());
        var2.putInt("Air", var0.getAirSupply());
        var2.putInt("Fire", var0.getRemainingFireTicks());
        ListTag var3 = new ListTag();

        for (MobEffectInstance var5 : var0.getActiveEffects()) {
            var3.add(var5.save());
        }

        var2.put("Effects", var3);
        if (var1) {
            putPos(var2, var0);
        }

        return var2;
    }

    public static void putPos(CompoundTag var0, ServerPlayer var1) {
        var0.putString("Dim", var1.level().dimension().location().toString());
        var0.putDouble("X", var1.getX());
        var0.putDouble("Y", var1.getY());
        var0.putDouble("Z", var1.getZ());
        var0.putFloat("YRot", var1.getYRot());
        var0.putFloat("XRot", var1.getXRot());
    }

    public static CompoundTag inventoryOnly(ServerPlayer var0) {
        CompoundTag var1 = new CompoundTag();
        var1.put("Inv", var0.getInventory().save(new ListTag()));
        var1.putInt("Sel", var0.getInventory().selected);
        return var1;
    }

    public static void apply(ServerPlayer var0, CompoundTag var1, boolean var2) {
        ExtraHands.release(var0);
        if (var1 != null) {
            if (var1.contains("Inv", 9)) {
                var0.getInventory().clearContent();
                var0.getInventory().load(var1.getList("Inv", 10));
                var0.getInventory().selected = Math.max(0, Math.min(8, var1.getInt("Sel")));
            }

            if (var1.contains("Health")) {
                var0.removeAllEffects();
                ListTag var3 = var1.getList("Effects", 10);

                for (int var4 = 0; var4 < var3.size(); var4++) {
                    MobEffectInstance var5 = MobEffectInstance.load(var3.getCompound(var4));
                    if (var5 != null) {
                        var0.addEffect(var5);
                    }
                }

                var0.setHealth(Math.max(1.0F, Math.min(var0.getMaxHealth(), var1.getFloat("Health"))));
                var0.setAbsorptionAmount(var1.getFloat("Abs"));
                var0.getFoodData().setFoodLevel(var1.getInt("Food"));
                var0.getFoodData().setSaturation(var1.getFloat("Sat"));
                var0.setAirSupply(var1.getInt("Air"));
                var0.setRemainingFireTicks(var1.getInt("Fire"));
            }

            var0.inventoryMenu.broadcastChanges();
            var0.containerMenu.broadcastChanges();
            if (var2 && var1.contains("Dim")) {
                teleport(var0, var1);
            }
        }
    }

    public static boolean teleport(ServerPlayer var0, CompoundTag var1) {
        ServerLevel var2 = level(var0, var1.getString("Dim"));
        if (var2 == null) {
            return false;
        } else {
            Possession.tp(var0, var2, var1.getDouble("X"), var1.getDouble("Y"), var1.getDouble("Z"), var1.getFloat("YRot"), var1.getFloat("XRot"));
            return true;
        }
    }

    public static ServerLevel level(ServerPlayer var0, String var1) {
        ResourceLocation var2 = ResourceLocation.tryParse(var1);
        if (var2 == null) {
            return null;
        } else {
            ResourceKey var3 = ResourceKey.create(Registries.DIMENSION, var2);
            return var0.getServer().getLevel(var3);
        }
    }

    public static CompoundTag offline(MinecraftServer var0, UUID var1) {
        File var2 = var0.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(var1 + ".dat").toFile();
        if (!var2.isFile()) {
            return null;
        } else {
            try {
                CompoundTag var3 = NbtIo.readCompressed(var2.toPath(), NbtAccounter.unlimitedHeap());
                CompoundTag var4 = new CompoundTag();
                var4.put("Inv", var3.getList("Inventory", 10).copy());
                var4.putInt("Sel", var3.getInt("SelectedItemSlot"));
                var4.putFloat("Health", var3.getFloat("Health"));
                var4.putFloat("Abs", var3.getFloat("AbsorptionAmount"));
                var4.putInt("Food", var3.getInt("foodLevel"));
                var4.putFloat("Sat", var3.getFloat("foodSaturationLevel"));
                var4.putInt("Air", var3.getShort("Air"));
                var4.putInt("Fire", var3.getShort("Fire"));
                var4.put("Effects", var3.getList("ActiveEffects", 10).copy());
                var4.putString("Dim", var3.getString("Dimension"));
                ListTag var5 = var3.getList("Pos", 6);
                ListTag var6 = var3.getList("Rotation", 5);
                var4.putDouble("X", var5.getDouble(0));
                var4.putDouble("Y", var5.getDouble(1));
                var4.putDouble("Z", var5.getDouble(2));
                var4.putFloat("YRot", var6.getFloat(0));
                var4.putFloat("XRot", var6.getFloat(1));
                return var4;
            } catch (IOException var7) {
                throw new IllegalStateException("Cannot read offline body for " + var1, var7);
            }
        }
    }

    public static void empty(ServerPlayer var0) {
        ExtraHands.release(var0);
        var0.getInventory().clearContent();
        var0.removeAllEffects();
        var0.inventoryMenu.broadcastChanges();
        var0.containerMenu.broadcastChanges();
    }
}
