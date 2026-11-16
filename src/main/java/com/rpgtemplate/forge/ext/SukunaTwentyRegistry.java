package com.rpgtemplate.forge.ext;
import net.minecraft.core.registries.BuiltInRegistries;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.forge.skill.SkillRegistry;
import com.rpgtemplate.forge.skill.WorldSlashSkill;
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
public final class SukunaTwentyRegistry {
    public static EntityType<WorldRiftEntity> WORLD_RIFT;

    private SukunaTwentyRegistry() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent var0) {
        var0.register(
            Registries.ENTITY_TYPE,
            var0x -> {
                WORLD_RIFT = Builder.of(WorldRiftEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSave()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(40)
                    .setShouldReceiveVelocityUpdates(false)
                    .build("world_rift");
                var0x.register(ResourceLocation.fromNamespaceAndPath("rpgtemplate", "world_rift"), WORLD_RIFT);
            }
        );
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent var0) {
        SkillRegistry.register("rpgtemplate:world_slash", new WorldSlashSkill());
    }
}
