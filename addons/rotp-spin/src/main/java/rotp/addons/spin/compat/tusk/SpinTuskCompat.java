package rotp.addons.spin.compat.tusk;

import java.lang.reflect.Method;

import com.mojang.logging.LogUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.power.SpinData;

public final class SpinTuskCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "rotp_t";
    private static final String INFUSION = "rotp_spin_tusk_herb_ticks";
    private static final TagKey<Item> HERBS = TagKey.create(Registries.ITEM, SpinAddon.id("tusk_nail_herbs"));
    private static final ResourceKey<MobEffect> INFINITE = ResourceKey.create(Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "infinite_rotation"));
    private static volatile Api api;

    private SpinTuskCompat() {}

    public static void register(IEventBus bus) {
        if (ModList.get().isLoaded(MOD_ID)) bus.addListener(SpinTuskCompat::setup);
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                api = Api.resolve();
                NeoForge.EVENT_BUS.addListener(SpinTuskCompat::join);
                NeoForge.EVENT_BUS.addListener(SpinTuskCompat::chew);
                NeoForge.EVENT_BUS.addListener(SpinTuskCompat::tick);
            } catch (ReflectiveOperationException | LinkageError error) {
                LOGGER.warn("Spin's optional Tusk compatibility is disabled: unsupported Tusk API", error);
            }
        });
    }

    private static void join(EntityJoinLevelEvent event) {
        Api bridge = api;
        if (bridge == null || event.loadedFromDisk() || event.getLevel().isClientSide()
                || !SpinConfig.COMPAT_TUSK_ENABLED.get() || !bridge.nailType().isInstance(event.getEntity())
                || !(event.getEntity() instanceof Projectile nail) || !(nail.getOwner() instanceof LivingEntity owner)) return;
        int lesson = SpinData.lessonOf(owner);
        if (lesson < 4 || SpinData.goldenMultiplier(event.getLevel(), owner, false) <= 1) return;
        int charge = (lesson >= 5 ? SpinConfig.COMPAT_TUSK_CHARGE_5 : SpinConfig.COMPAT_TUSK_CHARGE_4).get();
        if (charge <= 0) return;
        try {
            bridge.setCharge().invoke(nail, (int) bridge.getCharge().invoke(nail) + charge);
        } catch (ReflectiveOperationException error) {
            disable(bridge, error);
        }
    }

    private static void chew(PlayerInteractEvent.RightClickItem event) {
        Api bridge = api;
        if (bridge == null || !SpinConfig.COMPAT_TUSK_ENABLED.get()
                || !SpinConfig.COMPAT_TUSK_HERBS_ENABLED.get() || !event.getItemStack().is(HERBS)) return;
        Player player = event.getEntity();
        try {
            if (!(boolean) bridge.isTuskUser().invoke(null, player)) return;
        } catch (ReflectiveOperationException error) {
            disable(bridge, error);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var tag = player.getPersistentData();
        int duration = SpinConfig.COMPAT_TUSK_HERB_DURATION.get();
        tag.putInt(INFUSION, TuskHerbRules.extendInfusion(tag.getInt(INFUSION), duration,
                duration * SpinConfig.COMPAT_TUSK_HERB_MAX_STACK.get()));
        event.getItemStack().consume(1, player);
        level.playSound(null, player, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    private static void tick(PlayerTickEvent.Post event) {
        Api bridge = api;
        Player player = event.getEntity();
        if (bridge == null || player.level().isClientSide()) return;
        var tag = player.getPersistentData();
        int remaining = tag.getInt(INFUSION);
        if (remaining > 0) {
            if (--remaining == 0) tag.remove(INFUSION);
            else tag.putInt(INFUSION, remaining);
        }
        if (!SpinConfig.COMPAT_TUSK_ENABLED.get()) return;
        int interval = SpinConfig.COMPAT_TUSK_HERB_INTERVAL.get();
        boolean herb = SpinConfig.COMPAT_TUSK_HERBS_ENABLED.get()
                && TuskHerbRules.growsNailThisTick(remaining, interval, 0, TuskHerbRules.MAX_NAILS);
        boolean canCharge = SpinConfig.COMPAT_TUSK_INFINITE_ENABLED.get()
                && SpinData.lessonOf(player) >= SpinConfig.COMPAT_TUSK_INFINITE_LESSON.get() && hasSuperSpin(player);
        if (!herb && !canCharge) return;
        try {
            if (!(boolean) bridge.isTuskUser().invoke(null, player)) return;
            Object state = bridge.state().invoke(null, player);
            boolean changed = false;
            if (herb) {
                int nails = (int) bridge.nails().invoke(state);
                if (nails < TuskHerbRules.MAX_NAILS) {
                    bridge.snapshot().invoke(state, nails + 1, bridge.regen().invoke(state), bridge.charged().invoke(state));
                    changed = true;
                }
            }
            if (canCharge && (boolean) bridge.isAct4User().invoke(null, player)) {
                changed |= (boolean) bridge.setCharged().invoke(state, true);
            }
            if (changed) bridge.sync().invoke(null, player);
        } catch (ReflectiveOperationException error) {
            disable(bridge, error);
        }
    }

    public static void beforeSpinningHit(LivingEntity victim, LivingEntity owner, boolean chipped) {
        if (api == null || !(victim.level() instanceof ServerLevel level) || !SpinConfig.COMPAT_TUSK_ENABLED.get()) return;
        var effect = BuiltInRegistries.MOB_EFFECT.getHolder(INFINITE).orElse(null);
        if (effect == null || !TuskInfiniteRules.counterRotates(SpinConfig.COMPAT_TUSK_COUNTER_ENABLED.get(),
                victim.hasEffect(effect), chipped, SpinData.lessonOf(owner), SpinConfig.COMPAT_TUSK_INFINITE_LESSON.get(),
                SpinConfig.COMPAT_TUSK_COUNTER_NEEDS_SUPER_SPIN.get(), hasSuperSpin(owner))) return;
        victim.removeEffect(effect);
        if (victim.getVehicle() instanceof LivingEntity mount) mount.removeEffect(effect);
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 12,
                victim.getBbWidth() * 0.5, victim.getBbHeight() * 0.3, victim.getBbWidth() * 0.5, 0.2);
        level.playSound(null, victim, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
    }

    private static boolean hasSuperSpin(LivingEntity owner) {
        return SpinData.of(owner).map(data -> data.hasSuperSpin(owner.level().getGameTime())).orElse(false);
    }

    private static synchronized void disable(Api failed, ReflectiveOperationException error) {
        if (api == failed) {
            api = null;
            LOGGER.warn("Spin's optional Tusk compatibility stopped after a Tusk API failure", error);
        }
    }

    private record Api(Class<?> nailType, Method getCharge, Method setCharge, Method isTuskUser, Method isAct4User,
            Method state, Method nails, Method regen, Method charged, Method snapshot, Method setCharged, Method sync) {
        static Api resolve() throws ReflectiveOperationException {
            ClassLoader loader = SpinTuskCompat.class.getClassLoader();
            var addon = Class.forName("rotp.addons.tusk.TuskAddon", false, loader);
            var nail = Class.forName("rotp.addons.tusk.entity.NailEntity", false, loader);
            var state = addon.getMethod("nailState", Player.class);
            var data = state.getReturnType();
            return new Api(nail, nail.getMethod("getSpinCharge"), nail.getMethod("setSpinCharge", int.class),
                    addon.getMethod("isTuskUser", Player.class), addon.getMethod("isAct4User", LivingEntity.class),
                    state, data.getMethod("nails"), data.getMethod("regenTicksRemaining"),
                    data.getMethod("isInfiniteRotationCharged"),
                    data.getMethod("applySnapshot", int.class, int.class, boolean.class),
                    data.getMethod("setInfiniteRotationCharged", boolean.class), addon.getMethod("syncNailState", Player.class));
        }
    }
}
