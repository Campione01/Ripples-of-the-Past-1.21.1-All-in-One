package rotp.addons.highwaystar.room;

import java.util.Optional;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record HighwayStarRoomLure(Kind kind, Optional<ResourceLocation> entityType) {
    public enum Kind { COMBAT, REST, FOOD, CHEST }

    public static final HighwayStarRoomLure CHEST = new HighwayStarRoomLure(Kind.CHEST, Optional.empty());
    private static final StreamCodec<FriendlyByteBuf, Kind> KIND_CODEC = StreamCodec.of(
            (buf, kind) -> buf.writeEnum(kind), buf -> buf.readEnum(Kind.class));
    public static final StreamCodec<FriendlyByteBuf, HighwayStarRoomLure> STREAM_CODEC = StreamCodec.composite(
            KIND_CODEC, HighwayStarRoomLure::kind,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), HighwayStarRoomLure::entityType,
            HighwayStarRoomLure::new);
}
