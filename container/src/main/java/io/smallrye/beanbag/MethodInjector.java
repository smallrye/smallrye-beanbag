package io.smallrye.beanbag;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A method injection.
 */
final class MethodInjector<C, T> implements Injector<C> {
    private final Method method;
    private final BeanSupplier<T> supplier;

    MethodInjector(final Method method, final BeanSupplier<T> supplier) {
        this.method = method;
        this.supplier = supplier;
    }

    public void injectInto(Scope scope, C instance) {
        final T value;
        try {
            value = supplier.get(scope);
        } catch (Throwable t) {
            throw new InjectionException("Failed to acquire value from provider for method "
                    + method.getDeclaringClass().getSimpleName() + "#" + method.getName() + " of object " + instance, t);
        }
        try {
            method.invoke(instance, value);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new InjectionException("Failed to inject value " + value + " into method "
                    + method.getDeclaringClass().getSimpleName() + "#" + method.getName() + " of object " + instance, e);
        }
    }
}
