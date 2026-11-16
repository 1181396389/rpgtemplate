package com.rpgtemplate.nothingthere;

import com.rpgtemplate.core.data.PlayerSystemData;
import com.rpgtemplate.forge.system.SystemDataManager;
import com.rpgtemplate.forge.util.ItemNbt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = "rpgtemplate")
public final class NtMimicBlade {
    public static final String ID = "nt_mimic_blade";
    public static final String OWNER_KEY = "nt.bound_owner";
    public static final String HITS_KEY = "nt.hits";

    public static final double DODGE_BASE = 0.05;
    public static final double DODGE_PER_HIT = 0.01;
    public static final double DODGE_CAP = 0.40;
    public static final double PARRY_CHANCE = 0.35;
    static final double LIBERATED_DODGE_PARRY = NtMimicCombat.LIBERATED_DODGE_PARRY;

    public static final DeferredRegister<Item> REGISTER =
            DeferredRegister.create(BuiltInRegistries.ITEM, "rpgtemplate");

    public static final DeferredHolder<Item, Item> ITEM =
            REGISTER.register(ID, Blade::new);

    static final class BladeTier implements Tier {
        static final BladeTier INSTANCE = new BladeTier();

        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return Tiers.DIAMOND.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return Tiers.DIAMOND.getAttackDamageBonus();
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return Tiers.DIAMOND.getEnchantmentValue();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Tiers.DIAMOND.getRepairIngredient();
        }
    }

    public static final class Blade extends SwordItem {
        public Blade() {
            super(BladeTier.INSTANCE, new Item.Properties()
                .stacksTo(1)
                .attributes(SwordItem.createAttributes(BladeTier.INSTANCE, 36, -2.0f)));
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (player instanceof ServerPlayer sp) {
                NtMimicCombat.use(sp, player.isShiftKeyDown());
            }
            return InteractionResultHolder.consume(stack);
        }
    }

    private NtMimicBlade() {
    }

    public static void init() {
    }

    public static boolean isBlade(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ITEM.get();
    }

    static boolean isNothingThere(Player player) {
        if (!SystemDataManager.available(player)) {
            return false;
        }
        PlayerSystemData data = SystemDataManager.get(player);
        return data.isActivated() && NtBody.TEMPLATE_ID.equals(data.loadedTemplateId());
    }

    static boolean existsAnywhere(ServerLevel level) {
        AABB everywhere = new AABB(-3.0E7, -2048.0, -3.0E7, 3.0E7, 2048.0, 3.0E7);
        if (!level.getEntitiesOfClass(ItemEntity.class, everywhere, e -> isBlade(e.getItem())).isEmpty()) {
            return true;
        }
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (isBlade(inv.getItem(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void tryDrop(ServerPlayer victim, ServerPlayer hunter) {
        if (hunter == victim || !isNothingThere(victim)) {
            return;
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (existsAnywhere(level)) {
            return;
        }
        ItemEntity drop = new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(ITEM.get()));
        level.addFreshEntity(drop);
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        if (!isBlade(stack) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String me = player.getUUID().toString();
        String owner = ItemNbt.getString(stack, OWNER_KEY);
        if (owner.isEmpty()) {
            ItemNbt.putString(stack, OWNER_KEY, me);
        } else if (!owner.equals(me)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        String me = victim.getUUID().toString();
        event.getDrops().removeIf(d -> isBlade(d.getItem())
                && me.equals(ItemNbt.getString(d.getItem(), OWNER_KEY)));
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (NtMimicCombat.ticking || !(event.getSource().getEntity() instanceof ServerPlayer hunter)) {
            return;
        }
        ItemStack held = hunter.getItemInHand(InteractionHand.MAIN_HAND);
        if (!isBlade(held)) {
            return;
        }
        ItemNbt.putInt(held, HITS_KEY, ItemNbt.getInt(held, HITS_KEY) + 1);
    }

    @SubscribeEvent
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        ItemStack held = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!isBlade(held)) {
            return;
        }
        boolean lib = NtMimicCombat.liberated(victim);
        int hits = ItemNbt.getInt(held, HITS_KEY);
        double dodge = lib ? LIBERATED_DODGE_PARRY : Math.min(DODGE_CAP, DODGE_BASE + hits * DODGE_PER_HIT);
        double parry = lib ? LIBERATED_DODGE_PARRY : PARRY_CHANCE;
        boolean dodged = victim.getRandom().nextDouble() < dodge;
        boolean parried = victim.getRandom().nextDouble() < parry;
        if (dodged || parried) {
            event.setCanceled(true);
        }
    }
}
