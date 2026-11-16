package com.rpgtemplate.forge.ext;
import net.minecraft.core.registries.BuiltInRegistries;


import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;

@EventBusSubscriber(
    modid = "rpgtemplate",
    bus = Bus.MOD
)
public final class ExtRegistry {
    public static EntityType<FugaArrowEntity> FUGA_ARROW;
    public static EntityType<FugaBlastEntity> FUGA_BLAST;
    public static EntityType<ShrineEntity> SHRINE;

    private ExtRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent var0) {
        var0.register(
            Registries.ENTITY_TYPE,
            var0x -> {
                FUGA_ARROW = Builder.of(FugaArrowEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(true)
                    .build("fuga_arrow");
                var0x.register(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "fuga_arrow"), FUGA_ARROW);
                FUGA_BLAST = Builder.of(FugaBlastEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("fuga_blast");
                var0x.register(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "fuga_blast"), FUGA_BLAST);
                SHRINE = Builder.of(ShrineEntity::new, MobCategory.MISC)
                    .sized(4.0F, 8.0F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("malevolent_shrine");
                var0x.register(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "malevolent_shrine"), SHRINE);
            }
        );
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent var0) {
        var0.enqueueWork(ExtNetwork::register);
    }
}
