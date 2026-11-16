package com.rpgtemplate.forge.registry;

import com.rpgtemplate.forge.entity.TechniqueEffectEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "rpgtemplate");
    public static final DeferredHolder<EntityType<?>, EntityType<TechniqueEffectEntity>> TECHNIQUE_EFFECT = REGISTER.register(
        "technique_effect",
        () -> Builder.of(TechniqueEffectEntity::new, MobCategory.MISC)
                .sized(0.1F, 0.1F)
                .noSave()
                .noSummon()
                .fireImmune()
                .clientTrackingRange(24)
                .updateInterval(2)
                .build("technique_effect")
    );

    private ModEntities() {
    }
}
