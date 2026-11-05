package rotp.addons.sexpistols.client;

import java.util.List;

import rotp.core.client.ui.marker.MarkerRenderer;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsAim;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsForcedTarget;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsTargetMode;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// 0.1.13 SexPistolsForcedTargetMarker: a marker over every forced target
// (outlined while looked at) and, in AIM mode, over the aimed target.
public final class SexPistolsForcedTargetMarker extends MarkerRenderer {
    public SexPistolsForcedTargetMarker(Minecraft minecraft) {
        super(SexPistolsIds.SEX_PISTOLS_FORCED_TARGET_PATH, minecraft);
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
                        == SexPistolsStands.SEX_PISTOLS.get();
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> markers, float partialTick) {
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(mc.player);
        List<SexPistolsForcedTarget> targets =
                coordinator.forcedTargets();
        boolean aimMode =
                coordinator.targetMode() == SexPistolsTargetMode.AIM;
        if (targets.isEmpty() && !aimMode) {
            return;
        }
        Entity lookedEntity = null;
        BlockPos lookedBlock = null;
        if (!targets.isEmpty()) {
            HitResult look = SexPistolsAim.forcedLookTarget(mc.player);
            if (look instanceof EntityHitResult entityHit) {
                lookedEntity = entityHit.getEntity();
            }
            else if (look instanceof BlockHitResult blockHit
                    && look.getType() == HitResult.Type.BLOCK) {
                lookedBlock = blockHit.getBlockPos();
            }
        }
        for (SexPistolsForcedTarget target : targets) {
            if (!target.isValid(mc.level)) {
                continue;
            }
            if (target.isEntity()) {
                LivingEntity living = target.living(mc.level);
                markers.add(new MarkerInstance(
                        entityMarkerPos(living, partialTick),
                        living == lookedEntity));
            }
            else {
                markers.add(new MarkerInstance(
                        Vec3.atCenterOf(target.blockPos())
                                .add(0.0D, 0.65D, 0.0D),
                        target.blockPos().equals(lookedBlock)));
            }
        }
        if (aimMode) {
            LivingEntity aim = SexPistolsAim.findAimTarget(mc.player);
            if (aim != null) {
                markers.add(new MarkerInstance(
                        entityMarkerPos(aim, partialTick), true));
            }
        }
    }
}
