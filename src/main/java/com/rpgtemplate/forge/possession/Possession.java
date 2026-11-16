package com.rpgtemplate.forge.possession;
import com.google.gson.JsonElement;
import com.rpgtemplate.forge.util.ItemNbt;
import net.minecraft.world.level.portal.DimensionTransition;


import net.neoforged.fml.common.EventBusSubscriber;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.rpgtemplate.core.config.CoreConfig;
import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.core.stats.StatCalculator;
import com.rpgtemplate.core.stats.StatType;
import com.rpgtemplate.core.template.SkillEntry;
import com.rpgtemplate.core.template.TemplateRegistry;
import com.rpgtemplate.forge.king.KingRules;
import com.rpgtemplate.forge.skill.DomainExpansionSkill;
import com.rpgtemplate.forge.skill.SkillContext;
import com.rpgtemplate.forge.system.Msg;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.NameFormat;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class Possession {
    public static final String SKILL_ID = "rpgtemplate:possession";
    private static final Map<UUID, Possession.Sight> SIGHT = new HashMap<>();
    private static final Set<UUID> DESTROYED = new HashSet<>();
    private static final Map<UUID, long[]> PRESS = new HashMap<>();
    private static final Set<UUID> BORROWING = new HashSet<>();
    private static List<PossessionNet.BodyMap.Entry> lastBodies = List.of();
    public static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();
    private static final Map<UUID, Entity> FAKE_CAMERA = new HashMap<>();

    private Possession() {
    }

    static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    static PossessionData data() {
        MinecraftServer var0 = server();
        return var0 == null ? null : PossessionData.get(var0);
    }

    static ServerPlayer online(UUID var0) {
        MinecraftServer var1 = server();
        if (var1 != null && var0 != null) {
            ServerPlayer var2 = var1.getPlayerList().getPlayer(var0);
            return var2 != null ? var2 : TEST_PLAYERS.get(var0);
        } else {
            return null;
        }
    }

    public static PossessionData.Rec recOf(UUID var0) {
        PossessionData var1 = data();
        if (var1 != null && var0 != null) {
            PossessionData.Rec var2 = var1.recs.get(var0);
            if (var2 != null) {
                return var2;
            } else {
                for (PossessionData.Rec var4 : var1.recs.values()) {
                    if (var0.equals(var4.host) && var4.phase != PossessionData.Phase.FINGER) {
                        return var4;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean involved(Player var0) {
        return !var0.level().isClientSide && recOf(var0.getUUID()) != null;
    }

    public static UUID controller(PossessionData.Rec var0) {
        return switch (var0.phase) {
            case TRAPPED -> var0.host;
            case POSSESSING, CONTRACT -> var0.sukuna;
            default -> null;
        };
    }

    public static UUID soul(PossessionData.Rec var0) {
        return switch (var0.phase) {
            case TRAPPED -> var0.sukuna;
            case POSSESSING, CONTRACT -> var0.host;
            case FINGER, BATTLE -> var0.sukuna;
        };
    }

    public static PossessionData.Rec bodyOf(UUID var0) {
        PossessionData var1 = data();
        if (var1 == null) {
            return null;
        } else {
            PossessionData.Rec var2 = var1.recs.get(var0);
            return var2 != null && (var2.phase == PossessionData.Phase.POSSESSING || var2.phase == PossessionData.Phase.CONTRACT) ? var2 : null;
        }
    }

    static JsonObject params(PossessionData.Rec var0) {
        try {
            JsonElement var1 = JsonParser.parseString(var0.params);
            return var1.isJsonObject() ? var1.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException var2) {
            return new JsonObject();
        }
    }

    static double num(PossessionData.Rec var0, String var1, double var2) {
        JsonElement var4 = params(var0);

        for (String var8 : var1.split("\\.")) {
            if (var4 == null || !var4.isJsonObject() || !var4.getAsJsonObject().has(var8)) {
                return var2;
            }

            var4 = var4.getAsJsonObject().get(var8);
        }

        try {
            return var4.getAsDouble();
        } catch (RuntimeException var9) {
            return var2;
        }
    }

    static long now() {
        MinecraftServer var0 = server();
        return var0 == null ? 0L : var0.overworld().getGameTime();
    }

    public static UUID fingerId(ItemStack var0) {
        CompoundTag var1 = ItemNbt.getTag(var0);
        return var1 != null && var1.hasUUID("FingerId") ? var1.getUUID("FingerId") : null;
    }

    public static boolean live(ItemStack var0) {
        UUID var1 = fingerId(var0);
        PossessionData var2 = data();
        if (var1 != null && var2 != null) {
            for (PossessionData.Rec var4 : var2.recs.values()) {
                if (var4.phase == PossessionData.Phase.FINGER && var1.equals(var4.fingerId)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    static PossessionData.Rec recByFinger(UUID var0) {
        PossessionData var1 = data();
        if (var0 != null && var1 != null) {
            for (PossessionData.Rec var3 : var1.recs.values()) {
                if (var3.phase == PossessionData.Phase.FINGER && var0.equals(var3.fingerId)) {
                    return var3;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static void sighted(ItemStack var0, Entity var1) {
        UUID var2 = fingerId(var0);
        if (var2 != null && !var1.level().isClientSide) {
            SIGHT.put(var2, new Possession.Sight(var1, now(), var1.level().dimension().location().toString(), new Vec3(var1.getX(), var1.getY(), var1.getZ())));
        }
    }

    public static void destroyed(ItemStack var0) {
        UUID var1 = fingerId(var0);
        if (var1 != null) {
            DESTROYED.add(var1);
        }
    }

    public static boolean startFinger(SkillContext var0) {
        ServerPlayer var1 = var0.player;
        PossessionData var2 = data();
        if (var2 != null && recOf(var1.getUUID()) == null) {
            if (DomainExpansionSkill.active(SystemDataManager.raw(var1), var0.now)) {
                DomainExpansionSkill.end(var1, "possession");
            }

            PossessionData.Rec var3 = new PossessionData.Rec();
            var3.sukuna = var1.getUUID();
            var3.sukunaName = var1.getGameProfile().getName();
            var3.phase = PossessionData.Phase.FINGER;
            var3.fingerId = UUID.randomUUID();
            var3.sukunaMode = var1.gameMode.getGameModeForPlayer().getId();
            JsonObject var4 = var0.entry.params();
            var3.params = var4 == null ? "{}" : var4.toString();
            var3.startedAt = now();
            var3.bx = var1.getX();
            var3.by = var1.getY();
            var3.bz = var1.getZ();
            var3.bdim = var1.level().dimension().location().toString();
            var2.recs.put(var3.sukuna, var3);
            var2.setDirty();
            ItemStack var5 = SukunaFingerItem.create(var3.fingerId, var1);
            ServerLevel var6 = var1.serverLevel();
            ItemEntity var7 = new ItemEntity(var6, var1.getX(), var1.getY() + 1.0, var1.getZ(), var5);
            var7.setDeltaMovement(0.0, 0.25, 0.0);
            var7.setPickUpDelay(30);
            var7.setUnlimitedLifetime();
            var6.addFreshEntity(var7);
            sighted(var5, var7);
            var6.sendParticles(ParticleTypes.SOUL, var1.getX(), var1.getY() + 1.0, var1.getZ(), 60, 0.5, 0.9, 0.5, 0.05);
            var6.sendParticles(ParticleTypes.LARGE_SMOKE, var1.getX(), var1.getY() + 1.0, var1.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
            var6.playSound(null, var1.getX(), var1.getY(), var1.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.6F);
            var1.setGameMode(GameType.SPECTATOR);
            setCam(var1, var7);
            Msg.chat(var1, Msg.tr("possession.finger.start").withStyle(ChatFormatting.DARK_RED));
            pushState(var3);
            return true;
        } else {
            return false;
        }
    }

    static void cancelFinger(PossessionData.Rec var0, ServerPlayer var1) {
        Possession.Sight var2 = SIGHT.get(var0.fingerId);
        removeFingerStack(var0.fingerId, var2);
        CompoundTag var3 = new CompoundTag();
        var3.putInt("Mode", var0.sukunaMode);
        if (var2 != null) {
            CompoundTag var4 = new CompoundTag();
            var4.putString("Dim", var2.dim);
            var4.putDouble("X", var2.pos.x);
            var4.putDouble("Y", var2.pos.y);
            var4.putDouble("Z", var2.pos.z);
            var4.putFloat("YRot", var1.getYRot());
            var4.putFloat("XRot", 0.0F);
            var3.put("Pos", var4);
        }

        var3.putString("Msg", "possession.finger.cancel");
        finish(var0, var1.getUUID(), var3, null, null);
    }

    static void fingerDestroyed(PossessionData.Rec var0) {
        CompoundTag var1 = new CompoundTag();
        var1.putInt("Mode", var0.sukunaMode);
        var1.putBoolean("ToSpawn", true);
        var1.putString("Msg", "possession.finger.destroyed");
        SIGHT.remove(var0.fingerId);
        finish(var0, var0.sukuna, var1, null, null);
    }

    private static void removeFingerStack(UUID var0, Possession.Sight var1) {
        if (var1 != null) {
            label31: {
                Entity var2 = var1.entity;
                if (var2 instanceof ItemEntity var3 && !var3.isRemoved()) {
                    var3.discard();
                    break label31;
                }

                if (var2 instanceof Player var5) {
                    for (int var4 = 0; var4 < var5.getInventory().getContainerSize(); var4++) {
                        if (var0.equals(fingerId(var5.getInventory().getItem(var4)))) {
                            var5.getInventory().setItem(var4, ItemStack.EMPTY);
                        }
                    }
                }
            }

            SIGHT.remove(var0);
        }
    }

    public static boolean eat(ServerPlayer var0, ItemStack var1, ServerPlayer var2) {
        UUID var3 = fingerId(var1);
        PossessionData.Rec var4 = recByFinger(var3);
        if (var4 == null) {
            crumble(var0, var1);
            return true;
        } else if (var0.getUUID().equals(var4.sukuna)) {
            return false;
        } else if (recOf(var0.getUUID()) == null && !var0.isSpectator()) {
            ServerPlayer var5 = online(var4.sukuna);
            if (var5 == null) {
                Msg.actionBar(var2 != null ? var2 : var0, Msg.tr("possession.eat.dormant").withStyle(ChatFormatting.GRAY));
                return false;
            } else if (var4.phase == PossessionData.Phase.FINGER && var0.isAlive() && var5.isAlive()) {
                try {
                    startBattle(var4, var5, var0, var2);
                    return true;
                } catch (RuntimeException var7) {
                    LogUtils.getLogger().error("[rpgtemplate] finger transition failed; restoring participants", var7);
                    recover(var4, null);
                    return false;
                }
            } else {
                return false;
            }
        } else {
            Msg.actionBar(var2 != null ? var2 : var0, Msg.tr("possession.eat.occupied").withStyle(ChatFormatting.GRAY));
            return false;
        }
    }

    static void crumble(ServerPlayer var0, ItemStack var1) {
        ServerLevel var2 = var0.serverLevel();
        var2.sendParticles(ParticleTypes.ASH, var0.getX(), var0.getY() + 1.2, var0.getZ(), 30, 0.3, 0.3, 0.3, 0.01);
        Msg.actionBar(var0, Msg.tr("possession.finger.inert").withStyle(ChatFormatting.GRAY));
    }

    static void startBattle(PossessionData.Rec var0, ServerPlayer var1, ServerPlayer var2, ServerPlayer var3) {
        PossessionData var4 = data();
        long var5 = now();
        var0.phase = PossessionData.Phase.BATTLE;
        var0.host = var2.getUUID();
        var0.hostMode = var2.gameMode.getGameModeForPlayer().getId();
        var0.hostName = var2.getGameProfile().getName();
        var0.battleStart = var5;
        var0.battleEnd = var5 + (long)num(var0, "battle.duration_ticks", 200.0);
        int var7 = SystemDataManager.available(var1) ? SystemDataManager.raw(var1).level() : 1;
        int var8 = SystemDataManager.available(var2) && SystemDataManager.raw(var2).isActivated() ? SystemDataManager.raw(var2).level() : 1;
        double var9 = num(var0, "battle.level_bias_cap", 35.0);
        var0.progress = Math.max(-var9, Math.min(var9, (double)(var7 - var8) * num(var0, "battle.level_bias", 0.6)));
        var0.bx = var2.getX();
        var0.by = var2.getY();
        var0.bz = var2.getZ();
        var0.bdim = var2.level().dimension().location().toString();
        SIGHT.remove(var0.fingerId);
        var4.setDirty();
        ServerLevel var11 = var2.serverLevel();
        var11.playSound(null, var2.getX(), var2.getY(), var2.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.7F);
        var11.sendParticles(ParticleTypes.SCULK_SOUL, var2.getX(), var2.getY() + 1.0, var2.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
        if (var3 != null) {
            Msg.chat(var3, Msg.tr("possession.eat.fed", var0.hostName).withStyle(ChatFormatting.DARK_RED));
        }

        title(
            var2,
            Msg.tr("possession.battle.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
            Msg.tr("possession.battle.sub_host", var0.sukunaName).withStyle(ChatFormatting.GRAY)
        );
        title(
            var1,
            Msg.tr("possession.battle.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
            Msg.tr("possession.battle.sub_sukuna", var0.hostName).withStyle(ChatFormatting.GRAY)
        );
        follow(var1, var2);
        pushState(var0);
    }

    static boolean resonance(PossessionData.Rec var0, long var1) {
        long var3 = Math.max(10L, (long)num(var0, "battle.resonance_period", 40.0));
        long var5 = Math.max(2L, (long)num(var0, "battle.resonance_window", 10.0));
        return (var1 - var0.battleStart) % var3 >= var3 - var5;
    }

    public static double push(ServerPlayer var0) {
        PossessionData.Rec var1 = recOf(var0.getUUID());
        if (var1 != null && var1.phase == PossessionData.Phase.BATTLE) {
            long var2 = now();
            long[] var4 = PRESS.computeIfAbsent(var0.getUUID(), var2x -> new long[]{var2, 0L});
            if (var2 - var4[0] >= 20L) {
                var4[0] = var2;
                var4[1] = 0L;
            }

            if (var4[1] >= (long)num(var1, "battle.max_presses_per_second", 12.0)) {
                return 0.0;
            } else {
                var4[1]++;
                double var5 = num(var1, "battle.press", 2.0) * (resonance(var1, var2) ? num(var1, "battle.resonance_mult", 2.5) : 1.0);
                boolean var7 = var0.getUUID().equals(var1.sukuna);
                var1.progress = Math.max(-100.0, Math.min(100.0, var1.progress + (var7 ? var5 : -var5)));
                return var5;
            }
        } else {
            return 0.0;
        }
    }

    static void tickBattle(PossessionData.Rec var0, long var1) {
        ServerPlayer var3 = online(var0.sukuna);
        ServerPlayer var4 = online(var0.host);
        if (var4 == null || !var4.isAlive()) {
            abortBattle(var0, var3);
        } else if (var3 == null) {
            resolve(var0, false);
        } else {
            if (!var4.level().dimension().location().toString().equals(var0.bdim) || var4.distanceToSqr(var0.bx, var0.by, var0.bz) > 0.04) {
                ServerLevel var5 = BodyState.level(var4, var0.bdim);
                tp(var4, var5, var0.bx, var0.by, var0.bz, var4.getYRot(), var4.getXRot());
            }

            var4.setDeltaMovement(Vec3.ZERO);
            if (var1 % 10L == 0L) {
                var4.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 9, false, false, false));
                var4.serverLevel()
                    .sendParticles(
                        resonance(var0, var1) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                        var4.getX(),
                        var4.getY() + 1.0,
                        var4.getZ(),
                        6,
                        0.35,
                        0.6,
                        0.35,
                        0.02
                    );
            }

            follow(var3, var4);
            if (Math.abs(var0.progress) < 100.0) {
                var0.progress = Math.max(-99.9, Math.min(100.0, var0.progress + num(var0, "battle.drift", 0.05)));
            }

            if (!(Math.abs(var0.progress) >= 100.0) && var1 < var0.battleEnd) {
                if (var1 % 2L == 0L) {
                    pushState(var0);
                }
            } else {
                resolve(var0, var0.progress > 0.0);
            }
        }
    }

    static void abortBattle(PossessionData.Rec var0, ServerPlayer var1) {
        CompoundTag var2 = new CompoundTag();
        var2.putInt("Mode", var0.sukunaMode);
        CompoundTag var3 = new CompoundTag();
        var3.putString("Dim", var0.bdim);
        var3.putDouble("X", var0.bx);
        var3.putDouble("Y", var0.by);
        var3.putDouble("Z", var0.bz);
        var2.put("Pos", var3);
        var2.putString("Msg", "possession.battle.abort");
        finish(var0, var0.sukuna, var2, null, null);
    }

    public static void resolve(PossessionData.Rec var0, boolean var1) {
        KingRules.possessionResolved(var0.sukuna, var1);
        ServerPlayer var2 = online(var0.sukuna);
        ServerPlayer var3 = online(var0.host);
        if (var3 != null) {
            var3.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }

        if (var1 && var2 != null && var3 != null) {
            possess(var0, var2, var3, false);
            title(
                var2,
                Msg.tr("possession.win.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
                Msg.tr("possession.win.sub_sukuna", var0.hostName)
            );
            title(
                var3,
                Msg.tr("possession.lose.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.BOLD}),
                Msg.tr("possession.lose.sub_host", var0.sukunaName)
            );
        } else {
            PossessionData var4 = data();
            var0.phase = PossessionData.Phase.TRAPPED;
            var0.watch = true;
            var0.lending = false;
            if (var0.realmSlot < 0) {
                var0.realmSlot = var4.nextSlot++;
            }

            var4.setDirty();
            if (var2 != null) {
                title(
                    var2,
                    Msg.tr("possession.trapped.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
                    Msg.tr("possession.trapped.sub_sukuna", var0.hostName)
                );
                Msg.chat(var2, Msg.tr("possession.trapped.help").withStyle(ChatFormatting.GRAY));
            }

            if (var3 != null) {
                title(
                    var3,
                    Msg.tr("possession.resist.title").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}),
                    Msg.tr("possession.resist.sub_host", var0.sukunaName)
                );
                Msg.chat(var3, Msg.tr("possession.resist.help", var0.sukunaName).withStyle(ChatFormatting.GRAY));
            }

            pushState(var0);
        }

        if (var3 != null) {
            var3.serverLevel()
                .playSound(
                    null,
                    var3.getX(),
                    var3.getY(),
                    var3.getZ(),
                    var1 ? SoundEvents.WITHER_SPAWN : SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS,
                    1.0F,
                    var1 ? 0.8F : 0.6F
                );
        }
    }

    static void possess(PossessionData.Rec var0, ServerPlayer var1, ServerPlayer var2, boolean var3) {
        PossessionData var4 = data();
        long var5 = now();
        CompoundTag var7 = BodyState.capture(var2, true);
        var0.hostBackup = var7.copy();
        var0.sukunaStash = BodyState.capture(var1, false);
        var0.hostMode = var2.gameMode.getGameModeForPlayer().getId();
        var0.swapping = true;
        var4.setDirty();
        setCam(var1, var1);
        setCam(var2, var2);
        BodyState.empty(var2);
        var2.setGameMode(GameType.SPECTATOR);
        setCam(var1, var1);
        var1.setGameMode(GameType.byId(var0.hostMode));
        BodyState.empty(var1);
        BodyState.apply(var1, var7, false);
        tp(
            var1,
            BodyState.level(var1, var7.getString("Dim")),
            var7.getDouble("X"),
            var7.getDouble("Y"),
            var7.getDouble("Z"),
            var7.getFloat("YRot"),
            var7.getFloat("XRot")
        );
        var0.phase = var3 ? PossessionData.Phase.CONTRACT : PossessionData.Phase.POSSESSING;
        var0.swapping = false;
        if (var3) {
            var0.contractUntil = var5 + (long)num(var0, "contract.duration_ticks", 1200.0);
        }

        var0.watch = true;
        var4.setDirty();
        follow(var2, var1);
        ServerLevel var8 = var1.serverLevel();
        var8.sendParticles(ParticleTypes.SCULK_SOUL, var1.getX(), var1.getY() + 1.0, var1.getZ(), 50, 0.4, 0.9, 0.4, 0.04);
        var8.playSound(null, var1.getX(), var1.getY(), var1.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.6F);
        refreshNames(var1);
        broadcastBodies();
        pushState(var0);
    }

    static void endContract(PossessionData.Rec var0, ServerPlayer var1, ServerPlayer var2) {
        PossessionData var3 = data();
        CompoundTag var4 = BodyState.capture(var1, true);
        CompoundTag var5 = new CompoundTag();
        var5.putInt("Mode", var0.hostMode);
        var5.put("Body", var4);
        var5.putString("Msg", "possession.contract.returned");
        var3.addPending(var0.host, var5);
        BodyState.empty(var1);
        BodyState.apply(var1, var0.sukunaStash, false);
        var0.sukunaStash = null;
        var1.setGameMode(GameType.SPECTATOR);
        var0.phase = PossessionData.Phase.TRAPPED;
        var0.watch = true;
        var0.contractCooldownUntil = now() + (long)num(var0, "contract.cooldown_ticks", 6000.0);
        var3.setDirty();
        if (var2 != null) {
            runPending(var2);
            follow(var1, var2);
        }

        Msg.chat(var1, Msg.tr("possession.contract.end").withStyle(ChatFormatting.DARK_RED));
        refreshNames(var1);
        broadcastBodies();
        pushState(var0);
    }

    static void finish(PossessionData.Rec var0, UUID var1, CompoundTag var2, UUID var3, CompoundTag var4) {
        PossessionData var5 = data();
        var5.recs.remove(var0.sukuna);
        if (var2 != null) {
            var5.addPending(var1, var2);
        }

        if (var4 != null) {
            var5.addPending(var3, var4);
        }

        var5.setDirty();
        ServerPlayer var6 = online(var0.sukuna);
        ServerPlayer var7 = online(var0.host);

        for (ServerPlayer var11 : new ServerPlayer[]{var6, var7}) {
            if (var11 != null) {
                PossessionNet.send(var11, PossessionNet.State.NONE);
                runPending(var11);
            }
        }

        if (var7 != null && BORROWING.remove(var7.getUUID())) {
            PossessionNet.send(var7, PossessionNet.Borrowed.NONE);
        }

        if (var6 != null) {
            refreshNames(var6);
        }

        broadcastBodies();
    }

    public static boolean runPending(ServerPlayer var0) {
        PossessionData var1 = data();
        if (var1 != null && var0.isAlive() && !var0.isRemoved()) {
            CompoundTag var2 = var1.pending.get(var0.getUUID());
            if (var2 == null) {
                return false;
            } else {
                var1.setDirty();
                setCam(var0, var0);
                if (var2.contains("Mode")) {
                    var0.setGameMode(GameType.byId(var2.getInt("Mode")));
                }

                if (var2.contains("Body", 10)) {
                    CompoundTag var3 = var2.getCompound("Body");
                    BodyState.apply(var0, var3, false);
                    if (var3.contains("Dim")) {
                        tp(
                            var0,
                            BodyState.level(var0, var3.getString("Dim")),
                            var3.getDouble("X"),
                            var3.getDouble("Y"),
                            var3.getDouble("Z"),
                            var3.getFloat("YRot"),
                            var3.getFloat("XRot")
                        );
                    }
                }

                if (var2.contains("Inv", 10)) {
                    BodyState.apply(var0, var2.getCompound("Inv"), false);
                }

                if (var2.getBoolean("ToSpawn")) {
                    toSpawn(var0);
                }

                if (var2.contains("Pos", 10)) {
                    CompoundTag var4 = var2.getCompound("Pos");
                    tp(
                        var0,
                        BodyState.level(var0, var4.getString("Dim")),
                        var4.getDouble("X"),
                        var4.getDouble("Y"),
                        var4.getDouble("Z"),
                        var4.getFloat("YRot"),
                        var4.getFloat("XRot")
                    );
                }

                var0.fallDistance = 0.0F;
                var0.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                var1.pending.remove(var0.getUUID());
                var1.setDirty();
                if (var2.contains("Msg")) {
                    Msg.chat(var0, Msg.tr(var2.getString("Msg")).withStyle(ChatFormatting.GRAY));
                }

                return true;
            }
        } else {
            return false;
        }
    }

    static void toSpawn(ServerPlayer var0) {
        MinecraftServer var1 = var0.getServer();
        DimensionTransition tr = var0.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
        if (tr != null && tr.newLevel() != null && !tr.missingRespawnBlock()) {
            tp(var0, tr.newLevel(), tr.pos().x, tr.pos().y, tr.pos().z, tr.yRot(), tr.xRot());
            return;
        }

        ServerLevel var7 = var1.overworld();
        BlockPos var5 = var7.getSharedSpawnPos();
        int var6 = var7.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var5.getX(), var5.getZ());
        tp(var0, var7, (double)var5.getX() + 0.5, (double)Math.max(var6, var7.getMinBuildHeight() + 1), (double)var5.getZ() + 0.5, 0.0F, 0.0F);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post var0) {
        if (true) {
            PossessionData var1 = data();
            if (var1 != null) {
                long var2 = now();
                if (!var1.pending.isEmpty()) {
                    for (UUID var5 : new ArrayList<>(var1.pending.keySet())) {
                        ServerPlayer var6 = online(var5);
                        if (var6 != null && recOf(var5) == null) {
                            try {
                                runPending(var6);
                            } catch (RuntimeException var10) {
                                if (var2 % 200L == 0L) {
                                    LogUtils.getLogger().error("[rpgtemplate] pending restore failed for " + var5, var10);
                                }
                            }
                        }
                    }
                }

                for (PossessionData.Rec var12 : new ArrayList<>(var1.recs.values())) {
                    if (var1.recs.containsKey(var12.sukuna) && !var12.suspended) {
                        try {
                            tickRec(var12, var2);
                        } catch (RuntimeException var9) {
                            LogUtils.getLogger().error("[rpgtemplate] possession tick failed; terminating unsafe session", var9);

                            try {
                                recover(var12, null);
                            } catch (RuntimeException var8) {
                                LogUtils.getLogger().error("[rpgtemplate] recovery requires administrator attention", var8);
                                var12.suspended = true;
                                var1.setDirty();
                            }
                        }
                    }
                }

                if (var2 % 40L == 0L) {
                    broadcastBodies();
                }
            }
        }
    }

    static void tickRec(PossessionData.Rec var0, long var1) {
        switch (var0.phase) {
            case TRAPPED:
                ServerPlayer var3 = online(var0.sukuna);
                ServerPlayer var4 = online(var0.host);
                handleSoul(var0, var3, var4, var1);
                tickLending(var0, var3, var4, var1);
                break;
            case POSSESSING:
            case CONTRACT:
                ServerPlayer var5 = online(var0.sukuna);
                ServerPlayer var6 = online(var0.host);
                if (var0.phase == PossessionData.Phase.CONTRACT && var5 != null && var1 >= var0.contractUntil) {
                    endContract(var0, var5, var6);
                    return;
                }

                handleSoul(var0, var6, var5, var1);
                if (var5 != null && var1 % 20L == 0L) {
                    tattooParticles(var5);
                }
                break;
            case FINGER:
                tickFinger(var0, var1);
                break;
            case BATTLE:
                tickBattle(var0, var1);
        }

        if (var1 % 5L == 0L) {
            pushState(var0);
        }
    }

    static void tickFinger(PossessionData.Rec var0, long var1) {
        if (DESTROYED.remove(var0.fingerId)) {
            fingerDestroyed(var0);
        } else {
            ServerPlayer var3 = online(var0.sukuna);
            Possession.Sight var4 = SIGHT.get(var0.fingerId);
            if (var4 != null && var4.entity instanceof ItemEntity var5 && var5.isRemoved() && var1 - var4.tick > 2L) {
                RemovalReason var8 = var5.getRemovalReason();
                if (var8 == RemovalReason.KILLED || var5.getY() < (double)(var5.level().getMinBuildHeight() - 32)) {
                    fingerDestroyed(var0);
                    return;
                }
            }

            if (var3 != null) {
                if (!var3.isSpectator()) {
                    var3.setGameMode(GameType.SPECTATOR);
                }

                if (var4 != null) {
                    boolean var7 = var1 - var4.tick <= 3L && !var4.entity.isRemoved() && var4.entity != var3;
                    if (var7) {
                        follow(var3, var4.entity);
                    } else {
                        if (cameraOf(var3) != var3) {
                            setCam(var3, var3);
                        }

                        ServerLevel var9 = BodyState.level(var3, var4.dim);
                        if (var9 != null && (var3.level() != var9 || var3.distanceToSqr(var4.pos) > 1.0)) {
                            tp(var3, var9, var4.pos.x, var4.pos.y + 0.5, var4.pos.z, var3.getYRot(), var3.getXRot());
                        }
                    }
                }
            }
        }
    }

    static void handleSoul(PossessionData.Rec var0, ServerPlayer var1, ServerPlayer var2, long var3) {
        if (var1 != null) {
            boolean var5 = var2 != null && var2.isAlive() && !var2.isRemoved();
            boolean var6 = var0.phase == PossessionData.Phase.TRAPPED;
            if (!var5 || !var0.watch && var6) {
                if (var6) {
                    if (cameraOf(var1) != var1) {
                        setCam(var1, var1);
                    }

                    if (!SoulRealm.inRealm(var1)) {
                        if (var1.isSpectator() || var1.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                            var1.setGameMode(GameType.ADVENTURE);
                        }

                        if (!SoulRealm.enter(var1, var0.realmSlot)) {
                            recover(var0, null);
                            return;
                        }
                    }

                    if (var1.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                        var1.setGameMode(GameType.ADVENTURE);
                    }

                    SoulRealm.clamp(var1, var0.realmSlot);
                    var1.getFoodData().setFoodLevel(20);
                    var1.setAirSupply(var1.getMaxAirSupply());
                    var1.clearFire();
                    if (var3 % 40L == 0L) {
                        ServerLevel var7 = SoulRealm.level(var1.getServer());
                        if (var7 != null) {
                            SoulRealm.ensureShrine(var7, var0.realmSlot, var1);
                        }
                    }
                } else {
                    if (!var1.isSpectator()) {
                        var1.setGameMode(GameType.SPECTATOR);
                    }

                    if (cameraOf(var1) != var1) {
                        setCam(var1, var1);
                    }
                }
            } else {
                if (!var1.isSpectator()) {
                    var1.setGameMode(GameType.SPECTATOR);
                }

                follow(var1, var2);
            }
        }
    }

    public static boolean realmOccupied(int var0) {
        PossessionData var1 = data();
        if (var1 == null) {
            return false;
        } else {
            for (PossessionData.Rec var3 : var1.recs.values()) {
                if (var3.phase == PossessionData.Phase.TRAPPED && var3.realmSlot == var0 && !var3.watch) {
                    ServerPlayer var4 = online(var3.sukuna);
                    if (var4 != null && SoulRealm.inRealm(var4)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    static void follow(ServerPlayer var0, Entity var1) {
        if (var0 != null && var1 != null && var1 != var0 && !var1.isRemoved() && var1.isAlive()) {
            if (var0.level() != var1.level() || var0.distanceToSqr(var1.getX(), var1.getY(), var1.getZ()) > 16.0) {
                tp(var0, (ServerLevel)var1.level(), var1.getX(), var1.getY(), var1.getZ(), var1.getYRot(), var1.getXRot());
            }

            if (var0.isSpectator() && cameraOf(var0) != var1) {
                setCam(var0, var1);
            }
        }
    }

    public static void tp(ServerPlayer var0, ServerLevel var1, double var2, double var4, double var6, float var8, float var9) {
        if (var1 == null
            || !Double.isFinite(var2)
            || !Double.isFinite(var4)
            || !Double.isFinite(var6)
            || Math.abs(var2) > 2.99999E7
            || Math.abs(var6) > 2.99999E7) {
            var1 = var0.getServer().overworld();
            BlockPos var10 = var1.getSharedSpawnPos();
            var2 = (double)var10.getX() + 0.5;
            var6 = (double)var10.getZ() + 0.5;
            var4 = (double)var1.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var10.getX(), var10.getZ());
        }

        if (!Float.isFinite(var8)) {
            var8 = 0.0F;
        }

        if (!Float.isFinite(var9)) {
            var9 = 0.0F;
        }

        if (var0 instanceof FakePlayer) {
            if (var0.level() != var1) {
                var0.setServerLevel(var1);
            }

            var0.moveTo(var2, var4, var6, var8, var9);
        } else {
            var0.teleportTo(var1, var2, var4, var6, var8, var9);
        }
    }

    static void setCam(ServerPlayer var0, Entity var1) {
        if (var0 instanceof FakePlayer) {
            FAKE_CAMERA.put(var0.getUUID(), (Entity)(var1 == null ? var0 : var1));
        } else {
            if (var1 == null || var1.isRemoved() || var1.level() != var0.level()) {
                var1 = var0;
            }

            if (var1 instanceof ServerPlayer var2 && var2 != var0 && var2.getCamera() == var0) {
                var2.setCamera(var2);
            }

            var0.setCamera((Entity)var1);
        }
    }

    public static Entity cameraOf(ServerPlayer var0) {
        if (!(var0 instanceof FakePlayer)) {
            return var0.getCamera();
        } else {
            Entity var1 = FAKE_CAMERA.get(var0.getUUID());
            return (Entity)(var1 != null && !var1.isRemoved() ? var1 : var0);
        }
    }

    static void tattooParticles(ServerPlayer var0) {
        var0.serverLevel().sendParticles(ParticleTypes.SMOKE, var0.getX(), var0.getY() + 1.5, var0.getZ(), 2, 0.2, 0.3, 0.2, 0.0);
    }

    public static UUID effectCompanion(ServerPlayer var0) {
        PossessionData.Rec var1 = recOf(var0.getUUID());
        if (var1 != null && !var1.suspended && var1.phase != PossessionData.Phase.FINGER && var1.host != null) {
            return var0.getUUID().equals(var1.sukuna) ? var1.host : var1.sukuna;
        } else {
            return null;
        }
    }

    public static ServerPlayer lenderFor(ServerPlayer var0) {
        if (!var0.level().isClientSide && !SkillBorrow.active()) {
            PossessionData var1 = data();
            if (var1 != null && !var1.recs.isEmpty()) {
                PossessionData.Rec var2 = recOf(var0.getUUID());
                if (var2 != null && var2.phase == PossessionData.Phase.TRAPPED && var0.getUUID().equals(var2.host) && var2.lending && var2.hostUsesBorrowed) {
                    ServerPlayer var3 = online(var2.sukuna);
                    return var3 != null && SystemDataManager.available(var3) && SystemDataManager.raw(var3).isActivated() ? var3 : null;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean lendBlocked(ServerPlayer var0, String var1) {
        UUID var2 = SkillBorrow.lenderOf(var0.getUUID());
        if (var2 == null) {
            return false;
        } else {
            PossessionData var3 = data();
            PossessionData.Rec var4 = var3 == null ? null : var3.recs.get(var2);
            JsonArray var5 = null;
            if (var4 != null) {
                JsonObject var6 = params(var4).getAsJsonObject("lend");
                if (var6 != null && var6.has("blocked") && var6.get("blocked").isJsonArray()) {
                    var5 = var6.getAsJsonArray("blocked");
                }
            }

            if (var5 != null) {
                for (JsonElement var7 : var5) {
                    if (var1.equals(var7.getAsString())) {
                        return true;
                    }
                }

                return false;
            } else {
                return var1.equals("rpgtemplate:possession") || var1.equals("rpgtemplate:domain_expansion");
            }
        }
    }

    static void tickLending(PossessionData.Rec var0, ServerPlayer var1, ServerPlayer var2, long var3) {
        if (var2 != null) {
            ServerPlayer var5 = lenderFor(var2);
            if (var5 == null) {
                if (BORROWING.remove(var2.getUUID())) {
                    PossessionNet.send(var2, PossessionNet.Borrowed.NONE);
                }
            } else {
                BORROWING.add(var2.getUUID());
                if (var3 % 5L == 0L) {
                    PlayerSystemData var6 = SystemDataManager.raw(var5);
                    ArrayList var7 = new ArrayList();
                    String var8 = var6.loadedTemplateId();
                    if (var8 != null) {
                        int var9 = var6.loadedTemplate().map(var0x -> var0x.level()).orElse(1);

                        for (SkillEntry var11 : TemplateRegistry.get().effectiveSkills(var8)) {
                            int var12 = (int)Math.max(0.0, var6.value("cd:" + var11.id(), 0.0) - (double)var3);
                            boolean var13 = false;
                            JsonObject var14 = params(var0).getAsJsonObject("lend");
                            if (var14 != null && var14.has("blocked")) {
                                for (JsonElement var16 : var14.getAsJsonArray("blocked")) {
                                    var13 |= var11.id().equals(var16.getAsString());
                                }
                            } else {
                                var13 = var11.id().equals("rpgtemplate:possession") || var11.id().equals("rpgtemplate:domain_expansion");
                            }

                            var7.add(new PossessionNet.Borrowed.Skill(var11.name(), var12, var11.unlockedAt(var9) && !var13));
                        }
                    }

                    float var17 = (float)StatCalculator.maxStat(StatType.MP, var6, TemplateRegistry.get(), CoreConfig.get());
                    PossessionNet.send(var2, new PossessionNet.Borrowed(true, var0.sukunaName, (float)var6.mp(), var17, var7));
                }
            }
        }
    }

    public static boolean interceptSkillInput(ServerPlayer var0, int var1, boolean var2) {
        PossessionData var3 = data();
        if (var3 != null && !var3.recs.isEmpty()) {
            PossessionData.Rec var4 = recOf(var0.getUUID());
            if (var4 == null) {
                return false;
            } else {
                boolean var5 = var0.getUUID().equals(var4.sukuna);
                if (var5) {
                    switch (var4.phase) {
                        case TRAPPED:
                            if (var2) {
                                Msg.actionBar(var0, Msg.tr("possession.trapped.keys").withStyle(ChatFormatting.GRAY));
                            }

                            return true;
                        case POSSESSING:
                        default:
                            return false;
                        case CONTRACT:
                            if (var2 && var1 == DomainExpansionSkill.slotOf(var0, "rpgtemplate:possession")) {
                                endContract(var4, var0, online(var4.host));
                                return true;
                            }

                            return false;
                        case FINGER:
                            if (var2 && var1 == DomainExpansionSkill.slotOf(var0, "rpgtemplate:possession")) {
                                cancelFinger(var4, var0);
                            } else if (var2) {
                                Msg.actionBar(var0, Msg.tr("possession.finger.hint").withStyle(ChatFormatting.GRAY));
                            }

                            return true;
                        case BATTLE:
                            return true;
                    }
                } else {
                    return var4.phase == PossessionData.Phase.BATTLE
                        || var4.phase == PossessionData.Phase.POSSESSING
                        || var4.phase == PossessionData.Phase.CONTRACT;
                }
            }
        } else {
            return false;
        }
    }

    public static void onSoulAction(ServerPlayer var0, int var1) {
        PossessionData.Rec var2 = recOf(var0.getUUID());
        if (var2 != null) {
            boolean var3 = var0.getUUID().equals(var2.sukuna);
            long var4 = now();
            switch (var1) {
                case 0:
                    push(var0);
                    break;
                case 1:
                    if (var3 && var2.phase == PossessionData.Phase.TRAPPED) {
                        var2.watch = !var2.watch;
                        data().setDirty();
                        Msg.actionBar(
                            var0, Msg.tr(var2.watch ? "possession.view.watch" : "possession.view.realm", var2.hostName).withStyle(ChatFormatting.DARK_RED)
                        );
                        handleSoul(var2, var0, online(var2.host), var4);
                        pushState(var2);
                    }
                    break;
                case 2:
                    if (var3 && var2.phase == PossessionData.Phase.TRAPPED) {
                        contract(var2, var0);
                    } else if (var3 && var2.phase == PossessionData.Phase.CONTRACT) {
                        endContract(var2, var0, online(var2.host));
                    }
                    break;
                case 3:
                    if (var2.phase != PossessionData.Phase.TRAPPED) {
                        return;
                    }

                    ServerPlayer var6 = online(var2.host);
                    if (var3) {
                        var2.lending = !var2.lending;
                        Msg.actionBar(
                            var0, Msg.tr(var2.lending ? "possession.lend.on" : "possession.lend.off", var2.hostName).withStyle(ChatFormatting.DARK_RED)
                        );
                        if (var6 != null) {
                            Msg.chat(
                                var6,
                                Msg.tr(var2.lending ? "possession.lend.on_host" : "possession.lend.off_host", var2.sukunaName)
                                    .withStyle(var2.lending ? ChatFormatting.DARK_RED : ChatFormatting.GRAY)
                            );
                        }
                    } else if (var2.lending) {
                        var2.hostUsesBorrowed = !var2.hostUsesBorrowed;
                        Msg.actionBar(
                            var0, Msg.tr(var2.hostUsesBorrowed ? "possession.lend.use_borrowed" : "possession.lend.use_own").withStyle(ChatFormatting.GOLD)
                        );
                    }

                    data().setDirty();
                    tickLending(var2, online(var2.sukuna), var6, var4 - var4 % 5L);
                    pushState(var2);
            }
        }
    }

    public static boolean contract(PossessionData.Rec var0, ServerPlayer var1) {
        long var2 = now();
        ServerPlayer var4 = online(var0.host);
        if (var4 == null || !var4.isAlive()) {
            Msg.actionBar(var1, Msg.tr("possession.contract.no_host").withStyle(ChatFormatting.GRAY));
            return false;
        } else if (var2 < var0.contractCooldownUntil) {
            Msg.actionBar(var1, Msg.tr("possession.contract.cooldown", (var0.contractCooldownUntil - var2 + 19L) / 20L).withStyle(ChatFormatting.GRAY));
            return false;
        } else {
            if (BORROWING.remove(var4.getUUID())) {
                PossessionNet.send(var4, PossessionNet.Borrowed.NONE);
            }

            possess(var0, var1, var4, true);
            int var5 = (int)(num(var0, "contract.duration_ticks", 1200.0) / 20.0);
            title(
                var4,
                Msg.tr("possession.contract.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
                Msg.tr("possession.contract.sub_host", var0.sukunaName, var5)
            );
            title(
                var1,
                Msg.tr("possession.contract.title").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}),
                Msg.tr("possession.contract.sub_sukuna", var5)
            );
            return true;
        }
    }

    @SubscribeEvent(
        priority = EventPriority.LOWEST
    )
    public static void onDeath(LivingDeathEvent var0) {
        if (!var0.isCanceled() && var0.getEntity() instanceof ServerPlayer var1) {
            PossessionData var11 = data();
            if (var11 != null && !var11.recs.isEmpty()) {
                PossessionData.Rec var3 = recOf(var1.getUUID());
                if (var3 != null) {
                    UUID var4 = var1.getUUID();
                    if (var3.phase == PossessionData.Phase.BATTLE && var4.equals(var3.host)) {
                        CompoundTag var13 = new CompoundTag();
                        var13.putInt("Mode", var3.sukunaMode);
                        CompoundTag var15 = new CompoundTag();
                        BodyState.putPos(var15, var1);
                        var13.put("Pos", var15);
                        var13.putString("Msg", "possession.battle.abort");
                        finish(var3, var3.sukuna, var13, null, null);
                    } else if (var3.phase == PossessionData.Phase.TRAPPED && var4.equals(var3.host)) {
                        CompoundTag var12 = new CompoundTag();
                        var12.putInt("Mode", var3.sukunaMode);
                        CompoundTag var14 = new CompoundTag();
                        BodyState.putPos(var14, var1);
                        var12.put("Pos", var14);
                        var12.putString("Msg", "possession.release.host_died");
                        finish(var3, var3.sukuna, var12, null, null);
                    } else if ((var3.phase == PossessionData.Phase.POSSESSING || var3.phase == PossessionData.Phase.CONTRACT) && var4.equals(var3.sukuna)) {
                        boolean var5 = var1.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                        CompoundTag var6 = new CompoundTag();
                        var6.putInt("Mode", var3.hostMode);
                        var6.putBoolean("ToSpawn", true);
                        CompoundTag var7 = var5 ? BodyState.inventoryOnly(var1) : new CompoundTag();
                        if (!var5) {
                            var7.put("Inv", new ListTag());
                        }

                        var6.put("Inv", var7);
                        var6.putString("Msg", "possession.release.body_died_host");
                        CompoundTag var8 = new CompoundTag();
                        var8.putInt("Mode", var3.sukunaMode);
                        CompoundTag var9 = var3.sukunaStash == null ? new CompoundTag() : var3.sukunaStash.copy();
                        var9.remove("Health");
                        CompoundTag var10 = new CompoundTag();
                        if (var9.contains("Inv")) {
                            var10.put("Inv", var9.get("Inv"));
                            var10.putInt("Sel", var9.getInt("Sel"));
                        }

                        var8.put("Inv", var10);
                        var8.putString("Msg", "possession.release.body_died_sukuna");
                        finish(var3, var3.host, var6, var3.sukuna, var8);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PossessionData var6 = data();
            if (var6 != null && !var6.recs.isEmpty()) {
                PossessionData.Rec var3 = recOf(var1.getUUID());
                if (var3 != null) {
                    UUID var4 = controller(var3);
                    boolean var5 = !var1.getUUID().equals(var4) && (var3.phase != PossessionData.Phase.BATTLE || !var1.getUUID().equals(var3.host));
                    if (var5 && !var0.getSource().is(DamageTypes.GENERIC_KILL)) {
                        var0.setCanceled(true);
                    }
                }
            }
        }
    }

    public static boolean recover(PossessionData.Rec var0, ServerPlayer var1) {
        if (var0 == null) {
            return false;
        } else {
            ServerPlayer var2 = var1 != null && var1.getUUID().equals(var0.sukuna) ? var1 : online(var0.sukuna);
            ServerPlayer var3 = var1 != null && var1.getUUID().equals(var0.host) ? var1 : online(var0.host);
            CompoundTag var4 = new CompoundTag();
            var4.putInt("Mode", var0.sukunaMode);
            var4.putBoolean("ToSpawn", true);
            var4.putString("Msg", "possession.recovered");
            CompoundTag var5 = new CompoundTag();
            var5.putInt("Mode", var0.hostMode);
            var5.putString("Msg", "possession.recovered");
            boolean var6 = var0.phase == PossessionData.Phase.POSSESSING || var0.phase == PossessionData.Phase.CONTRACT;
            if (var6 || var0.swapping) {
                CompoundTag var7 = var0.swapping && var0.hostBackup != null
                    ? var0.hostBackup.copy()
                    : (var2 != null ? BodyState.capture(var2, true) : BodyState.offline(server(), var0.sukuna));
                if (var7 == null || var0.sukunaStash == null) {
                    throw new IllegalStateException("Missing body snapshot; recovery suspended instead of deleting inventories");
                }

                var5.put("Body", var7);
                var4.put("Body", var0.sukunaStash.copy());
            }

            if (var2 != null) {
                setCam(var2, var2);
            }

            if (var3 != null) {
                setCam(var3, var3);
                var3.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }

            finish(var0, var0.sukuna, var4, var0.host, var0.host == null ? null : var5);
            if (var1 != null) {
                runPending(var1);
            }

            return true;
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PossessionData.Rec var5 = recOf(var1.getUUID());
            if (var5 != null) {
                try {
                    recover(var5, var1);
                } catch (RuntimeException var4) {
                    var5.suspended = true;
                    data().setDirty();
                    LogUtils.getLogger().error("[rpgtemplate] logout recovery suspended", var4);
                }
            }

            PRESS.remove(var1.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            PossessionData.Rec var6 = recOf(var1.getUUID());
            if (var6 != null) {
                try {
                    recover(var6, var1);
                } catch (RuntimeException var5) {
                    var6.suspended = true;
                    data().setDirty();
                    setCam(var1, var1);
                    var1.setGameMode(GameType.byId(var1.getUUID().equals(var6.sukuna) ? var6.sukunaMode : var6.hostMode));
                    LogUtils.getLogger().error("[rpgtemplate] login recovery incomplete; session suspended", var5);
                }
            }

            try {
                runPending(var1);
            } catch (RuntimeException var4) {
                LogUtils.getLogger().error("[rpgtemplate] login pending restore", var4);
            }

            PossessionNet.send(var1, PossessionNet.State.NONE);
            PossessionNet.send(var1, PossessionNet.Borrowed.NONE);
            PossessionNet.send(var1, new PossessionNet.BodyMap(bodies()));
            refreshNames(var1);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            runPending(var1);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(NameFormat var0) {
        Player var1 = var0.getEntity();
        if (!var1.level().isClientSide) {
            PossessionData.Rec var2 = bodyOf(var1.getUUID());
            if (var2 != null) {
                var0.setDisplayname(Component.literal(var2.hostName));
            }
        }
    }

    @SubscribeEvent
    public static void onTabName(TabListNameFormat var0) {
        Player var1 = var0.getEntity();
        if (!var1.level().isClientSide) {
            PossessionData.Rec var2 = bodyOf(var1.getUUID());
            if (var2 != null) {
                var0.setDisplayName(Component.literal(var2.hostName));
            }
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent var0) {
        SIGHT.clear();
        DESTROYED.clear();
        PRESS.clear();
        BORROWING.clear();
        lastBodies = List.of();
        SoulRealm.reset();
        DomainExpansionSkill.reset();
        FAKE_CAMERA.clear();
        TEST_PLAYERS.clear();
    }

    static List<PossessionNet.BodyMap.Entry> bodies() {
        PossessionData var0 = data();
        ArrayList var1 = new ArrayList();
        if (var0 != null) {
            for (PossessionData.Rec var3 : var0.recs.values()) {
                if (!var3.suspended && (var3.phase == PossessionData.Phase.POSSESSING || var3.phase == PossessionData.Phase.CONTRACT) && var3.host != null) {
                    var1.add(new PossessionNet.BodyMap.Entry(var3.sukuna, var3.host, var3.hostName));
                }
            }
        }

        return var1;
    }

    static void broadcastBodies() {
        List var0 = bodies();
        if (!var0.equals(lastBodies)) {
            lastBodies = var0;
            PossessionNet.sendAll(new PossessionNet.BodyMap(var0));
        }
    }

    static void refreshNames(ServerPlayer var0) {
        var0.refreshDisplayName();
        var0.refreshTabListName();
    }

    static PossessionNet.State stateFor(PossessionData.Rec var0, UUID var1, long var2) {
        boolean var4 = var1.equals(var0.sukuna);

        int var5 = switch (var0.phase) {
            case TRAPPED -> var4 ? 3 : 4;
            case POSSESSING -> var4 ? 5 : 7;
            case CONTRACT -> var4 ? 6 : 7;
            case FINGER -> var4 ? 1 : 0;
            case BATTLE -> 2;
        };
        boolean var6 = false;
        if (var0.phase == PossessionData.Phase.FINGER) {
            Possession.Sight var7 = SIGHT.get(var0.fingerId);
            var6 = var7 == null || var2 - var7.tick > 3L;
        }

        return new PossessionNet.State(
            var5,
            var4 ? var0.hostName : var0.sukunaName,
            (float)var0.progress,
            (int)Math.max(0L, var0.battleEnd - var2),
            var0.phase == PossessionData.Phase.BATTLE && resonance(var0, var2),
            var4,
            var0.watch,
            var0.lending,
            var0.hostUsesBorrowed,
            var0.phase == PossessionData.Phase.CONTRACT ? (int)Math.max(0L, var0.contractUntil - var2) : 0,
            (int)Math.max(0L, var0.contractCooldownUntil - var2),
            var6
        );
    }

    static void pushState(PossessionData.Rec var0) {
        long var1 = now();
        ServerPlayer var3 = online(var0.sukuna);
        if (var3 != null) {
            PossessionNet.send(var3, stateFor(var0, var0.sukuna, var1));
        }

        if (var0.host != null && var0.phase != PossessionData.Phase.FINGER) {
            ServerPlayer var4 = online(var0.host);
            if (var4 != null) {
                PossessionNet.send(var4, stateFor(var0, var0.host, var1));
            }
        }
    }

    static void title(ServerPlayer var0, Component var1, Component var2) {
        if (var0 != null && var0.connection != null) {
            var0.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            var0.connection.send(new ClientboundSetSubtitleTextPacket(var2));
            var0.connection.send(new ClientboundSetTitleTextPacket(var1));
        }
    }

    public static Map<UUID, PossessionData.Rec> debugRecs() {
        PossessionData var0 = data();
        return var0 == null ? Map.of() : var0.recs;
    }

    public static Map<UUID, CompoundTag> debugPending() {
        PossessionData var0 = data();
        return var0 == null ? Map.of() : var0.pending;
    }

    private static record Sight(Entity entity, long tick, String dim, Vec3 pos) {
    }
}
