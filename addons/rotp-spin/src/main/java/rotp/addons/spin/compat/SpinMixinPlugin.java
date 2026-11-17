package rotp.addons.spin.compat;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** The provenance kept on every MobEffectInstance only serves D4C's Love Train: without D4C no player carries it. */
public final class SpinMixinPlugin implements IMixinConfigPlugin {
    private static final String EFFECT_INSTANCE_MIXIN = "rotp.addons.spin.mixin.SpinLoveTrainEffectInstanceMixin";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !EFFECT_INSTANCE_MIXIN.equals(mixinClassName) || LoadingModList.get().getModFileById("rotp_d4c") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {}

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
