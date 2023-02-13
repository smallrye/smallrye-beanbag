package io.smallrye.beanbag;

/**
 * A supplier that resolves a bean from a scope.
 */
final class BeanResolver<T> implements BeanSupplier<T> {
    private final Class<T> type;
    private final String name;
    private final boolean optional;
    private final DependencyFilter filter;

    BeanResolver(final Class<T> type, final String name, final boolean optional, final DependencyFilter filter) {
        this.type = type;
        this.name = name;
        this.optional = optional;
        this.filter = filter;
    }

    public T get(Scope scope) {
        return scope.getBean(type, name, optional, filter);
    }
}
