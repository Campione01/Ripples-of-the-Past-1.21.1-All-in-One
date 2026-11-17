package rotp.addons.kingcrimson.client.epitaph;

import org.slf4j.Logger;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import rotp.addons.kingcrimson.entity.KingCrimsonPhantomCarrierEntity;

/**
 * Draws a phantom of Epitaph where its carrier stands: the phantom itself, by the renderer of its own type, in the
 * look of the foreseen future. Inside the flag, so that what its renderer looks up (a Stand its user, a rider its
 * mount) is a phantom too. A renderer that throws ends the drawing of that phantom, not the frame: it draws on a
 * pose stack of its own.
 */
@OnlyIn(Dist.CLIENT)
public final class EpitaphPhantomRenderer extends EntityRenderer<KingCrimsonPhantomCarrierEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean failureLogged;

    public EpitaphPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(KingCrimsonPhantomCarrierEntity carrier) {
        return null;
    }

    @Override
    public void render(KingCrimsonPhantomCarrierEntity carrier, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int packedLight) {
        Entity puppet = carrier.puppet();
        if (puppet == null || puppet.isRemoved() || !EpitaphClientSession.isPuppet(puppet)) {
            return;
        }
        // A phantom that no frame moves any more stands at its last pose, not between its last two.
        float part = EpitaphClientSession.isResting() ? 1.0F : partialTick;
        PoseStack own = new PoseStack();
        own.last().pose().set(pose.last().pose());
        own.last().normal().set(pose.last().normal());
        boolean active = PhantomContext.active;
        PhantomContext.active = true;
        try {
            EntityRenderer<? super Entity> renderer = entityRenderDispatcher.getRenderer(puppet);
            Vec3 at = puppet.getPosition(part).subtract(carrier.getPosition(partialTick)).add(renderer.getRenderOffset(puppet, part));
            own.translate(at.x, at.y, at.z);
            PhantomLook.EPITAPH_FUTURE.draw(renderer, puppet, Mth.lerp(part, puppet.yRotO, puppet.getYRot()), part, own, buffer);
        }
        catch (RuntimeException failure) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("Epitaph[client-draw] the phantom of a {} could not be drawn", puppet.getType().toShortString(), failure);
            }
        }
        finally {
            PhantomContext.active = active;
        }
    }
}
