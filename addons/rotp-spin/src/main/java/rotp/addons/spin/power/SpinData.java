package rotp.addons.spin.power;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.config.SpinConfig;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerData;
import rotp.core.powersystem.standpower.entity.StandEntity;

public final class SpinData extends PlayerPowerData {
    private static final int MAX_COOLDOWNS = 32;

    private int lesson = SpinLessons.FIRST;
    private int ballHits, hijacks, goldenHits;
    private long handFrameUntil;
    private int gallopTicks, slowTicks;
    private double horseSpeed, horseX, horseZ;
    private Entity lastHorse;
    private long detourUntil;
    private final Map<String, Cooldown> cooldowns = new HashMap<>();
    private float syncedEnergy, syncedMax, syncedCost;
    private int syncedLesson = SpinLessons.FIRST;
    private int syncedChargeTicks;
    private float syncedChippedMax;

    public SpinData() {
        super(SpinAddon.SPIN.get());
    }

    public static Optional<SpinData> of(LivingEntity user) {
        return PlayerPower.getPowerData(user, SpinAddon.SPIN);
    }

    public static int lessonOf(LivingEntity user) {
        return of(user).map(SpinData::lesson).orElse(0);
    }

    public int lesson() { return lesson; }
    public int ballHits() { return ballHits; }
    public int hijacks() { return hijacks; }
    public int goldenHits() { return goldenHits; }

    public int effectiveLesson(LivingEntity user) {
        return user.level().isClientSide() ? syncedLesson : effectiveLesson();
    }

    private int effectiveLesson() {
        return SpinConfig.LESSONS_ENABLED.get() ? lesson : SpinLessons.MAX;
    }

    public static float energy(LivingEntity user) {
        if (user.level().isClientSide()) return of(user).map(data -> data.syncedEnergy).orElse(0.0F);
        return user instanceof Player player ? player.getData(SpinAddon.ENERGY).get() : 0.0F;
    }

    public float displayMax() { return syncedMax; }
    public float displayCost() { return syncedCost; }
    public int displayChargeTicks() { return syncedChargeTicks; }
    public float displayChippedMax() { return syncedChippedMax; }

    public static boolean hasEnergy(LivingEntity user, float amount) {
        return Float.isFinite(amount) && (amount <= 0.0F
                || user instanceof Player player && (player.getAbilities().instabuild || energy(user) >= amount));
    }

    public static boolean consumeEnergy(LivingEntity user, float amount) {
        if (!(user instanceof Player player) || !Float.isFinite(amount)) return false;
        if (player.getAbilities().instabuild || amount <= 0.0F) return true;
        return player.level().isClientSide() ? hasEnergy(user, amount) : player.getData(SpinAddon.ENERGY).consume(amount);
    }

    public static void sync(LivingEntity user) {
        if (user instanceof ServerPlayer player) of(player).ifPresent(data -> data.syncToPlayer(player));
    }

    public void tickPlayer(ServerPlayer user) {
        var state = user.getData(SpinAddon.ENERGY);
        float max = SpinConfig.ENERGY_MAX.get().floatValue();
        float cost = SpinConfig.BALL_SPIN_COST.get().floatValue();
        state.set(state.get() + SpinConfig.ENERGY_REGEN_PER_TICK.get().floatValue(), max);
        tickHorseback(user);
        if ((int) Math.floor(state.get()) != (int) Math.floor(syncedEnergy)
                || max != syncedMax || cost != syncedCost || effectiveLesson() != syncedLesson) {
            syncToPlayer(user);
        }
    }

    @Override
    public void tick(Power<?> power) {
        super.tick(power);
        int steps = power instanceof PlayerPower playerPower ? playerPower.getCooldownSteps() : 1;
        if (steps > 0 && !cooldowns.isEmpty()) {
            cooldowns.replaceAll((name, cooldown) -> new Cooldown(cooldown.left - steps, cooldown.total));
            cooldowns.values().removeIf(cooldown -> cooldown.left <= 0);
        }
    }

    public boolean onCooldown(String name) {
        return cooldowns.containsKey(name);
    }

    public static void setCooldown(LivingEntity user, String name, int ticks) {
        if (!(user instanceof ServerPlayer player)) return;
        of(player).ifPresent(data -> {
            if (ticks > 0 && !player.getAbilities().instabuild) data.cooldowns.put(name, new Cooldown(ticks, ticks));
            else data.cooldowns.remove(name);
            data.syncToPlayer(player);
        });
    }

    @Override
    public float getAbilityCooldownRatio(String abilityName, float partialTick) {
        Cooldown cooldown = cooldowns.get(abilityName);
        return cooldown == null ? 0.0F : Mth.clamp((cooldown.left - partialTick) / cooldown.total, 0.0F, 1.0F);
    }

    @Override
    public void resetAbilityCooldowns(LivingEntity user) {
        if (!cooldowns.isEmpty()) {
            cooldowns.clear();
            sync(user);
        }
    }

    @Override
    public void syncCooldownsAtRate(ServerPlayer user) {
        if (!cooldowns.isEmpty()) syncToPlayer(user);
    }

    @Override
    public float getCommandMaxEnergy(LivingEntity user) {
        return SpinConfig.ENERGY_MAX.get().floatValue();
    }

    @Override
    public float getCommandEnergy() {
        return syncedEnergy;
    }

    @Override
    public void setCommandEnergy(LivingEntity user, float amount) {
        if (user instanceof ServerPlayer player) {
            player.getData(SpinAddon.ENERGY).set(amount, getCommandMaxEnergy(player));
            syncToPlayer(player);
        }
    }

    public static void practiceHit(LivingEntity thrower, Entity target) {
        if (thrower.level().isClientSide() || !(target instanceof LivingEntity)
                || target instanceof ArmorStand || target instanceof StandEntity) return;
        of(thrower).ifPresent(data -> {
            if (data.lesson == 1) {
                data.progress(thrower, 2, ++data.ballHits, SpinConfig.LESSON2_BALL_HITS.get());
            }
            if (data.lesson >= 3 && data.lesson < 5) {
                data.progress(thrower, data.lesson + 1, ++data.goldenHits,
                        data.lesson == 3 ? SpinConfig.LESSON4_GOLDEN_HITS.get() : SpinConfig.LESSON5_GOLDEN_HITS.get());
            }
        });
    }

    public void practiceHijack(LivingEntity user) {
        if (!user.level().isClientSide() && lesson == 2) {
            progress(user, 3, ++hijacks, SpinConfig.LESSON3_HIJACKS.get());
        }
    }

    private void progress(LivingEntity user, int next, int done, int needed) {
        if (SpinConfig.LESSONS_ENABLED.get() && user instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("rotp_spin.message.lesson_progress",
                    next, Math.min(done, needed), needed).withStyle(ChatFormatting.YELLOW), true);
        }
        int reached = SpinLessons.reached(lesson, ballHits, hijacks, goldenHits,
                SpinConfig.LESSON2_BALL_HITS.get(), SpinConfig.LESSON3_HIJACKS.get(),
                SpinConfig.LESSON4_GOLDEN_HITS.get(), SpinConfig.LESSON5_GOLDEN_HITS.get());
        if (reached > lesson) setLesson(user, reached, true);
    }

    public void setLesson(LivingEntity user, int newLesson, boolean announce) {
        if (user.level().isClientSide()) return;
        int old = lesson;
        lesson = SpinLessons.clamp(newLesson);
        if (lesson < old) ballHits = hijacks = goldenHits = 0;
        if (user instanceof ServerPlayer player) {
            syncToPlayer(player);
            if (announce && lesson > old) {
                player.sendSystemMessage(Component.translatable("rotp_spin.message.lesson_learned",
                        lesson, Component.translatable("rotp_spin.lesson." + lesson)).withStyle(ChatFormatting.GOLD));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    public void frameGoldenRectangle(long time, int duration) {
        handFrameUntil = SpinGolden.handFrameUntil(time, duration);
    }

    public boolean hasHandFrame(long time) {
        return SpinGolden.isHandFrameActive(time, handFrameUntil);
    }

    public static double goldenMultiplier(Level level, LivingEntity user, boolean chipped) {
        int lesson = lessonOf(user);
        if (lesson < 4) return 1.0D;
        boolean buckle = user instanceof Player player && player.getInventory().contains(
                stack -> stack.is(SpinAddon.CALIBRATION_BUCKLE.get()));
        var head = user.blockPosition().above();
        boolean snow = SpinConfig.GOLDEN_SNOWFALL_CALIBRATES.get() && level.isRaining() && level.canSeeSky(head)
                && level.getBiome(head).value().getPrecipitationAt(head) == Biome.Precipitation.SNOW;
        var data = of(user).orElseThrow();
        boolean calibrated = SpinGolden.isCalibrated(lesson, SpinBiomes.category(level, user.blockPosition()),
                buckle, snow || data.hasHandFrame(level.getGameTime()), SpinConfig.GOLDEN_DEAD_CATEGORIES.get());
        double golden = SpinGolden.multiplier(lesson, calibrated, SpinConfig.GOLDEN_MULT_4.get(), SpinConfig.GOLDEN_MULT_5.get());
        return SpinSuperSpin.multiplier(!chipped && data.hasSuperSpin(level.getGameTime()), golden, SpinConfig.SUPER_SPIN_MULT.get());
    }

    private void tickHorseback(ServerPlayer rider) {
        boolean wasReady = gallopReady();
        if (lesson >= 4 && rider.getVehicle() instanceof AbstractHorse horse) {
            double dx = horse.getX() - horseX;
            double dz = horse.getZ() - horseZ;
            double raw = lastHorse == horse ? Math.sqrt(dx * dx + dz * dz) : 0.0D;
            horseX = horse.getX();
            horseZ = horse.getZ();
            lastHorse = horse;
            boolean crash = SpinSuperSpin.isCrash(horse.horizontalCollision, raw, horseSpeed,
                    SpinConfig.SUPER_SPIN_CRASH_STOP_FRACTION.get());
            horseSpeed = SpinSuperSpin.smoothSpeed(horseSpeed, raw);
            boolean galloping = SpinGolden.isGallopSuperSpin(lesson, horseSpeed, SpinConfig.GOLDEN_HORSE_GALLOP_SPEED.get());
            slowTicks = SpinSuperSpin.nextSlowTicks(slowTicks, galloping);
            gallopTicks = SpinSuperSpin.nextGallopTicks(gallopTicks, galloping, slowTicks,
                    SpinConfig.SUPER_SPIN_GRACE_TICKS.get(), crash || attacked(horse) || attacked(rider), healthy(horse));
        } else {
            gallopTicks = slowTicks = 0;
            horseSpeed = 0.0D;
            lastHorse = null;
        }
        if (wasReady != gallopReady()) {
            boolean ready = gallopReady();
            rider.displayClientMessage(Component.translatable(ready ? "rotp_spin.message.super_spin_ready"
                    : "rotp_spin.message.super_spin_broken").withStyle(ready ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        }
    }

    private static boolean attacked(LivingEntity entity) {
        var last = entity.getLastDamageSource();
        return entity.hurtTime > 0 && (last == null || !last.is(DamageTypes.FALL));
    }

    private boolean gallopReady() {
        return SpinSuperSpin.isGallopReady(gallopTicks, SpinConfig.SUPER_SPIN_GALLOP_TICKS.get());
    }

    public boolean hasGallopSuperSpin() {
        return lesson >= 4 && gallopReady();
    }

    public boolean hasSuperSpin(long time) {
        return hasGallopSuperSpin() || time < detourUntil;
    }

    private static boolean healthy(AbstractHorse horse) {
        return SpinSuperSpin.isHealthy(horse.getHealth(), horse.getMaxHealth(), SpinConfig.SUPER_SPIN_HORSE_MIN_HEALTH.get());
    }

    public static boolean tryDetour(LivingEntity user, AbstractHorse horse, boolean chipped) {
        if (user.level().isClientSide() || !SpinConfig.SUPER_SPIN_DETOUR_ENABLED.get()) return false;
        var data = of(user).orElse(null);
        boolean own = user.getVehicle() == horse || horse.isTamed() && user.getUUID().equals(horse.getOwnerUUID());
        if (data == null || !SpinSuperSpin.canDetour(data.lesson, chipped, own, healthy(horse), horse.distanceToSqr(user),
                SpinConfig.SUPER_SPIN_DETOUR_RANGE.get())) return false;
        int duration = SpinConfig.SUPER_SPIN_DETOUR_DURATION_TICKS.get();
        data.detourUntil = SpinSuperSpin.detourUntil(user.level().getGameTime(), duration);
        horse.makeMad();
        horse.playSound(SoundEvents.HORSE_ANGRY, 1.0F, 1.0F);
        float kick = SpinConfig.SUPER_SPIN_DETOUR_KICK_DAMAGE.get().floatValue();
        if (kick > 0.0F) user.hurt(user.damageSources().mobAttack(horse), kick);
        if (user instanceof ServerPlayer player) player.displayClientMessage(
                Component.translatable("rotp_spin.message.super_spin_detour", duration / 20).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    @Override
    public void syncToPlayer(ServerPlayer user) {
        syncedEnergy = user.getData(SpinAddon.ENERGY).get();
        syncedMax = SpinConfig.ENERGY_MAX.get().floatValue();
        syncedCost = SpinConfig.BALL_SPIN_COST.get().floatValue();
        syncedLesson = effectiveLesson();
        syncedChargeTicks = SpinConfig.CHARGE_MAX_TICKS.get();
        syncedChippedMax = SpinConfig.CHARGE_CHIPPED_MAX.get().floatValue();
        super.syncToPlayer(user);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        var tag = super.serializeNBT(registries);
        tag.putInt("Lesson", lesson);
        tag.putInt("BallHits", ballHits);
        tag.putInt("Hijacks", hijacks);
        tag.putInt("GoldenHits", goldenHits);
        tag.putLong("HandFrameUntil", handFrameUntil);
        var timers = new CompoundTag();
        cooldowns.forEach((name, value) -> timers.putIntArray(name, new int[]{value.left, value.total}));
        tag.put("SpinCooldowns", timers);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        super.deserializeNBT(registries, tag);
        lesson = SpinLessons.clamp(tag.getInt("Lesson"));
        ballHits = tag.getInt("BallHits");
        hijacks = tag.getInt("Hijacks");
        goldenHits = tag.getInt("GoldenHits");
        handFrameUntil = tag.getLong("HandFrameUntil");
        cooldowns.clear();
        var timers = tag.getCompound("SpinCooldowns");
        for (String name : timers.getAllKeys()) {
            if (cooldowns.size() >= MAX_COOLDOWNS) break;
            int[] raw = timers.getIntArray(name);
            if (raw.length == 2 && raw[0] > 0) cooldowns.put(name, new Cooldown(raw[0], Math.max(raw[0], raw[1])));
        }
    }

    @Override
    public void toBuf(FriendlyByteBuf buf, boolean tracking) {
        super.toBuf(buf, tracking);
        if (tracking) return;
        buf.writeVarInt(lesson);
        buf.writeVarInt(syncedLesson);
        buf.writeFloat(syncedEnergy);
        buf.writeFloat(syncedMax);
        buf.writeFloat(syncedCost);
        buf.writeVarInt(syncedChargeTicks);
        buf.writeFloat(syncedChippedMax);
        buf.writeVarInt(cooldowns.size());
        cooldowns.forEach((name, timer) -> {
            buf.writeUtf(name);
            buf.writeVarInt(timer.left);
            buf.writeVarInt(timer.total);
        });
    }

    @Override
    public void fromBuf(FriendlyByteBuf buf, boolean tracking) {
        super.fromBuf(buf, tracking);
        if (tracking) return;
        lesson = SpinLessons.clamp(buf.readVarInt());
        syncedLesson = SpinLessons.clamp(buf.readVarInt());
        syncedEnergy = buf.readFloat();
        syncedMax = buf.readFloat();
        syncedCost = buf.readFloat();
        syncedChargeTicks = buf.readVarInt();
        syncedChippedMax = buf.readFloat();
        cooldowns.clear();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_COOLDOWNS) throw new IllegalArgumentException("Invalid Spin cooldown count");
        for (int i = 0; i < count; ++i) {
            String name = buf.readUtf();
            int left = buf.readVarInt();
            int total = buf.readVarInt();
            if (left > 0) cooldowns.put(name, new Cooldown(left, Math.max(left, total)));
        }
    }

    private record Cooldown(int left, int total) {}
}
