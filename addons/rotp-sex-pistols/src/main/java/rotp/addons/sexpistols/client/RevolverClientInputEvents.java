package rotp.addons.sexpistols.client;

import com.mojang.blaze3d.platform.InputConstants;

import rotp.core.client.ClientGlobals;
import rotp.core.init.ModStatusEffects;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.network.RevolverChamberControlPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class RevolverClientInputEvents {
    // One shot or blow per press: the donor's adsAttackLatched.
    private static boolean attackLatched;

    private RevolverClientInputEvents() {}

    // 1.16's click ran at HIGHEST, ahead of the core's click at HIGH, so the gun in hand took the attack button from any
    // Stand. This core takes the button's raw press at LOW and an ability that goes off then leaves no vanilla click. The
    // press cancelled here reaches neither, so nothing starts to break a block or swings.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseButton(
            InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != InputConstants.PRESS
                || minecraft.screen != null
                || minecraft.getOverlay() != null
                || !minecraft.mouseHandler.isMouseGrabbed()
                || !minecraft.options.keyAttack.isActiveAndMatches(
                        InputConstants.Type.MOUSE.getOrCreate(
                                event.getButton()))
                || !holdsUsableRevolver(minecraft)) {
            return;
        }
        pressAttack(minecraft.player);
        event.setCanceled(true);
    }

    // What the raw press does not cover: an attack key on the keyboard, and a button held since before the gun came to
    // hand, which would go on breaking the block.
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onInteraction(
            InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isCanceled()
                || !event.isAttack()
                || event.getHand() != InteractionHand.MAIN_HAND
                || !holdsUsableRevolver(minecraft)) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
        if (!attackLatched) {
            pressAttack(minecraft.player);
            attackLatched = minecraft.options.keyAttack.isDown();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            attackLatched = false;
            return;
        }
        while (RevolverKeyMappings.RELOAD.consumeClick()) {
            if (player.getMainHandItem().getItem()
                    instanceof RevolverItem) {
                player.stopUsingItem();
                send(RevolverChamberControlPayload.TOGGLE_CYLINDER);
            }
        }
        KeyMapping attack = minecraft.options.keyAttack;
        if (!attack.isDown()) {
            attackLatched = false;
        }
        // Vanilla swallows attack clicks during an item's use, so an attack key
        // on the keyboard fires an aimed shot from here, as the donor did.
        else if (!attackLatched
                && minecraft.screen == null
                && player.isUsingItem()
                && holdsUsableRevolver(minecraft)) {
            pressAttack(player);
            attackLatched = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMouseScroll(
            InputEvent.MouseScrollingEvent event) {
        double delta = event.getScrollDeltaY();
        if (event.isCanceled()
                || delta == 0.0D
                || Minecraft.getInstance().screen != null
                || !requestSwitchCylinder(delta > 0.0D)) {
            return;
        }
        event.setCanceled(true);
    }

    public static boolean requestSwitchCylinder(boolean forward) {
        if (!hasOpenMainHandRevolver()) {
            return false;
        }
        send(forward
                ? RevolverChamberControlPayload.SWITCH_FORWARD
                : RevolverChamberControlPayload.SWITCH_BACKWARD);
        return true;
    }

    // With the cylinder open the button does nothing, as in the donor.
    private static void pressAttack(LocalPlayer player) {
        if (RevolverItem.state(player.getMainHandItem()).reloadMode()) {
            return;
        }
        send(player.isShiftKeyDown()
                ? RevolverChamberControlPayload.MELEE
                : RevolverChamberControlPayload.FIRE);
    }

    private static void send(int operation) {
        PacketDistributor.sendToServer(
                new RevolverChamberControlPayload(operation));
    }

    // A player steering something else, stunned or stopped in someone's time keeps the core's rules for the click.
    private static boolean holdsUsableRevolver(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null
                && !player.isSpectator()
                && minecraft.getCameraEntity() == player
                && player.getMainHandItem().getItem() instanceof RevolverItem
                && !ModStatusEffects.isStunned(player)
                && !TimeStopState.shouldFreezeClientEntity(player)
                && (ClientGlobals.playerStandEntity == null
                        || !ClientGlobals.playerStandEntity.isManuallyControlled());
    }

    private static boolean hasOpenMainHandRevolver() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof RevolverItem
                && RevolverItem.state(stack).reloadMode();
    }
}
