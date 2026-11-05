package rotp.addons.echoes.client;

import javax.annotation.Nullable;

import rotp.addons.echoes.network.EchoesSoundPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * R772 F11: ACT1's sounds as the player hears them (the master's ClientSoundPlayback, ClientSoundLoop and
 * LoopingSoundInstance): a single play at the player, or one loop in his head whose pitch climbs over 20 s and which
 * fades out in half a second when it stops.
 */
public final class EchoesSoundAudio {
    @Nullable private static Loop loop;

    private EchoesSoundAudio() {}

    public static void onPlay(EchoesSoundPayloads.Play payload) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = EchoesSoundPayloads.id(payload.sound());
        if (mc.player != null && id != null) {
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvent.createVariableRangeEvent(id),
                    SoundSource.PLAYERS, 1.0F, payload.pitch(), RandomSource.create(), mc.player.blockPosition()));
        }
    }

    public static void onLoop(EchoesSoundPayloads.Loop payload) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = EchoesSoundPayloads.id(payload.sound());
        if (loop != null) {
            loop.fadeOut();
            loop = null;
        }
        if (id != null && mc.player != null) {
            loop = new Loop(SoundEvent.createVariableRangeEvent(id), mc.player);
            mc.getSoundManager().play(loop);
        }
    }

    public static void clear() {
        if (loop != null) {
            loop.fadeOut();
            loop = null;
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final LocalPlayer player;
        private boolean stopping;
        private float fade = 1.0F;
        private int ticks;

        Loop(SoundEvent event, LocalPlayer player) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void fadeOut() {
            stopping = true;
        }

        @Override
        public void tick() {
            ticks++;
            if (!player.isAlive()) {
                stop();
                return;
            }
            if (stopping) {
                fade -= 0.1F;
                if (fade <= 0.0F) {
                    stop();
                    return;
                }
                volume = Mth.clamp(fade, 0.0F, 1.0F);
            }
            pitch = Mth.clamp(1.0F + ticks / 400.0F, 0.5F, 2.0F);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
