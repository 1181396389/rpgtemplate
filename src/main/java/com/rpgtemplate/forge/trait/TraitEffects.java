package com.rpgtemplate.forge.trait;
import net.minecraft.core.Holder;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.crafting.SingleRecipeInput;


import net.neoforged.fml.common.EventBusSubscriber;
import com.mojang.datafixers.util.Pair;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class TraitEffects {
    public static final String CURSED_OBJECT_AURA = "rpgtemplate:cursed_object_aura";
    public static final String RAW_MEAT_GOURMET = "rpgtemplate:raw_meat_gourmet";
    public static final String KING_PRESENCE = "rpgtemplate:king_presence";

    private TraitEffects() {
    }

    public static void perSecond(ServerPlayer var0, PlayerSystemData var1) {
        TraitLookup.loadedWeakness(var1, "rpgtemplate:cursed_object_aura").ifPresent(var1x -> cursedAura(var0, var1x));
        TraitLookup.activePassive(var1, "rpgtemplate:king_presence").ifPresent(var2 -> kingPresence(var0, var1, var2));
    }

    private static void kingPresence(ServerPlayer var0, PlayerSystemData var1, TraitEntry var2) {
        if (!var0.isCreative() && !var0.isSpectator() && !var0.isInvisible() && (var0.level().getGameTime() + (long)var0.getId()) % 4L == 0L) {
            double var3 = Math.max(0.0, TraitLookup.param(var2, "radius", 10.0));
            if (!(var3 <= 0.0)) {
                int var5 = (int)TraitLookup.param(var2, "rule_level_gap", 6.0);
                int var6 = Math.max(1, (int)TraitLookup.param(var2, "duration_ticks", 45.0));
                int var7 = (int)TraitLookup.param(var2, "weakness_amplifier", 0.0);
                int var8 = (int)TraitLookup.param(var2, "slowness_amplifier", 0.0);
                int var9 = var1.level() - var5;
                int var10 = 0;

                for (Monster var12 : var0.level().getEntitiesOfClass(Monster.class, new AABB(var0.blockPosition()).inflate(var3), var0x -> var0x.isAlive())) {
                    if (!((double)var12.distanceTo(var0) > var3) && TargetUtil.ruleLevel(var12) <= var9) {
                        if (var7 >= 0) {
                            var12.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, var6, var7, true, false, true));
                        }

                        if (var8 >= 0) {
                            var12.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, var6, var8, true, false, true));
                        }

                        var10++;
                    }
                }

                if (var10 > 0 && var0.level() instanceof ServerLevel var13) {
                    var13.sendParticles(ParticleTypes.SQUID_INK, var0.getX(), var0.getY() + 0.1, var0.getZ(), 4 + Math.min(12, var10 * 2), 0.6, 0.05, 0.6, 0.01);
                }
            }
        }
    }

    private static void cursedAura(ServerPlayer var0, TraitEntry var1) {
        if (!var0.isCreative() && !var0.isSpectator() && !var0.isInvisible() && (var0.level().getGameTime() + (long)var0.getId()) % 4L == 0L) {
            double var2 = TraitLookup.param(var1, "follow_range_multiplier", 1.5);
            if (!(var2 <= 1.0)) {
                double var4 = Math.min(64.0, 32.0 * var2);

                for (Monster var7 : var0.level()
                    .getEntitiesOfClass(Monster.class, new AABB(var0.blockPosition()).inflate(var4), var0x -> var0x.isAlive() && var0x.getTarget() == null)) {
                    double var8 = var7.getAttributeValue(Attributes.FOLLOW_RANGE);
                    double var10 = (double)var7.distanceTo(var0);
                    if (var10 > var8 && var10 <= var8 * var2 && var7.hasLineOfSight(var0) && var7.canAttack(var0)) {
                        var7.setTarget(var0);
                    }
                }
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGH
    )
    public static void onEat(Finish var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PlayerSystemData var12 = SystemDataManager.get(var1);
            if (var12.isActivated()) {
                Optional var3 = TraitLookup.loadedQuirk(var12, "rpgtemplate:raw_meat_gourmet");
                if (!var3.isEmpty()) {
                    TraitEntry var4 = (TraitEntry)var3.get();
                    ItemStack var5 = var0.getItem();
                    String var6 = var4.params() != null && var4.params().has("items_tag") ? var4.params().get("items_tag").getAsString() : "c:foods/raw_meat";
                    TagKey var7 = TagKey.create(Registries.ITEM, ResourceLocation.parse(var6));
                    if (var5.is(var7)) {
                        FoodProperties var8 = var5.getItem().getFoodProperties(var5, var1);
                        if (var8 != null) {
                            if (TraitLookup.paramBool(var4, "cooked_equivalent", true)) {
                                ItemStack var9 = var5.copy();
                                var9.setCount(1);
                                var1.level()
                                    .getRecipeManager()
                                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(var9), var1.level())
                                    .map(h -> h.value().getResultItem(var1.level().registryAccess()))
                                    .map(s -> s.getFoodProperties(var1))
                                    .ifPresent(var2 -> {
                                        int var3x = var2.nutrition() - var8.nutrition();
                                        float var4x = var2.saturation() - var8.saturation();
                                        if (var3x > 0 || var4x > 0.0F) {
                                            var1.getFoodData().eat(Math.max(0, var3x), Math.max(0.0F, var4x));
                                        }
                                    });
                            }

                            if (TraitLookup.paramBool(var4, "remove_negative_effects", true)) {
                                for (FoodProperties.PossibleEffect var10 : var8.effects()) {
                                    Holder<MobEffect> var11 = var10.effect().getEffect();
                                    if (var11.value().getCategory() == MobEffectCategory.HARMFUL) {
                                        var1.removeEffect(var11);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && !var0.wakeImmediately() && var1.isSleepingLongEnough()) {
            PlayerSystemData var4 = SystemDataManager.get(var1);
            if (var4.isActivated() && !(var4.value("hp_max_penalty", 0.0) <= 0.0)) {
                boolean var3 = TraitLookup.loadedNegative(var4, "rpgtemplate:soul_incomplete")
                    .map(var0x -> TraitLookup.paramBool(var0x, "recover_on_sleep", true))
                    .orElse(true);
                if (var3) {
                    var4.removeValue("hp_max_penalty");
                    StatCalculator.normalizeCurrent(var4, TemplateRegistry.get(), CoreConfig.get());
                    Msg.chat(var1, Msg.system(Msg.tr("weakness.soul_recovered").withStyle(ChatFormatting.GREEN)));
                    SystemDataManager.sync(var1);
                }
            }
        }
    }
}
