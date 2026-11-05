package rotp.addons.darkbluemoon.client;

import java.util.List;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.core.client.ClientPowerCache;
import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** 1.16 BarnacleTrapMarker: the trap icon, outlined, above the block of the local player's Barnacle Trap. */
public class BarnacleTrapMarker extends MarkerRenderer {
    public BarnacleTrapMarker(Minecraft mc) {
        super(DarkBlueMoonAddon.id("textures/action/barnacle_trap_marker.png"), mc);
    }

    @Override
    protected boolean shouldRender() {
        return trapStand() != null;
    }

    @Override
    protected void updatePositions(List<MarkerInstance> list, float partialTick) {
        DarkBlueMoonEntity dbm = trapStand();
        if (dbm != null) {
            BlockPos pos = dbm.getBarnacleTrapPos();
            list.add(new MarkerInstance(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), true));
        }
    }

    private static DarkBlueMoonEntity trapStand() {
        StandPower power = ClientPowerCache.getPower(PowerClass.STAND);
        return power != null && power.getSummonedStandEntity() instanceof DarkBlueMoonEntity dbm && dbm.hasBarnacleTrap()
                ? dbm : null;
    }
}
