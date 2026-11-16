package com.rpgtemplate.forge.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> CURSED_TECHNIQUE = ResourceKey.create(
        Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("rpgtemplate", "cursed_technique")
    );

    private ModDamageTypes() {
    }

    public static DamageSource technique(Entity var0) {
        return new DamageSource(var0.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CURSED_TECHNIQUE), var0);
    }

    public static boolean bypassesSystemHp(DamageSource var0) {
        String var1 = var0.getMsgId();
        return var0.is(DamageTypes.FELL_OUT_OF_WORLD)
            || var0.is(DamageTypes.GENERIC_KILL)
            || var1.equals("outOfWorld")
            || var1.equals("genericKill")
            || var1.equals("generic_kill");
    }

    public static boolean isSoulDamage(DamageSource var0) {
        String var1 = var0.getMsgId();
        return var0.is(DamageTypes.WITHER) || var0.is(DamageTypes.SONIC_BOOM) || var1.equals("wither") || var1.equals("sonic_boom") || var1.equals("sonicBoom");
    }
}
