package com.rpgtemplate.forge.king;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;


import net.neoforged.fml.common.EventBusSubscriber;
import com.rpgtemplate.forge.skill.TargetUtil;
import com.rpgtemplate.forge.system.SystemDataManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityEvent.Size;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(
    modid = "rpgtemplate"
)
public final class ExtraHands {
    private static final String ROOT = "rpgtemplate_four_hands";
    private static final Map<UUID, ExtraHands.Mining> MINING = new HashMap<>();
    private static final Map<UUID, Boolean> FORMS = new HashMap<>();
    private static final Map<UUID, Long> RATE = new HashMap<>();

    public static CompoundTag store(Player var0) {
        CompoundTag var1 = var0.getPersistentData();
        if (!var1.contains("PlayerPersisted")) {
            var1.put("PlayerPersisted", new CompoundTag());
        }

        CompoundTag var2 = var1.getCompound("PlayerPersisted");
        if (!var2.contains("rpgtemplate_four_hands")) {
            var2.put("rpgtemplate_four_hands", new CompoundTag());
        }

        return var2.getCompound("rpgtemplate_four_hands");
    }

    public static ItemStack get(Player var0, int var1) {
        return ItemStack.parseOptional(var0.registryAccess(), store(var0).getCompound("hand" + var1));
    }

    private static void set(Player var0, int var1, ItemStack var2) {
        store(var0).put("hand" + var1, var2.saveOptional(var0.registryAccess()));
    }

    public static boolean leased(Player var0) {
        return store(var0).contains("lease");
    }

    public static void release(ServerPlayer var0) {
        finishUse(var0, false);
    }

    private static void finishUse(ServerPlayer var0, boolean var1) {
        MINING.remove(var0.getUUID());
        CompoundTag var2 = store(var0);
        if (var2.contains("lease")) {
            if (var1) {
                var0.releaseUsingItem();
            } else {
                var0.stopUsingItem();
            }

            int var3 = var2.getInt("leaseSlot");
            int var4 = var2.getInt("leaseHand");
            if (var3 >= 0 && var3 < 9) {
                ItemStack var5 = var0.getInventory().getItem(var3);
                ItemStack var6 = ItemStack.parseOptional(var0.registryAccess(), var2.getCompound("lease"));
                ExtraCooldown.end(var0, var4);
                set(var0, var4, var5);
                var0.getInventory().setItem(var3, var6);
                var2.remove("lease");
                var2.remove("leaseSlot");
                var2.remove("leaseHand");
                var0.inventoryMenu.broadcastChanges();
                sync(var0);
            } else {
                var2.remove("lease");
            }
        }
    }

    private static void begin(ServerPlayer var0, int var1) {
        CompoundTag var2 = store(var0);
        ItemStack var3 = get(var0, var1);
        int var4 = var0.getInventory().selected;
        var2.put("lease", var0.getMainHandItem().saveOptional(var0.registryAccess()));
        var2.putInt("leaseSlot", var4);
        var2.putInt("leaseHand", var1);
        ExtraCooldown.begin(var0, var1, var3);
        set(var0, var1, ItemStack.EMPTY);
        var0.setItemInHand(InteractionHand.MAIN_HAND, var3);
        var0.inventoryMenu.broadcastChanges();
    }


    private static void applyMainHandModifiers(ServerPlayer p, ItemStack stack, boolean add) {
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
            var inst = p.getAttribute(attr);
            if (inst != null) {
                if (add) {
                    inst.removeModifier(mod.id());
                    inst.addTransientModifier(mod);
                } else {
                    inst.removeModifier(mod.id());
                }
            }
        });
    }

    private static void withHand(ServerPlayer var0, int var1, Runnable var2) {
        if (!leased(var0) && !var0.isUsingItem()) {
            ItemStack var3 = var0.getMainHandItem();
            ItemStack var4 = get(var0, var1);
            applyMainHandModifiers(var0, var3, false);
            applyMainHandModifiers(var0, var4, true);
            begin(var0, var1);

            try {
                var2.run();
            } finally {
                ExtraHands.Mining var7 = MINING.get(var0.getUUID());
                applyMainHandModifiers(var0, var4, false);
                release(var0);
                if (var7 != null) {
                    MINING.put(var0.getUUID(), var7);
                }

                applyMainHandModifiers(var0, var3, true);
                var0.inventoryMenu.broadcastChanges();
            }
        }
    }

    private static boolean eligible(ServerPlayer var0) {
        return KingRules.active(var0) && var0.isAlive() && !var0.isSpectator();
    }

    public static void input(ServerPlayer var0, int var1, int var2, boolean var3) {
        if ((var1 == 2 || var1 == 3) && !var3) {
            finishUse(var0, var1 == 3);
        } else {
            long var4 = var0.serverLevel().getGameTime();
            long var6 = RATE.getOrDefault(var0.getUUID(), -4611686018427387904L);
            if (var4 - var6 >= 2L) {
                RATE.put(var0.getUUID(), var4);
                if (var1 == 5) {
                    sync(var0);
                } else if (var1 == 4) {
                    release(var0);
                    store(var0).putInt("selected", var2);
                    sync(var0);
                } else if (var1 == 0) {
                    release(var0);
                    if (var0.isAlive() && !var0.isSpectator()) {
                        ItemStack var8 = get(var0, var2);
                        if (!eligible(var0)) {
                            if (!var8.isEmpty()) {
                                var0.getInventory().add(var8);
                                set(var0, var2, var8);
                            }

                            sync(var0);
                        } else {
                            KingRules.stop(var0, "king.basket", true);
                            ItemStack var9 = var0.getMainHandItem();
                            var0.setItemInHand(InteractionHand.MAIN_HAND, var8);
                            set(var0, var2, var9);
                            var0.inventoryMenu.broadcastChanges();
                            sync(var0);
                        }
                    }
                } else if (eligible(var0) && var0.containerMenu == var0.inventoryMenu) {
                    KingRules.stop(var0, "king.basket", true);
                    if (var1 == 2) {
                        if (leased(var0) || var0.isUsingItem()) {
                            return;
                        }

                        BlockHitResult var17 = ray(var0);
                        double var19 = Math.min(Math.min(16.0, var0.entityInteractionRange()), var0.getEyePosition().distanceTo(var17.getLocation()));
                        List var11 = TargetUtil.ray(var0, var19, 1, 0.2);
                        if (!var11.isEmpty()) {
                            Entity var12 = ((TargetUtil.Hit)var11.get(0)).entity();
                            if (!var0.canInteractWithEntity(var12, 0.0) || !var0.hasLineOfSight(var12)) {
                                return;
                            }

                            if (var12 instanceof Player var13 && !var0.canHarmPlayer(var13)) {
                                return;
                            }

                            withHand(var0, var2, () -> var0.attack(var12));
                            sync(var0);
                        } else if (var17.getType() == Type.BLOCK) {
                            BlockPos var21 = var17.getBlockPos();
                            if (!var0.serverLevel().mayInteract(var0, var21)) {
                                return;
                            }

                            MINING.put(var0.getUUID(), new ExtraHands.Mining(var2, var21, var0.serverLevel().getBlockState(var21), 0.0F, var4));
                        }
                    } else if (var1 == 3) {
                        if (leased(var0) || var0.isUsingItem()) {
                            return;
                        }

                        begin(var0, var2);

                        try {
                            BlockHitResult var18 = ray(var0);
                            InteractionResult var20 = InteractionResult.PASS;
                            if (var18.getType() == Type.BLOCK && var0.serverLevel().mayInteract(var0, var18.getBlockPos())) {
                                var20 = var0.gameMode.useItemOn(var0, var0.serverLevel(), var0.getMainHandItem(), InteractionHand.MAIN_HAND, var18);
                            }

                            if (!var20.consumesAction()) {
                                var0.gameMode.useItem(var0, var0.serverLevel(), var0.getMainHandItem(), InteractionHand.MAIN_HAND);
                            }
                        } finally {
                            if (!var0.isUsingItem()) {
                                release(var0);
                            }

                            sync(var0);
                        }
                    }
                }
            }
        }
    }

    private static BlockHitResult ray(ServerPlayer var0) {
        Vec3 var1 = var0.getEyePosition();
        return var0.serverLevel()
            .clip(new ClipContext(var1, var1.add(var0.getLookAngle().scale(Math.min(16.0, var0.blockInteractionRange()))), Block.OUTLINE, Fluid.NONE, var0));
    }

    public static KingNetwork.State state(ServerPlayer var0) {
        CompoundTag var1 = store(var0);
        ItemStack var2 = get(var0, 0);
        ItemStack var3 = get(var0, 1);
        if (leased(var0)) {
            int var4 = var1.getInt("leaseSlot");
            if (var4 >= 0 && var4 < 9) {
                if (var1.getInt("leaseHand") == 0) {
                    var2 = var0.getInventory().getItem(var4);
                } else {
                    var3 = var0.getInventory().getItem(var4);
                }
            }
        }

        boolean var5 = eligible(var0);
        return new KingNetwork.State(
            var0.getUUID(),
            var5,
            ((Double)KingConfig.HEIGHT.get()).floatValue(),
            ((Double)KingConfig.WIDTH.get()).floatValue(),
            KingRules.defense(SystemDataManager.raw(var0), "king.basket", var0.serverLevel().getGameTime()),
            var1.getInt("selected"),
            var2,
            var3
        );
    }

    public static void sync(ServerPlayer var0) {
        if (SystemDataManager.available(var0)) {
            KingNetwork.sendTrackingAndSelf(var0, state(var0));
        }
    }

    @SubscribeEvent
    public static void size(Size var0) {
        if (var0.getEntity() instanceof Player var1) {
            boolean var6 = var1.getPersistentData().getBoolean("king.form");
            if (var6) {
                float var3 = var1.getPersistentData().getFloat("king.height");
                float var4 = var1.getPersistentData().getFloat("king.width");
                if (!((double)var3 < 1.8) && !(var3 > 3.0F) && !((double)var4 < 0.4) && !((double)var4 > 1.2)) {
                    float var5 = switch (var0.getPose()) {
                        case STANDING -> var3;
                        case CROUCHING -> var3 - 0.3F;
                        default -> var0.getOldSize().height();
                    };
                    EntityDimensions dims = EntityDimensions.scalable(var4, var5);
                    if (var0.getPose() == Pose.STANDING || var0.getPose() == Pose.CROUCHING) {
                        dims = dims.withEyeHeight(var5 - 0.18F);
                    }
                    var0.setNewSize(dims);
                }
            }
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && SystemDataManager.available(var1)) {
            boolean var9 = eligible(var1);
            Boolean var3 = FORMS.put(var1.getUUID(), var9);
            float var4 = ((Double)KingConfig.HEIGHT.get()).floatValue();
            float var5 = ((Double)KingConfig.WIDTH.get()).floatValue();
            if (var3 == null
                || var3 != var9
                || var1.getPersistentData().getFloat("king.height") != var4
                || var1.getPersistentData().getFloat("king.width") != var5) {
                release(var1);
                var1.getPersistentData().putBoolean("king.form", var9);
                var1.getPersistentData().putFloat("king.height", var4);
                var1.getPersistentData().putFloat("king.width", var5);
                var1.refreshDimensions();
                sync(var1);
                if (!var9) {
                    for (int var6 = 0; var6 < 2; var6++) {
                        ItemStack var7 = get(var1, var6);
                        if (!var7.isEmpty()) {
                            var1.getInventory().add(var7);
                            set(var1, var6, var7);
                        }
                    }

                    sync(var1);
                }
            }

            if (leased(var1)
                && (!var9 || !var1.isUsingItem() || var1.containerMenu != var1.inventoryMenu || var1.getInventory().selected != store(var1).getInt("leaseSlot"))
                )
             {
                release(var1);
            }

            ExtraHands.Mining var10 = MINING.get(var1.getUUID());
            if (var10 != null) {
                BlockHitResult var11 = ray(var1);
                BlockState var8 = var1.serverLevel().getBlockState(var10.pos);
                if (var9
                    && !leased(var1)
                    && !var1.isUsingItem()
                    && var1.containerMenu == var1.inventoryMenu
                    && var11.getType() == Type.BLOCK
                    && var10.pos.equals(var11.getBlockPos())
                    && var8 == var10.state
                    && var1.serverLevel().getGameTime() - var10.lastInput <= 200L) {
                    withHand(var1, var10.hand, () -> {
                        float var3x = var10.progress + var8.getDestroyProgress(var1, var1.serverLevel(), var10.pos);
                        if (!(var3x >= 1.0F) && !var1.getAbilities().instabuild) {
                            MINING.put(var1.getUUID(), new ExtraHands.Mining(var10.hand, var10.pos, var8, var3x, var10.lastInput));
                        } else {
                            if (var1.serverLevel().mayInteract(var1, var10.pos)) {
                                var1.gameMode.destroyBlock(var10.pos);
                            }

                            MINING.remove(var1.getUUID());
                        }

                        var1.swing(InteractionHand.MAIN_HAND);
                    });
                } else {
                    MINING.remove(var1.getUUID());
                }
            }

            if (var1.tickCount % 20 == 0 && (var9 || var3 == null)) {
                sync(var1);
            }
        }
    }

    @SubscribeEvent(
        priority = EventPriority.HIGHEST
    )
    public static void death(LivingDeathEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            release(var1);
        }
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && !var1.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            for (int var4 = 0; var4 < 2; var4++) {
                ItemStack var3 = get(var1, var4);
                set(var1, var4, ItemStack.EMPTY);
                if (!var3.isEmpty() && !EnchantmentHelper.has(var3, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    var0.getDrops().add(new ItemEntity(var1.level(), var1.getX(), var1.getY(), var1.getZ(), var3));
                }
            }

            return;
        }
    }

    @SubscribeEvent
    public static void clone(Clone var0) {
        var0.getEntity().getPersistentData().put("PlayerPersisted", var0.getOriginal().getPersistentData().getCompound("PlayerPersisted").copy());
    }

    @SubscribeEvent
    public static void login(PlayerLoggedInEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            release(var1);
            sync(var1);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerLoggedOutEvent var0) {
        if (var0.getEntity() instanceof ServerPlayer var1) {
            release(var1);
        }

        UUID var3 = var0.getEntity().getUUID();
        MINING.remove(var3);
        FORMS.remove(var3);
        RATE.remove(var3);
    }

    @SubscribeEvent
    public static void tracking(StartTracking var0) {
        if (var0.getEntity() instanceof ServerPlayer var1 && var0.getTarget() instanceof ServerPlayer var2 && SystemDataManager.available(var2)) {
            KingNetwork.sendToPlayer(var1, state(var2));
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent var0) {
        MINING.clear();
        FORMS.clear();
        RATE.clear();
    }

    private static record Mining(int hand, BlockPos pos, BlockState state, float progress, long lastInput) {
    }
}
