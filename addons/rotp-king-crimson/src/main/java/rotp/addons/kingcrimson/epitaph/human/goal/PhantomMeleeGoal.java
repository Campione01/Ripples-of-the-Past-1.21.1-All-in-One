package rotp.addons.kingcrimson.epitaph.human.goal;

import java.util.EnumSet;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrain;

/**
 * A human phantom fights as a player without a bow does: it goes up to its foe, running while that is far, and
 * strikes with what it holds when the foe is in reach and its arm is ready again. The blow is the player's own
 * attack, with the weapon, the critical hit, the sweep and the enchantments of the body.
 */
public final class PhantomMeleeGoal extends Goal {
    /** It stops this close to its foe: nearer, the two only push each other. */
    private static final double STAND_OFF = 2.0D;
    private static final double RUN_BEYOND = 6.0D;

    private final PhantomBrain mind;
    private int repath;

    public PhantomMeleeGoal(PhantomBrain mind) {
        this.mind = mind;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mind.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        mind.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mind.getTarget();
        EpitaphPlayerPhantom body = mind.body();
        if (target == null || body == null) {
            return;
        }
        mind.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = body.distanceTo(target);
        if (--repath <= 0) {
            repath = 4 + mind.getRandom().nextInt(7);
            if (distance > STAND_OFF) {
                mind.getNavigation().moveTo(target, distance > RUN_BEYOND ? PhantomBrain.RUN : PhantomBrain.WALK);
            } else {
                mind.getNavigation().stop();
            }
        }
        if (body.canInteractWithEntity(target, 0.0D) && body.getAttackStrengthScale(0.5F) >= 1.0F
                && mind.getSensing().hasLineOfSight(target)) {
            body.swing(InteractionHand.MAIN_HAND, true);
            body.attack(target);
        }
    }
}
