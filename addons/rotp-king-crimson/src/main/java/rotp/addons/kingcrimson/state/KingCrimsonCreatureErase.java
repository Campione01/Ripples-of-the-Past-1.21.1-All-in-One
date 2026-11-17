package rotp.addons.kingcrimson.state;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import rotp.core.api.stand.BlinkLanding;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonDriftBodyEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Time Erase of a creature that carries King Crimson. The owner, 2026-11-11: "生物替身使者的紅王如果能用時刪，效果和玩家使用等同，但
 * 生物替身使者在時刪發動時的索敵在單人和多人模式都按玩家運動漂移後的假身計算，同時釋放時刪的生物自身不吃時刪結束時的暈眩", and on how it
 * goes: "出手時直接結束時刪，但非玩家生物發動時刪最少在裏面等三秒才能結束 ... 也會在原地留下生物的真身，並且可以正常用原ai做正常交互，在玩家看來
 * 沒有任何異常，實際在時刪中移動和索敵的反而是生物的假身（只保留原ai和移動方式以及必要的特性 ... 只在出手瞬間做一次真身的換位".
 * <ul>
 * <li>The creature itself stays where it is, in everybody's sight and reach, on its own AI.</li>
 * <li>Its fake body, a plain creature of its kind that no client is told of and nothing can touch, hunts the players
 * where the erase sees them: on a drift body the erase keeps on the shifted image of each (with one player online
 * too).</li>
 * <li>The blow of the fake body ends the erase, three seconds inside it at the earliest: the creature is put where its
 * fake body stands, every player is put on his image, and the creature strikes. With no blow the erase ends at its
 * longest and the creature stays.</li>
 * <li>A hit that would kill the creature during its erase is escaped by its passive erase: it is gone to where it could
 * have walked in those seconds, the players are taken to have gone on as they moved, and the erase is over.</li>
 * <li>Neither begins in stopped time, and neither against a player whom another add-on's rule names (the holder of
 * Gold Experience Requiem).</li>
 * </ul>
 * Cost and cooldown are a player's. Nothing of it is saved.
 */
@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class KingCrimsonCreatureErase {
    /** In the saved data of a fake body: one that a stopped server left behind is dropped as it loads. */
    public static final String FAKE_BODY_TAG = "rotp_kingcrimson:erase_fake_body";
    // The creature add-on rolls a Stand for a creature that joins a level unless it carries this mark of its own.
    private static final String NO_STAND_ROLL_TAG = "rotp_mwp:processed";
    private static final TagKey<EntityType<?>> NO_POWER_AI = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("rotp_mwp", "power_ai_excluded"));
    private static final String ERASE = "kingcrimson_timeerase";
    private static final ResourceLocation PACE_ID = KingCrimsonAddon.id("creature_erase.pace");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Erase[] NONE = {};
    private static final CopyOnWriteArrayList<Rule> ANSWERS = new CopyOnWriteArrayList<>();
    // Read by every out-of-reach hook: an array that is replaced, never changed.
    private static volatile Erase[] running = NONE;
    private static boolean answerFailureLogged;

    private KingCrimsonCreatureErase() {}

    /**
     * Another add-on's answer to a creature's erase (Gold Experience Requiem, the owner: "這部分邏輯對黃鎮持有者不生效，表現為發動
     * 瞬間自動被黃鎮歸零特效取消").
     */
    public interface Answer {
        /**
         * Whether this player returns the erase of this creature to zero. Asked on the server thread: of the player
         * the creature fights as its erase would begin, and of the player whose hit would kill it as its passive
         * erase would save it. It must change nothing.
         */
        boolean answers(ServerPlayer holder, Mob creature);

        /** This rule's yes was taken: the erase did not begin. For the effect the add-on shows on the creature. */
        default void show(ServerPlayer holder, Mob creature) {}
    }

    private record Rule(ResourceLocation owner, Answer answer) {}

    /**
     * One rule for each owner: a second call with the same owner takes the place of the first. Call this at the setup
     * of the add-on. The core's TimeEraseStartEvent and TimeEraseRunningEvent are posted for a creature's erase as for
     * a player's, with the creature as the eraser: this rule is for a holder those do not cover.
     */
    public static void registerAnswer(ResourceLocation owner, Answer answer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(answer, "answer");
        ANSWERS.removeIf(known -> known.owner().equals(owner));
        ANSWERS.add(new Rule(owner, answer));
    }

    /** The rule of this owner is asked no more. */
    public static void removeAnswer(ResourceLocation owner) {
        ANSWERS.removeIf(known -> known.owner().equals(owner));
    }

    /** Whether this is a creature whose Stand is King Crimson: one that erases time as a player does. */
    public static boolean bears(@Nullable Entity entity) {
        if (!(entity instanceof Mob mob) || mob instanceof KingCrimsonTimeEraseDecoyEntity || isPart(mob)
                || mob.getType().is(NO_POWER_AI)) {
            return false;
        }
        StandPower power = StandPower.get(mob);
        return power != null && power.getPowerType() == KingCrimsonAddon.KING_CRIMSON.get();
    }

    /** Whether the erase of this creature runs. */
    public static boolean isErasing(@Nullable Entity creature) {
        return of(creature) != null;
    }

    /**
     * Whether this creature may begin an erase now: it carries King Crimson, no erase of its own runs, time is not
     * stopped where it stands, no prediction holds it, the key does not wait and its bar pays what a player pays.
     */
    public static boolean canBegin(Mob creature) {
        if (!bears(creature) || !creature.isAlive() || !(creature.level() instanceof ServerLevel)
                || of(creature) != null || inStoppedTime(creature) || EpitaphPredictions.isOnTrack(creature)) {
            return false;
        }
        StandPower power = StandPower.get(creature);
        return power.getAbility(ERASE) != null && !power.isAbilityOnCooldown(ERASE)
                && StandAbilityStamina.canPay(power, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA);
    }

    /** Whether the entity is kept in the world by a creature's erase: a fake body, or a drift body. */
    public static boolean isPart(@Nullable Entity entity) {
        Erase[] all = running;
        if (all.length == 0 || entity == null) {
            return false;
        }
        if (entity instanceof KingCrimsonDriftBodyEntity) {
            return true;
        }
        for (Erase erase : all) {
            if (erase.fake == entity) {
                return true;
            }
        }
        return false;
    }

    /** Whether the entity is the fake body of a running erase. */
    public static boolean isFakeBody(@Nullable Entity entity) {
        return ofFake(entity) != null;
    }

    /** Whether a running erase keeps this drift body. */
    public static boolean owns(KingCrimsonDriftBodyEntity body) {
        for (Erase erase : running) {
            if (erase.bodies.containsValue(body)) {
                return true;
            }
        }
        return false;
    }

    // The one hunt that reaches a drift body: that of the fake body of its erase.
    static boolean isOwnHunt(@Nullable LivingEntity hunter, @Nullable LivingEntity hunted) {
        if (!(hunted instanceof KingCrimsonDriftBodyEntity body)) {
            return false;
        }
        Erase erase = ofFake(hunter);
        return erase != null && erase.bodies.containsValue(body);
    }

    /** The fake body that hunts for this creature while its erase runs; null when it does not erase. */
    @Nullable
    public static Mob fakeBodyOf(Mob creature) {
        Erase erase = of(creature);
        return erase != null ? erase.fake : null;
    }

    /** The body the erase of this creature keeps for this player; null when it keeps none. */
    @Nullable
    public static KingCrimsonDriftBodyEntity driftBodyOf(Mob creature, ServerPlayer player) {
        Erase erase = of(creature);
        return erase != null ? erase.bodies.get(player.getUUID()) : null;
    }

    /** Where the erase of this creature sees this body now; its own place when no erase of the creature shifts it. */
    public static Vec3 imageOf(Mob creature, LivingEntity other) {
        Erase erase = of(creature);
        return erase != null && erase.shift != null ? erase.shift.imageOf(other) : other.position();
    }

    /** The creature's own ticks inside its running erase; 0 when it does not erase. */
    public static int ticksInside(Mob creature) {
        Erase erase = of(creature);
        return erase != null ? erase.inside : 0;
    }

    /** Ends the erase of this creature where it stands, as its longest length does. */
    public static void end(Mob creature) {
        Erase erase = of(creature);
        if (erase != null) {
            close(erase, 0);
            KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
        }
    }

    /**
     * The key of a creature. True when its erase began. False with nothing spent when it may not begin now; false with
     * the cost paid and the cooldown started when it was returned to zero as it began.
     */
    public static boolean begin(Mob creature) {
        if (!canBegin(creature)) {
            return false;
        }
        ServerLevel level = (ServerLevel) creature.level();
        StandPower power = StandPower.get(creature);
        Ability ability = power.getAbility(ERASE);
        // Read once, as a player's: his Resolve level's share of the setting.
        int length = KingCrimsonConfig.timeEraseMaxTicks(power.getResolveLevel());
        Mob fake = length > 0 ? fakeBody(creature, level) : null;
        if (fake == null) {
            return false;
        }
        float charged = KingCrimsonEraseStamina.payStart(ability, power, KingCrimsonPhase5Spec.TIME_ERASE_ACTIVATION_STAMINA);
        ServerPlayer fought = playerBehind(creature.getTarget());
        Rule answered = fought != null ? answeredBy(fought, creature) : null;
        if (answered != null || !TimeEraseStartEvent.post(creature,
                (int) Math.ceil(length / KingCrimsonTemporalStateService.worldTimePerTick(creature)))) {
            // Returned to zero: it never begins, and costs what an erase costs.
            KingCrimsonTemporalStateService.setTimeEraseCooldown(creature, 0);
            KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
            if (answered != null) {
                answered.answer().show(fought, creature);
            }
            return false;
        }
        Erase erase = new Erase(creature, level, fake, KingCrimsonTimeEraseShiftService.begin(creature), length, charged);
        // Before the fake body joins the level: it is out of reach from its first moment there.
        add(erase);
        if (!level.addFreshEntity(fake)) {
            close(erase, 0);
            return false;
        }
        if (fought != null) {
            KingCrimsonDriftBodyEntity body = erase.bodyFor(fought);
            if (body != null) {
                fake.setTarget(body);
            }
        }
        return true;
    }

    // A plain creature of the same kind: where the creature is, as big, as fast and with what it holds. Nothing else
    // of the creature is its own: no Stand, no name, no armour, no effects, nothing it remembers.
    @Nullable
    private static Mob fakeBody(Mob creature, ServerLevel level) {
        Entity made = creature.getType().create(level);
        if (!(made instanceof Mob fake)) {
            return null;
        }
        fake.setSilent(true);
        fake.moveTo(creature.getX(), creature.getY(), creature.getZ(), creature.getYRot(), creature.getXRot());
        fake.setYHeadRot(creature.getYHeadRot());
        fake.setYBodyRot(creature.yBodyRot);
        // A creature that is not on the ground plans no way, and its attack then asks again only a second later:
        // it is made standing, and its first move says where it really is.
        fake.setOnGround(true);
        fake.setBaby(creature.isBaby());
        fake.getAttributes().assignBaseValues(creature.getAttributes());
        for (InteractionHand hand : InteractionHand.values()) {
            fake.setItemInHand(hand, creature.getItemInHand(hand).copy());
        }
        fake.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        fake.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        // In an erase its user moves at twice his pace; a walker's pace goes with the square of this value.
        AttributeInstance speed = fake.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addTransientModifier(new AttributeModifier(PACE_ID,
                    Math.sqrt(1.0D + KingCrimsonPhase5Spec.TIME_ERASE_MOVEMENT_MULTIPLIER) - 1.0D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        fake.setPersistenceRequired();
        fake.setCanPickUpLoot(false);
        fake.getPersistentData().putBoolean(FAKE_BODY_TAG, true);
        fake.getPersistentData().putBoolean(NO_STAND_ROLL_TAG, true);
        return fake;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Erase[] all = running;
        if (all.length == 0 || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Erase erase : all) {
            if (erase.level == level && !erase.ended) {
                tick(erase);
            }
        }
    }

    private static void tick(Erase erase) {
        Mob creature = erase.creature;
        if (creature.isRemoved() || !creature.isAlive() || creature.level() != erase.level) {
            close(erase, 0);
            KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
            return;
        }
        // As for a player frozen in someone's time stop: no erase time and no upkeep pass.
        if (TimeStopState.shouldFreezeOnServer(creature)) {
            erase.paused = true;
            return;
        }
        if (erase.paused) {
            erase.paused = false;
            if (erase.shift != null) {
                erase.shift.afterPause();
            }
        }
        erase.inside++;
        erase.world += KingCrimsonTemporalStateService.worldTimePerTick(creature);
        StandPower power = StandPower.get(creature);
        Ability ability = power != null ? power.getAbility(ERASE) : null;
        // Its longest length, a prediction that took the creature on its track, a Stand it lost, a bar that is empty.
        if (erase.world >= erase.lengthTicks || EpitaphPredictions.isOnTrack(creature) || ability == null
                || !bears(creature)) {
            end(creature);
            return;
        }
        // Another time ability throws the eraser out where he stands and shows its own effect in place of the end's.
        TimeEraseRunningEvent thrownOut = TimeEraseRunningEvent.post(creature, erase.elapsed());
        if (thrownOut.isCanceled()) {
            // Thrown out, its cost is kept: nothing of its start comes back.
            shut(erase, 0);
            if (thrownOut.getAnsweredBy() == null) {
                KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
            }
            return;
        }
        if (!StandAbilityStamina.consume(ability, power, KingCrimsonConfig.timeEraseStaminaPerTick()
                * KingCrimsonTemporalStateService.ownTimeRate(creature), true)) {
            end(creature);
            return;
        }
        KingCrimsonEraseStamina.takeRegenerationBack(power);
        if (erase.shift != null) {
            erase.shift.tick(creature);
        }
        erase.placeBodies();
        Mob fake = erase.fake;
        if (fake.isRemoved()) {
            // A fake body that went off with its own blow (a creeper's) struck where it last stood. One that is
            // gone otherwise hunts no more, and the creature stays.
            if (!erase.struck) {
                end(creature);
                return;
            }
        } else if (fake instanceof Creeper creeper) {
            // The fake body of a creeper never goes off (the core holds its fuse for us): an explosion is sent to the
            // players around whatever its event is told. A fuse it wanted for its whole length is its blow.
            erase.fuse = creeper.getSwellDir() > 0 ? erase.fuse + 1 : 0;
            if (erase.fuse >= KingCrimsonPhase5Spec.CREATURE_ERASE_FUSE_TICKS) {
                erase.noteStrike(null, true);
            }
        } else if (!erase.struck && fake.getTarget() instanceof KingCrimsonDriftBodyEntity hunted
                && fake.getBoundingBox().intersects(hunted.getBoundingBox())) {
            // A creature whose blow is its touch (a slime).
            erase.noteStrike(hunted.player(), false);
        }
        if (!erase.struck) {
            return;
        }
        if (erase.inside >= KingCrimsonPhase5Spec.CREATURE_ERASE_MIN_TICKS) {
            strike(erase);
        } else if (!fake.isRemoved()) {
            // Too early to end: this blow was none, and the fake body strikes again.
            erase.struck = false;
        }
    }

    // The one swap: the creature stands where its fake body struck, the erase is over, and the blow is the creature's.
    private static void strike(Erase erase) {
        Mob creature = erase.creature;
        Mob fake = erase.fake;
        Vec3 to = fake.isRemoved() ? erase.struckAt : fake.position();
        ServerPlayer struck = erase.struckPlayer;
        boolean shot = erase.shot;
        creature.stopRiding();
        creature.moveTo(to.x, to.y, to.z, fake.getYRot(), fake.getXRot());
        creature.setYHeadRot(fake.getYHeadRot());
        creature.setYBodyRot(fake.getYRot());
        creature.getNavigation().stop();
        creature.resetFallDistance();
        // Into stopped time it freezes where it lands, as after a Time Skip.
        TimeStopState.repinOrReleaseOnServer(creature);
        // One swap for those who watch too: its model does not slide to the place.
        PacketDistributor.sendToPlayersTrackingEntity(creature, new TrDirectEntityPosPacket(creature.getId(), to));
        close(erase, 0);
        KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
        LivingEntity victim = struck != null ? standingFor(struck) : null;
        if (victim == null || !victim.isAlive() || victim.level() != creature.level() || !creature.canAttack(victim)) {
            return;
        }
        creature.setTarget(victim);
        if (shot && creature instanceof Creeper creeper) {
            creeper.ignite();
        } else if (shot && creature instanceof RangedAttackMob ranged) {
            ranged.performRangedAttack(victim, 1.0F);
        } else if (creature.getAttribute(Attributes.ATTACK_DAMAGE) != null
                && (!shot || creature.isWithinMeleeAttackRange(victim))) {
            // The blow its fake body landed itself is the creature's, whatever the step the fake body took after it
            // says of the reach from here.
            creature.swing(InteractionHand.MAIN_HAND);
            creature.doHurtTarget(victim);
        }
    }

    /**
     * The end of an erase by any route, in one tick: what it kept in the world is gone, the key's cooldown starts, and
     * the world is as after any erase (KingCrimsonTimeEraseEnd), but the creature itself is not dazed.
     *
     * @param skippedTicks what a passive escape erased on top: the players are taken to have gone on for so long
     */
    private static void close(Erase erase, int skippedTicks) {
        if (erase.ended) {
            return;
        }
        shut(erase, skippedTicks);
        // Owner 2026-11-11: what an erase that ends early did not use of its start comes back, as a player's does.
        KingCrimsonEraseStamina.refundUnused(erase.creature, erase.startCharged, erase.lengthTicks, erase.elapsed());
    }

    private static void shut(Erase erase, int skippedTicks) {
        if (erase.ended) {
            return;
        }
        erase.ended = true;
        remove(erase);
        erase.fake.discard();
        for (KingCrimsonDriftBodyEntity body : erase.bodies.values()) {
            body.discard();
        }
        erase.bodies.clear();
        Mob creature = erase.creature;
        KingCrimsonTemporalStateService.setTimeEraseCooldown(creature, erase.elapsed());
        if (erase.shift != null && skippedTicks > 0) {
            erase.shift.driftAhead(skippedTicks);
        }
        KingCrimsonTimeEraseEnd.run(creature, erase.shift, erase.elapsed() + skippedTicks, false);
    }

    // After every other handler has had the hit: what is left of it is what would land.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethalHit(LivingIncomingDamageEvent event) {
        Erase erase = of(event.getEntity());
        DamageSource source = event.getSource();
        if (erase == null || erase.ended || event.getAmount() <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || KingCrimsonEraseReach.pierces(source)) {
            return;
        }
        Mob creature = erase.creature;
        float hit = KingCrimsonReflexErase.afterDefences(creature, source, event.getAmount());
        if (hit > 0.0F && hit >= creature.getHealth() + creature.getAbsorptionAmount() && escapes(erase, source)) {
            event.setCanceled(true);
        }
    }

    /**
     * The passive erase of a creature (owner: "如果在這期間受到致死傷害則只能通過被動時刪來瞬間移動一段距離逃跑，原時刪的發動也因此結束 ...
     * 強制讓玩家做運動漂移與估算這幾秒內生物可移動距離直接瞬移"). With the player's setting, skill and ten minutes: it is spent
     * whether or not it saves the creature.
     */
    private static boolean escapes(Erase erase, DamageSource source) {
        Mob creature = erase.creature;
        StandPower power = StandPower.get(creature);
        Ability passive = power != null ? power.getAbility(KingCrimsonReflexErase.REFLEX) : null;
        if (!KingCrimsonConfig.reflexErase() || passive == null || !passive.isAbilityUnlocked(power)
                || power.isAbilityOnCooldown(KingCrimsonReflexErase.LAST_CHANCE) || inStoppedTime(creature)) {
            return false;
        }
        power.setAbilityCooldown(KingCrimsonReflexErase.LAST_CHANCE, KingCrimsonConfig.lastChanceEraseCooldownTicks());
        int ticks = Math.min(KingCrimsonConfig.lastChanceEraseTicks(),
                KingCrimsonConfig.timeEraseMaxTicks(power.getResolveLevel()));
        ServerPlayer dealer = playerBehind(source.getEntity());
        Rule answered = dealer != null ? answeredBy(dealer, creature) : null;
        if (answered != null) {
            answered.answer().show(dealer, creature);
            return false;
        }
        // Over in the tick it starts: a step, in the words of the core's event.
        if (ticks <= 0 || !TimeEraseStartEvent.post(creature, ticks, TimeEraseStartEvent.Kind.STEP)) {
            return false;
        }
        Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition()
                : source.getEntity() != null ? source.getEntity().position() : null;
        Vec3 to = escapePlace(creature, erase.level, from, ticks);
        close(erase, ticks);
        creature.stopRiding();
        creature.moveTo(to.x, to.y, to.z, creature.getYRot(), creature.getXRot());
        creature.getNavigation().stop();
        creature.resetFallDistance();
        TimeStopState.repinOrReleaseOnServer(creature);
        // Nothing between the two places for those who watch: its model does not slide there.
        PacketDistributor.sendToPlayersTrackingEntity(creature, new TrDirectEntityPosPacket(creature.getId(), to));
        KingCrimsonTemporalStateService.sendTimeEraseEndSound(creature);
        return true;
    }

    /**
     * Where the creature could have got to in so many ticks of an erase, away from what hit it: an estimate walked
     * over the ground as a shifted image is, so it stops at a wall, at a drop and at a hazard. Never a simulation.
     */
    static Vec3 escapePlace(Mob creature, ServerLevel level, @Nullable Vec3 from, int ticks) {
        Vec3 at = creature.position();
        Vec3 away = from != null ? new Vec3(at.x - from.x, 0.0D, at.z - from.z) : Vec3.ZERO;
        if (away.lengthSqr() < 1.0E-6D) {
            away = Vec3.directionFromRotation(0.0F, creature.getYRot() + 180.0F);
        }
        away = away.normalize().scale(escapeReach(creature, ticks));
        EntityDimensions size = creature.getDimensions(creature.getPose());
        double ground = creature.isInWater() || creature.isInLava() || creature.isNoGravity() ? -1.0D
                : KingCrimsonShiftWalk.groundBelow(level, creature, size.makeBoundingBox(at));
        if (ground < 0.0D) {
            return KingCrimsonShiftWalk.fly(level, creature, size, at, at.add(away));
        }
        return KingCrimsonShiftWalk.walk(level, creature, size, at.subtract(0.0D, ground, 0.0D), at.x + away.x, at.z + away.z);
    }

    /**
     * The blocks a creature covers in so many ticks of an erase. A creature's AI gives its legs its speed value as the
     * push of each tick too, so it covers the core's walk (BlinkLanding) times that value; in an erase, twice.
     */
    public static double escapeReach(Mob creature, int ticks) {
        double pace = BlinkLanding.distancePerTick(creature) * creature.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * (1.0D + KingCrimsonPhase5Spec.TIME_ERASE_MOVEMENT_MULTIPLIER);
        return Math.min(pace * Math.max(ticks, 0), KingCrimsonConfig.timeEraseStepMaxDistance());
    }

    // The hunt of a fake body is turned from a player on the body its erase keeps for him. It hunts nothing else, and
    // nothing else hunts such a body (KingCrimsonEraseReach lets only that hunt through).
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity wanted = event.getNewAboutToBeSetTarget();
        Erase erase = wanted != null ? ofFake(event.getEntity()) : null;
        if (erase == null || wanted instanceof KingCrimsonDriftBodyEntity) {
            return;
        }
        KingCrimsonDriftBodyEntity body = wanted instanceof ServerPlayer player ? erase.bodyFor(player) : null;
        if (body != null) {
            event.setNewAboutToBeSetTarget(body);
        } else {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (event.loadedFromDisk()) {
            if (entity instanceof Mob && entity.getPersistentData().getBoolean(FAKE_BODY_TAG)) {
                event.setCanceled(true);
            }
            return;
        }
        // What a fake body shoots, throws or calls up never comes into the world: it is its blow.
        if (running.length != 0 && entity instanceof TraceableEntity launched) {
            Erase erase = ofFake(launched.getOwner());
            if (erase != null) {
                event.setCanceled(true);
                erase.noteStrike(null, true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        Erase erase = ofFake(event.getExplosion().getDirectSourceEntity());
        if (erase != null) {
            event.setCanceled(true);
            erase.noteStrike(null, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (ofFake(event.getEntity()) != null) {
            event.setCanGrief(false);
        }
    }

    // A fake body harms nobody: its blow is the creature's, after the swap.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFakeBodyHarm(LivingIncomingDamageEvent event) {
        if (running.length != 0 && (ofFake(event.getSource().getEntity()) != null
                || ofFake(event.getSource().getDirectEntity()) != null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        running = NONE;
    }

    /** A blow reached a drift body: the strike of its erase, if it came from the fake body of that erase. */
    public static void onBodyStruck(KingCrimsonDriftBodyEntity body, DamageSource source) {
        Erase erase = ofFake(source.getEntity());
        if (erase == null) {
            erase = ofFake(source.getDirectEntity());
        }
        if (erase != null && erase.bodies.containsValue(body)) {
            erase.noteStrike(body.player(), source.getDirectEntity() != erase.fake);
        }
    }

    // Time is stopped where it stands, whether or not it is frozen in it.
    private static boolean inStoppedTime(Entity entity) {
        return TimeStopState.shouldFreezeOnServer(entity)
                || entity.level() instanceof ServerLevel level && level.hasData(ModDataAttachmentTypes.TIME_STOP.get())
                        && level.getData(ModDataAttachmentTypes.TIME_STOP.get()).isTimeStopped(entity);
    }

    // The player a creature fights through what it hunts: himself, his Stand, or the decoy that stands for him.
    @Nullable
    private static ServerPlayer playerBehind(@Nullable Entity hunted) {
        if (hunted instanceof StandEntity stand) {
            hunted = stand.getUser();
        } else if (hunted instanceof KingCrimsonTimeEraseDecoyEntity decoy) {
            hunted = decoy.getOwner();
        } else if (hunted instanceof TraceableEntity launched && !(hunted instanceof LivingEntity)) {
            hunted = launched.getOwner();
        }
        return hunted instanceof ServerPlayer player ? player : null;
    }

    // What stands in the world for a player now: his decoy while he erases time himself.
    private static LivingEntity standingFor(ServerPlayer player) {
        KingCrimsonTimeEraseDecoyEntity decoy = KingCrimsonEraseReach.isOutOfReach(player)
                ? KingCrimsonTimeEraseProjectionService.decoyOf(player) : null;
        return decoy != null ? decoy : player;
    }

    // The first rule that says yes. A rule that throws answers no, and the log says so once.
    @Nullable
    private static Rule answeredBy(ServerPlayer holder, Mob creature) {
        for (Rule known : ANSWERS) {
            try {
                if (known.answer().answers(holder, creature)) {
                    return known;
                }
            }
            catch (RuntimeException failure) {
                if (!answerFailureLogged) {
                    answerFailureLogged = true;
                    LOGGER.warn("Time Erase[creature] the answer of {} threw and is taken for a no", known.owner(), failure);
                }
            }
        }
        return null;
    }

    @Nullable
    private static Erase of(@Nullable Entity creature) {
        for (Erase erase : running) {
            if (erase.creature == creature) {
                return erase;
            }
        }
        return null;
    }

    @Nullable
    private static Erase ofFake(@Nullable Entity fake) {
        Erase[] all = running;
        if (all.length == 0 || !(fake instanceof Mob)) {
            return null;
        }
        for (Erase erase : all) {
            if (erase.fake == fake) {
                return erase;
            }
        }
        return null;
    }

    private static synchronized void add(Erase erase) {
        Erase[] before = running;
        Erase[] after = new Erase[before.length + 1];
        System.arraycopy(before, 0, after, 0, before.length);
        after[before.length] = erase;
        running = after;
    }

    private static synchronized void remove(Erase erase) {
        Erase[] before = running;
        int at = -1;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == erase) {
                at = i;
            }
        }
        if (at < 0) {
            return;
        }
        Erase[] after = new Erase[before.length - 1];
        System.arraycopy(before, 0, after, 0, at);
        System.arraycopy(before, at + 1, after, at, before.length - at - 1);
        running = after.length == 0 ? NONE : after;
    }

    private static final class Erase {
        private final Mob creature;
        private final ServerLevel level;
        private final Mob fake;
        @Nullable
        private final KingCrimsonTimeEraseShiftService.Session shift;
        private final int lengthTicks;
        // What its start really took from the bar of the creature.
        private final float startCharged;
        // The body kept for each player the fake body has turned on.
        private final HashMap<UUID, KingCrimsonDriftBodyEntity> bodies = new HashMap<>();
        // The creature's own ticks inside, and the world's time that passed in them.
        private int inside;
        private float world;
        private boolean paused;
        private boolean ended;
        private boolean struck;
        // For how many ticks the fake body of a creeper has wanted its fuse.
        private int fuse;
        // Whether the blow was something sent off, and not the fake body's own touch.
        private boolean shot;
        @Nullable
        private ServerPlayer struckPlayer;
        // Where the fake body stood at its blow: where a fake body that went off with it is taken to stand.
        private Vec3 struckAt = Vec3.ZERO;

        private Erase(Mob creature, ServerLevel level, Mob fake,
                @Nullable KingCrimsonTimeEraseShiftService.Session shift, int lengthTicks, float startCharged) {
            this.creature = creature;
            this.level = level;
            this.fake = fake;
            this.shift = shift;
            this.lengthTicks = lengthTicks;
            this.startCharged = startCharged;
        }

        private int elapsed() {
            return (int) world;
        }

        @Nullable
        private KingCrimsonDriftBodyEntity bodyFor(ServerPlayer player) {
            KingCrimsonDriftBodyEntity body = bodies.get(player.getUUID());
            if (body != null && !body.isRemoved() && body.player() == player) {
                return body;
            }
            if (ended || player.level() != level || !player.isAlive() || player.isSpectator()) {
                return null;
            }
            body = KingCrimsonAddon.TIME_ERASE_DRIFT_BODY_ENTITY.get().create(level);
            if (body == null) {
                return null;
            }
            body.standFor(player);
            body.follow(seenPlace(player));
            // Known before it joins the level: out of reach from its first moment there.
            bodies.put(player.getUUID(), body);
            if (!level.addFreshEntity(body)) {
                bodies.remove(player.getUUID());
                return null;
            }
            return body;
        }

        // Where the erase sees him: on his image, or on that of his decoy while he erases time himself.
        private Vec3 seenPlace(ServerPlayer player) {
            LivingEntity stands = standingFor(player);
            return shift != null ? shift.imageOf(stands) : stands.position();
        }

        private void placeBodies() {
            bodies.values().removeIf(body -> {
                ServerPlayer player = body.player();
                if (body.isRemoved() || player == null || player.isRemoved() || !player.isAlive()
                        || player.level() != level || player.isSpectator()) {
                    body.discard();
                    return true;
                }
                body.follow(seenPlace(player));
                return false;
            });
        }

        // The blow of the fake body, at the player it hunts when the blow itself names none.
        private void noteStrike(@Nullable ServerPlayer at, boolean sentOff) {
            if (ended || struck) {
                return;
            }
            struck = true;
            shot = sentOff;
            struckPlayer = at != null ? at
                    : fake.getTarget() instanceof KingCrimsonDriftBodyEntity hunted ? hunted.player() : null;
            struckAt = fake.position();
        }
    }
}
