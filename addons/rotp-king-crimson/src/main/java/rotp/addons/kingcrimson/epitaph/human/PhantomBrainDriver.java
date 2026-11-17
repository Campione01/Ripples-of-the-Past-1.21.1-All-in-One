package rotp.addons.kingcrimson.epitaph.human;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;
import rotp.core.api.entity.MobAiPause;

/**
 * Body to mind, a thought, mind to body (EPITAPH-ARCH 3.1). The body is a player and moves as one, from the same
 * three things a client sends of its keys: forward and sideways, a jump, a sprint. They are set before the tick of
 * the body, which then moves by the physics of a player.
 */
public final class PhantomBrainDriver {
    /** What a creature goes in a tick, on the ground, for each unit of the speed it has under a rider who rides it on. */
    private static final double RIDDEN_PACE = 0.98D / (1.0D - 0.6D * 0.91D);
    private static final double JUMP = 0.42D;
    private static final double GRAVITY = 0.08D;
    private static final double DRAG = 0.98D;
    /** What a stroke of the paddles adds to a boat in a tick, and what water leaves of its pace in a tick. */
    private static final double PADDLE = 0.04D;
    private static final double WATER_GRIP = 0.9D;
    private static final double MAX_GRIP = 0.98D;
    /** Degrees a paddled boat turns in a tick. */
    private static final float BOAT_TURN = 10.0F;

    private PhantomBrainDriver() {}

    // The mount goes where its rider wants to go, at the pace it has under him, and stands when he wants to stand. A
    // creature with a player on it does not move by its own tick (the game leaves that to the client of its rider): it is
    // moved here, in the tick of its rider, as an entity moves, with a fall of its own.
    private static void ride(EpitaphPlayerPhantom body, PhantomBrain mind, Mob mount, boolean moving) {
        float yaw = mind.getYRot();
        double pace = 0.0D;
        if (moving) {
            mount.setYRot(yaw);
            mount.setYBodyRot(yaw);
            mount.setYHeadRot(yaw);
            pace = RIDDEN_PACE * ((LivingStateAccessor) mount).kingCrimson$riddenSpeed(body);
        }
        body.mountFall = mount.onGround() ? moving && mind.wantsJump() ? JUMP : -GRAVITY : (body.mountFall - GRAVITY) * DRAG;
        mount.move(MoverType.SELF, new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD) * pace, body.mountFall, Mth.cos(yaw * Mth.DEG_TO_RAD) * pace));
        mount.positionRider(body);
    }

    // A boat answers to the paddles of its rider (the ruling after stage G). With a player in it a boat is moved by his
    // client and stands in its own tick: it is moved here, in the tick of its rider. It turns to where he wants to go and
    // goes the way it points, at the pace where a stroke and the grip of what it floats or lies on balance.
    private static void sail(EpitaphPlayerPhantom body, PhantomBrain mind, Boat boat, boolean moving) {
        double pace = 0.0D;
        if (moving) {
            boat.setYRot(Mth.approachDegrees(boat.getYRot(), mind.getYRot(), BOAT_TURN));
            double grip = boat.isInWater() ? WATER_GRIP : boat.getGroundFriction();
            pace = PADDLE / (1.0D - Math.min(grip, MAX_GRIP));
        }
        float yaw = boat.getYRot();
        body.mountFall = boat.isInWater() ? 0.0D : boat.onGround() ? -GRAVITY : (body.mountFall - GRAVITY) * DRAG;
        boat.move(MoverType.SELF, new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD) * pace, body.mountFall, Mth.cos(yaw * Mth.DEG_TO_RAD) * pace));
        boat.positionRider(body);
    }

    /** Before the tick of a human phantom: its mind thinks, and its body is given what the mind wants. */
    public static void steer(EpitaphPlayerPhantom body) {
        PhantomBrain mind = body.mind();
        Entity mount = body.mount();
        if (mount != null && body.getVehicle() != mount) {
            // He is off it: it thinks for itself again.
            if (mount instanceof Mob free) {
                MobAiPause.end(free);
            }
            body.unseat();
            mount = null;
        }
        if (mind == null) {
            return;
        }
        mind.think();
        boolean moving = mind.zza != 0.0F || mind.xxa != 0.0F;
        // On its way it faces where it goes; standing, what it looks at.
        body.setYRot(moving ? mind.getYRot() : mind.getYHeadRot());
        body.setXRot(mind.getXRot());
        if (mount != null) {
            // His keys are for what he rides, as the keys of a rider are (the owner, 2026-11-11).
            body.zza = 0.0F;
            body.xxa = 0.0F;
            body.setSprinting(false);
            body.setJumping(false);
            // A minecart, and whatever else does not answer to him, is left to its own tick.
            if (body.steers() && mount instanceof Mob ridden) {
                ride(body, mind, ridden, moving);
            } else if (body.steers() && mount instanceof Boat boat) {
                sail(body, mind, boat, moving);
            }
            return;
        }
        // A whole key: the tick of a player takes its 0.98 of it, as it does of a client's.
        body.zza = Math.signum(mind.zza);
        body.xxa = Math.signum(mind.xxa);
        body.setSprinting(mind.wantsRun());
        body.setJumping(mind.wantsJump());
    }
}
