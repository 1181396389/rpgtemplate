package com.rpgtemplate.nothingthere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** 一无所有的实体注册：Holle 骨刺弹射物。渲染器在 client/NtEntityClientSetup 中绑定。 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NtEntities {
    public static final ResourceLocation HOLLE_SPIKE_ID = new ResourceLocation("rpgtemplate", "nt_holle_spike");
    public static EntityType<HolleSpike> HOLLE_SPIKE;

    private NtEntities() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent registerEvent) {
        registerEvent.register(ForgeRegistries.Keys.ENTITY_TYPES, registerHelper -> {
            HOLLE_SPIKE = EntityType.Builder.m_20704_(HolleSpike::new, MobCategory.MISC)
                .m_20699_(0.3F, 0.3F)
                .setTrackingRange(8)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .m_20712_(HOLLE_SPIKE_ID.toString());
            registerHelper.register(HOLLE_SPIKE_ID, HOLLE_SPIKE);
        });
    }
}
