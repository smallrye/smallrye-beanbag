package io.github.dmlloyd.unnamed.container;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.smallrye.common.constraint.Assert;

/**
 * A value supplier for use from within a given scope.
 */
public interface BeanSupplier<T> {

    /**
     * Get the supplied value.
     *
     * @param scope the scope of the current resolution operation (not {@code null})
     * @return the (possibly {@code null}) resolved value
     */
    T get(Scope scope);

    /**
     * Get a bean supplier which always returns the given value.
     *
     * @param instance the value to return
     * @return the value that was given
     * @param <T> the value type
     */
    static <T> BeanSupplier<T> of(T instance) {
        return scope -> instance;
    }

    /**
     * Get a bean supplier which applies the given transformation function to the result of this supplier.
     *
     * @param function the transformation function (must not be {@code null})
     * @return the transformed supplier (not {@code null})
     * @param <U> the transformed value type
     */
    default <U> BeanSupplier<U> transform(Function<T, U> function) {
        Assert.checkNotNullParam("function", function);
        return scope -> function.apply(get(scope));
    }

    /**
     * Get a bean supplier whose value is the result of resolution of a bean with the given parameters.
     *
     * @param type the bean type class (must not be {@code null})
     * @param name the bean name, or {@code ""} for any (must not be {@code null})
     * @param optional {@code true} if the bean should be optional, or {@code false} if it should be required
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the supplier (not {@code null})
     * @param <T> the bean type
     */
    static <T> BeanSupplier<T> resolving(final Class<T> type, final String name, final boolean optional, final DependencyFilter filter) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("filter", filter);
        return new BeanResolver<>(type, name, optional, filter);
    }

    /**
     * Get a bean supplier whose value is a list representing the result of resolving all the beans with the given parameters.
     *
     * @param type the bean type class (must not be {@code null})
     * @param name the bean name, or {@code ""} for any (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the supplier (not {@code null})
     * @param <T> the bean type
     */
    static <T> BeanSupplier<List<T>> resolvingAll(final Class<T> type, final String name, final DependencyFilter filter) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("filter", filter);
        return new AllBeansResolver<>(type, name, filter);
    }

    /**
     * Get a bean supplier whose value is a map representing the result of resolving all the beans with the given parameters.
     * The key of the map is the bean name, and the value is the bean instance.
     *
     * @param type the bean type class (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the supplier (not {@code null})
     * @param <T> the bean type
     */
    static <T> BeanSupplier<Map<String, T>> resolvingAllByName(Class<T> type, final DependencyFilter filter) {
        Assert.checkNotNullParam("type", type);
        Assert.checkNotNullParam("filter", filter);
        return new AllBeansWithNamesResolver<>(type, filter);
    }

}
