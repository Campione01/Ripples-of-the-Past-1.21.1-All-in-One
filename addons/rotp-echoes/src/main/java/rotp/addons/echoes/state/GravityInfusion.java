package rotp.addons.echoes.state;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The gravity on an item: whose it is, and whether it is still flying as his throw (the author's GravityItemHelper). */
public record GravityInfusion(UUID owner, boolean thrown) {
    public static final Codec<GravityInfusion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("owner").forGetter(GravityInfusion::owner),
                    Codec.BOOL.optionalFieldOf("thrown", false).forGetter(GravityInfusion::thrown))
            .apply(instance, GravityInfusion::new));
    public static final StreamCodec<ByteBuf, GravityInfusion> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GravityInfusion::owner,
            ByteBufCodecs.BOOL, GravityInfusion::thrown,
            GravityInfusion::new);
}
