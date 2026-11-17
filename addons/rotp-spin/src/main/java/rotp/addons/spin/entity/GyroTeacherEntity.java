package rotp.addons.spin.entity;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.power.SpinData;
import rotp.addons.spin.world.GyroSpawns;
import rotp.core.powersystem.playerpower.PlayerPower;

public final class GyroTeacherEntity extends PathfinderMob {
    public GyroTeacherEntity(EntityType<? extends GyroTeacherEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.25D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            teach(serverPlayer);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.VILLAGER_YES, getSoundSource(), 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    private void teach(ServerPlayer player) {
        var data = SpinData.of(player).orElse(null);
        if (data != null) {
            player.sendSystemMessage(Component.translatable("rotp_spin.message.gyro_status", data.lesson(),
                    Component.translatable("rotp_spin.lesson." + data.lesson()), data.ballHits(), data.hijacks(),
                    data.goldenHits()).withStyle(ChatFormatting.YELLOW));
            return;
        }
        PlayerPower power = PlayerPower.get(player);
        boolean given = power != null && power.getPowerType() == null && power.trySetPowerType(SpinAddon.SPIN.get());
        player.sendSystemMessage(Component.translatable(given ? "rotp_spin.message.gyro_spin_given"
                : "rotp_spin.message.gyro_has_other").withStyle(given ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData group) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group);
        GyroSpawns.spawnHorse(level, this, reason);
        return result;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
