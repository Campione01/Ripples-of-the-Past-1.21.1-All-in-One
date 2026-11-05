package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.state.ResolveDowned;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Next Album 0.1.13 ResolveDownedSyncPacket: server to trackers and self. */
public record ResolveDownedPayload(int entityId, boolean downed, int ticksLeft,
        int duration, int giveUpTicks, boolean lockSkills)
        implements CustomPacketPayload {
    public static final Type<ResolveDownedPayload> TYPE =
            new Type<>(NextAlbumIds.id("resolve_downed"));
    public static final StreamCodec<ByteBuf, ResolveDownedPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ResolveDownedPayload::entityId,
                    ByteBufCodecs.BOOL, ResolveDownedPayload::downed,
                    ByteBufCodecs.VAR_INT, ResolveDownedPayload::ticksLeft,
                    ByteBufCodecs.VAR_INT, ResolveDownedPayload::duration,
                    ByteBufCodecs.VAR_INT, ResolveDownedPayload::giveUpTicks,
                    ByteBufCodecs.BOOL, ResolveDownedPayload::lockSkills,
                    ResolveDownedPayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                TYPE, STREAM_CODEC, ResolveDownedPayload::handle);
        registrar.playToServer(
                ResolveDownedGiveUpPayload.TYPE,
                ResolveDownedGiveUpPayload.STREAM_CODEC,
                ResolveDownedGiveUpPayload::handle);
    }

    private static void handle(
            ResolveDownedPayload payload, IPayloadContext context) {
        Entity entity = context.player().level().getEntity(payload.entityId());
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        if (!payload.downed()) {
            ResolveDowned.setClientView(payload.entityId(), null);
            if (living != null && !ResolveDowned.isActuallyDead(living)) {
                living.deathTime = 0;
            }
        } else if (living != null && ResolveDowned.isActuallyDead(living)) {
            ResolveDowned.setClientView(payload.entityId(), null);
        } else {
            ResolveDowned.setClientView(payload.entityId(),
                    new ResolveDowned.View(payload.ticksLeft(),
                            payload.duration(), payload.giveUpTicks(),
                            payload.lockSkills()));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
