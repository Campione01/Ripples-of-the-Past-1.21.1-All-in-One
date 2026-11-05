package rotp.core.powersystem.standpower.entity;

import java.util.ArrayList;
import java.util.List;

import rotp.core.client.entityanim.barrage.BarrageSwings;
import rotp.core.client.sound.barrage.BarrageHitSoundHandler;

import net.minecraft.world.phys.Vec3;

public class ClientStandEntityStuff {
	public final BarrageSwings barrageSwings = new BarrageSwings();
	public final BarrageHitSoundHandler barrageHitSounds = new BarrageHitSoundHandler();

	public List<Vec3> tiltVecQueue = new ArrayList<>();
	public float lastMotionTiltTick = -1;
	// the last frame drew the hold pose of a grabbed entity, so the idle starts over once the Stand lets go
	public boolean grabHoldPosed;
	// the Stand's tick a grab of this client closed on no entity, so its empty-handed return (grab_miss) follows; -1: none
	public int grabMissTick = -1;

	public float getAlpha(StandEntity standEntity, float partialTick) {
		return (float) standEntity.rangeEfficiency * standEntity.modelAlpha.lerp(partialTick);
	}
}
