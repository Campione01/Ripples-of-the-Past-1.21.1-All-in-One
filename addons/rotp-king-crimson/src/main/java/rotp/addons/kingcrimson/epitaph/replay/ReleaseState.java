package rotp.addons.kingcrimson.epitaph.replay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.record.Checkpoint;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTimeEraseProjectionService;
import rotp.core.powersystem.standpower.entity.StandEntity;

/**
 * The end of a track: the body stands where its phantom stood, and takes its phantom's end state. The saved data of
 * the phantom, with the real UUIDs back in it, is loaded onto the body, which keeps the health fate left it, its
 * place, and whatever was taken out of its data when the copy was made (its raid, its lead, its riders); its target
 * is the body of its phantom's target. From there it decides anew. A player and an entity made of parts take nothing
 * but what the ops did. What a goal kept to itself and an action in progress are not carried.
 */
public final class ReleaseState {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Kept from the body as it is now: what is not the phantom's to say. */
    private static final List<String> KEPT = List.of("UUID", "Pos", "Team", "RaidId", "leash", "Passengers", "NeoForgeData");

    private ReleaseState() {}

    public static void apply(EpitaphSession session, Binding binding) {
        Entity body = binding.body;
        if (body.isRemoved()) {
            return;
        }
        Checkpoint state = binding.track.checkpoint();
        if (state != null && !(body instanceof Player) && !(body instanceof StandEntity) && !body.isMultipartEntity()) {
            try {
                CompoundTag data = state.data().copy();
                session.room().uuids().toReals(data);
                CompoundTag now = body.saveWithoutId(new CompoundTag());
                for (String key : KEPT) {
                    Tag kept = now.get(key);
                    if (kept != null) {
                        data.put(key, kept);
                    } else {
                        data.remove(key);
                    }
                }
                // The entity data of the mods stays the own of the body: a load would put new objects in its place, and such
                // an object may hold what its saved form does not (the Stand power of the core holds its Stand).
                data.remove(AttachmentHolder.ATTACHMENTS_NBT_KEY);
                if (body instanceof LivingEntity living) {
                    data.putFloat("Health", living.getHealth());
                    dropEffectsNotIn(living, data);
                }
                body.load(data);
                if (body instanceof LivingEntity living) {
                    ActionMirror.takePower(session, binding, living);
                }
            }
            catch (RuntimeException failure) {
                LOGGER.warn("Epitaph[release] the end state of a {} could not be put on its body; it goes on as it is",
                        BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()), failure);
            }
        }
        if (body instanceof Mob mob) {
            Entity target = state != null ? session.bodyOf(state.target()) : null;
            // T7: one who erases time is out of reach, and his decoy stands for him.
            if (!(target instanceof ServerPlayer hunted && KingCrimsonTemporalStateService.isErasing(hunted)
                    && KingCrimsonTimeEraseProjectionService.retarget(hunted, mob))) {
                mob.setTarget(target instanceof LivingEntity living && living.isAlive() ? living : null);
            }
            mob.getNavigation().stop();
        }
    }

    // Loading saved data puts its effects on and takes none off: what the phantom no longer has is taken off first.
    private static void dropEffectsNotIn(LivingEntity living, CompoundTag data) {
        Set<String> kept = new HashSet<>();
        ListTag effects = data.getList("active_effects", Tag.TAG_COMPOUND);
        for (int i = 0; i < effects.size(); i++) {
            kept.add(effects.getCompound(i).getString("id"));
        }
        for (MobEffectInstance effect : List.copyOf(living.getActiveEffects())) {
            String id = effect.getEffect().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (!kept.contains(id)) {
                living.removeEffect(effect.getEffect());
            }
        }
    }
}
