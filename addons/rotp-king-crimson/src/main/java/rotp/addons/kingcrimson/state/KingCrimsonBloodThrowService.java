package rotp.addons.kingcrimson.state;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonDecoyRules;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.BloodThrowSplashPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Blood Throw (以血蒙眼). The owner, 2026-11-07: "在時刪時可以通過時刪結束這個鍵的變體操作來對準星位置敵人釋放以血矇眼，套用原來沒用上過的資源，同時
 * 接入rotp新版本本身加入的血遮蔽屏幕效果", and "遮蔽屏幕持續兩秒，如果我們做的技能已經有血屏幕效果則不附加半盲了". In an erase, King Crimson's user
 * flicks his own blood at what is under his crosshair, for 2 health (never below 1), stamina and a cooldown. The drops
 * fly through the erased time, seen by him alone, and land when time returns, or when they arrive if that is later:
 * a player's screen is covered in blood for two seconds, a creature is half blinded as by Eye Jab. The blood itself
 * hurts nobody, and nothing lands if another ability threw the eraser out of his erase.
 */
public final class KingCrimsonBloodThrowService {
    public static final String ABILITY = "kingcrimson_bloodthrow";

    private KingCrimsonBloodThrowService() {}

    /** Whether something the user can throw at is under his crosshair. */
    public static boolean hasTarget(ServerPlayer eraser) {
        return aimedAt(eraser) != null;
    }

    /**
     * Throws at what is under the crosshair. False, and nothing spent, with no running erase of his, no target, no
     * stamina, a cooldown, or the skill locked.
     */
    public static boolean throwAtAim(ServerPlayer eraser) {
        StandPower power = StandPower.get(eraser);
        Ability ability = power != null ? power.getAbility(ABILITY) : null;
        if (ability == null || !ability.isAbilityUnlocked(power)
                || !KingCrimsonTemporalStateService.isErasing(eraser)
                // An erase frozen in someone's time stop: its user does nothing.
                || KingCrimsonTemporalStateService.isErasePaused(eraser)
                || power.isAbilityOnCooldown(ABILITY)
                || !StandAbilityStamina.canPay(power, KingCrimsonConfig.bloodThrowStamina())) {
            return false;
        }
        LivingEntity target = aimedAt(eraser);
        if (target == null) {
            return false;
        }
        // His own blood, straight off his health and never the last point of it: no damage event, which his erase
        // would stop.
        if (!eraser.getAbilities().instabuild) {
            float health = eraser.getHealth();
            eraser.setHealth(Math.max(Math.min(health, 1.0F), health - KingCrimsonConfig.bloodThrowHealthCost()));
        }
        StandAbilityStamina.consume(ability, power, KingCrimsonConfig.bloodThrowStamina(), false);
        ability.setCooldownOnUse(power);
        Vec3 from = eraser.getEyePosition();
        int flightTicks = Math.max(1,
                Mth.ceil(from.distanceTo(eyesOf(eraser, target)) / KingCrimsonPhase5Spec.BLOOD_THROW_SPEED));
        // Kept with his own state: the drops fly on after the erase, and go with him if he goes.
        KingCrimsonAddon.temporalState(eraser).bloodFlights()
                .add(new Flight(target, from, eraser.level().getGameTime(), flightTicks));
        // In the erase only he hears the flick (the donor's event, which had no sound of its own).
        eraser.playNotifySound(KingCrimsonAddon.KINGCRIMSON_BLOODTHROW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** Every tick of the user: the drops on their way are shown, and those that arrive after the erase land. */
    static void tick(ServerPlayer eraser) {
        if (KingCrimsonAddon.temporalState(eraser).hasBloodFlights()) {
            settle(eraser, KingCrimsonTemporalStateService.isErasing(eraser), true);
        }
    }

    /**
     * The erase is over: what has arrived lands in this tick, the first instant its target can see again; what is
     * still on its way lands when it arrives. Thrown out of his erase, the user's blood lands on nobody.
     */
    static void onEraseEnd(ServerPlayer eraser, boolean thrownOut) {
        if (thrownOut) {
            KingCrimsonAddon.temporalState(eraser).dropBloodFlights();
        } else if (KingCrimsonAddon.temporalState(eraser).hasBloodFlights()) {
            settle(eraser, false, false);
        }
    }

    private static void settle(ServerPlayer eraser, boolean erasing, boolean showDrops) {
        List<Flight> flights = KingCrimsonAddon.temporalState(eraser).bloodFlights();
        long now = eraser.level().getGameTime();
        for (Iterator<Flight> each = flights.iterator(); each.hasNext();) {
            Flight flight = each.next();
            LivingEntity target = flight.target;
            if (!target.isAlive() || target.level() != eraser.level()) {
                each.remove();
            } else if (now - flight.thrownAt < flight.flightTicks) {
                if (showDrops) {
                    showDrops(eraser, flight, erasing, now);
                }
            } else if (!erasing) {
                land(target);
                each.remove();
            }
        }
    }

    // In the erase the drops are in the erased time, where he alone is; once it is over they are in everybody's.
    private static void showDrops(ServerPlayer eraser, Flight flight, boolean erasing, long now) {
        Vec3 at = flight.from.lerp(eyesOf(eraser, flight.target), (now - flight.thrownAt) / (double) flight.flightTicks);
        ServerLevel level = eraser.serverLevel();
        if (erasing) {
            level.sendParticles(eraser, ModParticles.BLOOD.get(), true, at.x, at.y, at.z, 3, 0.05D, 0.05D, 0.05D, 0.0D);
        } else {
            level.sendParticles(ModParticles.BLOOD.get(), at.x, at.y, at.z, 3, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    private static void land(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            // The core's blood over his screen for two seconds, and so no half blindness on top of it.
            PacketDistributor.sendToPlayer(player, BloodThrowSplashPayload.INSTANCE);
        } else {
            target.addEffect(new MobEffectInstance(
                    target.getRandom().nextBoolean()
                            ? KingCrimsonAddon.HALF_BLINDNESS_LEFT
                            : KingCrimsonAddon.HALF_BLINDNESS_RIGHT,
                    KingCrimsonPhase5Spec.BLOOD_THROW_MOB_BLINDNESS_TICKS, 0, false, false, false));
        }
        // The splash the core's own thrown blood makes.
        target.level().playSound(null, target.getX(), target.getEyeY(), target.getZ(),
                ModSoundEvents.WATER_SPLASH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // What the user's crosshair is on, within the throw's range and with nothing solid before it. A body counts
    // where the user sees it, on its shifted image; a Stand counts for its user; his own decoy is nobody, and so is
    // one who erases time himself, whose decoy stands there for him.
    @Nullable
    private static LivingEntity aimedAt(ServerPlayer eraser) {
        Vec3 from = eraser.getEyePosition();
        Vec3 look = eraser.getLookAngle();
        Vec3 reach = look.scale(KingCrimsonPhase5Spec.BLOOD_THROW_RANGE);
        Vec3 to = from.add(reach);
        ServerLevel level = eraser.serverLevel();
        LivingEntity nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(eraser, eraser.getBoundingBox().expandTowards(reach).inflate(2.0D),
                entity -> entity instanceof LivingEntity && !(entity instanceof ArmorStand) && entity.isAlive()
                        && !entity.isSpectator())) {
            LivingEntity body = entity instanceof StandEntity stand ? stand.getUser() : (LivingEntity) entity;
            if (body == null || body == eraser || !body.isAlive() || body.isSpectator()
                    || KingCrimsonDecoyRules.decoyOf(body) instanceof KingCrimsonTimeEraseDecoyEntity decoy
                            && decoy.getOwner() == eraser
                    || KingCrimsonEraseReach.isOutOfReach(body)) {
                continue;
            }
            AABB seen = entity.getBoundingBox().move(seenAt(eraser, body).subtract(body.position())).inflate(0.3D);
            Vec3 hit = seen.contains(from) ? from : seen.clip(from, to).orElse(null);
            if (hit != null && from.distanceToSqr(hit) < nearestSqr) {
                nearestSqr = from.distanceToSqr(hit);
                nearest = body;
            }
        }
        if (nearest == null) {
            return null;
        }
        Vec3 until = from.add(look.scale(Math.sqrt(nearestSqr)));
        boolean walled = level.clip(new ClipContext(from, until, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                eraser)).getType() != HitResult.Type.MISS;
        return walled ? null : nearest;
    }

    private static Vec3 seenAt(ServerPlayer eraser, LivingEntity target) {
        return KingCrimsonTimeEraseShiftService.imageOf(eraser, target);
    }

    private static Vec3 eyesOf(ServerPlayer eraser, LivingEntity target) {
        return seenAt(eraser, target).add(0.0D, target.getEyeHeight(), 0.0D);
    }

    /** Blood on its way: at what, from where, since when and for how long. */
    record Flight(LivingEntity target, Vec3 from, long thrownAt, int flightTicks) {}
}
