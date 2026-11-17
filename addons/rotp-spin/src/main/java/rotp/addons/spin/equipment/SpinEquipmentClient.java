package rotp.addons.spin.equipment;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.SpinSail;

public final class SpinEquipmentClient {
    private static final KeyMapping HOLSTER_THROW = new KeyMapping(GyrosHolsterItem.THROW_KEY,
            KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, "key.categories.rotp_spin");
    private static LocalPlayer sailPlayer;
    private static boolean sailing;

    private SpinEquipmentClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SpinEquipmentClient::registerKey);
        modBus.addListener(SpinWearableRenderer::addLayers);
        modBus.addListener(SpinEquipmentClient::setupCuriosRenderers);
        NeoForge.EVENT_BUS.addListener(SpinEquipmentClient::tickKey);
        NeoForge.EVENT_BUS.addListener(SpinEquipmentClient::tickSail);
    }

    private static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(HOLSTER_THROW);
    }

    private static void setupCuriosRenderers(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("curios")) event.enqueueWork(SpinCuriosClient::register);
    }

    private static void tickKey(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean canThrow = player != null && mc.getConnection() != null && mc.screen == null && mc.getOverlay() == null;
        while (HOLSTER_THROW.consumeClick()) {
            if (canThrow) {
                PacketDistributor.sendToServer(SpinEquipment.HolsterThrowPayload.INSTANCE);
                canThrow = false;
            }
        }
        if (player != sailPlayer) {
            sailPlayer = player;
            sailing = false;
        }
    }

    private static void tickSail(PlayerTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getEntity() != player || player == null) return;
        if (player != sailPlayer) {
            sailPlayer = player;
            sailing = false;
        }
        sailing = SpinEquipment.shouldSail(player, sailing);
        if (sailing && player.getDeltaMovement().y < 0.0) {
            player.setDeltaMovement(SpinSail.sail(player.getDeltaMovement(), player.getLookAngle(),
                    SpinConfig.SAIL_MAX_FALL_SPEED.get(), SpinConfig.SAIL_FORWARD_PUSH.get(),
                    SpinConfig.SAIL_MAX_HORIZONTAL_SPEED.get()));
        }
    }

    public static boolean isSailing() {
        return sailing;
    }
}
