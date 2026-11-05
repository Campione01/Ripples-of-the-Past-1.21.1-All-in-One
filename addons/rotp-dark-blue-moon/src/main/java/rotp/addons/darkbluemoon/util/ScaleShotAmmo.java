package rotp.addons.darkbluemoon.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * 1.16 ScaleShotAmmoHandler: server-side scales per player, 20 at most, 5 back every 100 ticks. Concurrent maps: a
 * single-player client reads them through the HUD condition checks while the server thread writes them.
 */
public final class ScaleShotAmmo {
    private static final Map<UUID, Integer> AMMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> REGEN_TIMER = new ConcurrentHashMap<>();

    private ScaleShotAmmo() {}

    private static int maxAmmo() {
        return DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.SCALE_SHOT_MAX_AMMO);
    }

    public static int getAmmo(Player player) {
        return AMMO.getOrDefault(player.getUUID(), maxAmmo());
    }

    public static boolean consumeAmmo(Player player, int amount) {
        int current = getAmmo(player);
        if (current < amount) {
            return false;
        }
        AMMO.put(player.getUUID(), current - amount);
        return true;
    }

    public static void addAmmo(Player player, int amount) {
        AMMO.put(player.getUUID(), Math.min(getAmmo(player) + amount, maxAmmo()));
    }

    public static Component noAmmoMessage() {
        return Component.translatable("message.darkbluemoon.no_scales").withStyle(ChatFormatting.GRAY);
    }

    public static void tickRegen(Player player) {
        UUID id = player.getUUID();
        int timer = REGEN_TIMER.getOrDefault(id, 0) + 1;
        if (timer >= DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.SCALE_SHOT_REGEN_INTERVAL)) {
            timer = 0;
            addAmmo(player, DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.SCALE_SHOT_REGEN_AMOUNT));
        }
        REGEN_TIMER.put(id, timer);
    }

    public static void clear() {
        AMMO.clear();
        REGEN_TIMER.clear();
    }
}
