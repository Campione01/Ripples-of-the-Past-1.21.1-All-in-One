package rotp.addons.bastet;

import java.util.UUID;

import rotp.addons.bastet.block.BastetOutletBlock;
import rotp.addons.bastet.magnet.MagnetForceHandler;
import rotp.addons.bastet.magnet.MagnetizedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = BastetAddon.MOD_ID)
public final class BastetEvents {
    /** 1.16 BastetOutletTriggerHandler: touching an outlet hurts for 5 (magic) once per outlet and magnetizes. */
    public static final float OUTLET_DAMAGE = 5.0F;
    public static final double OUTLET_TRIGGER_RADIUS = 1.0D;
    private static final String OUTLET_HURT_PREFIX = "BastetOutletHurt_";
    /** Marks the armor the magnetism cursed, so that only that armor gets its Curse of Binding back off. */
    public static final String ARMOR_LOCK_TAG = "BastetMagnetLock";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    public static final int ITEM_RECOVERY_INTERVAL_TICKS = 20;
    public static final int RELEASED_ITEM_LIFESPAN = 6000;
    public static final int RELEASED_ITEM_PICKUP_DELAY = 10;

    private BastetEvents() {}

    // 1.16 LivingUpdateEvent, which ran before the entity's own tick
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) {
            return;
        }
        checkOutletTouch(entity);
        if (entity instanceof Mob && MagnetizedData.isMagnetized(entity)) {
            lockArmor(entity);
        }
        MagnetizedData.tick(entity);
    }

    // 1.16 BastetOutletTriggerHandler: any creature within a block of an outlet
    private static void checkOutletTouch(LivingEntity entity) {
        Level level = entity.level();
        AABB area = entity.getBoundingBox().inflate(OUTLET_TRIGGER_RADIUS);
        BlockPos min = BlockPos.containing(area.minX, area.minY, area.minZ);
        BlockPos max = BlockPos.containing(area.maxX, area.maxY, area.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockState(pos).getBlock() instanceof BastetOutletBlock)) {
                continue;
            }
            CompoundTag nbt = entity.getPersistentData();
            String key = OUTLET_HURT_PREFIX + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            if (!nbt.getBoolean(key)) {
                entity.hurt(entity.damageSources().magic(), OUTLET_DAMAGE);
                nbt.putBoolean(key, true);
            }
            if (!MagnetizedData.isMagnetized(entity)) {
                MagnetizedData.magnetize(entity);
            }
            return;
        }
    }

    // 1.16 MagnetArmorLockHandler: a magnetized player's iron armor gets Curse of Binding, and loses it again after
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (MagnetizedData.isMagnetized(player)) {
            lockArmor(player);
        }
        else {
            unlockArmor(player);
        }
    }

    // a mob's armor is never unlocked, as in 1.16
    private static void lockArmor(LivingEntity entity) {
        Level level = entity.level();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (MagnetForceHandler.isIronArmorItem(stack)
                    && MagnetForceHandler.getEnchantLevel(level, stack, Enchantments.BINDING_CURSE) <= 0) {
                stack.enchant(MagnetForceHandler.enchantment(level, Enchantments.BINDING_CURSE), 1);
                CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ARMOR_LOCK_TAG, true));
                entity.setItemSlot(slot, stack);
            }
        }
    }

    /*
     * 1.16 took Curse of Binding off every worn piece of armor of every player who was not magnetized, the vanilla curse
     * included; only the curse the magnetism put on comes off here.
     */
    private static void unlockArmor(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !isMagnetLocked(stack)) {
                continue;
            }
            ItemStack copy = stack.copy();
            EnchantmentHelper.updateEnchantments(copy,
                    enchantments -> enchantments.removeIf(holder -> holder.is(Enchantments.BINDING_CURSE)));
            CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.remove(ARMOR_LOCK_TAG));
            player.setItemSlot(slot, copy);
        }
    }

    private static boolean isMagnetLocked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(ARMOR_LOCK_TAG);
    }

    // 1.16 MagnetItemRecoveryHandler: once a second, items whose holder is gone or no longer magnetized fall again
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.getGameTime() % ITEM_RECOVERY_INTERVAL_TICKS != 0L) {
            return;
        }
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity item)) {
                continue;
            }
            CompoundTag nbt = item.getPersistentData();
            if (!nbt.hasUUID(MagnetForceHandler.ITEM_OWNER_TAG)) {
                continue;
            }
            UUID ownerId = nbt.getUUID(MagnetForceHandler.ITEM_OWNER_TAG);
            Entity owner = level.getEntity(ownerId);
            boolean ownerValid = owner instanceof LivingEntity living
                    && living.isAlive() && MagnetizedData.isMagnetized(living);
            if (!ownerValid) {
                item.setNoGravity(false);
                item.lifespan = RELEASED_ITEM_LIFESPAN;
                item.setPickUpDelay(RELEASED_ITEM_PICKUP_DELAY);
                nbt.remove(MagnetForceHandler.ITEM_OWNER_TAG);
                nbt.remove(MagnetForceHandler.ITEM_HURT_TAG);
                item.hurtMarked = true;
            }
        }
    }
}
