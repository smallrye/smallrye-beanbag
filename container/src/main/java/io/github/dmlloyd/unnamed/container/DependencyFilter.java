package io.github.dmlloyd.unnamed.container;

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

    default DependencyFilter and(DependencyFilter other) {
        return (concreteType, name, priority) -> test(concreteType, name, priority) && other.test(concreteType, name, priority);
    }

    default DependencyFilter or(DependencyFilter other) {
        return (concreteType, name, priority) -> test(concreteType, name, priority) || other.test(concreteType, name, priority);
    }
}
