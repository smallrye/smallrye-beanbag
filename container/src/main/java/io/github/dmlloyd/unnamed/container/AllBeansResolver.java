package io.github.dmlloyd.unnamed.container;

import java.util.Collection;

/**
 * A supplier that resolves all bean from a scope.
 */
final class AllBeansResolver<T> implements BeanSupplier<Collection<T>> {
    private final Class<T> type;
    private final String name;
    private final DependencyFilter filter;

    AllBeansResolver(final Class<T> type, final String name, final DependencyFilter filter) {
        this.type = type;
        this.name = name;
        this.filter = filter;
    }

    public Collection<T> get(Scope scope) {
        return scope.getAllBeans(type, name, filter);
    }
}
