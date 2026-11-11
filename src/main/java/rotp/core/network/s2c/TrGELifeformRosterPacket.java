package rotp.core.network.s2c;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Answer;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Place;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Row;
import rotp.core.impl.stands.goldexperience.client.GoldExperienceLifeformsScreen;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.util.functions_network.NetworkUtil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The server's rows of the lifeform menu, and its answer to the revert the menu asked for, if it asked. */
public record TrGELifeformRosterPacket(List<Row> rows, Answer answer, @Nullable Component subject) implements CustomPacketPayload {
    private static CustomPacketPayload.Type<TrGELifeformRosterPacket> type;

    public TrGELifeformRosterPacket {
        NetworkPayloadValidation.requireOutboundCollectionSize(rows.size(), GoldExperienceLifeformRoster.MAX_ROWS, "lifeform rows");
        rows = List.copyOf(rows);
    }

    /** What the menu calls a lifeform: its name if it was given one, else its kind. */
    public static Component rowName(Row row) {
        return row.name() != null ? row.name() : kindName(row.type());
    }

    public static Component kindName(@Nullable ResourceLocation type) {
        return type != null
                ? BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(entityType -> entityType.getDescription())
                        .orElse(Component.literal(type.toString()))
                : Component.empty();
    }

    public static class Handler implements PacketsRegister.PacketCodecHandler<TrGELifeformRosterPacket> {
        public Handler(ResourceLocation packetId) {
            type = new CustomPacketPayload.Type<>(packetId);
        }

        @Override
        public Type<TrGELifeformRosterPacket> type() {
            return type;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, TrGELifeformRosterPacket> reader() {
            return STREAM_CODEC;
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, TrGELifeformRosterPacket> STREAM_CODEC = StreamCodec.ofMember(
                TrGELifeformRosterPacket::write,
                TrGELifeformRosterPacket::new);

        @Override
        public void handle(TrGELifeformRosterPacket payload, IPayloadContext context) {
            GoldExperienceLifeformsScreen.onRoster(payload);
        }
    }

    public TrGELifeformRosterPacket(RegistryFriendlyByteBuf buf) {
        this(NetworkUtil.readCollection(buf, TrGELifeformRosterPacket::readRow, GoldExperienceLifeformRoster.MAX_ROWS),
                buf.readEnum(Answer.class),
                ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf).orElse(null));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        NetworkUtil.writeCollection(buf, rows, TrGELifeformRosterPacket::writeRow, GoldExperienceLifeformRoster.MAX_ROWS);
        buf.writeEnum(answer);
        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, Optional.ofNullable(subject));
    }

    private static void writeRow(RegistryFriendlyByteBuf buf, Row row) {
        buf.writeUUID(row.id());
        buf.writeNullable(row.type(), (out, id) -> out.writeResourceLocation(id));
        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, Optional.ofNullable(row.name()));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, row.source());
        buf.writeNullable(row.dimension(), (out, id) -> out.writeResourceLocation(id));
        buf.writeNullable(row.pos(), (out, pos) -> out.writeVec3(pos));
        buf.writeInt(row.entityId());
        buf.writeVarLong(row.age());
        buf.writeBoolean(row.counts());
        buf.writeEnum(row.place());
    }

    private static Row readRow(RegistryFriendlyByteBuf buf) {
        return new Row(buf.readUUID(),
                buf.readNullable(in -> in.readResourceLocation()),
                ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf).orElse(null),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readNullable(in -> in.readResourceLocation()),
                buf.readNullable(in -> NetworkPayloadValidation.requireFinite(in.readVec3(), "lifeform position")),
                buf.readInt(),
                buf.readVarLong(),
                buf.readBoolean(),
                buf.readEnum(Place.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return type;
    }
}
