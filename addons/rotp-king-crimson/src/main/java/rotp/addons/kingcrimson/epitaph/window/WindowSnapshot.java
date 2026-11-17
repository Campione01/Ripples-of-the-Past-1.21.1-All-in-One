package rotp.addons.kingcrimson.epitaph.window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import rotp.addons.kingcrimson.mixin.epitaph.CooldownInstanceAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.ItemCooldownsAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;
import rotp.core.api.entity.EntityStateRestore;

/**
 * A participant of a window as he was at the press (EPITAPH-ARCH 3.5). His saved data whole, attachments and the data of
 * other mods with it, taken and put back by the helper of the core, which keeps the Stand he has out; and what saved
 * data does not hold: what he rides, how far his arm has swung back, and for a player the stack on his cursor, the
 * crafting grid of his inventory and his item cooldowns.
 */
public final class WindowSnapshot {
    private final EntityStateRestore.Snapshot state;
    @Nullable
    private final Entity vehicle;
    private final int attackStrengthTicker;
    private final ItemStack carried;
    private final List<ItemStack> grid = new ArrayList<>();
    private final Map<Item, Integer> itemCooldowns = new HashMap<>();

    private WindowSnapshot(Entity entity) {
        state = EntityStateRestore.capture(entity);
        vehicle = entity.getVehicle();
        attackStrengthTicker = entity instanceof LivingEntity living
                ? ((LivingStateAccessor) living).kingCrimson$attackStrengthTicker() : 0;
        if (entity instanceof ServerPlayer player) {
            carried = player.inventoryMenu.getCarried().copy();
            CraftingContainer craft = player.inventoryMenu.getCraftSlots();
            for (int slot = 0; slot < craft.getContainerSize(); slot++) {
                grid.add(craft.getItem(slot).copy());
            }
            cooldownsOf(player, itemCooldowns);
        } else {
            carried = ItemStack.EMPTY;
        }
    }

    public static WindowSnapshot of(Entity entity) {
        return new WindowSnapshot(entity);
    }

    /** The saved data and what the core keeps beside it. */
    EntityStateRestore.Snapshot state() {
        return state;
    }

    /** The item cooldowns of a player, put into the map: for each item, the ticks it still has to wait. */
    public static void cooldownsOf(ServerPlayer player, Map<Item, Integer> left) {
        ItemCooldownsAccessor waits = (ItemCooldownsAccessor) player.getCooldowns();
        for (Map.Entry<Item, Object> entry : waits.kingCrimson$cooldowns().entrySet()) {
            int ticks = ((CooldownInstanceAccessor) entry.getValue()).kingCrimson$endTime() - waits.kingCrimson$tickCount();
            if (ticks > 0) {
                left.put(entry.getKey(), ticks);
            }
        }
    }

    /**
     * The saved data of an entity put back on it by the helper of the core, and then what a load cannot do: an attachment
     * that came to it since, and is no part of that saved data, is taken off.
     */
    static void load(Entity entity, EntityStateRestore.Snapshot state) {
        EntityStateRestore.restore(entity, state);
        CompoundTag then = state.tag().getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        CompoundTag now = entity.saveWithoutId(new CompoundTag()).getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        for (String key : now.getAllKeys()) {
            ResourceLocation id = then.contains(key) ? null : ResourceLocation.tryParse(key);
            AttachmentType<?> type = id != null ? NeoForgeRegistries.ATTACHMENT_TYPES.get(id) : null;
            if (type != null) {
                entity.removeData(type);
            }
        }
    }

    /** One that was dying when its saved data was put back lives again: a load does not say so. */
    static void revive(Entity entity) {
        if (entity instanceof LivingEntity living && living.getHealth() > 0.0F) {
            ((LivingStateAccessor) living).kingCrimson$setDead(false);
            living.deathTime = 0;
            if (living.getPose() == Pose.DYING) {
                living.setPose(Pose.STANDING);
            }
        }
    }

    /** Puts the participant back as he was at the press, on the live entity: it stays in its level, with the Stand it has out. */
    void restore(Entity entity) {
        load(entity, state);
        if (entity instanceof LivingEntity living) {
            ((LivingStateAccessor) living).kingCrimson$setAttackStrengthTicker(attackStrengthTicker);
            // What he began to draw, eat or block with in the window is not carried on: his phantom did none of it.
            living.stopUsingItem();
        }
        revive(entity);
        if (entity.getVehicle() != vehicle) {
            entity.stopRiding();
            if (vehicle != null && !vehicle.isRemoved()) {
                entity.startRiding(vehicle, true);
            }
        }
        if (entity instanceof ServerPlayer player) {
            player.inventoryMenu.setCarried(carried.copy());
            CraftingContainer craft = player.inventoryMenu.getCraftSlots();
            for (int slot = 0; slot < grid.size() && slot < craft.getContainerSize(); slot++) {
                craft.setItem(slot, grid.get(slot).copy());
            }
            ItemCooldowns waits = player.getCooldowns();
            Map<Item, Integer> now = new HashMap<>();
            cooldownsOf(player, now);
            for (Item item : now.keySet()) {
                if (!itemCooldowns.containsKey(item)) {
                    waits.removeCooldown(item);
                }
            }
            for (Map.Entry<Item, Integer> wait : itemCooldowns.entrySet()) {
                if (!wait.getValue().equals(now.get(wait.getKey()))) {
                    waits.addCooldown(wait.getKey(), wait.getValue());
                }
            }
            player.inventoryMenu.broadcastFullState();
        }
    }
}
