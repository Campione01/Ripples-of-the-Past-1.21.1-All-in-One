package rotp.addons.sexpistols.content.revolver.item;

import javax.annotation.Nullable;

import rotp.core.api.client.render.HeldItemButtonClaims;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.registry.SexPistolsItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The buttons the revolver takes from the Stand's moves, for the HUD (HeldItemButtonClaims). The left button is always the
 * gun's (owner M0114). The right button is the gun's in the main hand with no Stand out, open (loads) or closed (aims): as
 * in 1.16, a Stand that is out keeps it (InputHandler.handleMouseClickPowerHud), and the core's hold key gives it back.
 */
public final class RevolverButtonClaim {
    private RevolverButtonClaim() {}

    public static boolean claims(@Nullable Player player, ItemStack held, HeldItemButtonClaims.Button button) {
        boolean revolver = held.is(SexPistolsItems.REVOLVER.get()) || held.is(SexPistolsItems.HEAVY_REVOLVER.get());
        if (!revolver) {
            return false;
        }
        if (button == HeldItemButtonClaims.Button.LEFT) {
            return true;
        }
        if (player == null || player.getMainHandItem() != held) {
            return false;
        }
        StandPower power = StandPower.get(player);
        return power == null || !power.isSummoned();
    }
}
