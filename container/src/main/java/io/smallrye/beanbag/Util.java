package io.smallrye.beanbag;

import java.util.List;
import java.util.function.Function;

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
     * @return the transformed list (not {@code null})
     * @param <R> the output list element type
     * @param <T> the input list element type
     */
    @SuppressWarnings("unchecked")
    public static <R, T> List<R> mapList(List<T> input, Function<T, R> mapper) {
        Assert.checkNotNullParam("input", input);
        Assert.checkNotNullParam("mapper", mapper);
        final int size = input.size();
        return switch (size) {
            case 0 -> List.of();
            case 1 -> List.of(mapper.apply(input.get(0)));
            case 2 -> List.of(mapper.apply(input.get(0)), mapper.apply(input.get(1)));
            case 3 -> List.of(mapper.apply(input.get(0)), mapper.apply(input.get(1)), mapper.apply(input.get(2)));
            default -> {
                final R[] array = (R[]) new Object[size];
                for (int i = 0; i < size; i ++) {
                    array[i] = mapper.apply(input.get(i));
                }
                yield List.of(array);
            }
        };
    }
}
