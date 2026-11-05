package rotp.addons.echoes.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesOnomatopoeia;
import rotp.addons.echoes.state.EchoesOnomatopoeiaMark;
import rotp.addons.echoes.state.EchoesOnomatopoeiaService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * R772 F11: ACT2's word choice (master OnomatopoeiaSelectPacket) and the words on living entities that clients
 * draw (master OnomatopoeiaSyncPacket and the attached word entity).
 */
public final class EchoesOnomatopoeiaPayloads {
    private static Consumer<Mark> clientMarkHandler = payload -> {};

    private EchoesOnomatopoeiaPayloads() {}

    public record Select(String word) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(RotpEchoes.id("onomatopoeia_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Select::word, Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** An empty word: the entity carries none. */
    public record Mark(int entityId, String word, int variant) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(RotpEchoes.id("onomatopoeia_mark"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Mark::entityId,
                ByteBufCodecs.STRING_UTF8, Mark::word,
                ByteBufCodecs.VAR_INT, Mark::variant,
                Mark::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Select.TYPE, Select.STREAM_CODEC, EchoesOnomatopoeiaPayloads::handleSelect)
                .playToClient(Mark.TYPE, Mark.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> clientMarkHandler.accept(payload)));
    }

    public static void installClientMarkHandler(Consumer<Mark> handler) {
        clientMarkHandler = Objects.requireNonNull(handler);
    }

    private static void handleSelect(Select payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                StandPower power = StandPower.get(player);
                EchoesOnomatopoeia word = EchoesOnomatopoeia.byId(payload.word());
                if (power != null && power.getPowerType() == EchoesStands.ECHOES_ACT2.get() && word != null) {
                    EchoesOnomatopoeiaService.select(player, word);
                }
            }
        });
    }

    public static void syncMark(LivingEntity target) {
        if (!target.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, markOf(target));
        }
    }

    public static void sendMark(ServerPlayer player, LivingEntity target) {
        if (EchoesOnomatopoeiaService.markOf(target) != null) {
            PacketDistributor.sendToPlayer(player, markOf(target));
        }
    }

    private static Mark markOf(LivingEntity target) {
        EchoesOnomatopoeiaMark mark = EchoesOnomatopoeiaService.markOf(target);
        return mark != null && mark.word() != null
                ? new Mark(target.getId(), mark.word().id(), mark.variant())
                : new Mark(target.getId(), "", 0);
    }
}
