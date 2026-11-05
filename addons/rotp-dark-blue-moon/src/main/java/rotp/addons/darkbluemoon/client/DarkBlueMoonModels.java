package rotp.addons.darkbluemoon.client;

import java.io.Reader;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** The small code models of the 1.16 renderers, and the shipped geo models the 1.16 renderers read at run time. */
public final class DarkBlueMoonModels {
    public static final ModelLayerLocation SCALE_SHOT = layer("scale_shot");
    public static final ModelLayerLocation BARNACLE = layer("barnacle");
    public static final ModelLayerLocation BARNACLE_PULL = layer("barnacle_pull");

    private DarkBlueMoonModels() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(DarkBlueMoonAddon.id(name), "main");
    }

    /** 1.16 ScaleShotRenderer: ModelRenderer(16, 16, 0, 0).addBox(-1, -1.5, -2.5, 2, 3, 5). */
    public static LayerDefinition scaleShot() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("scale",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.5F, -2.5F, 2.0F, 3.0F, 5.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    /** 1.16 BarnacleRenderer: ModelRenderer(8, 8, 0, 0).addBox(-1, -1, -1, 2, 2, 2). */
    public static LayerDefinition barnacle() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("barnacle",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 8, 8);
    }

    /** 1.16 BarnaclePullModel: a 16x16 model whose repeated part is addBox(-1, -1, -2, 2, 2, 4), 4 long. */
    public static LayerDefinition barnaclePull() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("barnacle",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 4.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    /**
     * Reads a geo model shipped in assets/darkbluemoon/geo, as 1.16's DarkBlueMoonWhirlpoolModel read whirlpool.geo.json
     * through the core's Gecko parser. Null if it is missing or unreadable.
     */
    @Nullable
    public static ModelPart loadGeoBone(ResourceManager resourceManager, String fileName, String boneName) {
        ResourceLocation path = DarkBlueMoonAddon.id("geo/" + fileName + ".geo.json");
        Resource resource = resourceManager.getResource(path).orElse(null);
        if (resource == null) {
            return null;
        }
        try (Reader reader = resource.openAsReader()) {
            LayerDefinition definition = ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO);
            ModelPart root = definition.bakeRoot();
            return root.hasChild(boneName) ? root.getChild(boneName) : null;
        }
        catch (Exception e) {
            return null;
        }
    }
}
