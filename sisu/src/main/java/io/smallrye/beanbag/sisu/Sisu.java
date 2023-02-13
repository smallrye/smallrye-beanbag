package io.smallrye.beanbag.sisu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.smallrye.beanbag.BeanSupplier;
import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.common.constraint.Assert;
import org.eclipse.sisu.Nullable;
import org.eclipse.sisu.Priority;
import org.eclipse.sisu.Typed;

/**
 * A utility which can configure a {@link BeanBag} using Eclipse SISU resources and annotations.
 */
public final class Sisu {
    private Sisu() {}

    /**
     * Perform the SISU configuration.
     *
     * @param classLoader the class loader to look in for SISU resources (must not be {@code null})
     * @param builder the container builder to configure (must not be {@code null})
     * @param filter a filter to apply to all SISU dependency resolutions (must not be {@code null})
     */
    public static void configureSisu(ClassLoader classLoader, BeanBag.Builder builder, DependencyFilter filter) {
        Assert.checkNotNullParam("classLoader", classLoader);
        Assert.checkNotNullParam("builder", builder);
        Assert.checkNotNullParam("filter", filter);
        try {
            final Enumeration<URL> e = classLoader.getResources("META-INF/sisu/javax.inject.Named");
            final Set<Class<?>> visited = new HashSet<>();
            while (e.hasMoreElements()) {
                final URL url = e.nextElement();
                final URLConnection conn = url.openConnection();
                try (InputStream is = conn.getInputStream()) {
                    try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        try (BufferedReader br = new BufferedReader(isr)) {
                            String line;
                            while ((line = (br.readLine())) != null) {
                                int idx = line.indexOf('#');
                                if (idx != -1) {
                                    line = line.substring(0, idx);
                                }
                                final String className = line.trim();
                                if (className.isBlank()) {
                                    continue;
                                }
                                final Class<?> clazz;
                                try {
                                    clazz = Class.forName(className, false, classLoader);
                                } catch (ClassNotFoundException ex) {
                                    throw new RuntimeException("Could not load " + className);
                                }
                                addOne(builder, clazz, filter, visited);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void addOne(BeanBag.Builder builder, Class<T> clazz, final DependencyFilter filter, final Set<Class<?>> visited) {
        if (! visited.add(clazz)) {
            // duplicate
            return;
        }
        final BeanBag.BeanBuilder<T> beanBuilder = builder.addBean(clazz);

        final Named named = clazz.getAnnotation(Named.class);
        if (named != null) {
            beanBuilder.setName(named.value());
        }
        final Typed typed = clazz.getAnnotation(Typed.class);
        if (typed != null) {
            beanBuilder.addRestrictedTypes((List<Class<? super T>>) (List<?>) List.of(typed.value()));
        }
        if (clazz.isAnnotationPresent(Singleton.class)) {
            beanBuilder.setSingleton(true);
        }
        final Priority priority = clazz.getAnnotation(Priority.class);
        if (priority != null) {
            int pv = priority.value();
            if (pv >= 0 && named != null && named.value().equals("default")) {
                // shift ranking in a similar way to how SISU does it
                pv += Integer.MIN_VALUE;
            }
            beanBuilder.setPriority(pv);
        }
        final BeanBag.SupplierBuilder<T> supplierBuilder = beanBuilder.buildSupplier();
        Constructor<T> ctor = findConstructor(clazz);
        for (Parameter parameter : ctor.getParameters()) {
            boolean optional = parameter.isAnnotationPresent(Nullable.class);
            final Named paramNamed = parameter.getAnnotation(Named.class);
            final String name = paramNamed == null ? "" : paramNamed.value();
            final Class<?> parameterType = parameter.getType();
            supplierBuilder.addConstructorArgument(
                getSupplier(parameterType, parameter.getParameterizedType(), name, optional, filter)
            );
        }
        supplierBuilder.setConstructor(ctor);
        // scan for injectable fields and methods
        addFieldInjections(clazz, supplierBuilder, filter);
        addMethodInjections(clazz, supplierBuilder, filter);

        supplierBuilder.build();

        beanBuilder.build();

        // If the bean implements `Provider<Something>`, then also register the bean info under the thing it provides

        for (Type genericInterface : clazz.getGenericInterfaces()) {
            if (getRawType(genericInterface) == Provider.class) {
                // it's a provider for something
                addOneProvider(builder, genericInterface, clazz.asSubclass(Provider.class), named, priority);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, P extends Provider<T>> void addOneProvider(BeanBag.Builder builder, Type genericInterface, Class<P> clazz, Named named, Priority priority) {
        final Class<T> providedType = (Class<T>) getRawType(getTypeArgument(genericInterface, 0));
        final BeanBag.BeanBuilder<T> providedBuilder = builder.addBean(providedType);
        if (named != null) {
            // replicate name from provider bean
            providedBuilder.setName(named.value());
        }
        if (priority != null) {
            // replicate priority from provider bean
            int pv = priority.value();
            if (pv >= 0 && named != null && named.value().equals("default")) {
                // shift ranking in a similar way to how SISU does it
                pv += Integer.MIN_VALUE;
            }
            providedBuilder.setPriority(pv);
        }
        final String name = named == null ? "" : named.value();
        providedBuilder.setSupplier(BeanSupplier.resolving(clazz, name, false, DependencyFilter.ACCEPT).transform(Provider::get));
        providedBuilder.build();
    }

    private static BeanSupplier<?> getSupplier(final Class<?> rawType, final Type parameterizedType, final String name, final boolean optional, final DependencyFilter filter) {
        if (rawType == Provider.class) {
            final Type providerType = getTypeArgument(parameterizedType, 0);
            final BeanSupplier<?> supplier = getSupplier(getRawType(providerType), providerType, name, optional, filter);
            return scope -> (Provider<?>) () -> supplier.get(scope);
        } else if (rawType == Set.class) {
            final Class<?> argType = getRawType(getTypeArgument(parameterizedType, 0));
            return BeanSupplier.resolvingAll(argType, name, filter).transform(Set::copyOf);
        } else if (rawType == List.class || rawType == Collection.class) {
            final Class<?> argType = getRawType(getTypeArgument(parameterizedType, 0));
            return BeanSupplier.resolvingAll(argType, name, filter);
        } else if (rawType == Map.class) {
            final Class<?> keyType = getRawType(getTypeArgument(parameterizedType, 0));
            final Class<?> valType = getRawType(getTypeArgument(parameterizedType, 1));
            if (keyType == String.class) {
                // OK
                return BeanSupplier.resolvingAllByName(valType, filter);
            } else {
                throw new IllegalArgumentException("Invalid key type " + keyType + " for map");
            }
        } else {
            return BeanSupplier.resolving(rawType, name == null ? "" : name, optional, filter);
        }
    }

    private static Type getTypeArgument(Type type, int position) {
        if (type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[position];
        } else {
            throw new IllegalArgumentException("No type argument given for " + type);
        }
    }

    private static Class<?> getRawType(Type type) {
        if (type instanceof Class<?> clz) {
            return clz;
        } else if (type instanceof ParameterizedType pt) {
            return getRawType(pt.getRawType());
        } else if (type instanceof GenericArrayType gat) {
            return getRawType(gat.getGenericComponentType()).arrayType();
        } else if (type instanceof WildcardType wt) {
            final Type[] ub = wt.getUpperBounds();
            return ub.length >= 1 ? getRawType(ub[0]) : Object.class;
        } else if (type instanceof TypeVariable<?> tv) {
            final Type[] bounds = tv.getBounds();
            return bounds.length >= 1 ? getRawType(bounds[0]) : Object.class;
        } else {
            throw new IllegalArgumentException("Cannot determine raw type of " + type);
        }
    }

    private static <T> void addFieldInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder, final DependencyFilter filter) {
        if (clazz == Object.class) {
            return;
        }
        final Class<? super T> superclass = clazz.getSuperclass();
        if (superclass != null) {
            addFieldInjections(superclass, supplierBuilder, filter);
        }
        for (Field field : clazz.getDeclaredFields()) {
            final int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                continue;
            }
            if (! field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            field.setAccessible(true);
            boolean optional = field.isAnnotationPresent(Nullable.class);
            final Named paramNamed = field.getAnnotation(Named.class);
            final String name = paramNamed == null ? "" : paramNamed.value();
            final Class<?> fieldType = field.getType();
            supplierBuilder.injectField(field, getSupplier(fieldType, field.getGenericType(), name, optional, filter));
        }
    }

    private static <T> void addMethodInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder, final DependencyFilter filter) {
        addMethodInjections(clazz, supplierBuilder, filter, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> void addMethodInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder, final DependencyFilter filter, final Set<Class<? super T>> visited) {
        if (visited.add(clazz)) {
            if (clazz == Object.class) {
                return;
            }
            final Class<? super T> superclass = clazz.getSuperclass();
            if (superclass != null) {
                addMethodInjections(superclass, supplierBuilder, filter);
            }
            for (Class<?> anInterface : clazz.getInterfaces()) {
                addMethodInjections((Class<? super T>) anInterface, supplierBuilder, filter);
            }
            for (Method method : clazz.getDeclaredMethods()) {
                final int mods = method.getModifiers();
                if (Modifier.isStatic(mods)) {
                    continue;
                }
                if (! method.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                if (method.getParameterCount() != 1) {
                    continue;
                }
                final Named named = method.getAnnotation(Named.class);
                final String name = named == null ? "" : named.value();
                final Parameter argParam = method.getParameters()[0];
                boolean optional = argParam.isAnnotationPresent(Nullable.class);
                supplierBuilder.injectMethod(method, argParam.getType(), name, optional, filter);
            }
        }

    }

    @SuppressWarnings("unchecked")
    private static <T> Constructor<T> findConstructor(Class<T> clazz) {
        Constructor<T> defaultConstructor = null;
        final Constructor<?>[] declaredConstructors;
        try {
            declaredConstructors = clazz.getDeclaredConstructors();
        } catch (Throwable t) {
            throw new RuntimeException("Cannot get declared constructors from " + clazz, t);
        }
        for (Constructor<?> constructor : declaredConstructors) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                constructor.setAccessible(true);
                return (Constructor<T>) constructor;
            } else if (constructor.getParameterCount() == 0) {
                defaultConstructor = (Constructor<T>) constructor;
            }
        }
        if (defaultConstructor != null) {
            defaultConstructor.setAccessible(true);
            return defaultConstructor;
        }
        throw new RuntimeException("No valid constructor found on " + clazz);
    }
}
