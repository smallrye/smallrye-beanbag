package io.github.dmlloyd.unnamed.container;

import java.lang.reflect.Field;

/**
 * A field injection.
 */
final class FieldInjector<C, T> implements Injector<C> {
    private final Field field;
    private final BeanSupplier<T> supplier;

    FieldInjector(final Field field, final BeanSupplier<T> supplier) {
        this.field = field;
        this.supplier = supplier;
    }

    public void injectInto(Scope scope, C instance) {
        final T value;
        try {
            value = supplier.get(scope);
        } catch (Throwable t) {
            throw new InjectionException("Failed to acquire value from provider for field " + field.getDeclaringClass().getSimpleName() + "#" + field.getName() + " of object " + instance, t);
        }
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new InjectionException("Failed to inject value " + value + " into field " + field.getDeclaringClass().getSimpleName() + "#" + field.getName() + " of object " + instance, e);
        }
    }
}
