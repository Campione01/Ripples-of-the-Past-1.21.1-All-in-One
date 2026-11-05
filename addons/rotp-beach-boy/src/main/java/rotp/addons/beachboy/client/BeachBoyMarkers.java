package rotp.addons.beachboy.client;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.addons.beachboy.entity.BeachBoyHookEntity;
import rotp.addons.beachboy.util.BeachBoyStandUtil;
import rotp.core.client.ui.marker.MarkerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 1.16 BeachBoyEmbedMarker and BeachBoyTrapMarker: icons over the hooked creature and the trap block. */
public final class BeachBoyMarkers {
    private BeachBoyMarkers() {}

    public static void register(Minecraft mc) {
        MarkerRenderer.registerMarkerRenderer(new Embed(mc));
        MarkerRenderer.registerMarkerRenderer(new Trap(mc));
    }

    static final class Embed extends MarkerRenderer {
        Embed(Minecraft mc) {
            super(BeachBoyAddon.id("textures/action/beach_boy_retract_hook.png"), mc);
            renderThroughBlocks = true;
        }

        @Override
        protected boolean shouldRender() {
            return mc.player != null && BeachBoyStandUtil.isBeachBoyUser(mc.player);
        }

        @Override
        protected void updatePositions(List<MarkerInstance> list, float partialTick) {
            if (mc.player == null || mc.level == null) {
                return;
            }
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(mc.player);
            Entity host = hook != null ? hook.getHookedEntity() : null;
            if (host != null && host.isAlive()) {
                list.add(new MarkerInstance(host.getRopeHoldPosition(partialTick).add(0.0, host.getBbHeight() * 1.1, 0.0),
                        true));
            }
        }
    }

    static final class Trap extends MarkerRenderer {
        Trap(Minecraft mc) {
            super(BeachBoyAddon.id("textures/action/beach_boy_trap.png"), mc);
            renderThroughBlocks = true;
        }

        @Override
        protected boolean shouldRender() {
            return mc.player != null && BeachBoyStandUtil.isBeachBoyUser(mc.player) && trapBlock() != null;
        }

        @Override
        protected void updatePositions(List<MarkerInstance> list, float partialTick) {
            BlockPos pos = trapBlock();
            if (pos != null) {
                list.add(new MarkerInstance(markerPosForBlock(pos), true));
            }
        }

        @Nullable
        private BlockPos trapBlock() {
            if (mc.player == null) {
                return null;
            }
            BeachBoyHookEntity hook = BeachBoyStandUtil.findHook(mc.player);
            if (hook == null || !hook.isTrapArmed()) {
                return null;
            }
            Optional<BlockPos> trap = hook.getTrapBlockPos();
            return trap.orElse(null);
        }

        private Vec3 markerPosForBlock(BlockPos pos) {
            if (mc.level == null) {
                return Vec3.atCenterOf(pos).add(0.0, 0.55, 0.0);
            }
            BlockState state = mc.level.getBlockState(pos);
            VoxelShape shape = state.getShape(mc.level, pos);
            AABB bounds = shape.isEmpty() ? new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) : shape.bounds();
            return new Vec3(pos.getX() + (bounds.minX + bounds.maxX) * 0.5, pos.getY() + bounds.maxY + 0.18,
                    pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5);
        }
    }
}
