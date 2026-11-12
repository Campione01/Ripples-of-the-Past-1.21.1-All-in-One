package rotp.core.client.sound.sounds;

import javax.annotation.Nullable;

import rotp.core.client.sound.util.EventlessSound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;

/**
 * A voice pack line: a sound file with no sound event, following its speaker as
 * {@link EntityLingeringSoundInstance} does. It answers to the id of the sound event it stands for, so a stop
 * packet for the 1.16 line (or the trigger) cuts it off too.
 */
public class VoicePackLineSoundInstance extends EventlessSound implements TickableSoundInstance {
	private final ResourceLocation stopId;
	@Nullable private Entity entity;
	private boolean stopped;

	public VoicePackLineSoundInstance(ResourceLocation soundFile, @Nullable Component subtitle, ResourceLocation stopId,
			SoundSource source, float volume, float pitch, Entity entity) {
		super(new Sound(soundFile, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, false, false, 16),
				source, subtitle, volume, pitch, false, 0, Attenuation.LINEAR, entity.getX(), entity.getY(), entity.getZ(), false);
		this.stopId = stopId;
		this.entity = entity;
	}

	@Override
	public ResourceLocation getLocation() {
		return stopId;
	}

	@Override
	public boolean canPlaySound() {
		return entity == null || !entity.isSilent();
	}

	@Override
	public boolean isStopped() {
		return stopped;
	}

	@Override
	public void tick() {
		if (entity != null) {
			if (entity.isRemoved()) {
				if (entity.isSilent()) {
					stopped = true;
				}
				else {
					entity = null;
				}
			}
			else {
				x = entity.getX();
				y = entity.getY();
				z = entity.getZ();
			}
		}
	}
}
