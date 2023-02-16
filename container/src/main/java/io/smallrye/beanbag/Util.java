package io.smallrye.beanbag;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import io.smallrye.common.constraint.Assert;

/**
 * Shared utilities.
 */
public final class Util {
    private Util() {}

    /**
     * Efficiently create a copy of the input list with each list element being transformed by the given function.
     *
     * @param input the input list (must not be {@code null})
     * @param mapper the mapping function (must not be {@code null})
     * @param generator the generator for an array of type {@code R} (must not be {@code null})
     * @return the transformed list (not {@code null})
     * @param <R> the output list element type
     * @param <T> the input list element type
     */
    public static <R, T> List<R> mapList(List<T> input, Function<T, R> mapper, IntFunction<R[]> generator) {
        Assert.checkNotNullParam("input", input);
        Assert.checkNotNullParam("mapper", mapper);
        final int size = input.size();
        switch (size) {
            case 0: return List.of();
            case 1: return List.of(mapper.apply(input.get(0)));
            case 2: return List.of(mapper.apply(input.get(0)), mapper.apply(input.get(1)));
            case 3: return List.of(mapper.apply(input.get(0)), mapper.apply(input.get(1)), mapper.apply(input.get(2)));
            default: {
                final R[] array = generator.apply(size);
                for (int i = 0; i < size; i++) {
                    array[i] = mapper.apply(input.get(i));
                }
                return List.of(array);
            }
        }
    }
}
