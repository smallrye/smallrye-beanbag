package io.github.dmlloyd.unnamed.container;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * A supplier of a bean from within a given scope.
 */
public interface BeanSupplier<T> {

    T get(Scope scope);

    static <T> BeanSupplier<T> of(T instance) {
        return scope -> instance;
    }

    default <U> BeanSupplier<U> transform(Function<T, U> function) {
        return scope -> function.apply(get(scope));
    }

    static <T> BeanSupplier<T> resolving(final Class<T> type, final String name, final boolean optional, final DependencyFilter filter) {
        return new BeanResolver<>(type, name, optional, filter);
    }

    static <T> BeanSupplier<Collection<T>> resolvingAll(final Class<T> type, final String name, final DependencyFilter filter) {
        return new AllBeansResolver<>(type, name, filter);
    }

    static <T> BeanSupplier<Map<String, T>> resolvingAllByName(Class<T> type, final DependencyFilter filter) {
        return new AllBeansWithNamesResolver<>(type, filter);
    }

}
