package io.smallrye.beanbag;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 *
 */
final class ConstructorSupplier<T> implements BeanSupplier<T> {
    private final Constructor<T> constructor;
    private final List<BeanSupplier<?>> argumentSuppliers;

    ConstructorSupplier(final Constructor<T> constructor, final List<BeanSupplier<?>> argumentSuppliers) {
        if (argumentSuppliers.size() != constructor.getParameterCount()) {
            throw new IllegalArgumentException("Not enough argument suppliers for the given constructor");
        }
        this.constructor = constructor;
        this.argumentSuppliers = argumentSuppliers;
    }

    public T get(Scope scope) {
        final int size = argumentSuppliers.size();
        Object[] arguments = new Object[size];
        for (int i = 0; i < size; i++) {
            try {
                arguments[i] = argumentSuppliers.get(i).get(scope);
            } catch (Exception ex) {
                throw new BeanInstantiationException("Failed to inject argument " + i + " of constructor for " + constructor.getDeclaringClass(), ex);
            }
        }
        try {
            return constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new BeanInstantiationException("Constructor usage failed", e);
        } catch (InvocationTargetException e) {
            throw new BeanInstantiationException("Constructor invocation failed", e.getCause());
        }
    }
}
