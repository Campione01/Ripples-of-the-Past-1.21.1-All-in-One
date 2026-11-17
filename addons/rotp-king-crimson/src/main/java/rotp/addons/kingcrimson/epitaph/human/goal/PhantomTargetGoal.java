package rotp.addons.kingcrimson.epitaph.human.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrain;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * Whom a human phantom fights: only what is a foe of the player it stands for, the nearest of them. For the one who
 * foresees these are the creatures that hunt him and, when it is hostile or a player, what was under his crosshair.
 * Whatever else stands in the room is left alone.
 */
public final class PhantomTargetGoal extends Goal {
    private static final int LOOK_AGAIN = 10;

    private final PhantomBrain mind;
    private final SandboxRoom room;
    private final Predicate<LivingEntity> foe;
    private int wait;

    public PhantomTargetGoal(PhantomBrain mind, SandboxRoom room, Predicate<LivingEntity> foe) {
        this.mind = mind;
        this.room = room;
        this.foe = foe;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (wait-- > 0) {
            return false;
        }
        wait = LOOK_AGAIN;
        return nearest() != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mind.getTarget();
        return target != null && target.isAlive() && !target.isRemoved() && foe.test(target);
    }

    @Override
    public void start() {
        mind.setTarget(nearest());
    }

    @Override
    public void stop() {
        mind.setTarget(null);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (wait-- <= 0) {
            wait = LOOK_AGAIN;
            LivingEntity nearer = nearest();
            if (nearer != null && nearer != mind.getTarget()) {
                mind.setTarget(nearer);
            }
        }
    }

    @Nullable
    private LivingEntity nearest() {
        Entity body = mind.body();
        LivingEntity found = null;
        double best = Double.MAX_VALUE;
        for (Entity phantom : room.phantoms()) {
            if (phantom != body && phantom instanceof LivingEntity living && living.isAlive() && foe.test(living)) {
                double distance = living.distanceToSqr(body);
                if (distance < best) {
                    best = distance;
                    found = living;
                }
            }
        }
        return found;
    }
}
