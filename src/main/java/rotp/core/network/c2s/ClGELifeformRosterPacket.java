package rotp.core.network.c2s;

import java.util.UUID;

import rotp.core.PacketsRegister;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Answer;
import rotp.core.network.s2c.TrGELifeformRosterPacket;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The lifeform menu asks the server for its rows, or to revert one of them; the server answers with the rows. */
public record ClGELifeformRosterPacket(boolean revert, UUID lifeformId) implements CustomPacketPayload {
    private static final UUID NONE = new UUID(0L, 0L);
    private static CustomPacketPayload.Type<ClGELifeformRosterPacket> type;

    public static ClGELifeformRosterPacket list() {
        return new ClGELifeformRosterPacket(false, NONE);
    }

    public static ClGELifeformRosterPacket revert(UUID lifeformId) {
        return new ClGELifeformRosterPacket(true, lifeformId);
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<ClGELifeformRosterPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<ClGELifeformRosterPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, ClGELifeformRosterPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ClGELifeformRosterPacket> STREAM_CODEC = StreamCodec.ofMember(
                ClGELifeformRosterPacket::write,
                ClGELifeformRosterPacket::new);

        @Override
        public void handle(ClGELifeformRosterPacket payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Answer answer = Answer.NONE;
            Component subject = null;
            if (payload.revert) {
                subject = GoldExperienceLifeformRoster.rows(player).stream()
                        .filter(row -> row.id().equals(payload.lifeformId))
                        .findFirst().map(TrGELifeformRosterPacket::rowName).orElse(null);
                answer = GoldExperienceLifeformRoster.revert(player, payload.lifeformId);
            }
            PacketDistributor.sendToPlayer(player, new TrGELifeformRosterPacket(
                    GoldExperienceLifeformRoster.rows(player), answer, subject));
        }
    }

    public ClGELifeformRosterPacket(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUUID());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(revert);
        buf.writeUUID(lifeformId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
