package com.rpgtemplate.cronus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 假面骑士 Cronus 真实弹射物与能量波实体注册：
 * <ul>
 *   <li>{@link #LASER_BOLT}：光线枪模式普攻真实高速翡翠激光弹射物。</li>
 *   <li>{@link #ENERGY_ORB}：光线枪必杀技二巨大绿色追踪爆炸弹丸（常态慢速弧线追踪 / 时停高速强追踪）。</li>
 *   <li>{@link #SAW_WAVE}：电锯必杀技一巨大旋转绿色回旋刃能量波（未命中持续飞出；命中后压制敌人高频转动持续绞杀）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = "rpgtemplate", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CronusEntities {
    public static final ResourceLocation LASER_BOLT_ID = new ResourceLocation("rpgtemplate", "cronus_laser_bolt");
    public static final ResourceLocation ENERGY_ORB_ID = new ResourceLocation("rpgtemplate", "cronus_energy_orb");
    public static final ResourceLocation SAW_WAVE_ID = new ResourceLocation("rpgtemplate", "cronus_saw_wave");

    public static EntityType<CronusLaserBoltEntity> LASER_BOLT;
    public static EntityType<CronusEnergyOrbEntity> ENERGY_ORB;
    public static EntityType<CronusSawWaveEntity> SAW_WAVE;

    private CronusEntities() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ENTITY_TYPES, helper -> {
            LASER_BOLT = EntityType.Builder.m_20704_(CronusLaserBoltEntity::new, MobCategory.MISC)
                .m_20699_(0.35f, 0.35f)
                .setTrackingRange(10)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .m_20712_(LASER_BOLT_ID.toString());
            helper.register(LASER_BOLT_ID, LASER_BOLT);

            ENERGY_ORB = EntityType.Builder.m_20704_(CronusEnergyOrbEntity::new, MobCategory.MISC)
                .m_20699_(1.35f, 1.35f)
                .setTrackingRange(12)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .m_20712_(ENERGY_ORB_ID.toString());
            helper.register(ENERGY_ORB_ID, ENERGY_ORB);

            SAW_WAVE = EntityType.Builder.m_20704_(CronusSawWaveEntity::new, MobCategory.MISC)
                .m_20699_(2.8f, 0.9f)
                .setTrackingRange(12)
                .setUpdateInterval(1)
                .setShouldReceiveVelocityUpdates(true)
                .m_20712_(SAW_WAVE_ID.toString());
            helper.register(SAW_WAVE_ID, SAW_WAVE);
        });
    }
}
