package rotp.addons.kingcrimson.epitaph.human.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomBrain;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * What a human phantom gets out of the way of, before anything else it wants: a creeper that swells and primed TNT,
 * which it runs from until it is out of their blast; a shot that is going to meet it, whose line it steps out of; a
 * block that is falling on it, from under which it steps.
 */
public final class PhantomAvoidHarmGoal extends Goal {
    /** A blast harms within twice its power; a creeper's power is 3 (6 when charged), that of TNT is 4. */
    private static final double CREEPER_BLAST = 6.0D;
    private static final double TNT_BLAST = 8.0D;
    private static final double BLAST_MARGIN = 1.5D;
    /** How many places away from a blast the mind looks at before it runs. */
    private static final int WAYS_TRIED = 5;
    /** How many ticks ahead a shot is followed along its flight, and how near the body that flight counts as a hit. */
    private static final int SHOT_TICKS = 20;
    private static final double SHOT_NEAR = 0.9D;
    /** What a shot keeps of its speed each tick in the air. */
    private static final double SHOT_DRAG = 0.99D;
    private static final double SIDE_STEP = 2.5D;
    private static final int LOOK_AGAIN = 4;

    private final PhantomBrain mind;
    private final SandboxRoom room;
    @Nullable
    private Vec3 wayOut;
    private int wait;

    public PhantomAvoidHarmGoal(PhantomBrain mind, SandboxRoom room) {
        this.mind = mind;
        this.room = room;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        wayOut = wayOut();
        return wayOut != null;
    }

    @Override
    public boolean canContinueToUse() {
        return wayOut != null;
    }

    @Override
    public void start() {
        wait = LOOK_AGAIN;
        go();
    }

    @Override
    public void stop() {
        wayOut = null;
        mind.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (--wait <= 0 || mind.getNavigation().isDone()) {
            wait = LOOK_AGAIN;
            Vec3 chosen = wayOut;
            wayOut = wayOut();
            if (wayOut != null && wayOut != chosen) {
                go();
            }
        }
    }

    private void go() {
        if (wayOut != null) {
            mind.getNavigation().moveTo(wayOut.x, wayOut.y, wayOut.z, PhantomBrain.RUN);
        }
    }

    // Where to go to be out of the nearest harm, null when nothing threatens.
    @Nullable
    private Vec3 wayOut() {
        EpitaphPlayerPhantom body = mind.body();
        if (body == null) {
            return null;
        }
        Vec3 at = body.position();
        for (Entity other : room.phantoms()) {
            if (other == body || other.isRemoved()) {
                continue;
            }
            if (other instanceof Creeper creeper && (creeper.getSwellDir() > 0 || creeper.isIgnited())) {
                double blast = (creeper.isPowered() ? 2.0D : 1.0D) * CREEPER_BLAST + BLAST_MARGIN;
                if (creeper.distanceToSqr(body) < blast * blast) {
                    return awayFrom(creeper.position(), at, blast);
                }
            } else if (other instanceof PrimedTnt tnt) {
                double blast = TNT_BLAST + BLAST_MARGIN;
                if (tnt.distanceToSqr(body) < blast * blast) {
                    return awayFrom(tnt.position(), at, blast);
                }
            } else if (other instanceof FallingBlockEntity block) {
                double dx = at.x - block.getX();
                double dz = at.z - block.getZ();
                if (block.getY() > at.y + 1.0D && dx * dx + dz * dz < 1.0D) {
                    Vec3 aside = dx * dx + dz * dz > 1.0E-4D ? new Vec3(dx, 0.0D, dz).normalize() : body.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                    return at.add(aside.scale(SIDE_STEP));
                }
            } else if (other instanceof Projectile shot && shot.getOwner() != body) {
                Vec3 aside = outOfTheLine(shot, body);
                if (aside != null) {
                    return at.add(aside.scale(SIDE_STEP));
                }
            }
        }
        return null;
    }

    // A run away from a blast is run to its end: a new place every few ticks would turn the mind about on the spot. Of the
    // places it finds, the nearest that is out of the blast; if none is, the farthest from it; else straight away.
    private Vec3 awayFrom(Vec3 harm, Vec3 at, double blast) {
        if (wayOut != null && !mind.getNavigation().isDone() && wayOut.distanceToSqr(harm) > at.distanceToSqr(harm)) {
            return wayOut;
        }
        Vec3 best = null;
        for (int tried = 0; tried < WAYS_TRIED; tried++) {
            Vec3 found = DefaultRandomPos.getPosAway(mind, 12, 5, harm);
            if (found != null && (best == null || better(found, best, harm, at, blast))) {
                best = found;
            }
        }
        if (best != null && best.distanceToSqr(harm) > at.distanceToSqr(harm)) {
            return best;
        }
        Vec3 away = at.subtract(harm).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() > 1.0E-4D ? away.normalize() : mind.body().getLookAngle().multiply(-1.0D, 0.0D, -1.0D);
        return at.add(away.scale(blast));
    }

    private static boolean better(Vec3 found, Vec3 than, Vec3 harm, Vec3 at, double blast) {
        boolean out = found.distanceToSqr(harm) >= blast * blast;
        if (out != than.distanceToSqr(harm) >= blast * blast) {
            return out;
        }
        return out ? found.distanceToSqr(at) < than.distanceToSqr(at) : found.distanceToSqr(harm) > than.distanceToSqr(harm);
    }

    // The side to step to when this shot, flown on as it flies (it slows a little and falls), is going to pass through
    // the body; null when it is not.
    @Nullable
    private static Vec3 outOfTheLine(Projectile shot, EpitaphPlayerPhantom body) {
        Vec3 speed = shot.getDeltaMovement();
        if (speed.lengthSqr() < 0.01D) {
            return null;
        }
        double beside = SHOT_NEAR - body.getBbWidth() / 2.0D;
        AABB near = body.getBoundingBox().inflate(beside, 0.3D, beside);
        Vec3 from = shot.position();
        double fall = shot.getGravity();
        for (int tick = 0; tick < SHOT_TICKS; tick++) {
            Vec3 to = from.add(speed);
            Vec3 met = near.contains(from) ? from : near.clip(from, to).orElse(null);
            if (met != null) {
                // Across its flight, to the side the body already is; dead on, to the left of the shot.
                Vec3 across = new Vec3(-speed.z, 0.0D, speed.x);
                if (across.lengthSqr() < 1.0E-8D) {
                    // It comes straight down: any side will do.
                    across = body.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
                }
                if (across.lengthSqr() < 1.0E-8D) {
                    return null;
                }
                across = across.normalize();
                double side = body.getBoundingBox().getCenter().subtract(from).dot(across);
                return side < -1.0E-3D ? across.scale(-1.0D) : across;
            }
            from = to;
            speed = speed.scale(SHOT_DRAG).add(0.0D, -fall, 0.0D);
        }
        return null;
    }
}
