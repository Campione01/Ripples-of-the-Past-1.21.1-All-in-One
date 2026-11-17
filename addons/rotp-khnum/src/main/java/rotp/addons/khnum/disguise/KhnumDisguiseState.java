package rotp.addons.khnum.disguise;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.khnum.KhnumAddon;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The disguise on its user, so that it goes on with the Stand recalled. A synced attachment with no serializer: a
 * client gets it when it starts tracking the user, and nothing saves it, so a relog does not bring it back.
 */
public record KhnumDisguiseState(
        DisguiseMode mode,
        Optional<ResourceLocation> entityId,
        Optional<UUID> playerId,
        String playerName) {
    public static final KhnumDisguiseState NONE =
            new KhnumDisguiseState(
                    DisguiseMode.NONE,
                    Optional.empty(),
                    Optional.empty(),
                    "");
    public static final StreamCodec<
            RegistryFriendlyByteBuf, KhnumDisguiseState> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE,
                    state -> state.mode().id(),
                    ByteBufCodecs.optional(
                            ResourceLocation.STREAM_CODEC),
                    KhnumDisguiseState::entityId,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    KhnumDisguiseState::playerId,
                    ByteBufCodecs.STRING_UTF8,
                    KhnumDisguiseState::playerName,
                    (id, entityId, playerId, playerName) ->
                            new KhnumDisguiseState(
                                    DisguiseMode.byId(id),
                                    entityId,
                                    playerId,
                                    playerName));

    public static KhnumDisguiseState of(@Nullable LivingEntity user) {
        KhnumDisguiseState state = user != null
                ? user.getExistingDataOrNull(
                        KhnumAddon.DISGUISE_STATE)
                : null;
        return state != null ? state : NONE;
    }

    public static void clear(@Nullable LivingEntity user) {
        if (user == null || user.level().isClientSide()) {
            return;
        }
        if (user.hasData(KhnumAddon.DISGUISE_STATE)) {
            user.removeData(KhnumAddon.DISGUISE_STATE);
        }
        DisguiseScaleService.reset(user);
    }

    public boolean isDisguised() {
        return mode != DisguiseMode.NONE;
    }
}
