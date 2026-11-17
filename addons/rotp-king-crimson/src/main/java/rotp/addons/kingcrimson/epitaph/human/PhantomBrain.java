package rotp.addons.kingcrimson.epitaph.human;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomAvoidHarmGoal;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomMeleeGoal;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomStandPowerGoal;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomTargetGoal;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomActions;
import rotp.addons.kingcrimson.epitaph.sandbox.SandboxRoom;

/**
 * The mind of a human phantom (EPITAPH-ARCH 3.1): a creature that senses, picks goals and finds paths as a mob does,
 * and is never an entity of any world. Nothing ticks it and nothing can look it up. Each tick of the room it is put
 * where its body stands, thinks once, and its body is then steered by what it wants: a turn, a step, a run, a jump.
 * The paths are a mob's, for a body of a player's size; the movement is the player's own.
 */
public final class PhantomBrain extends PathfinderMob {
    /** The speeds a goal asks the navigation for: the body walks at the one and sprints at the other. */
    public static final double WALK = 1.0D;
    public static final double RUN = 1.3D;

    @Nullable
    private EpitaphPlayerPhantom body;

    public PhantomBrain(EntityType<? extends PhantomBrain> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /** Gives a human phantom its mind: it flees what would harm it, and fights what this rule calls a foe of its player. */
    @Nullable
    public static PhantomBrain give(SandboxRoom room, EpitaphPlayerPhantom body, Predicate<LivingEntity> foe) {
        PhantomBrain mind = KingCrimsonAddon.PHANTOM_BRAIN_ENTITY.get().create(room.level());
        if (mind == null) {
            return null;
        }
        mind.body = body;
        mind.goalSelector.addGoal(0, new FloatGoal(mind));
        mind.goalSelector.addGoal(1, new PhantomAvoidHarmGoal(mind, room));
        mind.goalSelector.addGoal(2, new PhantomStandPowerGoal(mind, body));
        mind.goalSelector.addGoal(3, new PhantomMeleeGoal(mind));
        mind.targetSelector.addGoal(1, new PhantomTargetGoal(mind, room, foe));
        body.mind(mind);
        PhantomActions.handOverToMind(room, body);
        return mind;
    }

    /** The body this mind steers. */
    public EpitaphPlayerPhantom body() {
        return body;
    }

    /** One thought: the mind stands where its body stands, senses, chooses, finds its way and wants a move. */
    void think() {
        EpitaphPlayerPhantom of = body;
        if (of == null) {
            return;
        }
        // A rider thinks from where his mount stands: his way is the way it will go.
        Entity at = of.isPassenger() ? of.getRootVehicle() : of;
        setPos(at.getX(), at.getY(), at.getZ());
        setDeltaMovement(at.getDeltaMovement());
        setOnGround(at.onGround());
        setYRot(of.getYRot());
        setXRot(of.getXRot());
        yHeadRot = of.getYHeadRot();
        yBodyRot = of.getYRot();
        tickCount = of.tickCount;
        setHealth(Math.max(1.0F, Math.min(of.getHealth(), getMaxHealth())));
        updateInWaterStateAndDoFluidPushing();
        serverAiStep();
    }

    boolean wantsJump() {
        return jumping;
    }

    boolean wantsRun() {
        return zza > 0.0F && getMoveControl().getSpeedModifier() > WALK;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
