package rotp.addons.kingcrimson.epitaph.record;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.TickPriority;
import rotp.core.powersystem.entityaction.ActionReplayState;

/**
 * One thing a phantom did to the world at one tick of the room. It did not happen there: it is carried out for real,
 * through the real method and with the bodies in the place of the phantoms, when the bodies reach that tick. Entities
 * are named by key; a damage source is kept as the room made it and rebuilt for the bodies when it is used.
 */
public sealed interface RecordedOp {
    /** The phantom whose tick did it; the future the op belongs to is that of the owner of its track. */
    ActorKey actor();

    record SetBlock(ActorKey actor, BlockPos pos, BlockState state, int flags, BlockState expectedOld) implements RecordedOp {}

    record DestroyBlock(ActorKey actor, BlockPos pos, boolean drop, BlockState expectedOld) implements RecordedOp {}

    record BlockEvent(ActorKey actor, BlockPos pos, Block block, int id, int param) implements RecordedOp {}

    /** Neighbour updates around a position, all six or all but one side. */
    record Neighbours(ActorKey actor, BlockPos pos, Block block, @Nullable Direction skip) implements RecordedOp {}

    /** A scheduled tick of a block or of a fluid, due this many ticks after the op. */
    record ScheduleTick(ActorKey actor, BlockPos pos, @Nullable Block block, @Nullable Fluid fluid, int delay,
            TickPriority priority) implements RecordedOp {}

    /** An explosion of origin (nobody for one without an entity), with the blocks it took in the room and their states. */
    record Explode(ActorKey actor, ActorKey origin, @Nullable DamageSource source, @Nullable ExplosionDamageCalculator calculator,
            double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction,
            ParticleOptions smallParticles, ParticleOptions largeParticles, Holder<SoundEvent> sound,
            Map<BlockPos, BlockState> blocks) implements RecordedOp {}

    /** An entity made in the room; free ones (items, experience) are not carried along a track once they are real. */
    record Spawn(ActorKey actor, ActorKey child, EntityType<?> type, CompoundTag data, boolean free) implements RecordedOp {}

    /**
     * A Stand was summoned in the room. It cannot be made from saved data (that holds neither its kind nor its user): its
     * body is summoned by the power of the body of its user, under the id its phantom has.
     */
    record StandSummon(ActorKey actor, ActorKey stand, ActorKey user, boolean armsOnly, boolean mainArm, boolean offArm)
            implements RecordedOp {}

    /**
     * The action in one slot of a performer changed (the user of a power, or a Stand): the action as it then stood, or
     * none. It is put on the body of the performer, where it runs muted: what it did is in the other ops. When another set
     * it earlier in the tick in which its performer then ticked it, it is kept as it stood before that (ticksAtOnce), and
     * the body ticks it once as it is put on.
     */
    record Action(ActorKey actor, int slot, ActionReplayState state, boolean ticksAtOnce) implements RecordedOp {}

    /**
     * A press of the actor set a cooldown of its Stand power, this long and of this length in all. A press is not made
     * again on the body: what it cost is put on the power of the body.
     */
    record Cooldown(ActorKey actor, String ability, int ticks, int total) implements RecordedOp {}

    /** A phantom left the room without dying: actor is the one that left, by the one whose tick took it out. */
    record Remove(ActorKey actor, ActorKey by, Entity.RemovalReason reason) implements RecordedOp {}

    /** A hit as it was passed to hurt, and the health the phantom of the victim had after it. */
    record Damage(ActorKey actor, ActorKey victim, DamageSource source, float amount, float healthAfter) implements RecordedOp {}

    /** An item a death dropped in the room: the stack, and the saved data of its phantom as it left the dying one. */
    record Drop(ActorKey child, ItemStack stack, CompoundTag data) {}

    record Death(ActorKey actor, ActorKey victim, DamageSource source, List<Drop> drops, int experience) implements RecordedOp {}

    record Effect(ActorKey actor, ActorKey victim, MobEffectInstance effect) implements RecordedOp {}

    record EffectRemoved(ActorKey actor, ActorKey victim, Holder<MobEffect> effect) implements RecordedOp {}

    /** A slot of the equipment of the actor changed; taken names the items of the room it took from the ground for it. */
    record Equip(ActorKey actor, EquipmentSlot slot, ItemStack stack, List<ActorKey> taken) implements RecordedOp {}

    /** The actor mounted a vehicle, or left its seat when vehicle is nobody. */
    record Mount(ActorKey actor, ActorKey vehicle) implements RecordedOp {}

    /** A sound at a position, or on an entity when one is named. */
    record Sound(ActorKey actor, ActorKey except, ActorKey on, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) implements RecordedOp {}

    record Particles(ActorKey actor, ActorKey onlyTo, ParticleOptions particle, boolean longDistance, double x, double y,
            double z, int count, double xSpread, double ySpread, double zSpread, double speed) implements RecordedOp {}

    record LevelEvent(ActorKey actor, ActorKey except, int type, BlockPos pos, int data, boolean global) implements RecordedOp {}

    record GameEventOp(ActorKey actor, Holder<GameEvent> event, Vec3 pos, ActorKey source,
            @Nullable BlockState affected) implements RecordedOp {}

    /** Packets the room would have sent the trackers of a phantom, encoded when they were made. */
    record Packets(ActorKey actor, ActorKey about, byte[] bytes, boolean andSelf, boolean viewOnly) implements RecordedOp {}

    /** A packet the room would have sent everybody within a radius of a place. */
    record AreaPackets(ActorKey actor, ActorKey except, double x, double y, double z, double radius, byte[] bytes)
            implements RecordedOp {}
}
