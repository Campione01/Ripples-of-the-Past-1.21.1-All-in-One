package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The Stand of a root that is out at the press. A Stand cannot be copied from saved data, which holds neither its kind,
 * its user nor what it does: it is summoned again in the room by the power of the copy of its user, put where the real
 * one is and given its id, and stands in the room as a root whose future belongs to that of its user.
 */
final class PhantomStands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private PhantomStands() {}

    /** A phantom died: the Stand it has out is taken back by its power now, as the core does in the next tick of a real one. */
    static void leaveWith(LivingEntity user) {
        StandPower power = StandPower.get(user);
        if (power != null && power.getSummonedStandEntity() != null && power.getPowerType() instanceof EntityStandType type) {
            type.forceUnsummon(user, power);
        }
    }

    /** The real Stand whose phantom now stands in the room, null when this root has none out or it could not be made. */
    @Nullable
    static StandEntity mirror(SandboxRoom room, LivingEntity real, LivingEntity copy) {
        StandPower power = StandPower.get(real);
        StandEntity out = power != null ? power.getSummonedStandEntity() : null;
        StandPower copied = StandPower.get(copy);
        SandboxRoom.Phantom user = room.info(copy);
        if (out == null || out.isRemoved() || out.level() != room.level() || copied == null || copied.isSummoned() || user == null
                || !(copied.getPowerType() instanceof EntityStandType type) || room.phantom(out.getId()) != null) {
            return null;
        }
        boolean outside = EpitaphSandbox.depth == 0;
        if (outside) {
            EpitaphSandbox.enter(room, EpitaphSandbox.Mode.SEALED);
        }
        Entity acting = EpitaphSandbox.actor();
        // The real Stand was summoned long ago: what its summon does to the world is not to happen again.
        room.mute();
        room.silence();
        EpitaphSandbox.setActor(copy);
        StandEntity made = null;
        try {
            type.summon(copy, copied, stand -> {
                stand.setId(out.getId());
                stand.summonLockTicks = out.summonLockTicks;
                stand.gradualSummonWeaknessTicks = out.gradualSummonWeaknessTicks;
                stand.moveTo(out.getX(), out.getY(), out.getZ(), out.getYRot(), out.getXRot());
                stand.setYHeadRot(out.getYHeadRot());
                stand.yBodyRot = out.yBodyRot;
                stand.setOldPosAndRot();
                if (out.isArmsOnlyMode()) {
                    stand.setArmsOnlyMode(out.showArm(InteractionHand.MAIN_HAND), out.showArm(InteractionHand.OFF_HAND));
                }
                stand.tickCount = out.tickCount;
                RandomState.copy(out.getRandom(), stand.getRandom());
            }, false);
            made = copied.getSummonedStandEntity();
        }
        catch (Throwable failure) {
            room.counts().notCopied++;
            LOGGER.warn("Epitaph[copy] the Stand of a {} could not be summoned for its copy and is not predicted",
                    BuiltInRegistries.ENTITY_TYPE.getKey(real.getType()), failure);
        }
        finally {
            EpitaphSandbox.setActor(acting);
            room.unsilence();
            room.unmute();
            if (outside) {
                EpitaphSandbox.exit();
            }
        }
        if (made == null) {
            return null;
        }
        room.uuids().assign(out.getUUID(), made.getUUID());
        SandboxRoom.Phantom phantom = room.addRoot(made, user.owner, Map.of());
        made.onAddedToLevel();
        phantom.equipment = PhantomFactory.equipment(made);
        phantom.track.add(TrackFrame.of(made, List.of()));
        SandboxDriver.pair(room, phantom);
        SandboxDriver.joinQuietly(room, made);
        return out;
    }
}
