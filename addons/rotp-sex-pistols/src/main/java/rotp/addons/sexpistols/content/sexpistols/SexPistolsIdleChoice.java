package rotp.addons.sexpistols.content.sexpistols;

import net.minecraft.util.RandomSource;

/**
 * Which idle a member shows, as Next Album 0.1.13 SexPistolsEntity picked it: idle or idle2 by the idle variant rolled
 * at each summon (playSummonAnimation), and now and then one of the five idle_random clips (tickIdleRandomAnimation)
 * followed by a short blend back (startRecoveryAnimation(3), tickRecoveryAnimation). The server runs it and syncs the
 * result through the entity's data.
 */
public final class SexPistolsIdleChoice {
    public static final int IDLE_VARIANTS = 2;
    public static final int IDLE_RANDOM_VARIANTS = 5;
    public static final int IDLE_RANDOM_TICKS = 20;
    public static final int IDLE_RECOVERY_TICKS = 7;
    public static final int IDLE_RANDOM_MIN_DELAY = 100;
    public static final int IDLE_RANDOM_EXTRA_DELAY = 200;

    private int idleRandomDelay;

    void onSummon(SexPistolsEntity member, RandomSource random) {
        member.setIdleVariant(random.nextInt(IDLE_VARIANTS));
        member.setIdleRandom(0, member.getIdleRandomVariant());
        member.setIdleRecoveryTicks(0);
        resetDelay(random);
    }

    void tick(SexPistolsEntity member, RandomSource random, boolean idlePose) {
        int randomTicks = member.getIdleRandomTicks();
        if (randomTicks > 0) {
            if (!idlePose) {
                member.setIdleRandom(0, member.getIdleRandomVariant());
                resetDelay(random);
                return;
            }
            member.setIdleRandom(randomTicks - 1, member.getIdleRandomVariant());
            if (randomTicks - 1 <= 0) {
                // 1.16 left the recovery's first tick alone (recoveryAnimationStartTick)
                member.setIdleRecoveryTicks(IDLE_RECOVERY_TICKS);
                resetDelay(random);
            }
            return;
        }
        int recovery = member.getIdleRecoveryTicks();
        if (recovery > 0) {
            member.setIdleRecoveryTicks(idlePose ? recovery - 1 : 0);
            resetDelay(random);
            return;
        }
        if (!idlePose) {
            resetDelay(random);
            return;
        }
        if (idleRandomDelay <= 0) {
            resetDelay(random);
        }
        if (--idleRandomDelay <= 0) {
            member.setIdleRandom(IDLE_RANDOM_TICKS, random.nextInt(IDLE_RANDOM_VARIANTS));
        }
    }

    int delay() {
        return idleRandomDelay;
    }

    private void resetDelay(RandomSource random) {
        idleRandomDelay = IDLE_RANDOM_MIN_DELAY + random.nextInt(IDLE_RANDOM_EXTRA_DELAY + 1);
    }
}
