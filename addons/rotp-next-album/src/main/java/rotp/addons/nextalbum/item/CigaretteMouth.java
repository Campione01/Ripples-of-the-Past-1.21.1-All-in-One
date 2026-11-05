package rotp.addons.nextalbum.item;

import rotp.addons.nextalbum.network.CigaretteMouthPayload;
import rotp.addons.nextalbum.state.CigaretteFocus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Next Album 0.1.13 CigaretteMouth: up to five lit cigarettes held in the
 * mouth; they burn at the idle rate, give 80 Focus ticks each every
 * second and are gone when burned out.
 */
public final class CigaretteMouth {
    public static final int MAX_MOUTH = 5;
    public static final String LIST_KEY = "RotpNextAlbumMouthCigs";
    private static final int PASSIVE_INTERVAL = 20;
    private static final int PASSIVE_FOCUS_PER_CIG = 80;

    private CigaretteMouth() {}

    public static int getCount(Player player) {
        return getList(player).size();
    }

    public static boolean add(Player player, ItemStack stack) {
        if (player == null || !SpiritCigaretteItem.isCigarette(stack)
                || getCount(player) >= MAX_MOUTH) {
            return false;
        }
        ListTag list = getList(player);
        CompoundTag entry = new CompoundTag();
        entry.putFloat("Burn", SpiritCigaretteItem.getBurn(stack));
        list.add(entry);
        saveList(player, list);
        stack.shrink(1);
        sync(player);
        if (getCount(player) >= MAX_MOUTH
                && player instanceof ServerPlayer serverPlayer) {
            SpiritCigaretteItem.award(serverPlayer, "jotaro_trick");
        }
        return true;
    }

    public static void tick(Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        ListTag list = getList(player);
        if (list.isEmpty()) {
            return;
        }
        ListTag next = new ListTag();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            float burn = entry.getFloat("Burn")
                    + SpiritCigaretteItem.BASE_BURN_PER_TICK;
            if (burn >= 1.0F) {
                removed = true;
            } else {
                entry.putFloat("Burn", burn);
                next.add(entry);
            }
        }
        saveList(player, next);
        if (removed) {
            player.level().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                    0.25F, 1.4F);
            sync(player);
        }
        if (!next.isEmpty() && player.tickCount % PASSIVE_INTERVAL == 0) {
            CigaretteFocus.addFocus(
                    player, PASSIVE_FOCUS_PER_CIG * next.size());
        }
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            CigaretteMouthPayload.sendToTrackingAndSelf(
                    serverPlayer, getCount(player));
        }
    }

    public static ListTag copyList(Player player) {
        return getList(player).copy();
    }

    public static void setList(Player player, ListTag list) {
        if (list.isEmpty()) {
            player.getPersistentData().remove(LIST_KEY);
        } else {
            saveList(player, list);
        }
    }

    private static ListTag getList(Player player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(LIST_KEY, Tag.TAG_LIST)
                ? data.getList(LIST_KEY, Tag.TAG_COMPOUND)
                : new ListTag();
    }

    private static void saveList(Player player, ListTag list) {
        player.getPersistentData().put(LIST_KEY, list);
    }
}
