package rotp.addons.echoes.state;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * R772 F11: the master's EchoesAct0AwakeningHandler. The arrow gives ACT0, the egg; once a second it may hatch into
 * ACT1, the likelier the lower its user's health and the more hostile mobs within 10 blocks, each miss adding 5% up
 * to 50%, and at the latest after ten minutes with the egg. Hatching sets Resolve level 1 and gives the Resolve
 * effect for two minutes. The master counted the ten minutes in wall-clock time and the misses in memory; here both
 * are game time and saved with the user.
 */
public final class EchoesAct0Awakening {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final double DETECTION_RANGE = 10.0D;
    private static final float FAIL_BONUS_PER_FAIL = 0.05F;
    private static final float FAIL_BONUS_MAX = 0.50F;
    public static final long GUARANTEE_TICKS = 10L * 60L * 20L;
    private static final int RESOLVE_EFFECT_TICKS = 2400;

    private EchoesAct0Awakening() {}

    public static void tick(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime % CHECK_INTERVAL_TICKS != 0L || !player.isAlive()) {
            return;
        }
        StandPower power = StandPower.get(player);
        boolean egg = power != null && EchoesForms.actOf(power.getPowerType()) == 0;
        if (!egg) {
            if (player.hasData(EchoesAttachments.USER_STATE)) {
                EchoesUserState state = RotpEchoes.userState(player);
                if (state.act0Since() != Long.MIN_VALUE || state.act0FailCount() != 0) {
                    state.setAct0Since(Long.MIN_VALUE);
                    state.setAct0FailCount(0);
                }
            }
            return;
        }
        EchoesUserState state = RotpEchoes.userState(player);
        if (state.act0AwakeningDisabled()) {
            return;
        }
        if (state.act0Since() == Long.MIN_VALUE) {
            state.setAct0Since(gameTime);
        }
        else if (gameTime - state.act0Since() >= GUARANTEE_TICKS) {
            awaken(player, power);
            return;
        }
        float baseChance = chance(player.getHealth(), player.getHealth() >= player.getMaxHealth(),
                countNearbyEnemies(player));
        if (baseChance <= 0.0F) {
            return;
        }
        int fails = state.act0FailCount();
        float bonus = Math.min(fails * FAIL_BONUS_PER_FAIL, FAIL_BONUS_MAX);
        if (player.getRandom().nextFloat() < Math.min(baseChance + bonus, 1.0F)) {
            awaken(player, power);
        }
        else {
            state.setAct0FailCount(fails + 1);
        }
    }

    // master calculateChance: by health band, spread over the band's points of health, by the number of enemies
    static float chance(float health, boolean fullHealth, int enemyCount) {
        int hp = Mth.ceil(health);
        boolean fewEnemies = enemyCount >= 1 && enemyCount <= 3;
        boolean manyEnemies = enemyCount >= 4;
        if (hp >= 1 && hp <= 3) {
            if (fewEnemies) {
                return splitChance(0.45F, 3, hp, 1, 3);
            }
            return manyEnemies ? splitChance(0.55F, 3, hp, 1, 3) : 0.0F;
        }
        if (hp >= 4 && hp <= 10) {
            if (fewEnemies) {
                return splitChance(0.20F, 7, hp, 4, 10);
            }
            return manyEnemies ? splitChance(0.30F, 7, hp, 4, 10) : 0.0F;
        }
        if (hp >= 11 && hp <= 20) {
            if (fullHealth && manyEnemies) {
                return splitChance(0.40F, 10, hp, 11, 20);
            }
            return fewEnemies ? splitChance(0.05F, 10, hp, 11, 20) : 0.0F;
        }
        return 0.0F;
    }

    private static float splitChance(float totalChance, int totalParts, int hp, int minHp, int maxHp) {
        if (hp < minHp || hp > maxHp) {
            return 0.0F;
        }
        int parts = Mth.clamp(maxHp - hp + 1, 1, totalParts);
        return totalChance * parts / totalParts;
    }

    private static int countNearbyEnemies(ServerPlayer player) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DETECTION_RANGE),
                entity -> entity != player && entity.isAlive() && entity instanceof Enemy).size();
    }

    public static void awaken(ServerPlayer player, StandPower power) {
        EchoesUserState state = RotpEchoes.userState(player);
        state.setAct0Since(Long.MIN_VALUE);
        state.setAct0FailCount(0);
        EchoesForms.changeForm(player, power, 1);
        if (power.getResolveLevel() < 1) {
            power.setResolveLevel(1);
        }
        player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, RESOLVE_EFFECT_TICKS, 0, false, false, true));
        if (player.level() instanceof ServerLevel level) {
            spawnAwakeningParticles(level, player);
        }
        player.displayClientMessage(Component.translatable("rotp_echoes.message.echoes_awaken"), true);
    }

    private static void spawnAwakeningParticles(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        for (int i = 0; i < 120; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.5D + random.nextDouble() * 1.5D;
            level.sendParticles(ParticleTypes.END_ROD, x + Math.cos(angle) * radius,
                    y + (random.nextDouble() - 0.5D) * 2.0D, z + Math.sin(angle) * radius, 1,
                    (random.nextDouble() - 0.5D) * 0.3D, random.nextDouble() * 0.4D,
                    (random.nextDouble() - 0.5D) * 0.3D, 0.02D);
        }
        for (int i = 0; i < 80; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.0D + random.nextDouble() * 2.5D;
            level.sendParticles(ParticleTypes.FIREWORK, x + Math.cos(angle) * radius,
                    y + (random.nextDouble() - 0.5D) * 3.0D, z + Math.sin(angle) * radius, 1,
                    (random.nextDouble() - 0.5D) * 0.5D, random.nextDouble() * 0.8D,
                    (random.nextDouble() - 0.5D) * 0.5D, 0.05D);
        }
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.5D, z, 40, 0.5D, 0.5D, 0.5D, 0.15D);
    }
}
