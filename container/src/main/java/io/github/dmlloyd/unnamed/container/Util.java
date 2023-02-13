package io.github.dmlloyd.unnamed.container;

import java.util.List;
import java.util.function.Function;

/**
 * Shared utilities.
 */
public final class Util {
    private Util() {}


    @SuppressWarnings("unchecked")
    static <R, T> List<R> mapList(List<T> input, Function<T, R> mapper) {
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
