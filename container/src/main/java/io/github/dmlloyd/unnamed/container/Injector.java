package io.github.dmlloyd.unnamed.container;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.smallrye.common.constraint.Assert;

/**
 * An injector step which populates a given instance.
 *
 * @param <C> the instance type
 */
interface Injector<C> {
    void injectInto(Scope scope, C instance);

    static <C, T> Injector<C> forField(Field field, BeanSupplier<T> supplier) {
        Assert.checkNotNullParam("field", field);
        Assert.checkNotNullParam("supplier", supplier);
        final int mods = field.getModifiers();
        if (Modifier.isFinal(mods)) {
            throw new IllegalArgumentException("Cannot inject into final field " + field.getDeclaringClass().getSimpleName());
        }
        if (Modifier.isStatic(mods)) {
            throw new IllegalArgumentException("Cannot inject into static field " + field.getDeclaringClass().getSimpleName());
        }
        return new FieldInjector<>(field, supplier);
    }

    static <C, T> Injector<C> forSetterMethod(Method method, BeanSupplier<T> supplier) {
        Assert.checkNotNullParam("method", method);
        Assert.checkNotNullParam("supplier", supplier);
        final int mods = method.getModifiers();
        if (Modifier.isStatic(mods)) {
            throw new IllegalArgumentException("Cannot inject into static method " + method.getDeclaringClass().getSimpleName());
        }
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException("Cannot inject into method with more than one argument");
        }
        return new MethodInjector<>(method, supplier);
    }
}
