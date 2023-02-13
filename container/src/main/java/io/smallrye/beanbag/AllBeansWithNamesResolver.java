package io.smallrye.beanbag;

import java.util.Map;

/**
 * A supplier that resolves all bean from a scope.
 */
final class AllBeansWithNamesResolver<T> implements BeanSupplier<Map<String, T>> {
    private final Class<T> type;
    private final DependencyFilter filter;

    AllBeansWithNamesResolver(final Class<T> type, final DependencyFilter filter) {
        this.type = type;
        this.filter = filter;
    }

    public Map<String, T> get(Scope scope) {
        return scope.getAllBeansWithNames(type, filter);
    }
}
