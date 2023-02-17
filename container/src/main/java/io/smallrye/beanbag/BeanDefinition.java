package io.smallrye.beanbag;

import java.util.Set;

/**
 * The formal definition of a bean.
 */
final class BeanDefinition<T> {
    private final String name;
    private final int priority;
    private final Class<T> type;
    private final Set<Class<? super T>> restrictedTypes;
    private final BeanSupplier<T> supplier;

    BeanDefinition(final String name, final int priority, final Class<T> type, final Set<Class<? super T>> restrictedTypes,
            final BeanSupplier<T> supplier) {
        this.name = name;
        this.priority = priority;
        this.type = type;
        this.restrictedTypes = restrictedTypes;
        this.supplier = supplier;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public Class<T> getType() {
        return type;
    }

    public Set<Class<? super T>> getRestrictedTypes() {
        return restrictedTypes;
    }

    public BeanSupplier<T> getBeanSupplier() {
        return supplier;
    }

    public String toString() {
        return "Definition for " + getType() + ", name=" + getName() + ", types=" + getRestrictedTypes();
    }
}
