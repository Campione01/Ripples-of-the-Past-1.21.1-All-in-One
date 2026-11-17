package rotp.addons.kingcrimson.epitaph.replay;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.PhantomUuidRemap;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.SynchedDataAccessor;

/**
 * Carries a body along its track. On its track a body is placed, not steered. An outside push takes it off: the
 * difference between the motion it has and the motion that was written for it is added to an offset, which moves on
 * by its own fading speed and then shrinks, the faster the further off the body is. A living body is never put
 * inside blocks: the offset is cut to what is free, and where the track itself runs through something solid the body
 * waits and takes a short cut in time, appearing at the first later place of its track that is free. A body that
 * rides is written to its frame and then seated by its vehicle: it is never off its track by itself.
 */
public final class PuppetTick {
    private static final double DRIFT_FADE = 0.6D;
    private static final int OFFSET_STEPS = 8;
    private static final double SHRUNK = 0.05D;
    // Entity.DATA_SHARED_FLAGS_ID and its bits for crouching, sprinting and swimming.
    private static final int SHARED_FLAGS = 0;
    private static final int CROUCHING = 1 << 1;
    private static final int SPRINTING = 1 << 3;
    private static final int SWIMMING = 1 << 4;

    private PuppetTick() {}

    /** Places the body where its track has it at body tick k; true when it got there by a short cut. */
    public static boolean place(ServerLevel level, Binding binding, double k) {
        EntityTrack track = binding.track;
        Entity body = binding.body;
        double at = Mth.clamp(k, track.firstTick(), track.lastTick());
        int from = Mth.floor(at);
        double part = at - from;
        TrackFrame a = track.frame(from);
        TrackFrame b = track.frame(Math.min(from + 1, track.lastTick()));
        Vec3 on = a.position().lerp(b.position(), part);
        Vec3 motion = a.motion().lerp(b.motion(), part);

        boolean shortCut = false;
        Vec3 to = on;
        if (body.isPassenger()) {
            // A rider's place is its seat, and its vehicle zeroes its motion every tick: that is no push from outside.
            binding.offset = Vec3.ZERO;
            binding.drift = Vec3.ZERO;
            binding.waiting = false;
        } else {
            // A walked player is not knocked off his track.
            if (binding.written != null && binding.walk == null) {
                Vec3 push = body.getDeltaMovement().subtract(binding.written);
                if (push.lengthSqr() > 1.0E-4D) {
                    binding.drift = binding.drift.add(push);
                }
            }
            binding.offset = binding.offset.add(binding.drift);
            binding.drift = binding.drift.lengthSqr() < 1.0E-4D ? Vec3.ZERO : binding.drift.scale(DRIFT_FADE);
            double off = binding.offset.length();
            double back = Math.max(KingCrimsonConfig.epitaphChaseReturnMin(), KingCrimsonConfig.epitaphChaseReturnShare() * off);
            binding.offset = off <= back ? Vec3.ZERO : binding.offset.scale((off - back) / off);

            to = on.add(binding.offset);
            if (body instanceof LivingEntity && !KingCrimsonConfig.epitaphTrustTrack()) {
                if (!fits(level, body, on)) {
                    binding.waiting = true;
                    to = body.position();
                } else {
                    if (binding.waiting || binding.offset.length() > KingCrimsonConfig.epitaphChaseMaxOffset()) {
                        shortCut = binding.waiting || !binding.offset.equals(Vec3.ZERO);
                        binding.waiting = false;
                        binding.offset = Vec3.ZERO;
                        binding.drift = Vec3.ZERO;
                    } else if (!binding.offset.equals(Vec3.ZERO)) {
                        Vec3 free = Vec3.ZERO;
                        for (int step = OFFSET_STEPS; step > 0; step--) {
                            Vec3 tried = binding.offset.scale(step / (double) OFFSET_STEPS);
                            if (fits(level, body, on.add(tried))) {
                                free = tried;
                                break;
                            }
                        }
                        binding.offset = free;
                    }
                    to = on.add(binding.offset);
                }
            }
        }
        float yRot = Mth.rotLerp((float) part, a.yRot(), b.yRot());
        float xRot = Mth.lerp((float) part, a.xRot(), b.xRot());
        float yHeadRot = Mth.rotLerp((float) part, a.yHeadRot(), b.yHeadRot());
        boolean onGround = part < 0.5D ? a.onGround() : b.onGround();
        if (binding.walk != null) {
            binding.walk.place(to, yRot, xRot, yHeadRot, onGround);
        } else {
            body.setPos(to.x, to.y, to.z);
            body.setYRot(yRot);
            body.setXRot(xRot);
            body.setYHeadRot(yHeadRot);
            body.setYBodyRot(Mth.rotLerp((float) part, a.yBodyRot(), b.yBodyRot()));
            body.setOnGround(onGround);
        }
        write(binding, motion);
        return shortCut;
    }

    /** The motion the track has for the body: also after an op of the record moved it, which is no outside push. */
    public static void write(Binding binding, Vec3 motion) {
        binding.body.setDeltaMovement(motion);
        binding.written = motion;
    }

    public static Vec3 motionAt(Binding binding, double k) {
        EntityTrack track = binding.track;
        double at = Mth.clamp(k, track.firstTick(), track.lastTick());
        int from = Mth.floor(at);
        return track.frame(from).motion().lerp(track.frame(Math.min(from + 1, track.lastTick())).motion(), at - from);
    }

    // On its track a body is tested with a slightly smaller box: a recorded position may graze a block.
    private static boolean fits(ServerLevel level, Entity body, Vec3 at) {
        return level.noBlockCollision(body, body.getBoundingBox().move(at.subtract(body.position())).deflate(SHRUNK));
    }

    /**
     * What the tick the body skipped would have done to how it looks: the synced data of its phantom at that tick
     * (its pose, its flags, a swelling creeper), written so that the body's own tracker shows it to every viewer, and
     * the timers of a hit running down. Health is not taken from the phantom: it is what the real hits leave.
     */
    public static void pass(Binding binding, int tick, PhantomUuidRemap uuids) {
        Entity body = binding.body;
        if (binding.walk != null) {
            // His body ticks by itself and keeps its own data: of his phantom he takes what its legs were doing.
            if (binding.track.has(tick)) {
                for (SynchedEntityData.DataValue<?> value : binding.track.frame(tick).data()) {
                    if (value.id() == SHARED_FLAGS && value.value() instanceof Byte flags) {
                        binding.walk.pose((flags & CROUCHING) != 0, (flags & SPRINTING) != 0, (flags & SWIMMING) != 0);
                    }
                }
            }
            return;
        }
        if (binding.track.has(tick)) {
            SynchedEntityData data = body.getEntityData();
            SynchedEntityData.DataItem<?>[] items = ((SynchedDataAccessor) data).kingCrimson$items();
            int health = body instanceof LivingEntity ? LivingStateAccessor.kingCrimson$healthData().id() : -1;
            for (SynchedEntityData.DataValue<?> value : binding.track.frame(tick).data()) {
                if (value.id() != health && value.id() >= 0 && value.id() < items.length) {
                    set(data, items[value.id()], value, uuids);
                }
            }
        }
        if (body instanceof LivingEntity living) {
            if (living.hurtTime > 0) {
                living.hurtTime--;
            }
            if (living.invulnerableTime > 0) {
                living.invulnerableTime--;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(SynchedEntityData data, SynchedEntityData.DataItem<T> item,
            SynchedEntityData.DataValue<?> value, PhantomUuidRemap uuids) {
        if (item.getAccessor().serializer() != value.serializer()) {
            return;
        }
        Object carried = value.value();
        if (carried instanceof UUID uuid) {
            UUID real = uuids.realOf(uuid);
            carried = real != null ? real : uuid;
        } else if (carried instanceof Optional<?> maybe && maybe.isPresent() && maybe.get() instanceof UUID uuid) {
            UUID real = uuids.realOf(uuid);
            carried = real != null ? Optional.of(real) : maybe;
        }
        data.set(item.getAccessor(), (T) carried, true);
    }
}
