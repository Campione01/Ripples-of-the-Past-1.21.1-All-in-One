package rotp.addons.nextalbum.client;

import java.util.List;

import rotp.core.api.client.render.PlayerSkinTextureProviders;
import rotp.core.api.client.render.ScopedPlayerModelVisibility;
import rotp.core.api.client.render.ScopedPlayerModelVisibility.Part;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem.Suit;
import rotp.addons.nextalbum.network.ProsciuttoSuitModePayload;
import rotp.addons.nextalbum.state.ProsciuttoShadow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.network.PacketDistributor;

/** Next Album 0.1.13 ProsciuttoSuitClientEvents and the suit overlays. */
@EventBusSubscriber(modid = NextAlbumIds.MOD_ID, value = Dist.CLIENT)
public final class CharacterSuitClientEvents {
    // 0.1.13 AbstractClientPlayerEntityMixin.getSkinTextureLocation: a
    // shadow is drawn with the all-dark skin of its model type.
    private static final ResourceLocation SHADOW_PLAYER =
            NextAlbumIds.id("textures/entity/shadow_player.png");
    private static final ResourceLocation SHADOW_PLAYER_SLIM =
            NextAlbumIds.id("textures/entity/shadow_player_slim.png");

    private CharacterSuitClientEvents() {}

    static void registerShadowSkin() {
        PlayerSkinTextureProviders.register(
                NextAlbumIds.id("prosciutto_shadow"),
                (player, skin) -> ProsciuttoShadow.isShadow(player)
                        ? skin.model() == PlayerSkin.Model.SLIM
                                ? SHADOW_PLAYER_SLIM : SHADOW_PLAYER
                        : null);
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof Player player
                && ProsciuttoShadow.isShadow(player)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    // Right-clicking a worn Prosciutto piece with the full set cycles the
    // look (default, no coat, stealth) instead of picking it up.
    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Slot slot = screen.getSlotUnderMouse();
        if (player == null || slot == null) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (!CharacterSuitArmorItem.isSuit(stack, Suit.PROSCIUTTO)
                || !CharacterSuitArmorItem.hasFullSet(player, Suit.PROSCIUTTO)) {
            return;
        }
        for (EquipmentSlot armorSlot : List.of(
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            if (player.getItemBySlot(armorSlot) == stack) {
                PacketDistributor.sendToServer(
                        ProsciuttoSuitModePayload.INSTANCE);
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void hideSuitOverlays(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        var model = event.getRenderer().getModel();
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem()
                instanceof CharacterSuitArmorItem) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player, model,
                    Part.JACKET, Part.LEFT_SLEEVE, Part.RIGHT_SLEEVE);
        }
        if (isSuitPiece(player.getItemBySlot(EquipmentSlot.LEGS))
                || isSuitPiece(player.getItemBySlot(EquipmentSlot.FEET))) {
            ScopedPlayerModelVisibility.hidePartsForCurrentRender(
                    player, model, Part.LEFT_PANTS, Part.RIGHT_PANTS);
        }
    }

    private static boolean isSuitPiece(ItemStack stack) {
        return stack.getItem() instanceof CharacterSuitArmorItem item
                && item.getType() != ArmorItem.Type.CHESTPLATE;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ProsciuttoShadow.clearClient();
    }
}
