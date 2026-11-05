package rotp.addons.sexpistols.client;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;

import rotp.core.client.entityrender.parsemodel.ParseModEntityModel;
import rotp.core.client.entityrender.parsemodel.ParseModEntityModel.ModelFormat;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.item.RevolverStats;
import rotp.addons.sexpistols.content.revolver.state.RevolverState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RevolverRenderer
        extends BlockEntityWithoutLevelRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation GEOMETRY =
            SexPistolsIds.id("geo/revolver.geo.json");
    private static final ResourceLocation TEXTURE =
            SexPistolsIds.id("textures/item/revolver.png");
    // Next Album 0.1.13 "revolver" model: bones gun/magazine, built along Z.
    private static final ResourceLocation HEAVY_GEOMETRY =
            SexPistolsIds.id("geo/heavy_revolver.geo.json");
    private static final ResourceLocation HEAVY_TEXTURE =
            SexPistolsIds.id("textures/item/heavy_revolver.png");
    private static final long RELOAD_TRANSITION_NANOS =
            83_300_000L;
    private static final Map<ItemStack, ReloadPose>
            RELOAD_POSES = new WeakHashMap<>();

    private static ModelPart modelRoot;
    private static ModelPart magazine;
    private static ModelPart heavyRoot;
    private static ModelPart heavyMagazine;

    public RevolverRenderer() {
        super(
                Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static synchronized void reload(
            ResourceManager resourceManager) {
        try {
            var resource = resourceManager
                    .getResource(GEOMETRY)
                    .orElseThrow(() -> new IOException(
                            "Missing revolver geometry "
                                    + GEOMETRY));
            try (var reader = resource.openAsReader()) {
                modelRoot = ParseModEntityModel.parse(
                                JsonParser.parseReader(reader),
                                ModelFormat.GECKO)
                        .bakeRoot();
            }
            magazine = modelRoot
                    .getChild("bone4")
                    .getChild("magazine");
        }
        catch (IOException | RuntimeException exception) {
            modelRoot = null;
            magazine = null;
            LOGGER.error(
                    "Failed to load Sex Pistols revolver geometry",
                    exception);
        }
        try {
            var resource = resourceManager
                    .getResource(HEAVY_GEOMETRY)
                    .orElseThrow(() -> new IOException(
                            "Missing revolver geometry "
                                    + HEAVY_GEOMETRY));
            try (var reader = resource.openAsReader()) {
                heavyRoot = ParseModEntityModel.parse(
                                JsonParser.parseReader(reader),
                                ModelFormat.GECKO)
                        .bakeRoot();
            }
            heavyMagazine = heavyRoot
                    .getChild("gun")
                    .getChild("magazine");
        }
        catch (IOException | RuntimeException exception) {
            heavyRoot = null;
            heavyMagazine = null;
            LOGGER.error(
                    "Failed to load Sex Pistols heavy revolver geometry",
                    exception);
        }
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (stack.getItem() instanceof RevolverItem revolver
                && revolver.stats() == RevolverStats.HEAVY) {
            renderHeavy(
                    stack,
                    displayContext,
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay);
            return;
        }
        if (modelRoot == null || magazine == null) {
            reload(Minecraft.getInstance()
                    .getResourceManager());
        }
        if (modelRoot == null || magazine == null) {
            return;
        }

        modelRoot.getAllParts().forEach(ModelPart::resetPose);
        RevolverState state = RevolverItem.state(stack);
        float reloadOpenProgress = reloadOpenProgress(
                stack, state.reloadMode());
        magazine.xRot += state.selectedChamber()
                * -60.0F * Mth.DEG_TO_RAD;
        magazine.xRot += reloadOpenProgress
                * 29.5F * Mth.DEG_TO_RAD;
        magazine.y += reloadOpenProgress * 0.4F;
        magazine.z -= reloadOpenProgress;

        poseStack.pushPose();
        boolean firstPerson = isFirstPerson(displayContext);
        boolean aiming = state.aiming() && !state.reloadMode();
        applyHandPresentationTransform(poseStack, displayContext);
        if (firstPerson) {
            applyFirstPersonStateTransform(
                    poseStack,
                    displayContext,
                    state.reloadMode(),
                    aiming);
            RevolverFirstPersonMotion.apply(
                    poseStack, handSide(displayContext));
        }
        SexPistolsPresentationDiagnostics.recordRevolverPose(
                firstPerson,
                aiming,
                state.reloadMode(),
                reloadOpenProgress,
                state.selectedChamber());
        // ItemRenderer contributes -0.5Y, while the Gecko parser uses a
        // 24-pixel Y baseline. 2.0 - 0.5 - (24 - y) / 16 restores y / 16,
        // matching the donor GeoItemRenderer item space.
        poseStack.translate(0.5D, 2.0D, 0.5D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(TEXTURE));
        modelRoot.render(
                poseStack,
                vertices,
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    // 0.1.13 RevolverGeoRenderer for its own models: the magazine turns about
    // Z (60 degrees a chamber, 29.5 open) and swings out 1 pixel sideways and
    // 0.4 down; the gun is set in item space by applyGeoToItemSpace.
    private void renderHeavy(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (heavyRoot == null || heavyMagazine == null) {
            reload(Minecraft.getInstance()
                    .getResourceManager());
        }
        if (heavyRoot == null || heavyMagazine == null) {
            return;
        }
        heavyRoot.getAllParts().forEach(ModelPart::resetPose);
        RevolverState state = RevolverItem.state(stack);
        float open = reloadOpenProgress(stack, state.reloadMode());
        heavyMagazine.zRot += (open * 29.5F
                - state.selectedChamber() * 60.0F) * Mth.DEG_TO_RAD;
        heavyMagazine.x -= open;
        heavyMagazine.y += open * 0.4F;

        poseStack.pushPose();
        boolean firstPerson = isFirstPerson(displayContext);
        boolean aiming = state.aiming() && !state.reloadMode();
        poseStack.translate(0.5D, 0.51D, 0.5D);
        if (firstPerson) {
            poseStack.scale(0.75F, 0.75F, 0.75F);
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(6.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.translate(0.0D, -2.4D / 16.0D, -1.2D / 16.0D);
        if (firstPerson) {
            applyHeavyFirstPersonStateTransform(
                    poseStack,
                    displayContext,
                    state.reloadMode(),
                    aiming);
            RevolverFirstPersonMotion.apply(
                    poseStack, handSide(displayContext));
        }
        SexPistolsPresentationDiagnostics.recordRevolverPose(
                firstPerson,
                aiming,
                state.reloadMode(),
                open,
                state.selectedChamber());
        // Gecko item space from the parsed model (see renderByItem).
        poseStack.translate(0.0D, 1.5D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(HEAVY_TEXTURE));
        heavyRoot.render(
                poseStack,
                vertices,
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    // 0.1.13 applyFirstPersonStateTransform (one-handed hold).
    private static void applyHeavyFirstPersonStateTransform(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            boolean reloadMode,
            boolean aiming) {
        float handSide = displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                ? -1.0F
                : 1.0F;
        if (reloadMode) {
            poseStack.translate(
                    0.08F * handSide, 0.16F, -0.22F);
            poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    40.0F * handSide));
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    5.0F * handSide));
        }
        else if (aiming) {
            poseStack.translate(0.0D, 0.08D, -0.65D * handSide);
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    -2.7F * handSide));
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    -5.2F * handSide));
        }
    }

    private static float handSide(ItemDisplayContext displayContext) {
        return displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                ? -1.0F
                : 1.0F;
    }

    private static boolean isFirstPerson(
            ItemDisplayContext displayContext) {
        return displayContext
                        == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
    }

    private static void applyHandPresentationTransform(
            PoseStack poseStack,
            ItemDisplayContext displayContext) {
        switch (displayContext) {
            case THIRD_PERSON_RIGHT_HAND -> {
                // Match CROSSBOW_HOLD's inward arm yaw, rather than 45 degrees.
                poseStack.mulPose(Axis.YP.rotation(-0.3F));
                poseStack.scale(1.25F, 1.25F, 1.25F);
            }
            case THIRD_PERSON_LEFT_HAND -> {
                poseStack.mulPose(Axis.YP.rotation(0.3F));
                poseStack.scale(1.25F, 1.25F, 1.25F);
            }
            default -> {
            }
        }
    }

    private static void applyFirstPersonStateTransform(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            boolean reloadMode,
            boolean aiming) {
        float handSide = displayContext
                        == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                ? -1.0F
                : 1.0F;
        if (reloadMode) {
            // Keep the cylinder-inspection pose when the idle display is
            // changed from a side view to the same forward axis as aiming.
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(7.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    90.0F - 55.0F * handSide));
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            poseStack.translate(
                    0.08F * handSide, 0.16F, -0.22F);
            poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(
                    40.0F * handSide));
            poseStack.mulPose(Axis.ZP.rotationDegrees(
                    5.0F * handSide));
            poseStack.scale(1.2F, 1.2F, 1.2F);
        }
        else if (aiming) {
            // The baked hand display already points the muzzle forward.
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.scale(0.85F, 0.85F, 0.85F);
            poseStack.translate(0.0D, 0.0D, -0.38D * handSide);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
        }
    }

    private static synchronized float reloadOpenProgress(
            ItemStack stack, boolean reloadMode) {
        long now = System.nanoTime();
        ReloadPose pose = RELOAD_POSES.get(stack);
        if (pose == null) {
            pose = new ReloadPose(reloadMode, reloadMode ? 1.0F : 0.0F);
            RELOAD_POSES.put(stack, pose);
            return pose.progress;
        }
        if (pose.reloadMode != reloadMode) {
            pose.reloadMode = reloadMode;
            if (reloadMode) {
                pose.progress = 0.0F;
                pose.transitionStarted = now;
            }
            else {
                pose.progress = 0.0F;
                pose.transitionStarted = 0L;
            }
        }
        if (pose.reloadMode && pose.progress < 1.0F) {
            pose.progress = Mth.clamp(
                    (float) (now - pose.transitionStarted)
                            / RELOAD_TRANSITION_NANOS,
                    0.0F,
                    1.0F);
        }
        return pose.progress;
    }

    private static final class ReloadPose {
        private boolean reloadMode;
        private long transitionStarted;
        private float progress;

        private ReloadPose(boolean reloadMode, float progress) {
            this.reloadMode = reloadMode;
            this.progress = progress;
        }
    }
}
