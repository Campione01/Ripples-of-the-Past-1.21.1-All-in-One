package rotp.addons.kingcrimson.state;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What the erasing user is shown of the world outside his erase beside its creatures (EV1105AC section 7, the owner:
 * "時刪時我希望也能看到時刪之外怪物的技能特效或者投射物/方塊實體運動的效果，你看能不能在原有紅暈上實現"). The creatures have their red afterimages
 * already. These rules say which other things get one on the same path, and around what a particle counts as a skill
 * at work; the eraser's client applies them (KingCrimsonEraseOutsideView). Nothing here predicts: it is the world as
 * it runs, seen from outside it.
 */
public final class KingCrimsonEraseOutsideRules {
    /** Ticks a moving thing's afterimage trails it: few, a projectile is gone in a few. */
    public static final int MOVING_DELAY_TICKS = 2;
    /** No more moving things have an afterimage at once: a swarm must not cost the frame. */
    public static final int MAX_MOVING = 96;
    /** Particles the eraser's client lets through in a tick. */
    public static final int PARTICLES_PER_TICK = 48;
    /** How near a creature or a moving thing a particle is a skill's effect, and not the world's own dust. */
    public static final double PARTICLE_REACH = 2.5D;

    private KingCrimsonEraseOutsideRules() {}

    /**
     * Whether the eraser is shown this thing in motion: a projectile of any kind (a Stand's and a mob's skills are
     * projectiles too), primed TNT, an evoker's fangs, a falling block, and whatever else an ability of the mod or of
     * its add-ons puts in the world. Not what lies about (dropped items, experience), not what he threw himself from
     * inside the erased time, and no creature: those have the afterimages the server gives.
     */
    public static boolean isShownMoving(Entity entity, @Nullable Player eraser) {
        if (entity instanceof LivingEntity || entity instanceof KingCrimsonAfterimageEntity
                || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || !entity.isAlive() || isErasersOwn(entity, eraser)) {
            return false;
        }
        return entity instanceof TraceableEntity || entity instanceof FallingBlockEntity || isAbilityEntity(entity);
    }

    /**
     * Whether what happens around this entity is the outside world at work: every creature but the eraser and his
     * Stand, and every moving thing he is shown.
     */
    public static boolean isEffectSource(Entity entity, @Nullable Player eraser) {
        if (entity == eraser || !entity.isAlive() || entity instanceof KingCrimsonAfterimageEntity) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            return eraser == null || StandUtil.getStandUser(living) != eraser;
        }
        return isShownMoving(entity, eraser);
    }

    private static boolean isErasersOwn(Entity entity, @Nullable Player eraser) {
        if (eraser == null || !(entity instanceof TraceableEntity traceable)) {
            return false;
        }
        Entity owner = traceable.getOwner();
        return owner == eraser || owner instanceof LivingEntity living && StandUtil.getStandUser(living) == eraser;
    }

    // An entity of the core or of an add-on that is no creature is what an ability made; the core's own afterimages
    // are a look and no thing.
    private static boolean isAbilityEntity(Entity entity) {
        String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace();
        return (namespace.equals(JojoMod.MOD_ID) || namespace.startsWith("rotp_"))
                && !(entity instanceof rotp.core.customobjects.AfterimageEntity);
    }

    /** One tick's allowance of particles. */
    public static final class Budget {
        private int left = PARTICLES_PER_TICK;

        public void refill() {
            left = PARTICLES_PER_TICK;
        }

        public boolean hasLeft() {
            return left > 0;
        }

        /** Takes one; false when the tick's allowance is spent. */
        public boolean take() {
            if (left <= 0) {
                return false;
            }
            left--;
            return true;
        }
    }
}
