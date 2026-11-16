package com.rpgtemplate.forge.combat;
import net.minecraft.core.registries.Registries;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.CombatBalance;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.core.template.TraitEntry;
import com.rpgtemplate.forge.ModConfigs;
import com.rpgtemplate.forge.skill.SkillManager;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.RuntimeState;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.system.TraitLookup;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class DamageHandler {
    public static final String SMITE_VULNERABLE = "rpgtemplate:smite_vulnerable";
    public static final String SOUL_INCOMPLETE = "rpgtemplate:soul_incomplete";
    public static final String VESSEL_SUPPRESSION = "rpgtemplate:vessel_suppression";

    private DamageHandler() {
    }

    @SubscribeEvent(
        priority = EventPriority.LOW
    )
    public static void onIncoming(LivingIncomingDamageEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PlayerSystemData var5 = SystemDataManager.get(var1);
            if (var5.isActivated()) {
                DamageSource var3 = var0.getSource();
                RuntimeState.State var4 = RuntimeState.of(var1.getUUID());
                var4.pendingSoulHit = false;
                TraitLookup.loadedWeakness(var5, "rpgtemplate:smite_vulnerable").ifPresent(var3x -> {
                    int var4x = smiteLevel(var1, var3);
                    if (var4x > 0) {
                        double var5x = (double)var4x * TraitLookup.param(var3x, "damage_per_level", 2.5) * TraitLookup.param(var3x, "multiplier", 2.0);
                        var0.setAmount((float)((double)var0.getAmount() + var5x));
                        var1.level().playSound(null, var1.getX(), var1.getY(), var1.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.4F, 1.8F);
                    }
                });
                TraitLookup.loadedNegative(var5, "rpgtemplate:soul_incomplete").ifPresent(var3x -> {
                    if (ModDamageTypes.isSoulDamage(var3)) {
                        var0.setAmount((float)((double)var0.getAmount() * TraitLookup.param(var3x, "soul_damage_multiplier", 1.5)));
                        var4.pendingSoulHit = true;
                    }
                });
            }
        }
    }

    public static int smiteLevel(ServerPlayer var0, DamageSource var1) {
        ItemStack var2 = ItemStack.EMPTY;
        if ((var2 == null || var2.isEmpty()) && var1.getEntity() instanceof LivingEntity var3) {
            var2 = var3.getMainHandItem();
        }

        final ItemStack weapon = var2;
        return weapon != null && !weapon.isEmpty() ? var0.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SMITE).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, weapon)).orElse(0) : 0;
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onDamagePre(LivingDamageEvent.Pre var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PlayerSystemData var12 = SystemDataManager.get(var1);
            if (var12.isActivated()) {
                float var3 = var0.getNewDamage();
                if (!(var3 <= 0.0F)) {
                    RuntimeState.State var4 = RuntimeState.of(var1.getUUID());
                    long var5 = var1.serverLevel().getGameTime();
                    var4.lastHurtTick = var5;
                    if (ModDamageTypes.bypassesSystemHp(var0.getSource())) {
                        var4.pendingSoulHit = false;
                    } else {
                        float var7 = absorb(var1, var12, var3, WorldSlashDamage.is(var0.getSource()));
                        var0.setNewDamage(var7);
                        TemplateRegistry var8 = TemplateRegistry.get();
                        CoreConfig var9 = CoreConfig.get();
                        double var10 = (Double)ModConfigs.SERVER.damageConversion.get();
                        if (var4.pendingSoulHit) {
                            var4.pendingSoulHit = false;
                            TraitLookup.loadedNegative(var12, "rpgtemplate:soul_incomplete")
                                .ifPresent(
                                    var6 -> {
                                        double var7x = StatCalculator.maxStatRaw(StatType.HP, var12, var8, var9);
                                        double var9x = var7x * TraitLookup.param(var6, "max_wound_ratio", 0.5);
                                        double var11 = CombatBalance.mitigate((double)var3 * var10, StatCalculator.maxStat(StatType.DEF, var12, var8, var9))
                                            * TraitLookup.param(var6, "wound_ratio", 0.5);
                                        double var13 = var12.value("hp_max_penalty", 0.0);
                                        var12.setValue("hp_max_penalty", Math.min(var9x, var13 + var11));
                                        StatCalculator.normalizeCurrent(var12, var8, var9);
                                    }
                                );
                        }

                        TraitLookup.loadedWeakness(var12, "rpgtemplate:vessel_suppression").ifPresent(var4x -> checkVessel(var1, var12, var4x, var5));
                        SystemDataManager.markDirty(var1);
                    }
                }
            }
        }
    }

    public static float absorb(ServerPlayer var0, PlayerSystemData var1, float var2) {
        return absorb(var0, var1, var2, false);
    }

    public static float absorb(ServerPlayer var0, PlayerSystemData var1, float var2, boolean var3) {
        double var4 = (Double)ModConfigs.SERVER.damageConversion.get();
        double var6 = var3 ? 0.0 : StatCalculator.maxStat(StatType.DEF, var1, TemplateRegistry.get(), CoreConfig.get());
        double var8 = CombatBalance.mitigate((double)var2 * var4, var6);
        double var10 = Math.max(0.0, var1.hp());
        if (var10 >= var8) {
            var1.setHp(var10 - var8);
            return 0.0F;
        } else {
            var1.setHp(0.0);
            return (float)((var8 - var10) / var4);
        }
    }

    private static void checkVessel(ServerPlayer var0, PlayerSystemData var1, TraitEntry var2, long var3) {
        double var5 = StatCalculator.maxStat(StatType.HP, var1, TemplateRegistry.get(), CoreConfig.get());
        if (!(var5 <= 0.0)
            && !(var1.hp() / var5 >= TraitLookup.param(var2, "hp_threshold", 0.25))
            && !((double)var3 < var1.value("sealed_until", 0.0))
            && !(var0.getRandom().nextDouble() >= TraitLookup.param(var2, "chance", 0.1))) {
            int var7 = (int)TraitLookup.param(var2, "seal_ticks", 100.0);
            SkillManager.seal(var0, var7);
            Msg.actionBar(
                var0, Msg.tr("weakness.vessel_suppressed", String.format(Locale.ROOT, "%.1f", (double)var7 / 20.0)).withStyle(ChatFormatting.DARK_RED)
            );
            var0.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }
}
