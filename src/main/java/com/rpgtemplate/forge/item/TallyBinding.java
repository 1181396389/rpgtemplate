package com.rpgtemplate.forge.item;

import com.rpgtemplate.forge.util.ItemNbt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record TallyBinding(UUID owner, String ownerName) {
    private static final String KEY = "rpgtemplate_tally_binding";

    public static TallyBinding get(ItemStack stack) {
        CompoundTag root = ItemNbt.getTag(stack);
        if (root != null && root.contains(KEY, 10)) {
            CompoundTag sub = root.getCompound(KEY);
            return sub.hasUUID("owner") ? new TallyBinding(sub.getUUID("owner"), sub.getString("owner_name")) : null;
        }
        return null;
    }

    public static void set(ItemStack stack, TallyBinding binding) {
        if (binding == null) {
            ItemNbt.remove(stack, KEY);
        } else {
            CompoundTag sub = new CompoundTag();
            sub.putUUID("owner", binding.owner());
            sub.putString("owner_name", binding.ownerName());
            ItemNbt.update(stack, root -> root.put(KEY, sub));
        }
    }
}
