package com.rpgtemplate.forge.util;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 1.21.1 ItemStack custom NBT compatibility helper backed by {@link DataComponents#CUSTOM_DATA}.
 */
public final class ItemNbt {
    private ItemNbt() {
    }

    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd != null && !cd.isEmpty();
    }

    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null || cd.isEmpty() ? null : cd.copyTag();
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CompoundTag t = getTag(stack);
        return t != null ? t : new CompoundTag();
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.copy()));
        }
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> editor) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, editor);
    }

    public static String getString(ItemStack stack, String key) {
        CompoundTag t = getTag(stack);
        return t != null ? t.getString(key) : "";
    }

    public static void putString(ItemStack stack, String key, String value) {
        update(stack, t -> t.putString(key, value));
    }

    public static int getInt(ItemStack stack, String key) {
        CompoundTag t = getTag(stack);
        return t != null ? t.getInt(key) : 0;
    }

    public static void putInt(ItemStack stack, String key, int value) {
        update(stack, t -> t.putInt(key, value));
    }

    public static float getFloat(ItemStack stack, String key) {
        CompoundTag t = getTag(stack);
        return t != null ? t.getFloat(key) : 0.0F;
    }

    public static void putFloat(ItemStack stack, String key, float value) {
        update(stack, t -> t.putFloat(key, value));
    }

    public static boolean getBoolean(ItemStack stack, String key) {
        CompoundTag t = getTag(stack);
        return t != null && t.getBoolean(key);
    }

    public static void putBoolean(ItemStack stack, String key, boolean value) {
        update(stack, t -> t.putBoolean(key, value));
    }

    public static void putUUID(ItemStack stack, String key, UUID value) {
        update(stack, t -> t.putUUID(key, value));
    }

    public static void remove(ItemStack stack, String key) {
        if (!hasTag(stack)) {
            return;
        }
        update(stack, t -> t.remove(key));
    }
}
