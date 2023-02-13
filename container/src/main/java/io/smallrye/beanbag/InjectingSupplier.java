package io.smallrye.beanbag;

import java.util.List;

/**
 * A provider which injects things into fields after getting the instance from another provider.
 */
final class InjectingSupplier<T> implements BeanSupplier<T> {
    private final BeanSupplier<T> instanceSupplier;
    private final List<Injector<T>> injectors;

    InjectingSupplier(final BeanSupplier<T> instanceSupplier, final List<Injector<T>> injectors) {
        this.instanceSupplier = instanceSupplier;
        this.injectors = injectors;
    }

    public T get(Scope scope) {
        final T instance = instanceSupplier.get(scope);
        for (Injector<T> injector : injectors) {
            injector.injectInto(scope, instance);
        }
        return instance;
    }
}
