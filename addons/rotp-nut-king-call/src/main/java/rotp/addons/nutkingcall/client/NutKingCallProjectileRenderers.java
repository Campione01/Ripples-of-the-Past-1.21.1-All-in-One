package rotp.addons.nutkingcall.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;

import rotp.addons.nutkingcall.NutKingCallAddon;
import rotp.addons.nutkingcall.entity.NutKingCallAttachedNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNailEntity;
import rotp.addons.nutkingcall.entity.NutKingCallNutEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 1.16 NutKingCallNailRenderer, NutKingCallNutRenderer and NutKingCallAttachedNailRenderer. */
public final class NutKingCallProjectileRenderers {
    public static final ResourceLocation NAIL_TEXTURE = NutKingCallAddon.id("textures/entity/nut_king_call_nail.png");
    public static final ResourceLocation NUT_TEXTURE = NutKingCallAddon.id("textures/entity/nut_king_call_nut.png");

    private NutKingCallProjectileRenderers() {}

    public static final class Nail
            extends SimpleEntityRenderer<NutKingCallNailEntity, NutKingCallNailModel<NutKingCallNailEntity>> {
        public Nail(EntityRendererProvider.Context context) {
            super(context);
            initTexture(NAIL_TEXTURE, false);
            initModel(new NutKingCallNailModel<>(context.bakeLayer(NutKingCallNailModel.LAYER), true));
            offsetModelByEntityHeight(false);
        }
    }

    public static final class Nut
            extends SimpleEntityRenderer<NutKingCallNutEntity, NutKingCallNutModel<NutKingCallNutEntity>> {
        public Nut(EntityRendererProvider.Context context) {
            super(context);
            initTexture(NUT_TEXTURE, false);
            initModel(new NutKingCallNutModel<>(context.bakeLayer(NutKingCallNutModel.LAYER)));
            offsetModelByEntityHeight(false);
        }
    }

    public static final class AttachedNail extends SimpleEntityRenderer<
            NutKingCallAttachedNailEntity, NutKingCallNailModel<NutKingCallAttachedNailEntity>> {
        public AttachedNail(EntityRendererProvider.Context context) {
            super(context);
            initTexture(NAIL_TEXTURE, false);
            initModel(new NutKingCallNailModel<>(context.bakeLayer(NutKingCallNailModel.LAYER), false));
            offsetModelByEntityHeight(false);
        }
    }
}
