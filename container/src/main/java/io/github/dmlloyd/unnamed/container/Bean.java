package io.github.dmlloyd.unnamed.container;

import java.util.Comparator;
import java.util.Set;

/**
 * A holder for an instance of a bean with a given definition within a given scope.
 *
 * @param <T> the bean type (which is usually, but not always, the concrete type of the instance)
 */
final class Bean<T> implements BeanSupplier<T> {
    private static final Comparator<Bean<?>> BY_PRIORITY = Comparator.<Bean<?>>comparingInt(Bean::getPriority).reversed();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <T> Comparator<Bean<? extends T>> byPriority() {
        return (Comparator<Bean<? extends T>>) (Comparator) BY_PRIORITY;
    }

    private final BeanDefinition<T> definition;

    private volatile Result<T> result;

    Bean(final BeanDefinition<T> definition) {
        this.definition = definition;
        this.result = new Pending(definition.getBeanSupplier());
    }

    Class<?> getType() {
        return definition.getType();
    }

    int getPriority() {
        return definition.getPriority();
    }

    String getName() {
        return definition.getName();
    }

    public T get(Scope scope) throws BeanInstantiationException {
        return result.get(scope);
    }

    boolean matchesByType(final Class<?> type) {
        final Set<Class<? super T>> restrictedTypes = definition.getRestrictedTypes();
        if (! type.isAssignableFrom(definition.getType())) {
            // cannot be assigned
            return false;
        }
        if (restrictedTypes.isEmpty()) {
            return true;
        } else {
            for (Class<? super T> restrictedType : restrictedTypes) {
                if (restrictedType.isAssignableFrom(type)) {
                    return true;
                }
            }
            return false;
        }
    }

    static abstract class Result<T> {
        abstract T get(Scope scope) throws BeanInstantiationException;
    }

    static final class Failed<T> extends Result<T> {
        private final String message;
        private final Throwable cause;

        Failed(final String message, final Throwable cause) {
            this.message = message;
            this.cause = cause;
        }

        T get(final Scope scope) throws BeanInstantiationException {
            throw cause == null ? new BeanInstantiationException(message) : new BeanInstantiationException(message, cause);
        }
    }

    static final class Instantiated<T> extends Result<T> {
        private final T instance;

        Instantiated(final T instance) {
            this.instance = instance;
        }

        T get(final Scope scope) {
            return instance;
        }
    }

    static final Result<Object> MISSING = new Result<>() {
        Object get(final Scope scope) {
            return null;
        }
    };

    @SuppressWarnings("unchecked")
    static <T> Result<T> missing() {
        return (Result<T>) MISSING;
    }

    /*non-static*/ final class Pending extends Result<T> {
        private final BeanSupplier<T> provider;

        Pending(final BeanSupplier<T> provider) {
            this.provider = provider;
        }

        T get(final Scope scope) throws BeanInstantiationException {
            T object;
            synchronized (Bean.this) {
                final Result<T> existing = Bean.this.result;
                if (existing != this) {
                    return existing.get(scope);
                }
                try {
                    object = provider.get(scope);
                } catch (BeanInstantiationException bie) {
                    final Failed<T> failed = new Failed<>(bie.getMessage(), bie);
                    Bean.this.result = failed;
                    return failed.get(scope);
                } catch (Throwable t) {
                    final Failed<T> failed = new Failed<>("Failed to instantiate a bean", t);
                    Bean.this.result = failed;
                    return failed.get(scope);
                }
                Bean.this.result = object == null ? missing() : new Instantiated<>(object);
            }
            return object;
        }
    }

    public String toString() {
        return "Instance for " + definition.getType() + ", name=" + getName() + ", types=" + definition.getRestrictedTypes();
    }
}
