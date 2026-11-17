package rotp.addons.stickyfingers.state;

import javax.annotation.Nullable;

import rotp.addons.stickyfingers.StickyFingersAddon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * What of its user's work goes on with Sticky Fingers recalled (the owner's rule of 2026-11-05), told to his client so that
 * the HUD can mark the slot of the skill that made it: the zippers on blocks, and the body parts taken off. The data of
 * both lives on the server only, so the server works the bits out again once a second.
 */
public final class RecallSign {
    public static final int ZIPPERS = 1;
    public static final int PARTS = 2;

    private RecallSign() {}

    public static void update(ServerPlayer player) {
        int sign = 0;
        if (StickyFingersAddon.holdsStickyFingers(player)) {
            ServerLevel level = player.serverLevel();
            if (ZipperLeaseData.get(level).ownsAny(player.getUUID())) {
                sign |= ZIPPERS;
            }
            if (ZipperCombatData.get(level).ownsParts(player.getUUID())) {
                sign |= PARTS;
            }
        }
        if (bits(player) == sign) {
            return;
        }
        if (sign == 0) {
            player.removeData(StickyFingersAddon.RECALL_SIGN);
        }
        else {
            player.setData(StickyFingersAddon.RECALL_SIGN, sign);
        }
    }

    public static boolean has(@Nullable LivingEntity user, int bit) {
        return (bits(user) & bit) != 0;
    }

    private static int bits(@Nullable LivingEntity user) {
        Integer sign = user != null
                ? user.getExistingDataOrNull(StickyFingersAddon.RECALL_SIGN)
                : null;
        return sign != null ? sign : 0;
    }
}
