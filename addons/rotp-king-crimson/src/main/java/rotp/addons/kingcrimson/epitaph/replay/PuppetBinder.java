package rotp.addons.kingcrimson.epitaph.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.core.api.entity.MobAiPause;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * The bodies of one prediction that are carried along their tracks. A bound body does not tick: its tick event is
 * cancelled, a rider's as a walker's (Entity.rideTick posts it and then seats the rider), and it is placed frame by frame
 * instead. A player on a track is the exception: he is walked (ForcedWalk), and his tick is his own.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class PuppetBinder {
    /** A body on its track, and what the chase and the fate rules keep about it. */
    public static final class Binding {
        public final Entity body;
        public final EntityTrack track;
        /** How far outside pushes have taken the body off its track, and how fast that still grows. */
        Vec3 offset = Vec3.ZERO;
        Vec3 drift = Vec3.ZERO;
        @Nullable
        Vec3 written;
        /** The track runs through something solid here: the body waits for it to come free. */
        boolean waiting;
        /** What outsiders took from its health, and what they would have taken had it been allowed to die. */
        float outsideTaken;
        float debt;
        @Nullable
        DamageSource lastOutside;
        /** The ticks of a pause of its thinking that struck it on its track: they begin when it is let go. */
        int pauseOwed;
        /** A real player is walked, not held: his body ticks on, and his place is written before each tick of the level. */
        @Nullable
        public ForcedWalk walk;
        /** The actions the record put on this body, by slot: ticked for it, muted, and ended when it leaves its track. */
        final EntityActionInstance[] mirrored = new EntityActionInstance[2];

        private Binding(Entity body, EntityTrack track) {
            this.body = body;
            this.track = track;
        }

        public Vec3 offset() {
            return offset;
        }

        public float debt() {
            return debt;
        }

        public int pauseOwed() {
            return pauseOwed;
        }
    }

    /** A pause of its thinking is taken off a body on its track, where it would not run down, and kept for the end. */
    public static void takePause(Binding binding) {
        if (binding.body instanceof Mob mob && MobAiPause.remainingTicks(mob) > 0) {
            binding.pauseOwed = Math.max(binding.pauseOwed, MobAiPause.remainingTicks(mob));
            MobAiPause.end(mob);
        }
    }

    /** The body leaves its track: the pause that struck it there begins now. */
    public static void givePause(Binding binding) {
        if (binding.pauseOwed > 0 && binding.body instanceof Mob mob) {
            MobAiPause.pause(mob, binding.pauseOwed);
            binding.pauseOwed = 0;
        }
    }

    private final Map<Integer, Binding> bound = new LinkedHashMap<>();

    public Binding bind(Entity body, EntityTrack track) {
        Binding binding = new Binding(body, track);
        bound.put(body.getId(), binding);
        return binding;
    }

    /** The binding of this very entity: a phantom has its body's id and is not bound. */
    @Nullable
    public Binding of(@Nullable Entity entity) {
        if (entity == null) {
            return null;
        }
        Binding binding = bound.get(entity.getId());
        return binding != null && binding.body == entity ? binding : null;
    }

    @Nullable
    public Binding release(Entity body) {
        Binding binding = of(body);
        if (binding != null) {
            bound.remove(body.getId());
        }
        return binding;
    }

    public List<Binding> all() {
        return new ArrayList<>(bound.values());
    }

    public boolean isEmpty() {
        return bound.isEmpty();
    }

    public void clear() {
        bound.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!EpitaphSessions.any) {
            return;
        }
        if (EpitaphSessions.holdsTick(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
