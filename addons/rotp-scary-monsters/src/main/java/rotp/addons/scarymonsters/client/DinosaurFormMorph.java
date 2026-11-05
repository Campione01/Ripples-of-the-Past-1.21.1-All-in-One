package rotp.addons.scarymonsters.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * 1.16 DinoFormMorphHandler: a player in dinosaur form is drawn as a client-only dinosaur that copies its pose, at
 * 1.7x the player's box, with the controlled model and texture; the first-person hands are hidden. The player itself
 * keeps walking, so there is nothing to steer and nothing to get off.
 */
@EventBusSubscriber(modid = ScaryMonstersAddon.MOD_ID, value = Dist.CLIENT)
public final class DinosaurFormMorph {
    private static final float SOURCE_SIZE_FACTOR = 1.7F;
    /** 1.16 DinosaurEntity.setPlayerControlled gave the proxy this speed, and the walk clip runs at speed / 0.3. */
    private static final double CONTROLLED_MOVEMENT_SPEED = 1.3D;
    private static final Map<UUID, ScaryDinosaurEntity> PROXIES = new HashMap<>();

    private DinosaurFormMorph() {}

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!ScaryMonstersAddon.state(player).dinosaurForm()) {
            PROXIES.remove(player.getUUID());
            return;
        }
        ScaryDinosaurEntity proxy = proxy(player);
        if (proxy == null) return;
        float partialTick = event.getPartialTick();
        copyPose(player, proxy, partialTick);
        event.setCanceled(true);
        Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy).render(
                proxy,
                Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot),
                partialTick,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPackedLight());
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ScaryMonstersAddon.state(player).dinosaurForm()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PROXIES.clear();
    }

    @Nullable
    private static ScaryDinosaurEntity proxy(Player player) {
        ScaryDinosaurEntity proxy = PROXIES.get(player.getUUID());
        if (proxy == null || proxy.level() != player.level()) {
            proxy = ScaryMonstersAddon.DINOSAUR.get().create(player.level());
            if (proxy == null) return null;
            proxy.setPlayerControlled(true);
            AttributeInstance speed = proxy.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(CONTROLLED_MOVEMENT_SPEED);
            PROXIES.put(player.getUUID(), proxy);
        }
        return proxy;
    }

    private static void copyPose(Player player, ScaryDinosaurEntity proxy, float partialTick) {
        proxy.setPos(player.getX(), player.getY(), player.getZ());
        proxy.xo = player.xo;
        proxy.yo = player.yo;
        proxy.zo = player.zo;
        proxy.setYRot(player.getYRot());
        proxy.yRotO = player.yRotO;
        proxy.setXRot(player.getXRot());
        proxy.xRotO = player.xRotO;
        proxy.yBodyRot = player.yBodyRot;
        proxy.yBodyRotO = player.yBodyRotO;
        proxy.yHeadRot = player.yHeadRot;
        proxy.yHeadRotO = player.yHeadRotO;
        proxy.tickCount = player.tickCount;
        proxy.swinging = player.swinging;
        proxy.swingTime = player.swingTime;
        // twice, so the proxy's previous and current walk speed are both the player's
        float walkSpeed = player.walkAnimation.speed(partialTick);
        proxy.walkAnimation.update(walkSpeed, 1.0F);
        proxy.walkAnimation.update(walkSpeed, 1.0F);
        proxy.setInvisible(player.isInvisible());
        proxy.setSourceSize(player.getBbWidth() * SOURCE_SIZE_FACTOR, player.getBbHeight() * SOURCE_SIZE_FACTOR);
    }
}
