package rotp.addons.killerqueen.btd;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.6.10 (2026-10-20) com.zeml.rotp_zkq.btd.BtdInstance, plus the game time it was placed at (the point its rewind
 * posts) and the countdown ticks a time stop held back.
 */
public final class BtdInstance {
    public final UUID owner;
    public final UUID carrier;
    public BtdState state = BtdState.ACTIVE;
    public int activeTicks;
    public int countdownTicks;
    @Nullable public Snapshot ownerSnapshot;
    @Nullable public Snapshot carrierSnapshot;
    public long dayTime;
    public long gameTime;
    public boolean standWasSummoned;

    public BtdInstance(UUID owner, UUID carrier) {
        this.owner = owner;
        this.carrier = carrier;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner);
        tag.putUUID("Carrier", carrier);
        tag.putInt("State", state.ordinal());
        tag.putInt("ActiveTicks", activeTicks);
        tag.putInt("Countdown", countdownTicks);
        tag.putLong("DayTime", dayTime);
        tag.putLong("GameTime", gameTime);
        tag.putBoolean("StandWasSummoned", standWasSummoned);
        if (ownerSnapshot != null) {
            tag.put("OwnerSnap", ownerSnapshot.save());
        }
        if (carrierSnapshot != null) {
            tag.put("CarrierSnap", carrierSnapshot.save());
        }
        return tag;
    }

    @Nullable
    public static BtdInstance load(CompoundTag tag) {
        if (!tag.hasUUID("Owner") || !tag.hasUUID("Carrier")) {
            return null;
        }
        BtdInstance inst = new BtdInstance(
                tag.getUUID("Owner"), tag.getUUID("Carrier"));
        inst.state = BtdState.byOrdinal(tag.getInt("State"));
        inst.activeTicks = tag.getInt("ActiveTicks");
        inst.countdownTicks = tag.getInt("Countdown");
        inst.dayTime = tag.getLong("DayTime");
        inst.gameTime = tag.getLong("GameTime");
        inst.standWasSummoned = tag.getBoolean("StandWasSummoned");
        if (tag.contains("OwnerSnap")) {
            inst.ownerSnapshot =
                    Snapshot.load(tag.getCompound("OwnerSnap"));
        }
        if (tag.contains("CarrierSnap")) {
            inst.carrierSnapshot =
                    Snapshot.load(tag.getCompound("CarrierSnap"));
        }
        return inst;
    }

    public record Snapshot(
            String dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            float health,
            int food) {
        public static Snapshot of(ServerPlayer player) {
            return new Snapshot(
                    player.level().dimension().location().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getHealth(),
                    player.getFoodData().getFoodLevel());
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putFloat("YRot", yRot);
            tag.putFloat("XRot", xRot);
            tag.putFloat("Health", health);
            tag.putInt("Food", food);
            return tag;
        }

        public static Snapshot load(CompoundTag tag) {
            return new Snapshot(
                    tag.getString("Dim"),
                    tag.getDouble("X"),
                    tag.getDouble("Y"),
                    tag.getDouble("Z"),
                    tag.getFloat("YRot"),
                    tag.getFloat("XRot"),
                    tag.getFloat("Health"),
                    tag.getInt("Food"));
        }
    }
}
