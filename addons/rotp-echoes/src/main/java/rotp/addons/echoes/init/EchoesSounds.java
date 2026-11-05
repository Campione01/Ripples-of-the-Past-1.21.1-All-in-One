package rotp.addons.echoes.init;

import rotp.addons.echoes.RotpEchoes;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EchoesSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RotpEchoes.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent>
            SUMMON_VOICE_ACT3 = register("summon_voice_act3");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_ATTACK =
            register("heavy_attack");
    public static final DeferredHolder<SoundEvent, SoundEvent> BARRAGE_HIT_SOUND =
            register("barrage_hit_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BARRAGE_HIT_SOUND_2 = register("barrage_hit_sound_2");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            BARRAGE_HIT_SOUND_3 = register("barrage_hit_sound_3");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_BGM =
            register("skill_bgm");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKILL_USE_SOUND =
            register("skill_use_sound");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            SKILL_USE_SOUND_2 = register("skill_use_sound_2");
    // R772 F11, the author's latest 1.16.5 build (RotP-EchoesACT-master)
    public static final DeferredHolder<SoundEvent, SoundEvent> ACT1_SUMMON =
            register("act1_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> ACT2_SUMMON =
            register("act2_summon");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ECHOES_SUMMON_BGM_ACT3 = register("echoes_summon_bgm_act3");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ECHOES_SUMMON_BGM_COMMON = register("echoes_summon_bgm_common");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            ECHOES_RECALL_BGM = register("echoes_recall_bgm");
    public static final DeferredHolder<SoundEvent, SoundEvent> FREEZE_VOICE =
            register("freeze_voice");
    public static final DeferredHolder<SoundEvent, SoundEvent>
            EASTER_EGG_SUMMON = register("easter_egg_summon");

    private EchoesSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, SoundEvent::createVariableRangeEvent);
    }
}
