package rotp.addons.kingcrimson.mixin.epitaph;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.level.ServerPlayer;

@Mixin(ServerPlayer.class)
public interface ServerPlayerStateAccessor {
    @Accessor("spawnInvulnerableTime")
    int kingCrimson$spawnInvulnerableTime();

    @Accessor("spawnInvulnerableTime")
    void kingCrimson$setSpawnInvulnerableTime(int ticks);
}
