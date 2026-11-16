package com.rpgtemplate.forge.entity;

import com.rpgtemplate.forge.network.PacketDistributor;
import com.rpgtemplate.forge.network.RainbowBurstPayload;
import com.rpgtemplate.forge.possession.Possession;
import com.rpgtemplate.forge.registry.ModEntities;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class Effects {
    public static final int SLASH_INK = 0;
    public static final int SLASH_CRIMSON = 1;

    private Effects() {
    }

    private static TechniqueEffectEntity create(ServerPlayer var0, Vec3 var1) {
        TechniqueEffectEntity var2 = new TechniqueEffectEntity((EntityType<? extends TechniqueEffectEntity>)ModEntities.TECHNIQUE_EFFECT.get(), var0.level());
        var2.setPos(var1.x, var1.y, var1.z);
        var2.owner(var0.getUUID());
        var2.companion(Possession.effectCompanion(var0));
        return var2;
    }

    private static void add(TechniqueEffectEntity var0) {
        var0.level().addFreshEntity(var0);
    }

    public static void domainField(ServerPlayer var0, Vec3 var1, float var2) {
        TechniqueEffectEntity var3 = create(var0, var1);
        var3.setup(8, 12, var2, (float)DomainExpansionSkill.planeHeight((double)var2), 0.0F, 16777215, 128);
        add(var3);
    }

    public static void giantGrid(ServerPlayer var0, Vec3 var1, Vec3 var2, float var3, float var4, float var5, int var6, double var7, double var9) {
        int var11 = Math.max(1, (int)Math.ceil(var9 / var7));
        TechniqueEffectEntity var12 = create(var0, var1);
        var12.setup(7, var11 + 5, var5, 0.2F, 0.0F, 16724787, var6);
        var12.orient(var3, var4);
        var12.fly(var2.scale(var9 / (double)var11), var11);
        add(var12);
    }

    public static void slash(ServerPlayer var0, Vec3 var1, float var2, float var3, float var4, float var5, float var6, int var7, int var8) {
        TechniqueEffectEntity var9 = create(var0, var1);
        var9.setup(0, var8, var5, var6, var4, 16777215, var7);
        var9.orient(var2, var3);
        add(var9);
    }

    public static int flyingSlash(ServerPlayer var0, Vec3 var1, Vec3 var2, float var3, float var4, float var5, float var6, int var7, double var8) {
        Vec3 var10 = var2.subtract(var1);
        double var11 = var10.length();
        int var13 = Math.max(1, (int)Math.ceil(var11 / Math.max(0.1, var8)));
        Vec3 var14 = var11 < 1.0E-4 ? Vec3.ZERO : var10.scale(1.0 / (double)var13);
        TechniqueEffectEntity var15 = create(var0, var1);
        var15.setup(0, var13 + 5, var6, var6 * 0.05F, var5, 16777215, var7);
        var15.orient(var3, var4);
        var15.fly(var14, var13);
        add(var15);
        return var13;
    }

    public static void slashFacing(ServerPlayer var0, Vec3 var1, float var2, float var3, float var4, int var5, int var6) {
        slash(var0, var1, var0.getYRot(), var0.getXRot(), var2, var3, var4, var5, var6);
    }

    public static void cleave(ServerPlayer var0, Entity var1, int var2, boolean var3) {
        TechniqueEffectEntity var4 = create(var0, var1.position());
        float var5 = Math.max(var1.getBbWidth(), var1.getBbHeight()) * 1.25F + 0.4F;
        var4.setup(1, var3 ? 18 : 14, var5, var1.getBbHeight(), 0.0F, var3 ? 16719904 : 16777215, Mth.clamp(var2, 2, 24));
        var4.follow(var1);
        add(var4);
    }

    public static void rctRing(ServerPlayer var0, Entity var1) {
        TechniqueEffectEntity var2 = create(var0, var1.position());
        var2.setup(2, 16, var1.getBbWidth() * 0.9F + 0.35F, var1.getBbHeight(), 0.0F, 16774358, 0);
        var2.follow(var1);
        add(var2);
    }

    public static void rctAura(ServerPlayer var0, Entity var1, int var2, int var3) {
        TechniqueEffectEntity var4 = create(var0, var1.position());
        var4.setup(6, var2 + 16, var1.getBbWidth(), var1.getBbHeight(), (float)var2, 16777215, var3);
        var4.follow(var1);
        add(var4);
    }

    public static void rctAuraAt(ServerPlayer var0, Vec3 var1, float var2, float var3, int var4, int var5) {
        TechniqueEffectEntity var6 = create(var0, var1.subtract(0.0, (double)var3 * 0.5, 0.0));
        var6.setup(6, var4 + 16, var2, var3, (float)var4, 16777215, var5);
        add(var6);
    }

    public static void rctBurst(ServerPlayer var0, Entity var1) {
        float var2 = Math.max(0.6F, var1.getBbWidth()) * 2.6F;
        float var3 = var1.getBbHeight() * 1.3F;
        Vec3 var4 = var1.getBoundingBox().getCenter();
        TechniqueEffectEntity var5 = create(var0, var4.subtract(0.0, (double)var3 * 0.5, 0.0));
        var5.setup(6, 22, var2, var3, 2.0F, 16777215, 28);
        add(var5);
    }

    public static void chargeAura(ServerPlayer var0, int var1, int var2) {
        TechniqueEffectEntity var3 = create(var0, var0.position());
        var3.setup(3, var2, 0.75F, var0.getBbHeight(), 0.0F, 9043984, var1);
        var3.follow(var0);
        add(var3);
    }

    public static void beam(ServerPlayer var0, Vec3 var1, Vec3 var2, int var3) {
        Vec3 var4 = var2.subtract(var1);
        double var5 = var4.length();
        if (!(var5 < 0.01)) {
            float var7 = (float)(Mth.atan2(-var4.x, var4.z) * 180.0 / (float) Math.PI);
            float var8 = (float)(-Mth.atan2(var4.y, Math.sqrt(var4.x * var4.x + var4.z * var4.z)) * 180.0 / (float) Math.PI);
            TechniqueEffectEntity var9 = create(var0, var1);
            var9.setup(4, 7, (float)var5, 0.22F, 0.0F, var3, 0);
            var9.orient(var7, var8);
            add(var9);
        }
    }

    public static void rainbow(ServerPlayer var0, int var1, int var2) {
        PacketDistributor.sendToPlayer(var0, new RainbowBurstPayload(var1, var2));
        TechniqueEffectEntity var3 = create(var0, var0.position());
        var3.setup(5, 60, 1.1F, var0.getBbHeight(), 0.0F, var2, var1);
        var3.follow(var0);
        add(var3);
    }
}
