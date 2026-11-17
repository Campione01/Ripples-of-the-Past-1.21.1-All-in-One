package rotp.addons.yellowtemperance.disguise;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * The disguises, the doll and the Protection on their user, so that they go on with the Stand recalled. A synced
 * attachment with no serializer: a client gets it when it starts tracking the user, and nothing saves it, so a relog
 * does not bring it back. The two ticks are the client's own, in its copy of the user's tick count.
 */
public record YellowTemperanceDisguiseState(
        DisguiseMode mode,
        Optional<ResourceLocation> disguiseId,
        Optional<UUID> playerId,
        String playerName,
        Optional<ResourceLocation> besideStand,
        boolean doll,
        boolean protection,
        int changedTick,
        int besideShownTick) {
    public static final YellowTemperanceDisguiseState NONE =
            new YellowTemperanceDisguiseState(
                    DisguiseMode.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    "",
                    Optional.empty(),
                    false,
                    false,
                    0,
                    0);
    // Long past any configured transition: a client that only now sees the user sees no grow-in.
    private static final int LONG_AGO = 1_000_000;

    private static final StreamCodec<
            ByteBuf, Optional<ResourceLocation>> OPTIONAL_ID =
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC);
    private static final StreamCodec<ByteBuf, Optional<UUID>>
            OPTIONAL_UUID =
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);

    public static final StreamCodec<
            RegistryFriendlyByteBuf, YellowTemperanceDisguiseState>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, state) -> {
                        buffer.writeByte(state.mode().id());
                        OPTIONAL_ID.encode(buffer, state.disguiseId());
                        OPTIONAL_UUID.encode(buffer, state.playerId());
                        buffer.writeUtf(state.playerName());
                        OPTIONAL_ID.encode(buffer, state.besideStand());
                        buffer.writeBoolean(state.doll());
                        buffer.writeBoolean(state.protection());
                    },
                    buffer -> new YellowTemperanceDisguiseState(
                            DisguiseMode.byId(buffer.readByte()),
                            OPTIONAL_ID.decode(buffer),
                            OPTIONAL_UUID.decode(buffer),
                            buffer.readUtf(),
                            OPTIONAL_ID.decode(buffer),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            0,
                            0));

    public static final AttachmentSyncHandler<YellowTemperanceDisguiseState>
            SYNC = new AttachmentSyncHandler<>() {
                @Override
                public void write(
                        RegistryFriendlyByteBuf buffer,
                        YellowTemperanceDisguiseState state,
                        boolean initialSync) {
                    STREAM_CODEC.encode(buffer, state);
                    buffer.writeBoolean(initialSync);
                }

                @Override
                public YellowTemperanceDisguiseState read(
                        IAttachmentHolder holder,
                        RegistryFriendlyByteBuf buffer,
                        @Nullable YellowTemperanceDisguiseState previous) {
                    YellowTemperanceDisguiseState state =
                            STREAM_CODEC.decode(buffer);
                    return state.shownOnClient(
                            previous,
                            buffer.readBoolean(),
                            holder instanceof Entity entity
                                    ? entity.tickCount
                                    : 0);
                }
            };

    public static YellowTemperanceDisguiseState of(
            @Nullable LivingEntity user) {
        YellowTemperanceDisguiseState state = user != null
                ? user.getExistingDataOrNull(
                        YellowTemperanceAddon.DISGUISE_STATE)
                : null;
        return state != null ? state : NONE;
    }

    public static void put(
            @Nullable LivingEntity user,
            YellowTemperanceDisguiseState state) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        if (!state.isEmpty()) {
            user.setData(YellowTemperanceAddon.DISGUISE_STATE, state);
        }
        else if (user.hasData(YellowTemperanceAddon.DISGUISE_STATE)) {
            user.removeData(YellowTemperanceAddon.DISGUISE_STATE);
        }
    }

    /** Death, logout, a change of level or the loss of the Stand: nothing is left on the user. */
    public static void end(@Nullable LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        YellowTemperanceDisguiseState old = of(user);
        put(user, NONE);
        DisguiseScaleService.reset(user);
        user.removeEffect(YellowTemperanceAddon.PROTECTION);
        if (user instanceof Player player) {
            if (old.mode() == DisguiseMode.PLAYER) {
                player.refreshDisplayName();
            }
            DisguiseRetaliationService.clear(player);
        }
    }

    public boolean isEmpty() {
        return mode == DisguiseMode.NONE
                && besideStand.isEmpty()
                && !doll
                && !protection;
    }

    /** Whether the user himself looks like something else; a fake Stand beside him does not count (1.16 isUserDisguised). */
    public boolean isDisguised() {
        return mode != DisguiseMode.NONE;
    }

    public boolean hasStandDisguise() {
        return besideStand.isPresent() || mode == DisguiseMode.STAND_SELF;
    }

    public YellowTemperanceDisguiseState withUserDisguise(
            DisguiseMode newMode,
            Optional<ResourceLocation> newDisguiseId,
            Optional<UUID> newPlayerId,
            String newPlayerName) {
        return new YellowTemperanceDisguiseState(
                newMode,
                newDisguiseId,
                newPlayerId,
                newPlayerName,
                besideStand,
                doll,
                protection,
                changedTick,
                besideShownTick);
    }

    public YellowTemperanceDisguiseState withBesideStand(
            Optional<ResourceLocation> newBesideStand) {
        return new YellowTemperanceDisguiseState(
                mode,
                disguiseId,
                playerId,
                playerName,
                newBesideStand,
                doll,
                protection,
                changedTick,
                besideShownTick);
    }

    public YellowTemperanceDisguiseState withDoll(boolean newDoll) {
        return new YellowTemperanceDisguiseState(
                mode,
                disguiseId,
                playerId,
                playerName,
                besideStand,
                newDoll,
                protection,
                changedTick,
                besideShownTick);
    }

    public YellowTemperanceDisguiseState withProtection(
            boolean newProtection) {
        return new YellowTemperanceDisguiseState(
                mode,
                disguiseId,
                playerId,
                playerName,
                besideStand,
                doll,
                newProtection,
                changedTick,
                besideShownTick);
    }

    // 1.16 StandDisguiseSetPacket set the shapeshift tick on each side from that side's own tick count.
    private YellowTemperanceDisguiseState shownOnClient(
            @Nullable YellowTemperanceDisguiseState previous,
            boolean firstSeen,
            int now) {
        int changed = firstSeen
                ? now - LONG_AGO
                : previous != null && sameUserLook(previous)
                        ? previous.changedTick
                        : now;
        int shown = firstSeen
                ? now - LONG_AGO
                : previous != null && besideStand.equals(previous.besideStand)
                        ? previous.besideShownTick
                        : now;
        return new YellowTemperanceDisguiseState(
                mode,
                disguiseId,
                playerId,
                playerName,
                besideStand,
                doll,
                protection,
                changed,
                shown);
    }

    private boolean sameUserLook(YellowTemperanceDisguiseState other) {
        return mode == other.mode
                && disguiseId.equals(other.disguiseId)
                && playerId.equals(other.playerId);
    }
}
