package rotp.addons.kingcrimson.epitaph.sandbox;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.mixin.epitaph.GoalSelectorAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.WrappedGoalAccessor;

/**
 * The warm start of a copy (EPITAPH-ARCH 7.3, stage G). A copy has fresh goal objects; a goal of its body has counted and
 * chosen things that are in no saved data: when it struck last, when it may try again, where it is going, whom it looks
 * at. For each goal of the body, the goal of the same class on the copy, the first for the first of that class, takes what
 * its twin keeps for itself: numbers, flags and enum values as they are, positions, entities as their phantoms, paths as
 * copies. One that is running runs on from there, without being started again; one that waits goes on waiting (the attack
 * of a zombie that stands at its foe stops between two blows, and what it waits is a field of its own). Anything else a
 * goal holds stays the own of the copy; and a goal that holds an entity which has no phantom is left as it is made.
 */
public final class GoalWarmStart {
    /** The fields of a goal class, its parents included, that are carried; CLOSED for a class that cannot be read. */
    private static final List<Field> CLOSED = new ArrayList<>(0);
    private static final ClassValue<List<Field>> STATE = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            try {
                for (Class<?> owner = type; owner != null && owner != Goal.class && owner != Object.class; owner = owner.getSuperclass()) {
                    for (Field field : owner.getDeclaredFields()) {
                        int modifiers = field.getModifiers();
                        if (!Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers) && carried(field.getType())) {
                            field.setAccessible(true);
                            fields.add(field);
                        }
                    }
                }
            }
            catch (RuntimeException | LinkageError closed) {
                return CLOSED;
            }
            return fields;
        }
    };

    private GoalWarmStart() {}

    // Only kinds of which a copy is safe: a value, or something this class knows how to map or to copy.
    private static boolean carried(Class<?> kind) {
        return kind.isPrimitive() || kind.isEnum() || kind == BlockPos.class || kind == BlockPos.MutableBlockPos.class
                || kind == Vec3.class || kind == Path.class || Entity.class.isAssignableFrom(kind);
    }

    /** What the running goals of a creature keep for themselves, onto the goals of its copy. */
    public static void carry(SandboxRoom room, Mob real, Mob copy) {
        carry(room, real.goalSelector, copy.goalSelector);
        carry(room, real.targetSelector, copy.targetSelector);
    }

    /**
     * Goals are matched by their class and their order among the goals of that class, not by their place in the list: a
     * copy has none of the goals a mod gave its body when it joined the world, and one of its own for its Stand.
     */
    public static void carry(SandboxRoom room, GoalSelector from, GoalSelector to) {
        List<WrappedGoal> theirs = new ArrayList<>(to.getAvailableGoals());
        Map<WrappedGoal, WrappedGoal> running = new IdentityHashMap<>();
        Map<Class<?>, Integer> counted = new HashMap<>();
        for (WrappedGoal wrapped : from.getAvailableGoals()) {
            Class<?> kind = wrapped.getGoal().getClass();
            int nth = counted.merge(kind, 1, Integer::sum) - 1;
            WrappedGoal twin = nthOf(theirs, kind, nth);
            if (twin != null && !twin.isRunning() && copyState(room, wrapped.getGoal(), twin.getGoal()) && wrapped.isRunning()) {
                // Not started: what a start would do it has done long ago, and its fields say where it is now.
                ((WrappedGoalAccessor) twin).kingCrimson$setRunning(true);
                running.put(wrapped, twin);
            }
        }
        // The flags a running goal holds are held by its twin, or the next goal that wants one would start beside it.
        Map<Goal.Flag, WrappedGoal> held = ((GoalSelectorAccessor) to).kingCrimson$lockedFlags();
        ((GoalSelectorAccessor) from).kingCrimson$lockedFlags().forEach((flag, holder) -> {
            WrappedGoal twin = running.get(holder);
            if (twin != null) {
                held.put(flag, twin);
            }
        });
    }

    @Nullable
    private static WrappedGoal nthOf(List<WrappedGoal> goals, Class<?> kind, int nth) {
        int seen = 0;
        for (WrappedGoal wrapped : goals) {
            if (wrapped.getGoal().getClass() == kind && seen++ == nth) {
                return wrapped;
            }
        }
        return null;
    }

    /**
     * The state of one goal onto another of its class. False, and nothing written, when the class cannot be read or the
     * goal holds an entity that has no phantom in this room: half a state would be worse than a fresh one.
     */
    public static boolean copyState(SandboxRoom room, Goal from, Goal to) {
        if (from.getClass() != to.getClass()) {
            return false;
        }
        List<Field> fields = STATE.get(from.getClass());
        if (fields == CLOSED) {
            return false;
        }
        Object[] values = new Object[fields.size()];
        try {
            for (int index = 0; index < values.length; index++) {
                Object value = fields.get(index).get(from);
                if (value instanceof Entity entity) {
                    Entity phantom = room.phantom(entity.getId());
                    if (phantom == null || !fields.get(index).getType().isInstance(phantom)) {
                        return false;
                    }
                    value = phantom;
                } else if (value instanceof Path path) {
                    value = path.copy();
                } else if (value instanceof BlockPos.MutableBlockPos position) {
                    value = position.mutable();
                }
                values[index] = value;
            }
            for (int index = 0; index < values.length; index++) {
                fields.get(index).set(to, values[index]);
            }
        }
        catch (IllegalAccessException | RuntimeException failure) {
            return false;
        }
        return true;
    }
}
