package io.github.dmlloyd.unnamed.container;

import io.smallrye.common.constraint.Assert;

/**
 * A filter which can be used to reject dependency candidates.
 */
public interface DependencyFilter {
    /**
     * Test to see whether the dependency is acceptable.
     *
     * @param concreteType the type of the bean which is to be instantiated (not {@code null})
     * @param name the name of the bean (maybe empty, not {@code null})
     * @param priority the priority of the bean (higher has precedent)
     * @return {@code true} to accept the dependency, or {@code false} to reject it
     */
    boolean test(Class<?> concreteType, String name, int priority);

    DependencyFilter ACCEPT = (concreteType, name, priority) -> true;
    DependencyFilter REJECT = (concreteType, name, priority) -> false;

    /**
     * Create a new filter which returns {@code true} when both this filter <em>and</em> the given filter return {@code true}.
     *
     * @param other the other filter (must not be {@code null})
     * @return the combined filter (not {@code null})
     */
    default DependencyFilter and(DependencyFilter other) {
        Assert.checkNotNullParam("other", other);
        return (concreteType, name, priority) -> test(concreteType, name, priority) && other.test(concreteType, name, priority);
    }

    /**
     * Create a new filter which returns {@code true} when either this filter <em>or</em> the given filter return {@code true}.
     *
     * @param other the other filter (must not be {@code null})
     * @return the combined filter (not {@code null})
     */
    default DependencyFilter or(DependencyFilter other) {
        Assert.checkNotNullParam("other", other);
        return (concreteType, name, priority) -> test(concreteType, name, priority) || other.test(concreteType, name, priority);
    }
}
