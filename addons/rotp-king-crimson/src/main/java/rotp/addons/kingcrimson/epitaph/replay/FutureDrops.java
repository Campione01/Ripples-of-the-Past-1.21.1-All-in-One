package rotp.addons.kingcrimson.epitaph.replay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.mixin.epitaph.LivingStateAccessor;

/**
 * The loot of a foreseen death. The room rolled it when the phantom died and showed it to the user; when the body
 * dies of that death, exactly those stacks drop in place of a fresh roll, each as its phantom left the dying one, and
 * the foreseen experience is awarded. The drops event and the experience event of other mods run once, here, for real.
 * Which death is the foreseen one is kept by the prediction it belongs to.
 */
public final class FutureDrops {
    private FutureDrops() {}

    /** From the head of the loot of a death: true when the foreseen loot took its place. */
    public static boolean drop(LivingEntity dying, ServerLevel level, DamageSource source) {
        for (EpitaphSession session : EpitaphSessions.all()) {
            RecordedOp.Death foreseen = session.takeForeseenDeath(dying);
            if (foreseen != null) {
                drop(session, foreseen, dying, level, source);
                return true;
            }
        }
        return false;
    }

    private static void drop(EpitaphSession session, RecordedOp.Death foreseen, LivingEntity dying, ServerLevel level,
            DamageSource source) {
        LivingStateAccessor state = (LivingStateAccessor) dying;
        List<ItemEntity> drops = new ArrayList<>();
        for (RecordedOp.Drop drop : foreseen.drops()) {
            ItemEntity item = EntityType.ITEM.create(level);
            if (item == null) {
                continue;
            }
            CompoundTag data = drop.data().copy();
            session.room().uuids().toReals(data);
            item.load(data);
            item.setId(drop.child().id());
            drops.add(item);
        }
        if (!CommonHooks.onLivingDrops(dying, source, drops, state.kingCrimson$lastHurtByPlayerTime() > 0)) {
            for (ItemEntity item : drops) {
                if (level.addFreshEntity(item)) {
                    session.registerFree(item);
                }
            }
        }
        if (foreseen.experience() > 0) {
            int reward = EventHooks.getExperienceDrop(dying, state.kingCrimson$lastHurtByPlayer(), foreseen.experience());
            ExperienceOrb.award(level, dying.position(), reward);
        }
    }
}
