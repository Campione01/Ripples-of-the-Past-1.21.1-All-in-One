package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.SynchedEntityData;

@Mixin(SynchedEntityData.class)
public interface SynchedDataAccessor {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] kingCrimson$items();
}
