package rotp.addons.spin.breaker;

import static rotp.core.init.power.ModStands.SWITCH_SPECIAL;
import static rotp.core.init.power.ModStands.USE_SPECIAL;

import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import rotp.addons.spin.SpinAddon;
import rotp.addons.spin.compat.SpinLoveTrain;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.entity.SteelBallEntity;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.power.SpinData;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

public final class BallBreakerAddon {
    public static final String PUNCH = "ball_breaker_punch";
    public static final String HEAVY = "ball_breaker_heavy_punch";
    public static final String GUARD = "ball_breaker_block";
    public static final String SENESCENCE = "ball_breaker_senescence";

    private static final float WIDTH = 0.6F;
    private static final float HEIGHT = 1.9F;
    private static final DeferredRegister<StandType> STANDS =
            DeferredRegister.create(JojoRegistries.DEFAULT_STANDS_REG, SpinAddon.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, SpinAddon.MOD_ID);
    private static final DeferredRegister<AbilityType<?>> ABILITIES =
            DeferredRegister.create(JojoRegistries.ABILITY_TYPES_REG, SpinAddon.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BallBreakerEntity>> ENTITY =
            ENTITIES.register("ball_breaker", id -> EntityType.Builder
                    .<BallBreakerEntity>of(BallBreakerEntity::new, MobCategory.MISC)
                    .sized(WIDTH, HEIGHT).noSave().noSummon().clientTrackingRange(32).updateInterval(2)
                    .build(id.toString()));
    public static final DeferredHolder<AbilityType<?>, AbilityType<BallBreakerSenescence>> TOUCH =
            ABILITIES.register(SENESCENCE, id -> new AbilityType<>(id, BallBreakerSenescence::new));
    public static final DeferredHolder<StandType, EntityStandType> STAND =
            STANDS.register("ball_breaker", BallBreakerAddon::createStand);

    private static final SteelBallEntity.ImpactHooks IMPACT_HOOKS = new SteelBallEntity.ImpactHooks() {
        @Override
        public double damageMultiplier(LivingEntity owner, double golden) {
            return golden > 1 && hasBallBreakerOut(owner)
                    ? golden * SpinConfig.BALL_BREAKER_SPIN_DAMAGE_MULT.get() : golden;
        }

        @Override
        public void onSpinThrow(Player owner, ItemStack ball) {
            tryManifest(owner, ball);
        }

        @Override
        public boolean hurtLiving(SteelBallEntity ball, LivingEntity owner, LivingEntity target,
                boolean chipped, BooleanSupplier hit) {
            return SpinLoveTrain.qualifiedBallHit(ball, owner, target, hit);
        }

        @Override
        public void afterLivingHit(SteelBallEntity ball, LivingEntity owner, LivingEntity target, boolean chipped) {
            if (carriesPayload(ball, owner)) {
                BallBreakerAging.apply(target, BallBreakerAging.senescenceScale(chipped));
            }
        }

        @Override
        public boolean takesPayload(LivingEntity owner) {
            return canPierceOnHit(owner);
        }
    };

    private BallBreakerAddon() {}

    public static void register(IEventBus bus) {
        ABILITIES.register(bus);
        ENTITIES.register(bus);
        STANDS.register(bus);
        bus.addListener(BallBreakerAddon::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ENTITY.get(), StandEntity.createAttributes().build());
    }

    private static EntityStandType createStand(ResourceLocation id) {
        MovesetBuilder moves = new MovesetBuilder()
                .addHumanoidStandStuff()
                .addAbility(PUNCH, ModStandAbilities.PUNCH)
                .addAbility(HEAVY, ModStandAbilities.HEAVY_PUNCH)
                .addAbility(GUARD, ModStandAbilities.GUARD)
                .addAbility(SENESCENCE, TOUCH)
                .makeControlScheme("default")
                    .bind(PUNCH, InputMethod.CLICK, InputKey.LMB)
                    .bind(HEAVY, InputMethod.CLICK, InputKey.RMB)
                    .bind(GUARD, InputMethod.HOLD, InputKey.RMB)
                    .makeHotbar(0, USE_SPECIAL, SWITCH_SPECIAL)
                    .addToHotbar(SENESCENCE, 0, InputMethod.CLICK)
                .finalizeControlScheme()
                .addSkill(StandUnlockableSkill.startingAbility(PUNCH))
                .addSkill(StandUnlockableSkill.startingAbility(HEAVY))
                .addSkill(StandUnlockableSkill.startingAbility(GUARD))
                .addSkill(StandUnlockableSkill.startingAbility(SENESCENCE))
                .addSkill(StandUnlockableSkill.startingAbility("manual_control"));
        return new EntityStandType(new StandStats.Builder()
                .power(16).speed(16).range(2, 10).durability(14).precision(8).build(), moves, ENTITY.get(), id)
                .standControlType(StandControlType.CLOSE_RANGE_DIRECT)
                .manualControl(true)
                .distanceStrengthDecay(true)
                .standDimensions(WIDTH, HEIGHT);
    }

    public static SteelBallEntity.ImpactHooks impactHooks() {
        return IMPACT_HOOKS;
    }

    public static boolean hasBallBreakerOut(LivingEntity owner) {
        return StandPower.getOptional(owner)
                .filter(power -> power.isSummoned() && power.getPowerType() == STAND.get())
                .isPresent();
    }

    public static boolean canPierceOnHit(LivingEntity owner) {
        return hasBallBreakerOut(owner) && SpinData.goldenMultiplier(owner.level(), owner, false) > 1;
    }

    /** Recall does not cancel what is already thrown, so the state at the throw counts as well as the donor's check at the hit. */
    public static boolean carriesPayload(SteelBallEntity ball, LivingEntity owner) {
        return ball.hasBreakerPayload() || canPierceOnHit(owner);
    }

    static boolean canManifest(boolean enabled, int lesson, int minLesson, boolean gallopSuperSpin,
            boolean perfectBall, boolean hasStand) {
        return enabled && lesson >= minLesson && gallopSuperSpin && perfectBall && !hasStand;
    }

    private static boolean tryManifest(Player user, ItemStack ball) {
        if (!(user.level() instanceof ServerLevel level)) {
            return false;
        }
        StandPower power = PowerClass.STAND.attachGet(user);
        boolean perfect = ball.is(SpinAddon.STEEL_BALL.get()) && !SteelBallItem.isChipped(ball);
        if (!canManifest(SpinConfig.BALL_BREAKER_MANIFEST_ENABLED.get(), SpinData.lessonOf(user),
                SpinConfig.BALL_BREAKER_MANIFEST_MIN_LESSON.get(),
                SpinData.of(user).map(SpinData::hasGallopSuperSpin).orElse(false), perfect, power.hasPower())) {
            return false;
        }

        EntityStandType breaker = STAND.get();
        power.setStand(breaker);
        if (SpinConfig.BALL_BREAKER_MANIFEST_SUMMON.get()) {
            breaker.summon(user, power);
        }
        user.displayClientMessage(Component.translatable("rotp_spin.message.ball_breaker_manifested")
                .withStyle(ChatFormatting.GOLD), true);
        level.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1, 1.4F);
        level.sendParticles(ParticleTypes.CRIT, user.getX(), user.getY(1), user.getZ(),
                40, 0.6, 0.8, 0.6, 0.2);
        return true;
    }
}
