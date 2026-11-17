package rotp.addons.whitesnake.disguise;

import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.whitesnake.WhitesnakeAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The look the Stand has taken, on its user as well as on the Stand, so that it goes on with the Stand recalled. A
 * synced attachment with no serializer: a client gets it when it starts tracking the user, and nothing saves it, so a
 * relog does not bring it back. The Stand keeps its own copy for a client that has it in range and not its user.
 */
public record WhitesnakeDisguiseState(Optional<ResourceLocation> type) {
    public static final WhitesnakeDisguiseState NONE =
            new WhitesnakeDisguiseState(Optional.empty());
    public static final StreamCodec<
            RegistryFriendlyByteBuf, WhitesnakeDisguiseState> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(
                            ResourceLocation.STREAM_CODEC),
                    WhitesnakeDisguiseState::type,
                    WhitesnakeDisguiseState::new);

    public static WhitesnakeDisguiseState of(
            @Nullable LivingEntity user) {
        WhitesnakeDisguiseState state = user != null
                ? user.getExistingDataOrNull(
                        WhitesnakeAddon.DISGUISE_STATE)
                : null;
        return state != null ? state : NONE;
    }

    public static void put(
            @Nullable LivingEntity user, ResourceLocation type) {
        if (user != null && !user.level().isClientSide()) {
            user.setData(
                    WhitesnakeAddon.DISGUISE_STATE,
                    new WhitesnakeDisguiseState(Optional.of(type)));
        }
    }

    public static void clear(@Nullable LivingEntity user) {
        if (user != null
                && !user.level().isClientSide()
                && user.hasData(WhitesnakeAddon.DISGUISE_STATE)) {
            user.removeData(WhitesnakeAddon.DISGUISE_STATE);
        }
    }

    public boolean isDisguised() {
        return type.isPresent();
    }
}
