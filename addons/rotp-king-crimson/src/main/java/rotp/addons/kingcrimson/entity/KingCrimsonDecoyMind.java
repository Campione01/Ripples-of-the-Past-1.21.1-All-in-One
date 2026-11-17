package rotp.addons.kingcrimson.entity;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * What a Time Erase's decoy does by itself. The owner, 2026-11-07: "時刪者留下的假人ai別忘了也要優化一下，可以參考roundabout的。
 * 但是我們的ai不打傷害，只做逃跑或拉扯", on top of his earlier "記下當下操作，自己演" and "不戰鬥，只移動". The idea of choosing at the
 * press by what the user was doing, and of a path in place of a blind key, is that of Roundabout's King Crimson clone
 * (its design only: see SOURCE_AND_LICENSE.md); this decoy never fights.
 * <ul>
 * <li>Nothing threatens it: it goes on with the keys its user held, round what would stop them, and stands rather
 * than walk off a cliff or into a hazard.</li>
 * <li>Its user was fighting: it kites, facing its threat inside a band of distance, stepping aside, in and out.</li>
 * <li>Its user had the threat behind him, was backing away or was low on health, or it was hit twice: it flees.</li>
 * </ul>
 * The mind only says where to go and where to look; the decoy's body acts that out with a player's keys.
 */
public final class KingCrimsonDecoyMind {
    public enum Mode {
        /** No threat: the kept keys. */
        CARRY_ON,
        /** 拉扯: keeps its threat at a distance, without ever striking. */
        KITE,
        /** 逃跑. */
        FLEE,
        /** Nothing left to do: it stands and looks around. */
        IDLE
    }

    private static final double THREAT_RANGE = 30.0D;
    private static final double THREAT_LOST_RANGE = 40.0D;
    /** How long ago a blow still means its user was fighting. */
    private static final int RECENT_FIGHT_TICKS = 200;
    private static final float LOW_HEALTH = 0.3F;
    private static final double KITE_NEAR = 4.0D;
    private static final double KITE_FAR = 7.0D;
    private static final double KITE_CHASE = 12.0D;
    private static final int HITS_TO_FLEE = 2;
    private static final double PATH_AHEAD = 20.0D;
    private static final int MAX_DETOURS = 4;

    private final KingCrimsonTimeEraseDecoyEntity decoy;
    private Mode mode = Mode.CARRY_ON;
    @Nullable
    private LivingEntity threat;
    private int hitsTaken;
    private boolean keptSprint;
    // Where its user looked and where his keys took him at the press: it keeps to that, whatever turned it since.
    private float homeYaw;
    private Vec3 homeHeading = Vec3.ZERO;

    // What the body is to act out this tick.
    private boolean keptKeys = true;
    private Vec3 travel = Vec3.ZERO;
    private boolean turnsView;
    private float viewYaw;
    private float viewPitch;
    private float turnRate;
    private boolean jumps;
    private boolean sprints;

    private boolean detouring;
    private int detours;
    private int strafe = 1;
    private int strafeTicks;
    private int dartTicks;
    private boolean dartingIn;
    private int repathTicks;
    private int glanceTicks;
    private int nextGlance = 40;
    private int idleTicks;
    private float idleYaw;

    KingCrimsonDecoyMind(KingCrimsonTimeEraseDecoyEntity decoy) {
        this.decoy = decoy;
    }

    public Mode mode() {
        return mode;
    }

    @Nullable
    public LivingEntity threat() {
        return threat;
    }

    boolean usesKeptKeys() {
        return keptKeys;
    }

    Vec3 travel() {
        return travel;
    }

    boolean turnsView() {
        return turnsView;
    }

    float viewYaw() {
        return viewYaw;
    }

    float viewPitch() {
        return viewPitch;
    }

    float turnRate() {
        return turnRate;
    }

    boolean jumps() {
        return jumps;
    }

    boolean sprints() {
        return sprints;
    }

    /** What its user was doing when he erased time decides what it does. */
    void chooseAtThePress(ServerPlayer user) {
        keptSprint = user.isSprinting();
        homeYaw = decoy.getYRot();
        homeHeading = decoy.keptHeading();
        threat = lastFought(user);
        if (threat == null) {
            threat = nearestTargeting(user, user);
        }
        if (threat == null) {
            mode = Mode.CARRY_ON;
            return;
        }
        Vec3 toThreat = threat.position().subtract(user.position());
        boolean behind = user.getLookAngle().multiply(1.0D, 0.0D, 1.0D).dot(toThreat.multiply(1.0D, 0.0D, 1.0D)) < 0.0D;
        boolean backing = decoy.keptForward() < 0.0F;
        boolean low = user.getHealth() <= user.getMaxHealth() * LOW_HEALTH;
        mode = behind || backing || low ? Mode.FLEE : Mode.KITE;
    }

    // What he last hit, else what last hit him, lately and near.
    @Nullable
    private LivingEntity lastFought(ServerPlayer user) {
        LivingEntity hit = KingCrimsonAddon.temporalState(user).lastHit(user.level().getGameTime(), RECENT_FIGHT_TICKS);
        if (isThreat(hit, user)) {
            return hit;
        }
        LivingEntity hitBy = user.getLastHurtByMob();
        if (hitBy != null && user.tickCount - user.getLastHurtByMobTimestamp() <= RECENT_FIGHT_TICKS) {
            LivingEntity body = StandUtil.getStandUser(hitBy);
            if (isThreat(body, user)) {
                return body;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity nearestTargeting(LivingEntity target, Entity around) {
        List<Mob> hunters = around.level().getEntitiesOfClass(Mob.class, around.getBoundingBox().inflate(THREAT_RANGE),
                mob -> mob.isAlive() && mob.getTarget() == target && mob != decoy);
        Mob nearest = null;
        for (Mob hunter : hunters) {
            if (nearest == null || hunter.distanceToSqr(around) < nearest.distanceToSqr(around)) {
                nearest = hunter;
            }
        }
        return nearest;
    }

    private boolean isThreat(@Nullable LivingEntity candidate, Entity around) {
        return candidate != null && candidate.isAlive() && candidate != decoy && candidate != decoy.getOwner()
                && !(candidate instanceof KingCrimsonTimeEraseDecoyEntity)
                && candidate.level() == around.level()
                && candidate.distanceToSqr(around) <= THREAT_RANGE * THREAT_RANGE;
    }

    /** A blow landed on the decoy. Twice, or once too many for its health, and it runs. */
    void onHit(@Nullable Entity attacker) {
        hitsTaken++;
        LivingEntity body = attacker instanceof LivingEntity living ? StandUtil.getStandUser(living) : null;
        if (isThreat(body, decoy)) {
            threat = body;
        }
        if (threat == null) {
            return;
        }
        if (mode == Mode.CARRY_ON || mode == Mode.IDLE) {
            mode = Mode.KITE;
        }
        if (mode == Mode.KITE
                && (hitsTaken >= HITS_TO_FLEE || decoy.getHealth() <= decoy.getMaxHealth() * LOW_HEALTH)) {
            mode = Mode.FLEE;
            repathTicks = 0;
        }
    }

    /** Once a tick, before the body moves. */
    void think() {
        keptKeys = false;
        travel = Vec3.ZERO;
        turnsView = false;
        jumps = false;
        sprints = false;
        PathNavigation navigation = decoy.getNavigation();
        // A decoy outside the stopped area still ticks, but its erase is frozen: it waits for its user.
        if (decoy.isOwnerErasePaused()) {
            navigation.stop();
            return;
        }
        if (decoy.tickCount % 10 == 0) {
            lookForThreats();
        }
        switch (mode) {
            case CARRY_ON -> carryOn(navigation);
            case KITE -> kite(navigation);
            case FLEE -> flee(navigation);
            case IDLE -> idle(navigation);
        }
    }

    private void lookForThreats() {
        if (threat != null && (!threat.isAlive() || threat.level() != decoy.level()
                || threat.distanceToSqr(decoy) > THREAT_LOST_RANGE * THREAT_LOST_RANGE)) {
            threat = null;
        }
        if (threat == null) {
            threat = nearestTargeting(decoy, decoy);
            if (threat == null) {
                if (mode == Mode.KITE || mode == Mode.FLEE) {
                    mode = Mode.IDLE;
                }
            } else if (mode == Mode.CARRY_ON || mode == Mode.IDLE) {
                mode = Mode.KITE;
            }
        }
    }

    private void carryOn(PathNavigation navigation) {
        Vec3 heading = homeHeading;
        if (heading == Vec3.ZERO) {
            keptKeys = true;
            return;
        }
        if (detouring) {
            if (!navigation.isDone()) {
                steerAlongPath(decoy.keptForward() > 0.0F && decoy.keptLeft() == 0.0F, 15.0F);
                sprints = keptSprint;
                return;
            }
            detouring = false;
        }
        // A sneaking player stops at a ledge and bumps at a wall; the body's own rules do that.
        if (!decoy.isShiftKeyDown() && decoy.onGround()
                && (decoy.isStoppedAhead(heading) || decoy.isUnsafeAhead(heading))) {
            if (detours < MAX_DETOURS && detourAlong(navigation, heading)) {
                detours++;
                detouring = true;
                return;
            }
            // No way on: it stands rather than bump at a wall or walk off.
            navigation.stop();
            mode = Mode.IDLE;
            return;
        }
        if (Math.abs(Mth.wrapDegrees(decoy.getYRot() - homeYaw)) > 0.5F) {
            // Round the obstacle: back to where he looked, still going his way.
            travel = heading;
            turnsView = true;
            viewYaw = homeYaw;
            viewPitch = decoy.getXRot();
            turnRate = 15.0F;
            sprints = keptSprint;
            return;
        }
        keptKeys = true;
    }

    // A path to a point further along its user's heading, round what the kept key would bump at.
    private boolean detourAlong(PathNavigation navigation, Vec3 heading) {
        Vec3 goal = decoy.position().add(heading.scale(PATH_AHEAD));
        Path path = navigation.createPath(goal.x, goal.y, goal.z, 1);
        if (path == null || path.getNodeCount() < 2) {
            return false;
        }
        Node end = path.getEndNode();
        Vec3 leadsTo = new Vec3(end.x + 0.5D, end.y, end.z + 0.5D).subtract(decoy.position());
        // It must lead on along his way, not just to the foot of the wall.
        return heading.dot(leadsTo) >= 2.5D && navigation.moveTo(path, 1.0D);
    }

    private void kite(PathNavigation navigation) {
        LivingEntity target = threat;
        if (target == null) {
            mode = Mode.IDLE;
            return;
        }
        Vec3 to = new Vec3(target.getX() - decoy.getX(), 0.0D, target.getZ() - decoy.getZ());
        double distance = to.length();
        face(target, 30.0F);
        if (distance < 1.0E-3D) {
            return;
        }
        RandomSource random = decoy.getRandom();
        if (distance > KITE_CHASE) {
            // Too far for a fight: it closes in, round whatever is in the way.
            if (--repathTicks <= 0 || navigation.isDone()) {
                repathTicks = 20;
                navigation.moveTo(target, 1.0D);
            }
            steerAlongPath(false, 30.0F);
            sprints = true;
            return;
        }
        navigation.stop();
        if (--strafeTicks <= 0) {
            strafe = -strafe;
            strafeTicks = 15 + random.nextInt(30);
        }
        if (--dartTicks <= 0) {
            dartingIn = !dartingIn;
            dartTicks = dartingIn ? 8 + random.nextInt(8) : 30 + random.nextInt(40);
        }
        Vec3 toward = to.scale(1.0D / distance);
        Vec3 radial = toward.scale(distance < KITE_NEAR ? -1.0D : distance > KITE_FAR ? 1.0D : dartingIn ? 0.6D : -0.15D);
        Vec3 aside = new Vec3(-toward.z, 0.0D, toward.x).scale(0.8D * strafe);
        Vec3 wish = radial.add(aside).normalize();
        if (isOpen(wish)) {
            travel = wish;
        } else {
            // A wall, a drop or a hazard on that side: the other side, or only in or out.
            strafe = -strafe;
            strafeTicks = 15 + random.nextInt(30);
            Vec3 other = radial.subtract(aside).normalize();
            Vec3 straight = radial.normalize();
            travel = isOpen(other) ? other : isOpen(straight) ? straight : Vec3.ZERO;
        }
        jumps = decoy.onGround() && random.nextInt(35) == 0;
        // The look of a fight; nothing is struck.
        if (distance < 5.0D && random.nextInt(12) == 0) {
            decoy.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void flee(PathNavigation navigation) {
        LivingEntity from = threat;
        if (from == null) {
            navigation.stop();
            mode = Mode.IDLE;
            return;
        }
        if (--repathTicks <= 0 || navigation.isDone() || ranPastItsWay(navigation)) {
            repathTicks = 30;
            pickWayOut(navigation, from);
        }
        if (!navigation.isDone()) {
            steerAlongRun(navigation);
        } else {
            Vec3 away = new Vec3(decoy.getX() - from.getX(), 0.0D, decoy.getZ() - from.getZ());
            if (away.lengthSqr() > 1.0E-6D && isOpen(away.normalize())) {
                travel = away.normalize();
                turnsView = true;
                viewYaw = yawOf(travel);
                viewPitch = 0.0F;
                turnRate = 25.0F;
            }
        }
        sprints = true;
        RandomSource random = decoy.getRandom();
        // As a player runs: jumping on, where the way ahead is flat and free.
        jumps = decoy.onGround() && travel != Vec3.ZERO && decoy.isFreeAhead(travel, 3.0D) && random.nextInt(4) != 0;
        // And looking back now and then, without turning from his way: the keys change, the direction does not.
        if (glanceTicks > 0) {
            glanceTicks--;
            if (travel != Vec3.ZERO) {
                float back = yawOf(new Vec3(from.getX() - decoy.getX(), 0.0D, from.getZ() - decoy.getZ()));
                float along = yawOf(travel);
                turnsView = true;
                viewYaw = along + Mth.clamp(Mth.wrapDegrees(back - along), -110.0F, 110.0F);
                viewPitch = 0.0F;
                turnRate = 45.0F;
            }
        } else if (--nextGlance <= 0) {
            glanceTicks = 8;
            nextGlance = 50 + random.nextInt(50);
        }
    }

    // The way that ends furthest from the threat, among a fan of ways away from it.
    private void pickWayOut(PathNavigation navigation, LivingEntity from) {
        float away = yawOf(new Vec3(decoy.getX() - from.getX(), 0.0D, decoy.getZ() - from.getZ()));
        Path best = null;
        double bestScore = from.distanceToSqr(decoy) + 4.0D;
        for (int i = 0; i < 7; i++) {
            float yaw = away + (i - 3) * 25.0F;
            Vec3 goal = decoy.position().add(directionOf(yaw).scale(14.0D));
            Path path = navigation.createPath(goal.x, goal.y, goal.z, 1);
            if (path == null || path.getNodeCount() < 2) {
                continue;
            }
            Node end = path.getEndNode();
            double score = from.distanceToSqr(end.x + 0.5D, end.y, end.z + 0.5D) - Math.abs(i - 3);
            if (score > bestScore) {
                bestScore = score;
                best = path;
            }
        }
        if (best != null) {
            navigation.moveTo(best, 1.0D);
        } else {
            navigation.stop();
        }
    }

    private void idle(PathNavigation navigation) {
        navigation.stop();
        if (--idleTicks <= 0) {
            RandomSource random = decoy.getRandom();
            idleTicks = 40 + random.nextInt(40);
            idleYaw = decoy.getYRot() + (random.nextBoolean() ? 1.0F : -1.0F) * (30.0F + random.nextInt(60));
        }
        turnsView = true;
        viewYaw = idleYaw;
        viewPitch = 0.0F;
        turnRate = 8.0F;
    }

    // A runner is in the air most of the time, and the navigation follows its path on the ground only: left to it, a
    // decoy that has jumped past a point of its way turns back for it, towards what it runs from. So the points it has
    // passed are taken off its way here, and it heads for the next one itself.
    private void steerAlongRun(PathNavigation navigation) {
        Path path = navigation.getPath();
        if (path == null || path.isDone()) {
            return;
        }
        while (path.getNextNodeIndex() < path.getNodeCount() - 1
                && isPast(path.getEntityPosAtNode(decoy, path.getNextNodeIndex()),
                        path.getEntityPosAtNode(decoy, path.getNextNodeIndex() + 1))) {
            path.advance();
        }
        Vec3 next = path.getNextEntityPos(decoy);
        Vec3 to = new Vec3(next.x - decoy.getX(), 0.0D, next.z - decoy.getZ());
        if (to.lengthSqr() < 1.0E-4D) {
            return;
        }
        travel = to.normalize();
        turnsView = true;
        viewYaw = yawOf(travel);
        viewPitch = 0.0F;
        turnRate = 25.0F;
    }

    // Its way has one point left and it has run past that too: a new way is due.
    private boolean ranPastItsWay(PathNavigation navigation) {
        Path path = navigation.getPath();
        if (path == null || path.isDone() || path.getNodeCount() < 2 || path.getNextNodeIndex() != path.getNodeCount() - 1) {
            return false;
        }
        Vec3 last = path.getEntityPosAtNode(decoy, path.getNodeCount() - 1);
        Vec3 before = path.getEntityPosAtNode(decoy, path.getNodeCount() - 2);
        return isPast(last, last.add(last.subtract(before)));
    }

    // Whether the decoy is beyond a point of its way, on the side its way goes on to.
    private boolean isPast(Vec3 point, Vec3 onward) {
        return (onward.x - point.x) * (decoy.getX() - point.x) + (onward.z - point.z) * (decoy.getZ() - point.z) > 0.0D;
    }

    // The navigation names the next point of its path to the move control; the body walks there on a player's keys.
    private void steerAlongPath(boolean turning, float rate) {
        MoveControl control = decoy.getMoveControl();
        if (!control.hasWanted()) {
            return;
        }
        Vec3 to = new Vec3(control.getWantedX() - decoy.getX(), 0.0D, control.getWantedZ() - decoy.getZ());
        if (to.lengthSqr() < 1.0E-4D) {
            return;
        }
        travel = to.normalize();
        if (turning) {
            turnsView = true;
            viewYaw = yawOf(travel);
            viewPitch = 0.0F;
            turnRate = rate;
        }
    }

    private void face(LivingEntity target, float rate) {
        Vec3 to = target.getEyePosition().subtract(decoy.getEyePosition());
        turnsView = true;
        viewYaw = yawOf(to);
        viewPitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        turnRate = rate;
    }

    private boolean isOpen(Vec3 direction) {
        return !decoy.isStoppedAhead(direction) && !decoy.isUnsafeAhead(direction);
    }

    private static float yawOf(Vec3 direction) {
        return (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static Vec3 directionOf(float yaw) {
        return new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0.0D, Mth.cos(yaw * Mth.DEG_TO_RAD));
    }
}
