package rotp.addons.kingcrimsonvoxy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Gate of the optional mixins. A mixin applies only if every mod is there and its target has the members it hooks: a
 * failed injection could take other mods' mixins on the same class down with it, so a mismatch must be seen before that.
 */
public final class KcVoxyMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "rotp.addons.kingcrimsonvoxy.mixin.";
    private static final List<String> MODS = List.of("voxy", "sodium", "iris", "rotp_kingcrimson");
    private static final Map<String, Target> TARGETS = Map.of(
            "VoxyShaderLoaderMixin", new Target(
                    "me.cortex.voxy.client.core.gl.shader.ShaderLoader",
                    List.of("parse(Ljava/lang/String;)Ljava/lang/String;"), List.of()),
            "VoxyShaderBuilderMixin", new Target(
                    "me.cortex.voxy.client.core.gl.shader.Shader$Builder",
                    List.of("compile()Lme/cortex/voxy/client/core/gl/shader/Shader;"), List.of("sources")),
            "VoxyRenderCountMixin", new Target(
                    "me.cortex.voxy.client.core.VoxyRenderSystem",
                    List.of("renderOpaque(Lme/cortex/voxy/client/core/rendering/Viewport;)V"), List.of()),
            "SodiumChunkRendererMixin", new Target(
                    "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",
                    List.of("render(Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;"
                            + "Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;"
                            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderListIterable;"
                            + "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;"
                            + "Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Z)V"), List.of()),
            "ObserverPoliciesMixin", new Target(
                    "rotp.core.api.client.render.ObserverWorldRenderPolicies",
                    List.of("suppresses(Lrotp/core/api/client/render/ObserverWorldRenderPolicyProvider$Pass;)Z"), List.of()),
            "IrisEntityIdMixin", new Target(
                    "net.irisshaders.iris.uniforms.CapturedRenderingState",
                    List.of("setCurrentEntity(I)V"), List.of()));

    private boolean gate;

    @Override
    public void onLoad(String mixinPackage) {
        LoadingModList mods = LoadingModList.get();
        gate = mods != null && MODS.stream().allMatch(id -> mods.getModFileById(id) != null);
        if (gate) {
            KcVoxyStatus.setApplicable();
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!gate) {
            return false;
        }
        String name = mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName;
        if (name.equals("VoxyRenderCountMixin") && !Boolean.getBoolean("rotp.kcvoxy.probe") && !Boolean.getBoolean("rotp.kcvoxy.trace")) {
            return false;
        }
        Target target = TARGETS.get(name);
        if (target == null || !target.className.equals(targetClassName)) {
            KcVoxyStatus.fail("unknown target of " + name + ": " + targetClassName);
            return false;
        }
        String missing = target.missing();
        if (missing != null) {
            KcVoxyStatus.fail(missing);
            return false;
        }
        return true;
    }

    private record Target(String className, List<String> methods, List<String> fields) {
        private String missing() {
            ClassNode node;
            try {
                // The loader has no untransformed view of a class: false only throws.
                node = MixinService.getService().getBytecodeProvider().getClassNode(className, true);
            } catch (Exception | LinkageError failure) {
                return "class " + className + " is absent";
            }
            for (String method : methods) {
                boolean found = false;
                for (MethodNode candidate : node.methods) {
                    found |= method.equals(candidate.name + candidate.desc);
                }
                if (!found) {
                    return className + " has no " + method;
                }
            }
            for (String field : fields) {
                boolean found = false;
                for (FieldNode candidate : node.fields) {
                    found |= field.equals(candidate.name);
                }
                if (!found) {
                    return className + " has no field " + field;
                }
            }
            return null;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
