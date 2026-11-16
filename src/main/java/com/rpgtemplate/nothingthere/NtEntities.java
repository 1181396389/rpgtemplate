package com.rpgtemplate.nothingthere;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** 一无所有的实体注册：Holle 骨刺弹射物。渲染器在 client/NtEntityClientSetup 中绑定。 */
@EventBusSubscriber(modid = "rpgtemplate", bus = EventBusSubscriber.Bus.MOD)
public final class NtEntities {
    public static final ResourceLocation HOLLE_SPIKE_ID = ResourceLocation.fromNamespaceAndPath("rpgtemplate", "nt_holle_spike");
    public static EntityType<HolleSpike> HOLLE_SPIKE;

    private NtEntities() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent registerEvent) {
        registerEvent.register(Registries.ENTITY_TYPE, registerHelper -> {
            HOLLE_SPIKE = EntityType.Builder.of(HolleSpike::new, MobCategory.MISC)
                .sized(0.3F, 0.3F)
                .setTrackingRange(8)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .build(HOLLE_SPIKE_ID.toString());
            registerHelper.register(HOLLE_SPIKE_ID, HOLLE_SPIKE);
        });
    }
}
