package rotp.addons.survivor.event;

import java.util.List;

import rotp.addons.survivor.SurvivorAddon;
import rotp.addons.survivor.util.AngerHelper;
import rotp.addons.survivor.util.WetHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/** An angry wet player near a Survivor user hits a random creature within 4 blocks instead of the one aimed at. */
public final class PlayerAttackHandler {
    private static final double BASE_SURVIVOR_RANGE = 8.0;
    private static final double RAIN_SURVIVOR_RANGE = 12.0;
    private static final double TARGET_RANGE = 4.0;
    private static boolean isAttacking = false;

    private PlayerAttackHandler() {}

    static void onPlayerAttack(AttackEntityEvent event) {
        if (isAttacking) {
            return;
        }
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!WetHelper.isWet(player) || !AngerHelper.isAngry(player)) {
            return;
        }
        if (SurvivorAddon.isSurvivorUser(player)) {
            return;
        }
        double survivorRange = player.level().isRaining() ? RAIN_SURVIVOR_RANGE : BASE_SURVIVOR_RANGE;
        boolean nearSurvivor = !player.level().getEntitiesOfClass(
                Player.class, player.getBoundingBox().inflate(survivorRange), SurvivorAddon::isSurvivorUser).isEmpty();
        if (!nearSurvivor) {
            return;
        }
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
                LivingEntity.class, player.getBoundingBox().inflate(TARGET_RANGE), e -> e != player && e.isAlive());
        if (!targets.isEmpty()) {
            LivingEntity newTarget = targets.get(player.level().random.nextInt(targets.size()));
            event.setCanceled(true);
            isAttacking = true;
            try {
                player.attack(newTarget);
            } finally {
                isAttacking = false;
            }
        }
    }
}
