package rotp.addons.sexpistols.client;

import java.util.List;

import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsMemberState;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

// 0.1.13 SexPistolsRemoteNumberMarker: the member's number over each member
// sent away from the user.
public final class SexPistolsRemoteNumberMarker extends MarkerRenderer {
    private final int number;

    public SexPistolsRemoteNumberMarker(Minecraft minecraft, int number) {
        super(SexPistolsIds.id("textures/gui/number_no" + number + ".png"),
                minecraft);
        this.number = number;
        renderThroughBlocks = true;
        useStandSkinColor = true;
    }

    @Override
    protected boolean shouldRender() {
        if (mc.player == null
                || mc.level == null
                || !mc.player.hasData(
                        SexPistolsAttachments.SEX_PISTOLS_COORDINATOR)) {
            return false;
        }
        StandPower power = StandPower.get(mc.player);
        return power != null
                && power.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get()
                && power.isSummoned();
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> markers, float partialTick) {
        Entity entity = mc.level.getEntity(
                SexPistolsAttachments.coordinator(mc.player)
                        .childEntityId(number));
        if (entity instanceof SexPistolsEntity member
                && member.isAlive()
                && member.getMemberState()
                        == SexPistolsMemberState.SCOUTING) {
            markers.add(new MarkerInstance(
                    member.getPosition(partialTick).add(
                            0.0D, member.getBbHeight() + 0.45D, 0.0D),
                    true));
        }
    }
}
