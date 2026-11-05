package rotp.addons.gratefuldead.compat;

import java.lang.reflect.Method;

import rotp.addons.gratefuldead.item.IceCubeItem;
import rotp.addons.gratefuldead.service.AgingRate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.ModList;

/**
 * Next Album 0.1.13 TemperatureCompat, ColdSweatTemperature,
 * SurviveTemperature and TemperatureItemEffects: the environment and body
 * temperature come from Cold Sweat or Survive when one is installed (read
 * by reflection, so neither is a dependency), else from the vanilla biome
 * with local offsets for depth, lava, fire and water.
 */
public final class TemperatureCompat {
    public static final String COLD_SWEAT = "cold_sweat";
    public static final String SURVIVE = "survive";

    private TemperatureCompat() {}

    public static boolean hasPreciseTemperature() {
        return ColdSweat.available() || Survive.available();
    }

    public static float environmentNorm(Level level, BlockPos pos, LivingEntity entity) {
        if (level == null || pos == null) {
            return 0.0F;
        }
        if (level.dimensionType().ultraWarm()) {
            return Math.max(1.15F, vanillaBiomeNorm(level, pos));
        }
        if (ColdSweat.available() && entity != null) {
            Float world = ColdSweat.trait(entity, ColdSweat.world);
            if (world != null) {
                return AgingRate.normalizeMcTemp(world);
            }
        }
        if (!ColdSweat.available() && Survive.available()) {
            Float temperature = Survive.environment(level, pos);
            if (temperature != null) {
                return AgingRate.normalizeMcTemp(temperature);
            }
        }
        return vanillaBiomeNorm(level, pos);
    }

    public static float vanillaBiomeNorm(Level level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        float norm = AgingRate.normalizeMcTemp(biome.value().getBaseTemperature());
        return level.dimensionType().ultraWarm() || biome.is(BiomeTags.IS_NETHER)
                ? Math.max(1.15F, norm)
                : norm;
    }

    public static float bodyZeroOffset(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (ColdSweat.available()) {
            Float body = ColdSweat.trait(entity, ColdSweat.body);
            if (body != null) {
                return Mth.clamp(body / 150.0F, -1.0F, 1.0F) * 0.2F;
            }
            return 0.0F;
        }
        return Survive.available() && entity instanceof Player
                ? Survive.bodyZeroOffset(entity)
                : 0.0F;
    }

    public static float vanillaLocalOffset(Level level, BlockPos pos, LivingEntity entity) {
        if (hasPreciseTemperature()) {
            return 0.0F;
        }
        int y = entity != null ? Mth.floor(entity.getY()) : pos.getY();
        float offset = Mth.clamp((64 - y) / 128.0F * 0.08F, -0.12F, 0.12F);
        if (entity != null) {
            if (entity.isInLava()) {
                offset += 0.4F;
            }
            else if (entity.isOnFire()) {
                offset += 0.22F;
            }
            if (entity.isInWaterRainOrBubble()) {
                offset -= 0.1F;
            }
            return offset;
        }
        if (level.isRainingAt(pos)) {
            offset -= 0.1F;
        }
        FluidState fluid = level.getFluidState(pos);
        if (fluid.is(FluidTags.LAVA)) {
            offset += 0.4F;
        }
        else if (fluid.is(FluidTags.WATER)) {
            offset -= 0.1F;
        }
        return offset;
    }

    /** Held and worn items and the Cold Sweat effects shift the rate, -0.7..+0.45. */
    public static float itemOffset(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        float cool = 0.0F;
        float heat = 0.0F;
        if (entity instanceof Player player) {
            cool += IceCubeItem.useCooling(player);
            if (hasPreciseTemperature()) {
                cool += externalCooling(player);
                heat += externalHeating(player);
            }
        }
        cool += effectOffset(entity, "cold_sweat:ice_resistance", 0.16F);
        cool += effectOffset(entity, "cold_sweat:grace", 0.1F);
        heat += effectOffset(entity, "cold_sweat:warmth", 0.16F);
        return Mth.clamp(heat - cool, -0.7F, 0.45F);
    }

    private static float effectOffset(LivingEntity entity, String id, float amount) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Holder<MobEffect> effect = key == null
                ? null
                : BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null);
        if (effect == null) {
            return 0.0F;
        }
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0.0F : amount * (1.0F + 0.25F * instance.getAmplifier());
    }

    private static float externalCooling(Player player) {
        float cool = held(player.getMainHandItem(), 0.18F, "waterskin", "canteen", "ice_pack", "soul_sprout")
                + held(player.getOffhandItem(), 0.18F, "waterskin", "canteen", "ice_pack", "soul_sprout");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                cool += held(player.getItemBySlot(slot), 0.06F, "wool", "fur", "hoglin", "chameleon");
            }
        }
        return Math.min(0.4F, cool);
    }

    private static float externalHeating(Player player) {
        float heat = held(player.getMainHandItem(), 0.16F, "lava", "magma", "blaze")
                + held(player.getOffhandItem(), 0.16F, "lava", "magma", "blaze");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                heat += held(player.getItemBySlot(slot), 0.05F, "honey", "netherite", "magma");
            }
        }
        return Math.min(0.3F, heat);
    }

    private static float held(ItemStack stack, float amount, String... tokens) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String token : tokens) {
            if (path.contains(token)) {
                return amount;
            }
        }
        return 0.0F;
    }

    private static final class ColdSweat {
        private static boolean resolved;
        private static boolean available;
        private static Method get;
        private static Object world;
        private static Object body;

        static boolean available() {
            resolve();
            return available;
        }

        static Float trait(LivingEntity entity, Object trait) {
            if (!available() || trait == null) {
                return null;
            }
            try {
                Object value = get.invoke(null, entity, trait);
                return value instanceof Number number ? number.floatValue() : null;
            }
            catch (ReflectiveOperationException | RuntimeException error) {
                return null;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static synchronized void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            if (!ModList.get().isLoaded(COLD_SWEAT)) {
                return;
            }
            try {
                Class<?> temperature = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
                Class traitClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");
                world = Enum.valueOf(traitClass, "WORLD");
                body = Enum.valueOf(traitClass, "BODY");
                get = temperature.getMethod("get", LivingEntity.class, traitClass);
                available = true;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                available = false;
            }
        }
    }

    private static final class Survive {
        private static final float DEFAULT_BODY_TEMP = 37.0F;
        private static boolean resolved;
        private static boolean available;
        private static Method environment;
        private static boolean environmentTakesBiome;
        private static Method stats;
        private static Method level;

        static boolean available() {
            resolve();
            return available;
        }

        static Float environment(Level world, BlockPos pos) {
            if (!available()) {
                return null;
            }
            try {
                Object value = environment.invoke(
                        null,
                        environmentTakesBiome ? world.getBiome(pos).value() : world,
                        pos);
                return value instanceof Number number ? number.floatValue() : null;
            }
            catch (ReflectiveOperationException | RuntimeException error) {
                return null;
            }
        }

        static float bodyZeroOffset(LivingEntity entity) {
            if (!available() || stats == null) {
                return 0.0F;
            }
            try {
                Object temperatureStats = stats.invoke(null, entity);
                if (temperatureStats == null) {
                    return 0.0F;
                }
                if (level == null) {
                    level = temperatureStats.getClass().getMethod("getTemperatureLevel");
                }
                Object value = level.invoke(temperatureStats);
                return value instanceof Number number
                        ? Mth.clamp((number.floatValue() - DEFAULT_BODY_TEMP) / 3.0F, -1.0F, 1.0F) * 0.2F
                        : 0.0F;
            }
            catch (ReflectiveOperationException | RuntimeException error) {
                return 0.0F;
            }
        }

        private static synchronized void resolve() {
            if (resolved) {
                return;
            }
            resolved = true;
            if (!ModList.get().isLoaded(SURVIVE)) {
                return;
            }
            try {
                Class<?> util = Class.forName("com.stereowalker.survive.util.TemperatureUtil");
                try {
                    environment = util.getMethod("getTemperature", Biome.class, BlockPos.class);
                    environmentTakesBiome = true;
                }
                catch (NoSuchMethodException biomeSignature) {
                    environment = util.getMethod("getTemperature", Level.class, BlockPos.class);
                    environmentTakesBiome = false;
                }
                try {
                    stats = Class.forName("com.stereowalker.survive.entity.SurviveEntityStats")
                            .getMethod("getTemperatureStats", LivingEntity.class);
                }
                catch (ReflectiveOperationException noStats) {
                    stats = null;
                }
                available = true;
            }
            catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                available = false;
            }
        }
    }
}
