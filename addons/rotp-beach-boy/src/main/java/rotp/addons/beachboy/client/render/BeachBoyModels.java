package rotp.addons.beachboy.client.render;

import java.io.Reader;

import javax.annotation.Nullable;

import com.google.gson.JsonParser;

import rotp.addons.beachboy.BeachBoyAddon;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The donor's GeckoLib models (geo/beach_boy.geo.json for the rod, geo/beach_boy_hook.geo.json for the hook), read
 * with the core's Gecko parser on every resource reload; 1.16 drew them through GeckoLib.
 */
public final class BeachBoyModels implements ResourceManagerReloadListener {
    public static final BeachBoyModels INSTANCE = new BeachBoyModels();
    @Nullable private ModelPart rod;
    @Nullable private ModelPart rodRoll;
    @Nullable private ModelPart hook;

    private BeachBoyModels() {}

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        rod = load(resourceManager, "beach_boy");
        rodRoll = rod != null ? find(rod, "roll") : null;
        hook = load(resourceManager, "beach_boy_hook");
    }

    @Nullable
    public ModelPart rod() {
        return rod;
    }

    @Nullable
    public ModelPart rodRoll() {
        return rodRoll;
    }

    @Nullable
    public ModelPart hook() {
        return hook;
    }

    @Nullable
    private static ModelPart find(ModelPart part, String name) {
        if (part.hasChild(name)) {
            return part.getChild(name);
        }
        for (String childName : java.util.List.of("group", "bb_main")) {
            if (part.hasChild(childName)) {
                ModelPart found = find(part.getChild(childName), name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private static ModelPart load(ResourceManager resourceManager, String name) {
        ResourceLocation path = BeachBoyAddon.id("geo/" + name + ".geo.json");
        Resource resource = resourceManager.getResource(path).orElse(null);
        if (resource == null) {
            return null;
        }
        try (Reader reader = resource.openAsReader()) {
            return ParseModEntityModel.parse(JsonParser.parseReader(reader), ModelFormat.GECKO).bakeRoot();
        }
        catch (Exception e) {
            return null;
        }
    }
}
