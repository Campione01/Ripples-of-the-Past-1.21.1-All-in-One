package rotp.addons.sexpistols.client;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.accessory.MistaHatItem;
import rotp.addons.sexpistols.network.MistaHatAmmoPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class MistaHatClientEvents {
    private MistaHatClientEvents() {}

    // The rotp-next-album shoes do not know this mod's ammo; show their count here.
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!MistaHatItem.isMistaShoes(stack)) {
            return;
        }
        event.getToolTip().add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.ammo",
                        MistaHatItem.storedAmmo(stack),
                        MistaHatItem.MAX_STORED_AMMO)
                .withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.translatable(
                        "item.rotp_sex_pistols.mista_suit_hat.transfer")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @SubscribeEvent
    public static void onMouseButtonPressed(
            ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1
                || !(event.getScreen()
                        instanceof AbstractContainerScreen<?> screen)
                || Minecraft.getInstance().player == null
                || Minecraft.getInstance().player
                        .getAbilities().instabuild) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) {
            return;
        }
        ItemStack slotStack = slot.getItem();
        ItemStack carried = screen.getMenu().getCarried();
        if (!canHandle(slotStack, carried)) {
            return;
        }
        int menuSlot = screen.getMenu().slots.indexOf(slot);
        if (menuSlot < 0) {
            return;
        }
        PacketDistributor.sendToServer(
                new MistaHatAmmoPayload(
                        screen.getMenu().containerId,
                        menuSlot));
        event.setCanceled(true);
    }

    private static boolean canHandle(
            ItemStack slotStack, ItemStack carried) {
        if (MistaHatItem.isAmmoContainer(slotStack)) {
            return carried.isEmpty()
                    ? MistaHatItem.storedAmmo(slotStack) > 0
                    : MistaHatItem.isAmmoItem(carried)
                            && MistaHatItem.remainingCapacity(
                                    slotStack) > 0;
        }
        return MistaHatItem.isAmmoContainer(carried)
                && MistaHatItem.isAmmoItem(slotStack)
                && MistaHatItem.remainingCapacity(carried) > 0;
    }
}
