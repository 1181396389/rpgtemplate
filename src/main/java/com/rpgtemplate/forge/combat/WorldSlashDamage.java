package com.rpgtemplate.forge.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class WorldSlashDamage {
    public static final ResourceKey<DamageType> WORLD_SLASH = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("rpgtemplate", "world_slash"));

    private WorldSlashDamage() {
    }

    public static DamageSource source(Entity var0) {
        return new DamageSource(var0.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(WORLD_SLASH), var0);
    }

    public static boolean is(DamageSource var0) {
        return var0 != null && (var0.is(WORLD_SLASH) || "rpgtemplate.world_slash".equals(var0.getMsgId()));
    }
}
