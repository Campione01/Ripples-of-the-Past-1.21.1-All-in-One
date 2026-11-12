package rotp.core.util.functions;

import java.util.List;
import java.util.function.Function;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

public class CodecUtil {

	public static <V> Codec<V> placeholderCodec(V defaultValue) {
		return new Codec<V>() {
			@Override public <T> DataResult<T> encode(V input, DynamicOps<T> ops, T prefix) { return DataResult.success(ops.empty()); }
			@Override public <T> DataResult<Pair<V, T>> decode(DynamicOps<T> ops, T input) { return DataResult.success(Pair.of(defaultValue, input)); }
		};
	}

	/** Reads one element or a list of them; writes a list of one as the single element. */
	public static <E> Codec<List<E>> listOrSingleCodec(Codec<E> elementCodec) {
		return Codec.either(elementCodec, elementCodec.listOf()).xmap(
				either -> either.map(List::of, Function.identity()),
				list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));
	}
}
