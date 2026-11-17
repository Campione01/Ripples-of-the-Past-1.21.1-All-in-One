package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.human.EpitaphPlayerPhantom;
import rotp.addons.kingcrimson.epitaph.human.PhantomPlayerPool;
import rotp.addons.kingcrimson.epitaph.human.goal.PhantomStandPowerGoal;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.TrackFrame;
import rotp.addons.kingcrimson.mixin.epitaph.EntityLinkAccessor;
import rotp.addons.kingcrimson.epitaph.window.WindowSnapshot;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;
import rotp.addons.kingcrimson.mixin.epitaph.ServerPlayerStateAccessor;
import rotp.core.api.entity.MobAiPause;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * Makes the root phantoms of a prediction: for each predicted entity a copy from its own saved data, with the id of
 * its body, a UUID of its own, the state of its body's random source and what its body has in mind. A type that
 * cannot be made or loaded, or that is not predicted, gets no phantom, and the prediction goes on without it.
 */
public final class PhantomFactory {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** While a player is saved for a copy, the recipe book is left out: it is large and no state a prediction needs. */
    public static boolean skippingRecipeBook;
    /**
     * Keys that would write to something real when a copy is loaded, or that name what is not in the room: the team
     * (the scoreboard), the raid, the lead (its holder is not there), the riders (linked by hand).
     */
    private static final List<String> STRIPPED = List.of("Team", "RaidId", "leash", "Passengers");
    /**
     * For a player also: the ender chest, the vehicle saved with him, the bed (loading a sleeping player wakes him
     * and frees the real bed) and what sits on his shoulders (a hit would set it free as a new entity).
     */
    private static final List<String> STRIPPED_PLAYER = List.of("EnderItems", "RootVehicle", "SleepingX", "SleepingY",
            "SleepingZ", "ShoulderEntityLeft", "ShoulderEntityRight");

    /** For as long as its rider sits on it: he lets it go when he is off it. */
    private static final int MOUNT_HELD_TICKS = 1_000_000;

    private PhantomFactory() {}

    /** The roots that got a phantom, each registered in the room with its first frame; the others are not predicted. */
    public static List<Entity> build(SandboxRoom room, List<Entity> roots) {
        ServerLevel level = room.level();
        Map<Entity, Entity> copies = new IdentityHashMap<>();
        Map<Entity, Map<String, Tag>> stripped = new IdentityHashMap<>();
        List<Entity> accepted = new ArrayList<>();
        for (Entity real : roots) {
            if (real.isRemoved() || real.level() != level || SandboxGuards.denied(real.getType())) {
                continue;
            }
            Entity blank = blank(level, real);
            if (blank != null) {
                room.uuids().assign(real.getUUID(), blank.getUUID());
                copies.put(real, blank);
                accepted.add(real);
            } else {
                room.counts().notCopied++;
            }
        }
        List<Entity> made = new ArrayList<>();
        for (Entity real : accepted) {
            Entity copy = copies.get(real);
            try {
                Map<String, Tag> taken = new HashMap<>();
                load(room, real, copy, taken);
                stripped.put(real, taken);
                made.add(real);
            }
            catch (Throwable failure) {
                copies.remove(real);
                release(copy);
                room.counts().notCopied++;
                LOGGER.warn("Epitaph[copy] a {} could not be copied and is not predicted",
                        BuiltInRegistries.ENTITY_TYPE.getKey(real.getType()), failure);
            }
        }
        // Every copy is in the room before any takes what its body has in mind: its target is another copy.
        for (Entity real : made) {
            Entity copy = copies.get(real);
            room.addRoot(copy, ActorKey.NONE, stripped.get(real));
            copy.onAddedToLevel();
        }
        for (Entity real : made) {
            Entity copy = copies.get(real);
            PhantomMinds.carry(room, real, copy);
            link(real, copy, copies);
        }
        for (Entity real : made) {
            // A player who rides is one with what he rides (the owner, 2026-11-11): its copy goes where he steers it, when
            // the real one answers to its rider, and nowhere by itself.
            Entity ridden = real.getVehicle() != null ? copies.get(real.getVehicle()) : null;
            if (real instanceof ServerPlayer rider && ridden != null && copies.get(real) instanceof EpitaphPlayerPhantom seated) {
                seated.seat(ridden, real.getVehicle().getControllingPassenger() == rider);
                if (ridden instanceof Mob thinking) {
                    MobAiPause.pause(thinking, MOUNT_HELD_TICKS);
                }
            }
        }
        for (Entity real : made) {
            Entity copy = copies.get(real);
            SandboxRoom.Phantom phantom = room.info(copy);
            phantom.equipment = equipment(copy);
            phantom.vehicle = copy.getVehicle();
            phantom.track.add(TrackFrame.of(copy, List.of()));
        }
        for (Entity real : made) {
            SandboxDriver.pair(room, room.info(copies.get(real)));
            SandboxDriver.joinQuietly(room, copies.get(real));
        }
        // Stands: the one a root has out is summoned again for its copy, and a creature that bears a Stand uses it.
        for (Entity real : List.copyOf(made)) {
            if (real instanceof LivingEntity bearer && copies.get(real) instanceof LivingEntity copy) {
                StandEntity stand = PhantomStands.mirror(room, bearer, copy);
                if (stand != null) {
                    made.add(stand);
                }
                if (copy instanceof Mob mob) {
                    PhantomStandPowerGoal.giveTo(mob);
                }
            }
        }
        takeOverActions(room, made);
        for (Entity real : made) {
            PhantomActions.baseline(room, copies.get(real));
        }
        return made;
    }

    @Nullable
    private static Entity blank(ServerLevel level, Entity real) {
        try {
            return real instanceof ServerPlayer player ? PhantomPlayerPool.make(level, player) : real.getType().create(level);
        }
        catch (Throwable failure) {
            LOGGER.warn("Epitaph[copy] no copy of a {} could be made; it is not predicted",
                    BuiltInRegistries.ENTITY_TYPE.getKey(real.getType()), failure);
            return null;
        }
    }

    private static void load(SandboxRoom room, Entity real, Entity copy, Map<String, Tag> taken) {
        CompoundTag tag;
        skippingRecipeBook = real instanceof ServerPlayer;
        try {
            tag = real.saveWithoutId(new CompoundTag());
        }
        finally {
            skippingRecipeBook = false;
        }
        strip(tag, STRIPPED, taken);
        if (real instanceof ServerPlayer) {
            strip(tag, STRIPPED_PLAYER, taken);
            // King Crimson's own state of an erase belongs to the real player alone.
            CompoundTag attachments = tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
            attachments.remove(KingCrimsonAddon.id("temporal_state").toString());
        }
        room.uuids().toPhantoms(tag);
        copy.load(tag);
        if (real.isPassenger()) {
            // A rider is saved at the x and z of its vehicle: its copy starts in its seat, where its body is.
            copy.moveTo(real.getX(), real.getY(), real.getZ(), copy.getYRot(), copy.getXRot());
        }
        copy.setId(real.getId());
        copy.tickCount = real.tickCount;
        RandomState.copy(real.getRandom(), copy.getRandom());
        if (real instanceof Mob thinking && copy instanceof Mob made) {
            // A pause of its thinking is not saved: the copy stands for as long as its body would have stood (T5).
            MobAiPause.pause(made, MobAiPause.remainingTicks(thinking));
        }
        if (real instanceof ServerPlayer player && copy instanceof ServerPlayer body) {
            body.loadGameTypes(tag);
            // What saved data does not hold of a player: how far his arm has swung back, and what his items wait for.
            ((LivingStateAccessor) body).kingCrimson$setAttackStrengthTicker(
                    ((LivingStateAccessor) player).kingCrimson$attackStrengthTicker());
            Map<Item, Integer> waits = new HashMap<>();
            WindowSnapshot.cooldownsOf(player, waits);
            waits.forEach(body.getCooldowns()::addCooldown);
            ((ServerPlayerStateAccessor) body).kingCrimson$setSpawnInvulnerableTime(
                    ((ServerPlayerStateAccessor) player).kingCrimson$spawnInvulnerableTime());
        }
    }

    private static void strip(CompoundTag tag, List<String> keys, Map<String, Tag> taken) {
        for (String key : keys) {
            Tag value = tag.get(key);
            if (value != null) {
                taken.put(key, value);
                tag.remove(key);
            }
        }
    }

    // Riders sit on the copy of their vehicle; the links are written as fields, with no mount event.
    private static void link(Entity real, Entity copy, Map<Entity, Entity> copies) {
        Entity vehicle = real.getVehicle() != null ? copies.get(real.getVehicle()) : null;
        if (vehicle != null) {
            ((EntityLinkAccessor) copy).kingCrimson$setVehicle(vehicle);
        }
        ImmutableList.Builder<Entity> riders = ImmutableList.builder();
        boolean any = false;
        for (Entity rider : real.getPassengers()) {
            Entity riderCopy = copies.get(rider);
            if (riderCopy != null) {
                riders.add(riderCopy);
                any = true;
            }
        }
        if (any) {
            ((EntityLinkAccessor) copy).kingCrimson$setPassengers(riders.build());
        }
    }

    @Nullable
    static ItemStack[] equipment(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        EquipmentSlot[] slots = EquipmentSlot.values();
        ItemStack[] worn = new ItemStack[slots.length];
        for (EquipmentSlot slot : slots) {
            worn[slot.ordinal()] = living.getItemBySlot(slot).copy();
        }
        return worn;
    }

    // In the room, and muted: what taking an action over does to the world is not to happen again for real.
    private static void takeOverActions(SandboxRoom room, List<Entity> made) {
        boolean outside = EpitaphSandbox.depth == 0;
        if (outside) {
            EpitaphSandbox.enter(room, EpitaphSandbox.Mode.SEALED);
        }
        Entity acting = EpitaphSandbox.actor();
        room.mute();
        try {
            for (Entity real : made) {
                if (real instanceof LivingEntity body && room.phantom(real.getId()) instanceof LivingEntity copy) {
                    EpitaphSandbox.setActor(copy);
                    try {
                        PhantomActions.takeOver(room, body, copy);
                    }
                    catch (RuntimeException failure) {
                        room.counts().notCopied++;
                        LOGGER.warn("Epitaph[copy] the action a {} was in the middle of could not be taken over by its copy",
                                BuiltInRegistries.ENTITY_TYPE.getKey(real.getType()), failure);
                    }
                }
            }
        }
        finally {
            EpitaphSandbox.setActor(acting);
            room.unmute();
            if (outside) {
                EpitaphSandbox.exit();
            }
        }
    }

    /** A copy that leaves the room for good: a human phantom hands its profile back. */
    public static void release(Entity copy) {
        if (copy instanceof EpitaphPlayerPhantom human) {
            PhantomPlayerPool.release(human);
        }
    }
}
