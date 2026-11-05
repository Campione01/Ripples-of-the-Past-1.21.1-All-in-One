package rotp.addons.darkbluemoon.ability;

import rotp.addons.darkbluemoon.entity.MiniTornadoEntity;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonMiniTornadoAction, the heavy punch's finisher (dark_blue_moon_finisher_punch): a heavy attack with its
 * windup, recovery and 50 stamina, the Stand 1.2 blocks in front, but no punch: in water a water sound and a mini water
 * tornado sent ahead (1.5 out, 0.6 a tick), on land 4 damage to the first living entity within 3 blocks in front.
 */
public class DbmMiniTornadoAbility extends StandEntityHeavyPunchAbility {
    public static final float LAND_BONUS_DAMAGE = 4.0F;
    private static final Vec3 OFFSET = new Vec3(0.0, 0.0, 1.2);

    public DbmMiniTornadoAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        this.createActionObj = MiniTornadoPunch::new;
    }

    public static class MiniTornadoPunch extends StandEntityHeavyPunch {
        public MiniTornadoPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(OFFSET, StandOffsetFromUser.Rotations.HEAD, false);
        }

        @Override
        public void updateStandOffset(StandEntity stand) {}

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide() || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = stand.getUser();
            if (user == null) {
                return;
            }
            Vec3 look = user.getLookAngle();
            if (DbmWater.isInWater(user)) {
                stand.playSound(DbmWater.randomWaterUseSound(stand.getRandom()), 1.0F, 1.0F);
                MiniTornadoEntity tornado = new MiniTornadoEntity(level, user, stand.position().add(look.scale(1.5)));
                tornado.setDeltaMovement(look.scale(0.6));
                level.addFreshEntity(tornado);
            }
            else {
                Vec3 start = stand.position().add(0.0, stand.getBbHeight() * 0.5, 0.0);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(3.0),
                        e -> e != user && e != stand && e.isAlive())) {
                    Vec3 toTarget = target.position().subtract(start);
                    if (toTarget.length() <= 3.0 && toTarget.normalize().dot(look) >= 0.5) {
                        target.hurt(user.damageSources().mobAttack(user), LAND_BONUS_DAMAGE);
                        break;
                    }
                }
            }
            // 1.16 StandEntityTask took the heavy attack's staminaCost (50) after standPerform
            StandAbilityStamina.consume(ability, StandPower.get(user), 50.0F, false);
        }
    }
}
