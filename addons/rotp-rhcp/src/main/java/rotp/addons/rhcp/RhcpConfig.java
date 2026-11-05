package rotp.addons.rhcp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Beta2 RotpRHCPConfig: the common config, synced to every client. */
public final class RhcpConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue SAVE_RHCP_DATA;

    private static volatile boolean syncedSaveRhcpData;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SAVE_RHCP_DATA = builder
                .comment("Keep RHCP's energy and potency on its user when RHCP is unsummoned"
                        + " (false: every summon starts a fresh RHCP at 0 energy and potency).")
                .define("SaveRHCPData", false);
        SPEC = builder.build();
        syncedSaveRhcpData = SAVE_RHCP_DATA.getDefault();
    }

    private RhcpConfig() {}

    public static boolean saveRhcpData(boolean clientSide) {
        if (clientSide) {
            return syncedSaveRhcpData;
        }
        return SPEC.isLoaded() ? SAVE_RHCP_DATA.get() : SAVE_RHCP_DATA.getDefault();
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncPayload(saveRhcpData(false)));
    }

    static void onReloading(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() != SPEC || server == null) {
            return;
        }
        server.execute(() -> PacketDistributor.sendToAllPlayers(
                new SyncPayload(saveRhcpData(false))));
    }

    public record SyncPayload(boolean saveRhcpData) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(RhcpAddon.id("common_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SyncPayload::saveRhcpData,
                        SyncPayload::new);

        static void handle(SyncPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> syncedSaveRhcpData = payload.saveRhcpData());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
