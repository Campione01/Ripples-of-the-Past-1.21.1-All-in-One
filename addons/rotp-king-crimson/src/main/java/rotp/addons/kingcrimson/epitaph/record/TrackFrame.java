package rotp.addons.kingcrimson.epitaph.record;

import java.util.List;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Where a phantom was and how it looked after one tick of the room. */
public record TrackFrame(double x, double y, double z, float yRot, float xRot, float yHeadRot, float yBodyRot,
        float motionX, float motionY, float motionZ, boolean onGround,
        List<SynchedEntityData.DataValue<?>> data) {

    public static TrackFrame of(Entity phantom, List<SynchedEntityData.DataValue<?>> data) {
        Vec3 motion = phantom.getDeltaMovement();
        float head = phantom.getYHeadRot();
        float body = phantom instanceof LivingEntity living ? living.yBodyRot : phantom.getYRot();
        return new TrackFrame(phantom.getX(), phantom.getY(), phantom.getZ(), phantom.getYRot(), phantom.getXRot(), head,
                body, (float) motion.x, (float) motion.y, (float) motion.z, phantom.onGround(), data);
    }

    public Vec3 position() {
        return new Vec3(x, y, z);
    }

    public Vec3 motion() {
        return new Vec3(motionX, motionY, motionZ);
    }
}
