package rotp.addons.kingcrimson.client.epitaph;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.epitaph.human.ForcedWalk;
import rotp.addons.kingcrimson.network.EpitaphForcedWalkPayload;

/**
 * The local player on a foreseen track (EPITAPH-ARCH 3.4). The server puts his body where the track has it and says
 * so each tick; here he is moved there, his movement keys count for nothing and his attack and use keys do nothing.
 * The camera stays his. When he is let go the server is told what his keys really are, since it heard nothing of
 * them meanwhile.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID, value = Dist.CLIENT)
public final class ForcedWalkClient {
    /** He is moved to where the server has him over this many ticks, as the client moves any entity of the server. */
    private static final int LERP_TICKS = 3;
    @Nullable
    private static Vec3 target;
    private static int steps;

    private ForcedWalkClient() {}

    /** From the network handler, on the client thread. */
    public static void accept(EpitaphForcedWalkPayload payload) {
        switch (payload.kind()) {
            case BEGIN -> {
                ForcedWalk.localPlayerWalked = true;
                target = new Vec3(payload.x(), payload.y(), payload.z());
                steps = 0;
            }
            case STEP -> {
                if (ForcedWalk.localPlayerWalked) {
                    target = new Vec3(payload.x(), payload.y(), payload.z());
                    steps = LERP_TICKS;
                }
            }
            case RELEASE -> release(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ForcedWalk.localPlayerWalked) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            release(false);
            return;
        }
        if (target != null) {
            Vec3 at = player.position();
            Vec3 next = steps > 1 ? at.add(target.subtract(at).scale(1.0D / steps)) : target;
            steps = Math.max(0, steps - 1);
            Entity ridden = player.getRootVehicle();
            if (ridden != player) {
                // He rides: what he rides is taken along, and seats him.
                ridden.setPos(ridden.position().add(next.subtract(at)));
                ridden.setDeltaMovement(Vec3.ZERO);
            }
            player.setPos(next.x, next.y, next.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ForcedWalk.localPlayerWalked) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (ForcedWalk.localPlayerWalked) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        release(false);
    }

    private static void release(boolean tellKeys) {
        boolean was = ForcedWalk.localPlayerWalked;
        ForcedWalk.localPlayerWalked = false;
        target = null;
        steps = 0;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (was && tellKeys && player != null && connection != null) {
            connection.send(new ServerboundPlayerCommandPacket(player, player.isShiftKeyDown()
                    ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            connection.send(new ServerboundPlayerCommandPacket(player, player.isSprinting()
                    ? ServerboundPlayerCommandPacket.Action.START_SPRINTING : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }
    }
}
