package rotp.addons.kingcrimson.epitaph.replay;

import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.PacketBytes;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Carries the ops of one tick of the record out for real: through the real methods, with every event, once, in the
 * order they were made, with the bodies in the place of the phantoms. A block op is done only where the block is
 * still what the room saw; an op on something that has left the prediction is skipped and counted.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class OpExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failureLogged;
    // The explosion of the record that is being set off: its blocks and its hold on bound bodies are the record's.
    @Nullable
    private static RecordedOp.Explode exploding;
    @Nullable
    private static EpitaphSession explodingIn;

    private OpExecutor() {}

    public static void run(EpitaphSession session, int tick) {
        List<RecordedOp> ops = session.record().ops(tick);
        if (ops.isEmpty()) {
            return;
        }
        EpitaphSandbox.setReplaying(true);
        try {
            for (int i = 0; i < ops.size(); i++) {
                RecordedOp op = ops.get(i);
                if (session.skips(op)) {
                    session.record().countSkipped();
                    letGoAtVoidBlow(session, op);
                    continue;
                }
                try {
                    if (!carryOut(session, tick, ops, i, op)) {
                        session.record().countSkipped();
                    }
                    // T2: a foreseen hit may have set off his reflex, and an erase voids his part from that moment.
                    if (session.touchesWalked(op)) {
                        session.checkErase();
                    }
                }
                catch (RuntimeException failure) {
                    session.record().countSkipped();
                    session.record().countFailed();
                    if (!failureLogged) {
                        failureLogged = true;
                        LOGGER.error("Epitaph[op] a foreseen {} could not be carried out", op.getClass().getSimpleName(), failure);
                    }
                }
            }
        }
        finally {
            EpitaphSandbox.setReplaying(false);
            exploding = null;
            explodingIn = null;
        }
    }

    // The other answer to what becomes of a creature whose foreseen blow no longer lands (a setting): it is let go there.
    private static void letGoAtVoidBlow(EpitaphSession session, RecordedOp op) {
        if (op instanceof RecordedOp.Damage damage && KingCrimsonConfig.epitaphReleaseOnVoidBlow()
                && session.bodyOf(damage.victim()) instanceof LivingEntity struck && !(struck instanceof Player)) {
            session.letGo(struck);
        }
    }

    /** The source of a hit of the room, with the bodies in the place of the phantoms and what the core keeps on it. */
    public static DamageSource rebuild(EpitaphSession session, DamageSource source) {
        Entity direct = session.bodyOf(ActorKey.of(source.getDirectEntity()));
        Entity causing = session.bodyOf(ActorKey.of(source.getEntity()));
        return DamageSourceModified.copyModifications(source,
                new DamageSource(source.typeHolder(), direct, causing, source.sourcePositionRaw()));
    }

    @Nullable
    private static RecordedOp.Death deathAfter(List<RecordedOp> ops, int index, ActorKey victim) {
        for (int i = index + 1; i < ops.size(); i++) {
            if (ops.get(i) instanceof RecordedOp.Death death && death.victim().equals(victim)) {
                return death;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!EpitaphSessions.any) {
            return;
        }
        EpitaphSandbox.countPass();
        RecordedOp.Explode op = exploding;
        EpitaphSession session = explodingIn;
        Explosion explosion = event.getExplosion();
        if (op == null || session == null || explosion.radius() != op.radius()
                || !explosion.center().equals(new Vec3(op.x(), op.y(), op.z()))) {
            return;
        }
        exploding = null;
        explodingIn = null;
        List<BlockPos> blocks = event.getAffectedBlocks();
        blocks.clear();
        for (var taken : op.blocks().entrySet()) {
            BlockState now = event.getLevel().getBlockState(taken.getKey());
            if (now == taken.getValue()) {
                blocks.add(taken.getKey());
            } else {
                session.record().countSkipped();
            }
        }
        // Their hurt and their flight are ops and track.
        event.getAffectedEntities().removeIf(entity -> session.binder().of(entity) != null);
    }

    // False when the op found nothing to act on.
    private static boolean carryOut(EpitaphSession session, int tick, List<RecordedOp> ops, int index, RecordedOp op) {
        ServerLevel level = session.level();
        switch (op) {
            case RecordedOp.SetBlock set -> {
                if (level.getBlockState(set.pos()) != set.expectedOld()) {
                    return false;
                }
                level.setBlock(set.pos(), set.state(), set.flags());
            }
            case RecordedOp.DestroyBlock destroy -> {
                if (level.getBlockState(destroy.pos()) != destroy.expectedOld()) {
                    return false;
                }
                level.destroyBlock(destroy.pos(), destroy.drop(), session.bodyOf(destroy.actor()), 512);
            }
            case RecordedOp.BlockEvent event -> level.blockEvent(event.pos(), event.block(), event.id(), event.param());
            case RecordedOp.Neighbours around -> {
                if (around.skip() == null) {
                    level.updateNeighborsAt(around.pos(), around.block());
                } else {
                    level.updateNeighborsAtExceptFromFacing(around.pos(), around.block(), around.skip());
                }
            }
            case RecordedOp.ScheduleTick due -> {
                if (due.block() != null) {
                    level.scheduleTick(due.pos(), due.block(), due.delay(), due.priority());
                } else if (due.fluid() != null) {
                    level.scheduleTick(due.pos(), due.fluid(), due.delay(), due.priority());
                }
            }
            case RecordedOp.Explode blast -> {
                exploding = blast;
                explodingIn = session;
                level.explode(session.bodyOf(blast.origin()), blast.source() != null ? rebuild(session, blast.source()) : null,
                        blast.calculator(), blast.x(), blast.y(), blast.z(), blast.radius(), blast.fire(), blast.interaction(),
                        blast.smallParticles(), blast.largeParticles(), blast.sound());
                exploding = null;
                explodingIn = null;
            }
            case RecordedOp.Spawn spawn -> {
                return spawn(session, tick, spawn);
            }
            case RecordedOp.StandSummon summon -> {
                return ActionMirror.summon(session, tick, summon);
            }
            case RecordedOp.Action action -> {
                return ActionMirror.apply(session, tick, action);
            }
            case RecordedOp.Cooldown cooldown -> {
                return ActionMirror.cooldown(session, cooldown);
            }
            case RecordedOp.Remove remove -> {
                Entity body = session.bodyOf(remove.actor());
                if (body == null) {
                    // Gone already: the action that took it away in the room has just run on its body.
                    return session.isGone(remove.actor());
                }
                session.unbind(body);
                // A Stand is taken back by its power, which then knows that it has none out.
                if (!(body instanceof StandEntity stand) || !ActionMirror.recall(stand)) {
                    body.remove(remove.reason());
                }
            }
            case RecordedOp.Damage damage -> {
                if (!(session.bodyOf(damage.victim()) instanceof LivingEntity victim) || !victim.isAlive()) {
                    return false;
                }
                // What his own state does to a walked player his own tick does: carried out here too, it would land twice.
                if (EpitaphSessions.isWalked(victim) && FateRules.ofOwnState(damage.source())) {
                    return false;
                }
                // A player dies his own death, with what he really carries: only a creature drops the foreseen loot.
                RecordedOp.Death fatal = victim instanceof Player ? null : deathAfter(ops, index, damage.victim());
                if (fatal != null) {
                    session.expectDeath(victim, fatal);
                }
                victim.hurt(rebuild(session, damage.source()), damage.amount());
                Binding binding = session.binder().of(victim);
                if (binding != null) {
                    FateRules.settle(binding, damage.healthAfter());
                }
            }
            case RecordedOp.Death death -> {
                if (!(session.bodyOf(death.victim()) instanceof LivingEntity victim)) {
                    return false;
                }
                if (victim instanceof Player && !KingCrimsonConfig.epitaphForcePlayerDeath()) {
                    // His foreseen death was his last foreseen blow, and that has landed for real: what is really on
                    // him decided. Alive, the death did not happen, and neither does what follows from it.
                    session.livedThroughHisDeath(victim);
                    return true;
                }
                // The foreseen hit did not kill for real: the foreseen death happens all the same, as one death.
                if (victim.isAlive()) {
                    if (!(victim instanceof Player)) {
                        session.expectDeath(victim, death);
                    }
                    victim.setHealth(0.0F);
                    victim.die(rebuild(session, death.source()));
                    if (victim.getHealth() > 0.0F) {
                        LOGGER.warn("Epitaph[death-undone] a foreseen death was undone by something else; the creature is let go");
                    }
                }
                session.forgetDeath(victim);
                session.unbind(victim);
            }
            case RecordedOp.Effect effect -> {
                if (!(session.bodyOf(effect.victim()) instanceof LivingEntity victim) || !victim.isAlive()) {
                    return false;
                }
                victim.addEffect(new MobEffectInstance(effect.effect()), session.bodyOf(effect.actor()));
            }
            case RecordedOp.EffectRemoved gone -> {
                if (!(session.bodyOf(gone.victim()) instanceof LivingEntity victim)) {
                    return false;
                }
                victim.removeEffect(gone.effect());
            }
            case RecordedOp.Equip equip -> {
                return equip(session, equip);
            }
            case RecordedOp.Mount mount -> {
                Entity rider = session.bodyOf(mount.actor());
                Entity vehicle = session.bodyOf(mount.vehicle());
                if (rider == null) {
                    return false;
                }
                if (vehicle != null) {
                    rider.startRiding(vehicle, true);
                } else {
                    rider.stopRiding();
                }
            }
            default -> {
                return present(session, level, op);
            }
        }
        return true;
    }

    private static boolean spawn(EpitaphSession session, int tick, RecordedOp.Spawn spawn) {
        ServerLevel level = session.level();
        Entity entity = spawn.type().create(level);
        if (entity == null) {
            return false;
        }
        CompoundTag data = spawn.data().copy();
        data.remove("Passengers");
        session.room().uuids().toReals(data);
        entity.load(data);
        // The id and the UUID its phantom has: neither was ever in the world.
        entity.setId(spawn.child().id());
        if (!level.addFreshEntity(entity)) {
            return false;
        }
        EntityTrack track = session.record().track(spawn.child());
        if (spawn.free() || track == null) {
            session.registerFree(entity);
        } else {
            PuppetTick.place(level, session.bind(entity, track), tick);
        }
        return true;
    }

    // What the actor took from the ground for this slot must still lie there: else it wears nothing new.
    private static boolean equip(EpitaphSession session, RecordedOp.Equip equip) {
        if (!(session.bodyOf(equip.actor()) instanceof LivingEntity wearer) || !wearer.isAlive()) {
            return false;
        }
        for (ActorKey taken : equip.taken()) {
            if (session.bodyOf(taken) == null) {
                return false;
            }
        }
        for (ActorKey taken : equip.taken()) {
            Entity item = session.bodyOf(taken);
            if (item != null) {
                session.unbind(item);
                item.discard();
            }
        }
        wearer.setItemSlot(equip.slot(), equip.stack().copy());
        return true;
    }

    // Sounds, particles, events and packets: the same call for real.
    private static boolean present(EpitaphSession session, ServerLevel level, RecordedOp op) {
        switch (op) {
            case RecordedOp.Sound sound -> {
                Player except = session.bodyOf(sound.except()) instanceof Player player ? player : null;
                Entity on = session.bodyOf(sound.on());
                if (on != null) {
                    level.playSeededSound(except, on, sound.sound(), sound.source(), sound.volume(), sound.pitch(), sound.seed());
                } else {
                    level.playSeededSound(except, sound.x(), sound.y(), sound.z(), sound.sound(), sound.source(),
                            sound.volume(), sound.pitch(), sound.seed());
                }
            }
            case RecordedOp.Particles particles -> {
                if (particles.onlyTo().isNone()) {
                    level.sendParticles(particles.particle(), particles.x(), particles.y(), particles.z(), particles.count(),
                            particles.xSpread(), particles.ySpread(), particles.zSpread(), particles.speed());
                } else if (session.bodyOf(particles.onlyTo()) instanceof ServerPlayer player) {
                    level.sendParticles(player, particles.particle(), particles.longDistance(), particles.x(), particles.y(),
                            particles.z(), particles.count(), particles.xSpread(), particles.ySpread(), particles.zSpread(),
                            particles.speed());
                } else {
                    return false;
                }
            }
            case RecordedOp.LevelEvent event -> {
                if (event.global()) {
                    level.globalLevelEvent(event.type(), event.pos(), event.data());
                } else {
                    level.levelEvent(session.bodyOf(event.except()) instanceof Player player ? player : null, event.type(),
                            event.pos(), event.data());
                }
            }
            case RecordedOp.GameEventOp event -> level.gameEvent(event.event(), event.pos(),
                    new GameEvent.Context(session.bodyOf(event.source()), event.affected()));
            case RecordedOp.Packets packets -> {
                Entity about = session.bodyOf(packets.about());
                if (packets.viewOnly() || about == null) {
                    return false;
                }
                for (Packet<?> packet : PacketBytes.decode(packets.bytes(), level.registryAccess())) {
                    if (packets.andSelf()) {
                        level.getChunkSource().broadcastAndSend(about, packet);
                    } else {
                        level.getChunkSource().broadcast(about, packet);
                    }
                }
            }
            case RecordedOp.AreaPackets packets -> {
                Player except = session.bodyOf(packets.except()) instanceof Player player ? player : null;
                for (Packet<?> packet : PacketBytes.decode(packets.bytes(), level.registryAccess())) {
                    level.getServer().getPlayerList().broadcast(except, packets.x(), packets.y(), packets.z(),
                            packets.radius(), level.dimension(), packet);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
