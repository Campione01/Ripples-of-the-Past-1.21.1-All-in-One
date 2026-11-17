package rotp.addons.ger.network;

import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerAnswerClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What everyone sees of a hit returned to zero: the user's gold glow and the flash of each answer. */
public final class GerAnswerPayloads {
    private GerAnswerPayloads() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(
                GlowPayload.TYPE,
                GlowPayload.STREAM_CODEC,
                GlowPayload::handle);
        registrar.playToClient(
                FlashPayload.TYPE,
                FlashPayload.STREAM_CODEC,
                FlashPayload::handle);
    }

    /** The Disabler's user glows gold for the ticks left of its window; 0 ends it. */
    public record GlowPayload(int entityId, int ticks)
            implements CustomPacketPayload {
        public static final Type<GlowPayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "answer_glow"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                GlowPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public GlowPayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new GlowPayload(
                                buffer.readVarInt(),
                                buffer.readVarInt());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            GlowPayload payload) {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeVarInt(payload.ticks());
                    }
                };

        private static void handle(
                GlowPayload payload, IPayloadContext context) {
            GerAnswerClientState.glow(
                    payload.entityId(), payload.ticks());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One answer: the user, the attacker put back (-1 for none), and whether
     * it is the short Return to Zero effect or the window's light flash.
     */
    public record FlashPayload(
            int userId, int attackerId, boolean full)
            implements CustomPacketPayload {
        public static final Type<FlashPayload> TYPE =
                new Type<>(GoldExperienceRequiemAddon.id(
                        "answer_flash"));
        public static final StreamCodec<
                RegistryFriendlyByteBuf,
                FlashPayload> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public FlashPayload decode(
                            RegistryFriendlyByteBuf buffer) {
                        return new FlashPayload(
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readBoolean());
                    }

                    @Override
                    public void encode(
                            RegistryFriendlyByteBuf buffer,
                            FlashPayload payload) {
                        buffer.writeVarInt(payload.userId());
                        buffer.writeVarInt(payload.attackerId());
                        buffer.writeBoolean(payload.full());
                    }
                };

        private static void handle(
                FlashPayload payload, IPayloadContext context) {
            GerAnswerClientState.flash(
                    payload.userId(),
                    payload.attackerId(),
                    payload.full());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
