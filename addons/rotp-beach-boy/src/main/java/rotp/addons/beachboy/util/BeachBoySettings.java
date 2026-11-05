package rotp.addons.beachboy.util;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyStandEntity;
import rotp.addons.beachboy.network.BeachBoySettingsPayload;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 BeachBoySettings with BeachBoySettingsEffect: the path line, crush damage and sound sense switches and the
 * line length a Beach Boy user keeps across summons, deaths and logouts (the effect was never removed). Here a player
 * attachment with the effect's NBT names, sent to its owner whenever it changes.
 */
public final class BeachBoySettings {
    public enum Toggle {
        PATH_LINE, CRUSH_DAMAGE, SOUND_SENSE
    }

    public static final class State {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("PathLine", false).forGetter(s -> s.pathLine),
                Codec.BOOL.optionalFieldOf("CrushDamage", true).forGetter(s -> s.crushDamage),
                Codec.BOOL.optionalFieldOf("SoundSense", true).forGetter(s -> s.soundSense),
                Codec.FLOAT.optionalFieldOf("MaxLength", BeachBoyLine.DEFAULT_LENGTH).forGetter(s -> s.maxLength))
                .apply(instance, State::new));

        boolean pathLine;
        boolean crushDamage = true;
        boolean soundSense = true;
        float maxLength = BeachBoyLine.DEFAULT_LENGTH;

        public State() {}

        public State(boolean pathLine, boolean crushDamage, boolean soundSense, float maxLength) {
            this.pathLine = pathLine;
            this.crushDamage = crushDamage;
            this.soundSense = soundSense;
            this.maxLength = maxLength;
        }

        public boolean pathLine() { return pathLine; }
        public boolean crushDamage() { return crushDamage; }
        public boolean soundSense() { return soundSense; }
        public float maxLength() { return maxLength; }
    }

    private BeachBoySettings() {}

    public static boolean isBeachBoy(@Nullable StandPower power) {
        return power != null && power.hasPower() && power.getPowerType() == BeachBoyAddon.BEACH_BOY.get();
    }

    @Nullable
    private static State state(@Nullable StandPower power) {
        if (!isBeachBoy(power) || !(power.getUser() instanceof Player player)) {
            return null;
        }
        return player.getData(BeachBoyAddon.SETTINGS);
    }

    public static boolean isPathLine(@Nullable StandPower power) {
        State state = state(power);
        if (state != null) {
            return state.pathLine;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(power);
        return isBeachBoy(power) && stand != null && stand.hasLineBlockCollision();
    }

    public static boolean isCrushDamage(@Nullable StandPower power) {
        if (!isBeachBoy(power)) {
            return false;
        }
        State state = state(power);
        if (state != null) {
            return state.crushDamage;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(power);
        return stand == null || stand.isCrushDamageEnabled();
    }

    public static boolean isSoundSense(@Nullable StandPower power) {
        if (!isBeachBoy(power)) {
            return false;
        }
        State state = state(power);
        if (state != null) {
            return state.soundSense;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(power);
        return stand == null || stand.isSoundSenseEnabled();
    }

    public static float getMaxLength(@Nullable StandPower power) {
        State state = state(power);
        if (state != null) {
            return state.maxLength;
        }
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(power);
        return stand != null ? stand.getMaxLineLength() : BeachBoyLine.DEFAULT_LENGTH;
    }

    public static boolean get(@Nullable StandPower power, Toggle toggle) {
        return switch (toggle) {
            case PATH_LINE -> isPathLine(power);
            case CRUSH_DAMAGE -> isCrushDamage(power);
            case SOUND_SENSE -> isSoundSense(power);
        };
    }

    public static void toggle(LivingEntity user, Toggle toggle) {
        State state = state(StandPower.get(user));
        if (state == null) {
            return;
        }
        switch (toggle) {
            case PATH_LINE -> state.pathLine = !state.pathLine;
            case CRUSH_DAMAGE -> state.crushDamage = !state.crushDamage;
            case SOUND_SENSE -> state.soundSense = !state.soundSense;
        }
        changed(user, state);
    }

    public static void setMaxLength(LivingEntity user, float length) {
        State state = state(StandPower.get(user));
        if (state == null) {
            return;
        }
        float next = Math.max(BeachBoyLine.MIN_LENGTH, length);
        if (Float.compare(state.maxLength, next) != 0) {
            state.maxLength = next;
            changed(user, state);
        }
    }

    private static void changed(LivingEntity user, State state) {
        BeachBoyStandEntity stand = BeachBoyStandUtil.getStand(user);
        if (stand != null) {
            stand.copySettings(state);
        }
        sync(user);
    }

    /** 1.16 syncWithUserOnly: the owner's client draws the switches and the reel from its copy. */
    public static void sync(LivingEntity user) {
        if (user instanceof ServerPlayer player && isBeachBoy(StandPower.get(player))) {
            State state = player.getData(BeachBoyAddon.SETTINGS);
            PacketDistributor.sendToPlayer(player, new BeachBoySettingsPayload(
                    state.pathLine, state.crushDamage, state.soundSense, state.maxLength));
        }
    }

    public static void applyFromServer(Player player, boolean pathLine, boolean crushDamage, boolean soundSense,
            float maxLength) {
        State state = player.getData(BeachBoyAddon.SETTINGS);
        state.pathLine = pathLine;
        state.crushDamage = crushDamage;
        state.soundSense = soundSense;
        state.maxLength = maxLength;
    }

    @Nullable
    public static State stateOf(LivingEntity user) {
        return state(StandPower.get(user));
    }
}
