package rotp.addons.kingcrimson.epitaph.window;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.BlockEntry;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.Made;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.Participant;
import rotp.addons.kingcrimson.epitaph.window.WindowJournal.Touched;
import rotp.core.api.entity.EntityStateRestore;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The end of a window (EPITAPH-ARCH 3.5, 5.4): everybody who took part is put back as he was at the press, with what he
 * did to the world undone, in one server tick. The rule: a pull-back either undoes everything a participant did, or does
 * not happen for him. So it is checked first, participant by participant, that all of it can be undone (who fails is a
 * hole), and only then carried out, with what each step replaced kept, so that a step that throws is taken back with all
 * before it and nobody is pulled back.
 */
public final class PullBack {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** No neighbour is told and nothing drops: every block that changed is itself put back as it was. */
    private static final int QUIET = 2 | 16 | 32;
    /** For a test only: the step of the commit that throws, counted from 1; 0 for none. */
    public static int failAtStep;

    private PullBack() {}

    /** Whether everybody who is no hole was put back. False: a step threw, all was taken back, and all are holes. */
    public static boolean run(WindowJournal journal) {
        journal.close();
        preflight(journal);
        List<Runnable> undo = new ArrayList<>();
        int[] done = new int[3];
        boolean whole = true;
        try {
            commit(journal, undo, done);
        }
        catch (RuntimeException failure) {
            whole = false;
            // A warning: the game goes on, with what everybody did left real.
            LOGGER.warn("Epitaph[pull-back] {}: a step of the pull-back threw; it is taken back and nobody is pulled back",
                    journal.name, failure);
            for (int step = undo.size() - 1; step >= 0; step--) {
                try {
                    undo.get(step).run();
                }
                catch (RuntimeException second) {
                    LOGGER.warn("Epitaph[pull-back] {}: a step could not be taken back", journal.name, second);
                }
            }
            for (Participant participant : journal.participants()) {
                journal.hole(participant, "the pull-back failed");
            }
        }
        int back = 0;
        int holes = 0;
        for (Participant participant : journal.participants()) {
            if (participant.hole) {
                holes++;
            } else {
                back++;
            }
        }
        LOGGER.info("Epitaph[pull-back] {}: {} pulled back, {} not; {} entities of theirs removed, {} blocks put back, {} entities"
                + " put back or made again", journal.name, back, holes, done[0], done[1], done[2]);
        return whole;
    }

    // Holes pull holes: who shares a block or an entity with one who is not pulled back is not pulled back either.
    private static void preflight(WindowJournal journal) {
        boolean found = true;
        while (found) {
            found = false;
            for (Participant participant : journal.participants()) {
                String why = participant.hole ? null : fault(journal, participant);
                if (why != null) {
                    journal.hole(participant, why);
                    found = true;
                }
            }
        }
    }

    private static boolean anyHole(Iterable<Participant> those) {
        for (Participant one : those) {
            if (one.hole) {
                return true;
            }
        }
        return false;
    }

    private static boolean destroyed(Entity entity) {
        return entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy();
    }

    // Why this participant cannot be pulled back, null when he can.
    @Nullable
    private static String fault(WindowJournal journal, Participant participant) {
        Entity entity = participant.entity;
        if (participant.owner != null && participant.owner.hole) {
            return "whose it is is not pulled back";
        }
        if (entity instanceof ServerPlayer player) {
            if (player.isRemoved() || !player.isAlive() || player.level() != journal.level || player.hasDisconnected()) {
                return "he is dead or gone";
            }
            if (player.containerMenu != player.inventoryMenu) {
                return "he has a container open";
            }
        } else if (entity.isRemoved() ? !destroyed(entity) && !(entity instanceof StandEntity) : entity.level() != journal.level) {
            return "it left the level";
        }
        for (Made thing : journal.made.values()) {
            if (thing.owner == participant && (thing.entity.isRemoved() ? !thing.goneUnderOwner : thing.entity.level() != journal.level)) {
                return "what he made is gone by another hand: " + WindowJournal.describe(thing.entity);
            }
        }
        for (var changed : journal.blocks.entrySet()) {
            BlockEntry entry = changed.getValue();
            if (!entry.by.contains(participant)) {
                continue;
            }
            if (anyHole(entry.by)) {
                return "he changed a block with one who is not pulled back, at " + changed.getKey().toShortString();
            }
            if (journal.level.getBlockState(changed.getKey()) != entry.left) {
                return "a block he changed was changed again, at " + changed.getKey().toShortString();
            }
        }
        for (Touched other : journal.touched.values()) {
            if (other.by.contains(participant) && anyHole(other.by)) {
                return "he touched what one who is not pulled back touched: " + WindowJournal.describe(other.entity);
            }
        }
        return null;
    }

    // What they made goes, the blocks and what they touched are as before, and then they themselves: a user before his Stand.
    private static void commit(WindowJournal journal, List<Runnable> undo, int[] done) {
        ServerLevel level = journal.level;
        int[] steps = new int[1];
        for (Made thing : List.copyOf(journal.made.values())) {
            if (!thing.owner.hole && !thing.entity.isRemoved()) {
                step(steps);
                remove(level, thing.entity, undo);
                done[0]++;
            }
        }
        for (var changed : journal.blocks.entrySet()) {
            if (!anyHole(changed.getValue().by)) {
                step(steps);
                putBack(level, changed.getKey(), changed.getValue(), undo);
                done[1]++;
            }
        }
        for (Touched other : journal.touched.values()) {
            if (!anyHole(other.by)) {
                step(steps);
                putBack(level, other.entity, other.state, -1, undo);
                done[2]++;
            }
        }
        for (Participant participant : journal.participants()) {
            if (!participant.hole) {
                step(steps);
                putBack(journal, participant, undo);
            }
        }
    }

    private static void step(int[] steps) {
        if (++steps[0] == failAtStep) {
            throw new IllegalStateException("a step of the pull-back fails, as a test asked");
        }
    }

    // An entity a participant made leaves the level. A Stand is taken back by its power, which then knows that it has none out.
    private static void remove(ServerLevel level, Entity entity, List<Runnable> undo) {
        if (entity instanceof StandEntity stand && stand.getUser() != null) {
            StandPower power = StandPower.get(stand.getUser());
            if (power != null && power.getSummonedStandEntity() == stand && power.getPowerType() instanceof EntityStandType type) {
                type.forceUnsummon(stand.getUser(), power);
                return;
            }
        }
        EntityStateRestore.Snapshot was = EntityStateRestore.capture(entity);
        int id = entity.getId();
        entity.discard();
        undo.add(() -> make(level, was, id));
    }

    // An entity is in the level again as its saved data has it, under the id it had when one is given.
    private static Entity make(ServerLevel level, EntityStateRestore.Snapshot state, int id) {
        Entity made = EntityType.loadEntityRecursive(state.tag().copy(), level, loaded -> loaded);
        if (made == null) {
            throw new IllegalStateException("an entity could not be made again from its saved data");
        }
        if (id >= 0) {
            made.setId(id);
        }
        made.noPhysics = state.noPhysics();
        if (!level.addFreshEntity(made)) {
            throw new IllegalStateException("an entity that was made again was not taken into the level");
        }
        return made;
    }

    // An entity is as it was: put back where it still is, made again where it is gone.
    private static Entity putBack(ServerLevel level, Entity entity, EntityStateRestore.Snapshot state, int id, List<Runnable> undo) {
        if (entity.isRemoved()) {
            Entity made = make(level, state, id);
            undo.add(made::discard);
            return made;
        }
        EntityStateRestore.Snapshot now = EntityStateRestore.capture(entity);
        undo.add(() -> WindowSnapshot.load(entity, now));
        WindowSnapshot.load(entity, state);
        WindowSnapshot.revive(entity);
        return entity;
    }

    private static void putBack(ServerLevel level, BlockPos pos, BlockEntry entry, List<Runnable> undo) {
        BlockState now = level.getBlockState(pos);
        BlockEntity data = level.getBlockEntity(pos);
        CompoundTag nowData = data != null ? data.saveWithFullMetadata(level.registryAccess()) : null;
        undo.add(() -> set(level, pos, now, nowData));
        set(level, pos, entry.old, entry.oldData);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state, @Nullable CompoundTag data) {
        level.setBlock(pos, state, QUIET);
        if (data != null) {
            BlockEntity loaded = BlockEntity.loadStatic(pos, state, data, level.registryAccess());
            if (loaded != null) {
                level.setBlockEntity(loaded);
            }
        }
        level.getChunkSource().blockChanged(pos);
    }

    // The participant himself: put back where he lives; a creature that died in the window is made again under its id.
    private static void putBack(WindowJournal journal, Participant participant, List<Runnable> undo) {
        Entity entity = participant.entity;
        if (entity instanceof StandEntity stand) {
            // A Stand is its user: out again if it was taken back in the window, and from here its track has its place.
            if (stand.isRemoved()) {
                StandEntity again = summonAgain(stand);
                if (again == null) {
                    throw new IllegalStateException("a Stand that was out at the press could not be summoned again");
                }
                participant.entity = again;
            }
            return;
        }
        if (entity.isRemoved()) {
            Entity made = make(journal.level, participant.snapshot.state(), entity.getId());
            undo.add(made::discard);
            participant.entity = made;
            return;
        }
        WindowSnapshot now = WindowSnapshot.of(entity);
        undo.add(() -> now.restore(entity));
        participant.snapshot.restore(entity);
    }

    @Nullable
    private static StandEntity summonAgain(StandEntity old) {
        LivingEntity user = old.getUser();
        StandPower power = user != null ? StandPower.get(user) : null;
        if (power == null || !(power.getPowerType() instanceof EntityStandType type)) {
            return null;
        }
        if (power.isSummoned()) {
            type.forceUnsummon(user, power);
        }
        boolean made = type.summon(user, power, stand -> {
            stand.setId(old.getId());
            stand.setUUID(old.getUUID());
            stand.moveTo(old.getX(), old.getY(), old.getZ(), old.getYRot(), old.getXRot());
            if (old.isArmsOnlyMode()) {
                stand.setArmsOnlyMode(old.showArm(InteractionHand.MAIN_HAND), old.showArm(InteractionHand.OFF_HAND));
            }
        }, false);
        StandEntity stand = made ? power.getSummonedStandEntity() : null;
        if (stand == null) {
            return null;
        }
        type.finalizeStandSummonFromAction(user, power, stand, true);
        stand.summonLockTicks = 0;
        return stand.isAddedToLevel() && !stand.isRemoved() ? stand : null;
    }
}
