package rotp.addons.highwaystar.client;

import java.io.Reader;
import java.util.Map;
import java.util.Optional;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** 1.16 HighwayStarSoleModel: the donor's sole geometry, its root turned by the head's yaw and pitch. */
public final class HighwayStarSoleModel extends EntityModel<HighwayStarSoleEntity> {
    private static final ResourceLocation MODEL = HighwayStarAddon.id("geo/highway_star_sole.geo.json");
    private final ModelPart root;

    public HighwayStarSoleModel() {
        this.root = load();
    }

    private static ModelPart load() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(MODEL);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                ModelPart baked = ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO).bakeRoot();
                return baked.hasChild("root") ? baked.getChild("root") : baked;
            }
            catch (Exception e) {
                LogUtils.getLogger().error("Failed to load Highway Star Sole model", e);
            }
        }
        return new ModelPart(java.util.List.of(), Map.of());
    }

    @Override
    public void setupAnim(HighwayStarSoleEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch) {
        root.yRot = netHeadYaw * ((float) Math.PI / 180.0F);
        root.xRot = headPitch * ((float) Math.PI / 180.0F);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
