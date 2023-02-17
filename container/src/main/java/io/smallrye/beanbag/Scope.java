package io.smallrye.beanbag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A (potentially nested) scope from which bean instances may be acquired.
 */
public final class Scope {
    private final Scope parent;
    private final List<Bean<?>> beans;
    private final Map<Class<?>, List<Bean<?>>> beansByType = new ConcurrentHashMap<>();

    Scope(final Scope parent, final List<Bean<?>> beans) {
        this.parent = parent;
        this.beans = beans;
    }

    static Scope of(final Scope parent, final List<BeanDefinition<?>> definitions) {
        return new Scope(parent, Util.mapList(definitions, Bean::new, Bean<?>[]::new));
    }

    @SuppressWarnings("unchecked")
    private <T> List<Bean<? extends T>> getBeansByType(Class<T> type) {
        List<Bean<? extends T>> list = (List<Bean<? extends T>>) (List<?>) beansByType.get(type);
        if (list == null) {
            // compute it
            if (parent != null) {
                list = new ArrayList<>(parent.getBeansByType(type));
            } else {
                list = new ArrayList<>();
            }
            for (Bean<?> bean : beans) {
                if (bean.matchesByType(type)) {
                    list.add((Bean<? extends T>) bean);
                }
            }
            list.sort(Bean.byPriority());
            if (list.isEmpty()) {
                list = List.of();
            }
            final List<Bean<? extends T>> appearing = (List<Bean<? extends T>>) (List<?>) beansByType.putIfAbsent(type,
                    (List<Bean<?>>) (List<?>) list);
            if (appearing != null) {
                list = appearing;
            }
        }
        return list;
    }

    /**
     * Get all constructable beans of the given type.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> List<T> getAllBeans(Class<T> type) {
        return getAllBeans(type, DependencyFilter.ACCEPT);
    }

    /**
     * Get all constructable beans of the given type.
     * The filter is applied to each bean to determine whether it should be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> List<T> getAllBeans(Class<T> type, DependencyFilter filter) {
        return getAllBeans(type, "", filter);
    }

    /**
     * Get all constructable beans of the given type and name.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> List<T> getAllBeans(final Class<T> type, final String name) {
        return getAllBeans(type, name, DependencyFilter.ACCEPT);
    }

    /**
     * Get all constructable beans of the given type and name.
     * The filter is applied to each bean to determine whether it should be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> List<T> getAllBeans(final Class<T> type, final String name, DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return List.of();
        }
        final List<T> list = new ArrayList<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            if ((name.isEmpty() || bean.getName().equals(name))
                    && filter.test(bean.getType(), bean.getName(), bean.getPriority())) {
                final T instance = bean.get(this);
                if (instance != null) {
                    list.add(instance);
                }
            }
        }
        return List.copyOf(list);
    }

    /**
     * Get all constructable beans of the given type as a map.
     * The filter is applied to each bean to determine whether it should be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> Map<String, T> getAllBeansWithNames(final Class<T> type, final DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return Map.of();
        }
        final Map<String, T> map = new LinkedHashMap<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            // preserve priority order
            if (!map.containsKey(bean.getName()) && filter.test(bean.getType(), bean.getName(), bean.getPriority())) {
                final T instance = bean.get(this);
                if (instance != null) {
                    map.put(bean.getName(), instance);
                }
            }
        }
        return Map.copyOf(map);
    }

    /**
     * Require a single bean with the given type.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the single bean (not {@code null})
     * @param <T> the allowed bean type
     * @throws NoSuchBeanException if the bean is not present
     * @throws BeanInstantiationException if some error occurred when instantiating the bean
     */
    public <T> T requireBean(Class<T> type) {
        return getBean(type, "", false, DependencyFilter.ACCEPT);
    }

    /**
     * Require a single bean with the given type and name.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @return the single bean (not {@code null})
     * @param <T> the allowed bean type
     * @throws NoSuchBeanException if the bean is not present
     * @throws BeanInstantiationException if some error occurred when instantiating the bean
     */
    public <T> T requireBean(Class<T> type, String name) {
        return getBean(type, name, false, DependencyFilter.ACCEPT);
    }

    /**
     * Get a single bean with the given type, if it exists and can be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     */
    public <T> T getOptionalBean(Class<T> type) {
        return getOptionalBean(type, "");
    }

    /**
     * Get a single bean with the given type and name, if it exists and can be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     */
    public <T> T getOptionalBean(Class<T> type, String name) {
        return getOptionalBean(type, name, DependencyFilter.ACCEPT);
    }

    /**
     * Get a single bean with the given type and name, if it exists and can be instantiated.
     * The filter is applied to each bean to determine whether it should be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     */
    public <T> T getOptionalBean(Class<T> type, String name, DependencyFilter filter) {
        return getBean(type, name, true, filter);
    }

    /**
     * Get a single bean with the given type and name, with configurable optionality.
     * The filter is applied to each bean to determine whether it should be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @param optional {@code true} to return null if no bean matches, or {@code false} to throw an exception if no bean matches
     * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     * @throws NoSuchBeanException if the bean is not present
     */
    public <T> T getBean(final Class<T> type, final String name, final boolean optional, final DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        List<Throwable> problems = null;
        for (Bean<? extends T> bean : beans) {
            try {
                if ((name.isEmpty() || bean.getName().equals(name))
                        && filter.test(bean.getType(), bean.getName(), bean.getPriority())) {
                    final T instance = bean.get(this);
                    if (instance != null) {
                        return instance;
                    }
                }
            } catch (Exception e) {
                if (!optional) {
                    if (problems == null) {
                        problems = new ArrayList<>();
                    }
                    problems.add(e);
                }
            }
        }
        if (optional) {
            return null;
        }
        StringBuilder msgBuilder = new StringBuilder("No matching bean available: type is ");
        msgBuilder.append(type);
        if (!name.isEmpty()) {
            msgBuilder.append(", name is \"").append(name).append('"');
        }
        final NoSuchBeanException nbe = new NoSuchBeanException(msgBuilder.toString());
        if (problems != null) {
            problems.forEach(nbe::addSuppressed);
        }
        throw nbe;
    }
}
