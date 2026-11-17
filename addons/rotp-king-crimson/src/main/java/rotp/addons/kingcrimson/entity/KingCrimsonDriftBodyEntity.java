package rotp.addons.kingcrimson.entity;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;

/**
 * Where a creature that erases time sees a player. The owner, 2026-11-11: "生物替身使者在時刪發動時的索敵在單人和多人模式都按玩家運動
 * 漂移後的假身計算". For the length of its erase the fake body of the creature hunts this body in place of the player; the
 * erase keeps it on his shifted image, where he is put when the erase ends.
 * <p>
 * On the server alone: no client is ever told of it, and it is never saved. Nothing but the fake body of its erase can
 * hunt it, and nothing can touch it. A blow of that fake body is the strike that ends the erase.
 */
public final class KingCrimsonDriftBodyEntity extends LivingEntity {
    @Nullable
    private ServerPlayer player;

    public KingCrimsonDriftBodyEntity(EntityType<? extends KingCrimsonDriftBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setSilent(true);
    }

    /** From now on it stands for this player. */
    public void standFor(ServerPlayer seen) {
        player = seen;
        follow(seen.position());
    }

    @Nullable
    public ServerPlayer player() {
        return player;
    }

    /** Put on the image of its player, turned as he is. */
    public void follow(Vec3 image) {
        setPos(image.x, image.y, image.z);
        setOldPosAndRot();
        if (player != null) {
            setYRot(player.getYRot());
            setYHeadRot(player.getYHeadRot());
        }
    }

    // Its erase places it. One that no erase owns any more is gone.
    @Override
    public void tick() {
        if (!level().isClientSide() && !KingCrimsonCreatureErase.owns(this)) {
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide()) {
            KingCrimsonCreatureErase.onBodyStruck(this, source);
        }
        return false;
    }

    // What hunts it must be able to see it: the erase lets nothing but its fake body hunt it.
    @Override
    public boolean canBeSeenByAnyone() {
        return isAlive();
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void push(Entity other) {
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer viewer) {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }
}
