package rotp.addons.kingcrimson.network;

import java.util.ArrayList;
import java.util.List;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonShiftedImagesClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Where a Time Erase's user sees the other players and their Stands this tick: sent to him alone. An entity that is
 * not in the list is seen where it is; the empty list lets go of them all.
 */
public record TimeEraseShiftPayload(List<Image> images) implements CustomPacketPayload {
    private static final int MAX_IMAGES = 4096;

    public static final Type<TimeEraseShiftPayload> TYPE =
            new Type<>(KingCrimsonAddon.id("time_erase_shift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeEraseShiftPayload> STREAM_CODEC =
            StreamCodec.ofMember(TimeEraseShiftPayload::encode, TimeEraseShiftPayload::decode);

    public record Image(int entityId, Vec3 position) {}

    public TimeEraseShiftPayload {
        images = List.copyOf(images);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(images.size());
        for (Image image : images) {
            buffer.writeVarInt(image.entityId());
            buffer.writeDouble(image.position().x);
            buffer.writeDouble(image.position().y);
            buffer.writeDouble(image.position().z);
        }
    }

    private static TimeEraseShiftPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_IMAGES);
        List<Image> images = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            images.add(new Image(buffer.readVarInt(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())));
        }
        return new TimeEraseShiftPayload(images);
    }

    public static void handle(TimeEraseShiftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> KingCrimsonShiftedImagesClient.apply(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
