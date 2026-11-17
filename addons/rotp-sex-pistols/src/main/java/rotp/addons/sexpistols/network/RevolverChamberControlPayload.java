package rotp.addons.sexpistols.network;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RevolverChamberControlPayload(int operation)
        implements CustomPacketPayload {
    public static final int TOGGLE_CYLINDER = 0;
    public static final int SWITCH_FORWARD = 1;
    public static final int SWITCH_BACKWARD = 2;
    public static final int FIRE = 3;
    public static final int MELEE = 4;

    public static final Type<RevolverChamberControlPayload> TYPE =
            new Type<>(SexPistolsIds.id(
                    "revolver_chamber_control"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RevolverChamberControlPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    RevolverChamberControlPayload::write,
                    RevolverChamberControlPayload::new);

    private RevolverChamberControlPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(operation);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TYPE,
                STREAM_CODEC,
                RevolverChamberControlPayload::handle);
    }

    private static void handle(
            RevolverChamberControlPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload.operation());
        }
    }

    public static void apply(ServerPlayer player, int operation) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RevolverItem revolver)) {
            return;
        }
        boolean open = RevolverItem.state(stack).reloadMode();
        switch (operation) {
            case TOGGLE_CYLINDER -> revolver.toggleReloadMode(
                    player.serverLevel(), player, stack);
            case SWITCH_FORWARD, SWITCH_BACKWARD -> {
                if (open) {
                    RevolverItem.switchSelectedChamber(
                            player.serverLevel(),
                            player,
                            stack,
                            operation == SWITCH_FORWARD);
                }
            }
            case FIRE -> revolver.fire(
                    player.serverLevel(), player, stack);
            case MELEE -> revolver.meleeAttack(
                    player.serverLevel(), player, stack);
            default -> {
                // Unknown operations are intentionally ignored.
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
