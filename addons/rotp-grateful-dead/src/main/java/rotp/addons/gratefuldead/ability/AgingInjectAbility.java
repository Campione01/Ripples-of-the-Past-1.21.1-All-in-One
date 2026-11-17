package rotp.addons.gratefuldead.ability;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Next Album 0.1.13 TheGratefulDeadAgingInjectAction, the attack-recovery
 * follow-up of the heavy punch: pressed on the heavy key while the Stand
 * throws a finisher, or a heavy punch at the entity it holds, it pays 15
 * stamina and ages that entity by 0.52 on 0.1.13's 0..2 scale (0.26 here) as
 * a contact. 0.1.13 took the press in any phase of that punch and aged at its
 * strike when pressed before it. Once per punch, as 0.1.13's triggered flag.
 */
public final class AgingInjectAbility extends Ability {
    public static final float STAMINA_COST = 15.0F;
    public static final float AGING_INJECT_AMOUNT = 0.52F / 2.0F;
    // keyed by the punch, not the Stand: client and server copies of a Stand are equal by network id
    private static final Set<EntityActionInstance> INJECTED =
            Collections.newSetFromMap(new WeakHashMap<>());
    // server: a press made before its punch struck, applied at the strike. The punch weakly: it holds its Stand, the
    // key, which a strong value would never let go.
    private static final Map<StandEntity, WeakReference<StandEntityHeavyPunch>> PENDING =
            new WeakHashMap<>();

    public AgingInjectAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        isSubAbility = true;
    }

    @Override
    public Ability replaceWithSubAbility(Power<?> context, AvailableAbilities abilities) {
        if (abilities != null && runningInjectablePunch(PowerClass.STAND.cast(context)) != null
                && checkPerformerStun(context).isPositive()) {
            abilities.replaceOtherAbilityWith(context, GratefulDeadAddon.HEAVY_PUNCH, this);
        }
        return null;
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck stun = checkPerformerStun(context);
        if (!stun.isPositive()) {
            return stun;
        }
        if (runningInjectablePunch(PowerClass.STAND.cast(context)) == null) {
            return ConditionCheck.NEGATIVE;
        }
        return super.checkSpecificConditions(context);
    }

    @Override
    public void onClick(Level level, LivingEntity user, FriendlyByteBuf extraClientInput) {
        StandPower power = StandPower.get(user);
        StandEntityHeavyPunch punch = runningInjectablePunch(power);
        if (punch == null) {
            return;
        }
        StandEntity stand = power.getSummonedStandEntity();
        synchronized (INJECTED) {
            INJECTED.add(punch);
        }
        if (level.isClientSide() || level.getServer() == null) {
            return;
        }
        StandAbilityStamina.consume(this, power, STAMINA_COST, false);
        // 0.1.13 .shout(PROSCIUTTO_AGING_PUNCH), said on the press
        sayShout(user, GratefulDeadAddon.randomAgingPunchShout());
        if (hasStruck(punch)) {
            inject(level, user, stand, punch);
        }
        else {
            synchronized (PENDING) {
                PENDING.put(stand, new WeakReference<>(punch));
            }
        }
    }

    /** Server, each tick of the Stand: a press made before the strike ages once the punch has struck. */
    public static void tickStand(StandEntity stand) {
        StandEntityHeavyPunch punch;
        synchronized (PENDING) {
            WeakReference<StandEntityHeavyPunch> pending = PENDING.get(stand);
            punch = pending != null ? pending.get() : null;
        }
        if (punch == null) {
            return;
        }
        boolean running = stand.getCurStandAction() == punch && !punch.isOver();
        if (running && !hasStruck(punch)) {
            return;
        }
        synchronized (PENDING) {
            PENDING.remove(stand);
        }
        LivingEntity user = stand.getUser();
        if (running && user != null && stand.level().getServer() != null) {
            inject(stand.level(), user, stand, punch);
        }
    }

    private static boolean hasStruck(StandEntityHeavyPunch punch) {
        return punch.punchedTarget != null
                || punch.getPhase() == ActionPhase.RECOVERY;
    }

    // 0.1.13 resolveInjectTarget: the held entity, else the punch's target, which 1.16 made the entity it struck
    private static void inject(Level level, LivingEntity user, StandEntity stand, StandEntityHeavyPunch punch) {
        LivingEntity target = LivingComponentGrab.getEntityGrabbedBy(stand);
        if (target == null && punch.punchedTarget != null
                && punch.punchedTarget.getEntity() instanceof LivingEntity struck) {
            target = struck;
        }
        if (target == null) {
            ActionTarget punchTarget = punch.getTaskTarget(level);
            Entity entity = punchTarget != null ? punchTarget.getMainEntity() : null;
            target = entity instanceof LivingEntity living ? living : null;
        }
        if (target != null && target != user) {
            AgingService.ageLiving(target, user.getUUID(),
                    AgingService.generation(level.getServer(), user.getUUID()),
                    AGING_INJECT_AMOUNT, true);
        }
    }

    /**
     * The finisher or grab heavy punch the Stand is throwing, not yet injected, in any phase: 0.1.13 offered the
     * follow-up through a finisher's whole task, and through a heavy punch's while it held a living entity or after it
     * struck one it held, which in the core is the grab heavy punch.
     */
    @Nullable
    static StandEntityHeavyPunch runningInjectablePunch(@Nullable StandPower power) {
        StandEntity stand = power != null && power.hasPower()
                ? power.getSummonedStandEntity()
                : null;
        EntityActionInstance action = stand != null ? stand.getCurStandAction() : null;
        if (!(action instanceof StandEntityHeavyPunch punch)
                || punch.isOver()
                || !(punch.ability instanceof Ability punchAbility)) {
            return null;
        }
        String name = punchAbility.getAbilityId().nameInMoveset();
        boolean injectable = punchAbility.isStandFinisherOf != null
                || GratefulDeadAddon.GRAB_HEAVY_PUNCH.equals(name);
        if (!injectable) {
            return null;
        }
        synchronized (INJECTED) {
            return INJECTED.contains(punch) ? null : punch;
        }
    }

    @Override
    public float getStaminaCost(Power<?> context) {
        return STAMINA_COST;
    }

    /** The session's presses, forgotten when its server has stopped. */
    public static void forgetSession() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        synchronized (INJECTED) {
            INJECTED.clear();
        }
    }
}
