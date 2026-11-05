package rotp.addons.darkbluemoon.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.entity.BarnacleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** 1.16 BarnacleBindHandler: which target each user's Barnacle Bind holds, for its cancel. */
public final class BarnacleBinds {
    private static final Map<UUID, UUID> TARGET_TO_USER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> USER_TO_TARGET = new ConcurrentHashMap<>();

    private BarnacleBinds() {}

    public static void bind(LivingEntity user, LivingEntity target) {
        TARGET_TO_USER.put(target.getUUID(), user.getUUID());
        USER_TO_TARGET.put(user.getUUID(), target.getUUID());
    }

    public static void unbind(@Nullable LivingEntity user) {
        if (user != null) {
            UUID targetId = USER_TO_TARGET.remove(user.getUUID());
            if (targetId != null) {
                TARGET_TO_USER.remove(targetId);
            }
        }
    }

    public static boolean isBinding(@Nullable LivingEntity user) {
        return user != null && USER_TO_TARGET.containsKey(user.getUUID());
    }

    /** The bound target within 64 blocks of the user, as 1.16 looked for it. */
    @Nullable
    public static LivingEntity getTarget(@Nullable LivingEntity user, Level level) {
        if (user == null) {
            return null;
        }
        UUID targetId = USER_TO_TARGET.get(user.getUUID());
        if (targetId == null) {
            return null;
        }
        return level.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(64.0)).stream()
                .filter(entity -> entity.getUUID().equals(targetId))
                .findFirst()
                .orElse(null);
    }

    /** Removes the barnacles on the target within 8 blocks and its Barnacle Bind effect. */
    public static void removeAllBarnacles(Level level, @Nullable LivingEntity target) {
        if (level.isClientSide() || target == null) {
            return;
        }
        for (BarnacleEntity barnacle : level.getEntitiesOfClass(BarnacleEntity.class, target.getBoundingBox().inflate(8.0))) {
            if (barnacle.getTarget() == target) {
                barnacle.discard();
            }
        }
        target.removeEffect(DarkBlueMoonAddon.BARNACLE_BIND);
    }

    public static void clear() {
        TARGET_TO_USER.clear();
        USER_TO_TARGET.clear();
    }
}
