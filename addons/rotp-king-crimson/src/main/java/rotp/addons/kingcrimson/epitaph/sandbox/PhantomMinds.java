package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;
import rotp.addons.kingcrimson.mixin.epitaph.MobMindAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.PathNavigationAccessor;

/**
 * What a creature has in mind that its saved data does not hold, put onto its copy: whom it is after, the path it
 * walks, and the memories of its brain, each with the phantoms in the place of the real entities; and what its running
 * goals keep for themselves (GoalWarmStart).
 */
public final class PhantomMinds {
    private PhantomMinds() {}

    public static void carry(SandboxRoom room, Entity real, Entity copy) {
        if (real instanceof Mob realMob && copy instanceof Mob copyMob) {
            Entity target = phantomOf(room, realMob.getTarget());
            ((MobMindAccessor) copyMob).kingCrimson$setTargetField(target instanceof LivingEntity living ? living : null);
            Path path = realMob.getNavigation().getPath();
            if (path != null && !path.isDone()) {
                copyMob.getNavigation().moveTo(path.copy(),
                        ((PathNavigationAccessor) realMob.getNavigation()).kingCrimson$speedModifier());
            }
            // Its running goals go on from where they are; the raised arms a start gave it are no saved data either.
            GoalWarmStart.carry(room, realMob, copyMob);
            copyMob.setAggressive(realMob.isAggressive());
        }
        if (real instanceof LivingEntity realLiving && copy instanceof LivingEntity copyLiving) {
            Entity avenged = phantomOf(room, realLiving.getLastHurtByMob());
            if (avenged instanceof LivingEntity living) {
                copyLiving.setLastHurtByMob(living);
            }
            memories(room, realLiving.getBrain(), copyLiving.getBrain());
        }
    }

    /** The phantom that stands for a real entity in this room, null when it has none. */
    @Nullable
    public static Entity phantomOf(SandboxRoom room, @Nullable Entity real) {
        return real == null ? null : room.phantom(real.getId());
    }

    @SuppressWarnings("unchecked")
    private static void memories(SandboxRoom room, Brain<?> from, Brain<?> to) {
        for (Map.Entry<MemoryModuleType<?>, Optional<? extends ExpirableValue<?>>> memory : from.getMemories().entrySet()) {
            if (memory.getValue().isEmpty() || !to.getMemories().containsKey(memory.getKey())) {
                continue;
            }
            ExpirableValue<?> held = memory.getValue().get();
            Object value = carried(room, held.getValue());
            if (value == null) {
                continue;
            }
            MemoryModuleType<Object> type = (MemoryModuleType<Object>) memory.getKey();
            if (held.canExpire()) {
                to.setMemoryWithExpiry(type, value, held.getTimeToLive());
            } else {
                to.setMemory(type, value);
            }
        }
    }

    // A value of a kind that could hold an entity and is not known here is left out: the sensors make it again.
    @Nullable
    private static Object carried(SandboxRoom room, Object value) {
        if (value instanceof Entity entity) {
            return phantomOf(room, entity);
        }
        if (value instanceof UUID uuid) {
            UUID phantom = room.uuids().phantomOf(uuid);
            return phantom != null ? phantom : uuid;
        }
        if (value instanceof WalkTarget walk) {
            PositionTracker target = tracker(room, walk.getTarget());
            return target != null ? new WalkTarget(target, walk.getSpeedModifier(), walk.getCloseEnoughDist()) : null;
        }
        if (value instanceof PositionTracker tracker) {
            return tracker(room, tracker);
        }
        if (value instanceof Path path) {
            return path.copy();
        }
        if (value instanceof BlockPos || value instanceof GlobalPos || value instanceof Number || value instanceof Boolean
                || value instanceof Unit) {
            return value;
        }
        return null;
    }

    @Nullable
    private static PositionTracker tracker(SandboxRoom room, PositionTracker tracker) {
        if (tracker instanceof EntityTracker entity) {
            Entity phantom = phantomOf(room, entity.getEntity());
            return phantom != null ? new EntityTracker(phantom, true) : null;
        }
        return tracker instanceof BlockPosTracker ? tracker : null;
    }
}
