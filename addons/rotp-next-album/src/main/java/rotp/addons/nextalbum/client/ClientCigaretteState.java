package rotp.addons.nextalbum.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Next Album 0.1.13 ClientCigaretteState: mouth counts and smoke. */
public final class ClientCigaretteState {
    private static final Map<Integer, Integer> MOUTH_COUNTS =
            new HashMap<>();

    private ClientCigaretteState() {}

    public static void handleMouthSync(int entityId, int count) {
        if (count <= 0) {
            MOUTH_COUNTS.remove(entityId);
        } else {
            MOUTH_COUNTS.put(entityId, count);
        }
    }

    public static int getMouthCount(int entityId) {
        Integer count = MOUTH_COUNTS.get(entityId);
        return count == null ? 0 : count;
    }

    public static void handleSmoke(int entityId, int intensity) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null
                || !(level.getEntity(entityId) instanceof LivingEntity living)) {
            return;
        }
        Vec3 look = living.getLookAngle();
        Vec3 mouth = living.getEyePosition(1.0F).add(look.scale(0.35D));
        int count = Math.max(4, Math.min(28, intensity / 3));
        RandomSource random = living.getRandom();
        double spread = 0.04D + intensity * 8.0E-4D;
        for (int i = 0; i < count; i++) {
            level.addParticle(
                    ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    mouth.x, mouth.y - 0.12D, mouth.z,
                    look.x * 0.04D + (random.nextDouble() - 0.5D) * spread,
                    0.02D + random.nextDouble() * 0.04D,
                    look.z * 0.04D + (random.nextDouble() - 0.5D) * spread);
        }
    }

    public static void spawnInhaleSmoke(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || entity == null) {
            return;
        }
        int side = entity.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
            side = -side;
        }
        RandomSource random = entity.getRandom();
        boolean firstPerson = entity == minecraft.player
                && minecraft.options.getCameraType()
                        == CameraType.FIRST_PERSON;
        double px;
        double py;
        double pz;
        double spreadXZ;
        double spreadY;
        if (firstPerson) {
            Vec3 view = entity.getEyePosition(1.0F);
            float yaw = entity.getYRot() * Mth.DEG_TO_RAD;
            float pitch = entity.getXRot() * Mth.DEG_TO_RAD;
            double forwardX = -Mth.sin(yaw) * Mth.cos(pitch);
            double forwardY = -Mth.sin(pitch);
            double forwardZ = Mth.cos(yaw) * Mth.cos(pitch);
            double rightX = -Mth.cos(yaw);
            double rightZ = -Mth.sin(yaw);
            px = view.x + forwardX * 0.45D + rightX * side * 0.22D;
            py = view.y + forwardY * 0.45D - 0.12D;
            pz = view.z + forwardZ * 0.45D + rightZ * side * 0.22D;
            spreadXZ = 0.02D;
            spreadY = 0.05D;
        } else {
            float bodyYaw = entity.yBodyRot * Mth.DEG_TO_RAD;
            double forwardX = -Mth.sin(bodyYaw);
            double forwardZ = Mth.cos(bodyYaw);
            double rightX = -Mth.cos(bodyYaw);
            double rightZ = -Mth.sin(bodyYaw);
            px = entity.getX() + forwardX * 0.55D + rightX * side * -0.08D;
            py = entity.getEyeY() - 0.18D;
            pz = entity.getZ() + forwardZ * 0.55D + rightZ * side * -0.08D;
            spreadXZ = 0.04D;
            spreadY = 0.08D;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(
                    ParticleTypes.SMOKE, px, py, pz,
                    (random.nextDouble() - 0.5D) * spreadXZ,
                    (random.nextDouble() - 0.5D) * spreadY,
                    (random.nextDouble() - 0.5D) * spreadXZ);
        }
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || MOUTH_COUNTS.isEmpty()) {
            return;
        }
        for (Player player : level.players()) {
            if (getMouthCount(player.getId()) > 0
                    && player.tickCount % 15 == 0) {
                Vec3 look = player.getLookAngle();
                Vec3 mouth = player.getEyePosition(1.0F)
                        .add(look.scale(0.28D));
                level.addParticle(
                        ParticleTypes.SMOKE,
                        mouth.x, mouth.y - 0.18D, mouth.z,
                        look.x * 0.01D, 0.015D, look.z * 0.01D);
            }
        }
    }

    public static void clear() {
        MOUTH_COUNTS.clear();
    }
}
