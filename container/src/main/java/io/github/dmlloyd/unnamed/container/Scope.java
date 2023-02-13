package io.github.dmlloyd.unnamed.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
        return new Scope(parent, Util.mapList(definitions, Bean::new));
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
            final List<Bean<? extends T>> appearing = (List<Bean<? extends T>>) (List<?>) beansByType.putIfAbsent(type, (List<Bean<?>>) (List<?>) list);
            if (appearing != null) {
                list = appearing;
            }
        }
        return list;
    }

    public <T> List<T> getAllBeansOfType(Class<T> type) {
        return getAllBeansOfType(type, DependencyFilter.ACCEPT);
    }

    public <T> List<T> getAllBeansOfType(Class<T> type, DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return List.of();
        }
        final List<T> list = new ArrayList<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            final T instance = bean.get(this);
            if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                list.add(instance);
            }
        }
        return List.copyOf(list);
    }

    public <T> List<T> getAllBeansOfTypeAndName(final Class<T> type, final String name) {
        return getAllBeansOfTypeAndName(type, name, DependencyFilter.ACCEPT);
    }

    public <T> List<T> getAllBeansOfTypeAndName(final Class<T> type, final String name, DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return List.of();
        }
        final List<T> list = new ArrayList<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            final T instance = bean.get(this);
            if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                list.add(instance);
            }
        }
        return List.copyOf(list);
    }

    public <T> T requireBeanOfType(Class<T> type) {
        final T instance = getOptionalBeanOfType(type);
        if (instance != null) {
            return instance;
        }
        throw new NoSuchBeanException("No beans of type " + type + " are available");
    }

    public <T> T requireBeanOfTypeAndName(Class<T> type, String name) {
        final T instance = getOptionalBeanOfTypeAndName(type, name);
        if (instance != null) {
            return instance;
        }
        throw new NoSuchBeanException("No beans of type " + type + " with name " + name + " are available");
    }

    public <T> T getOptionalBeanOfType(Class<T> type) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        for (Bean<? extends T> bean : beans) {
            final T instance = bean.get(this);
            if (instance != null) {
                return instance;
            }
        }
        return null;
    }

    public <T> T getOptionalBeanOfTypeAndName(Class<T> type, String name) {
        return getOptionalBeanOfTypeAndName(type, name, DependencyFilter.ACCEPT);
    }

    public <T> T getOptionalBeanOfTypeAndName(Class<T> type, String name, DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        for (Bean<? extends T> bean : beans) {
            if (bean.getName().equals(name)) {
                final T instance = bean.get(this);
                if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                    return instance;
                }
            }
        }
        return null;
    }

    public <T> T getBean(final Class<T> type, final String name, final boolean optional, final DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        List<Throwable> problems = null;
        for (Bean<? extends T> bean : beans) {
            if (name.isEmpty() || bean.getName().equals(name)) {
                try {
                    final T instance = bean.get(this);
                    if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                        return instance;
                    }
                } catch (Exception e) {
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
        if (! name.isEmpty()) {
            msgBuilder.append(", name is \"").append(name).append('"');
        }
        final NoSuchBeanException nbe = new NoSuchBeanException(msgBuilder.toString());
        if (problems != null) {
//            problems.forEach(nbe::addSuppressed);
        }
        throw nbe;
    }

    public <T> Collection<T> getAllBeans(final Class<T> type, final String name, final DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return List.of();
        }
        final List<T> list = new ArrayList<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            if (name.isEmpty() || bean.getName().equals(name)) {
                final T instance = bean.get(this);
                if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                    list.add(instance);
                }
            }
        }
        return List.copyOf(list);
    }

    public <T> Map<String, T> getAllBeansWithNames(final Class<T> type, final DependencyFilter filter) {
        final List<Bean<? extends T>> beans = getBeansByType(type);
        if (beans.isEmpty()) {
            return Map.of();
        }
        final Map<String, T> map = new HashMap<>(beans.size());
        for (Bean<? extends T> bean : beans) {
            final T instance = bean.get(this);
            if (instance != null && filter.test(instance.getClass(), bean.getName(), bean.getPriority())) {
                // preserve priority order
                map.putIfAbsent(bean.getName(), instance);
            }
        }
        return Map.copyOf(map);
    }
}
