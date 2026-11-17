package rotp.addons.kingcrimson.mixin.epitaph;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.google.common.collect.ImmutableList;

import net.minecraft.world.entity.Entity;

/** Riders and their vehicle are linked as fields in a room: mounting would post an event and a game event. */
@Mixin(Entity.class)
public interface EntityLinkAccessor {
    @Accessor("vehicle")
    void kingCrimson$setVehicle(@Nullable Entity vehicle);

    @Accessor("passengers")
    void kingCrimson$setPassengers(ImmutableList<Entity> passengers);
}
