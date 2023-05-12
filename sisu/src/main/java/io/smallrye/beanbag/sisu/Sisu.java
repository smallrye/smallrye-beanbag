package io.smallrye.beanbag.sisu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Provider;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.BeanSupplier;
import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.common.constraint.Assert;

/**
 * A utility which can configure a {@link BeanBag} using Eclipse SISU resources and annotations.
 */
public final class Sisu {
    private final Set<Class<?>> visited = new HashSet<>();
    private final BeanBag.Builder builder;

    private Sisu(final BeanBag.Builder builder) {
        this.builder = builder;
    }

    /**
     * Scan the given class loader for additional SISU items.
     *
     * @param classLoader the class loader to scan (must not be {@code null})
     * @param filter the dependency filter to apply (must not be {@code null})
     */
    public void addClassLoader(ClassLoader classLoader, DependencyFilter filter) {
        Assert.checkNotNullParam("classLoader", classLoader);
        Assert.checkNotNullParam("filter", filter);
        try {
            Enumeration<URL> e = classLoader.getResources("META-INF/sisu/javax.inject.Named");
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
                                } catch (ClassNotFoundException | LinkageError ex) {
                                    // todo: log it
                                    continue;
                                }
                                addClass(clazz, filter);
                            }
                        }
                    }
                }
            }
            // these are deprecated but still used in Maven < 4.x
            e = classLoader.getResources("META-INF/plexus/components.xml");
            while (e.hasMoreElements()) {
                final URL url = e.nextElement();
                final URLConnection conn = url.openConnection();
                try (InputStream is = conn.getInputStream()) {
                    try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        try (BufferedReader br = new BufferedReader(isr)) {
                            XMLStreamReader xr = XMLInputFactory.newInstance().createXMLStreamReader(br);
                            try (XMLCloser ignored = xr::close) {
                                while (xr.hasNext()) {
                                    if (xr.next() == XMLStreamReader.START_ELEMENT) {
                                        if (xr.getLocalName().equals("component-set")) {
                                            parseComponentSet(xr, classLoader, filter);
                                        } else {
                                            consume(xr);
                                        }
                                    }
                                }
                            }
                        } catch (XMLStreamException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    interface XMLCloser extends AutoCloseable {
        void close() throws XMLStreamException;
    }

    private void consume(final XMLStreamReader xr) throws XMLStreamException {
        while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    return;
                }
                case XMLStreamReader.START_ELEMENT: {
                    consume(xr);
                    break;
                }
            }
        }
    }

    private void parseComponentSet(final XMLStreamReader xr, final ClassLoader classLoader, final DependencyFilter filter)
            throws XMLStreamException {
        while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    return;
                }
                case XMLStreamReader.START_ELEMENT: {
                    if (xr.getLocalName().equals("components")) {
                        parseComponents(xr, classLoader, filter);
                    } else {
                        consume(xr);
                    }
                    break;
                }
            }
        }
    }

    private void parseComponents(final XMLStreamReader xr, final ClassLoader classLoader, final DependencyFilter filter)
            throws XMLStreamException {
        while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    return;
                }
                case XMLStreamReader.START_ELEMENT: {
                    if (xr.getLocalName().equals("component")) {
                        parseComponent(xr, classLoader, filter);
                    } else {
                        consume(xr);
                    }
                    break;
                }
            }
        }
    }

    private void parseComponent(final XMLStreamReader xr, final ClassLoader classLoader, final DependencyFilter filter)
            throws XMLStreamException {

        Class<?> clazz = null;
        Class<?> type = null;
        String name = null;
        boolean singleton = false;
        List<Requirement> requirements = List.of();

        loop: while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    // all done
                    break loop;
                }
                case XMLStreamReader.START_ELEMENT: {
                    switch (xr.getLocalName()) {
                        case "implementation": {
                            if (clazz == null) {
                                // try to load it up
                                final String className = xr.getElementText();
                                try {
                                    clazz = Class.forName(className, false, classLoader);
                                } catch (ClassNotFoundException | LinkageError ex) {
                                    // todo: log it
                                    continue;
                                }
                                if (new Annotations(clazz).getNamed() != null) {
                                    // it's a proper component; use the annotations to parse it
                                    addClass(clazz, filter);
                                    consume(xr);
                                    return;
                                }
                            } else {
                                consume(xr);
                            }
                            break;
                        }
                        case "role": {
                            if (type == null) {
                                final String className = xr.getElementText();
                                try {
                                    type = Class.forName(className, false, classLoader);
                                } catch (ClassNotFoundException | LinkageError ex) {
                                    // todo: log it
                                    continue;
                                }
                            } else {
                                consume(xr);
                            }
                            break;
                        }
                        case "role-hint": {
                            if (name == null) {
                                name = xr.getElementText();
                                if (name.equals("default")) {
                                    name = "";
                                }
                            } else {
                                consume(xr);
                            }
                            break;
                        }
                        case "instantiation-strategy": {
                            switch (xr.getElementText()) {
                                case "per-lookup": {
                                    singleton = false;
                                    break;
                                }
                                case "poolable":
                                case "keep-alive":
                                case "singleton": {
                                    singleton = true;
                                    break;
                                }
                            }
                            break;
                        }
                        case "requirements": {
                            requirements = parseRequirements(xr, classLoader, filter);
                            break;
                        }
                        default: {
                            consume(xr);
                            break;
                        }
                    }
                    break;
                }
            }
        }

        // add the bean the plexus way
        addBeanFromXml(clazz, type, name, singleton, filter, requirements, classLoader);
    }

    private List<Requirement> parseRequirements(final XMLStreamReader xr, final ClassLoader classLoader,
            final DependencyFilter filter) throws XMLStreamException {
        List<Requirement> list = null;
        loop: while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    break loop;
                }
                case XMLStreamReader.START_ELEMENT: {
                    switch (xr.getLocalName()) {
                        case "requirement": {
                            if (list == null) {
                                list = new ArrayList<>();
                            }
                            list.add(parseRequirement(xr, classLoader, filter));
                            break;
                        }
                        default: {
                            consume(xr);
                            break;
                        }
                    }
                }
            }
        }
        return list == null ? List.of() : List.copyOf(list);
    }

    private Requirement parseRequirement(final XMLStreamReader xr, final ClassLoader classLoader, final DependencyFilter filter)
            throws XMLStreamException {
        final Requirement requirement = new Requirement();
        while (xr.hasNext()) {
            switch (xr.next()) {
                case XMLStreamReader.END_ELEMENT: {
                    return requirement;
                }
                case XMLStreamReader.START_ELEMENT: {
                    switch (xr.getLocalName()) {
                        case "role": {
                            requirement.injectType = xr.getElementText();
                            break;
                        }
                        case "role-hint": {
                            requirement.injectName = xr.getElementText();
                            if (requirement.injectName.equals("default")) {
                                requirement.injectName = "";
                            }
                            break;
                        }
                        case "field":
                        case "field-name": {
                            requirement.fieldName = xr.getElementText();
                            break;
                        }
                        // todo: configuration
                        default: {
                            consume(xr);
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    static final class Requirement {
        String injectType;
        String injectName;
        String fieldName;
    }

    private <T> void addBeanFromXml(final Class<T> clazz, final Class<?> type, final String named, final boolean singleton,
            final DependencyFilter filter, List<Requirement> injections, final ClassLoader classLoader) {
        if (!visited.add(clazz)) {
            // duplicate
            return;
        }
        final BeanBag.BeanBuilder<T> beanBuilder = builder.addBean(clazz);
        final Annotations clazzAnnotations = Annotations.of(clazz);
        if (singleton) {
            beanBuilder.setSingleton(true);
        }
        if (named != null && !named.isEmpty()) {
            beanBuilder.setName(named);
        }
        if (type != null && !type.equals(clazz)) {
            beanBuilder.addRestrictedTypes(List.of((Class<? super T>) type));
        }
        final BeanBag.SupplierBuilder<T> supplierBuilder = beanBuilder.buildSupplier();
        // despite being a legacy component, there's no reason why we couldn't inject things like normal
        Constructor<T> ctor = findConstructor(clazz);
        for (Parameter parameter : ctor.getParameters()) {
            Annotations paramAnnotations = Annotations.of(parameter);
            final boolean optional = paramAnnotations.isNullable();
            final String paramNamed = paramAnnotations.getNamed();
            final String name = paramNamed == null ? "" : paramNamed;
            final Class<?> parameterType = parameter.getType();
            supplierBuilder.addConstructorArgument(
                    getSupplier(parameterType, parameter.getParameterizedType(), name, optional, filter));
        }
        supplierBuilder.setConstructor(ctor);
        // scan for injectable fields and methods
        addFieldInjections(clazz, supplierBuilder, filter);
        addMethodInjections(clazz, supplierBuilder, filter);

        // now add our manual injections
        for (Requirement req : injections) {
            String fieldName = req.fieldName;
            if (fieldName == null) {
                continue;
            }
            Field field;
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (Exception e) {
                // ignore & continue
                continue;
            }
            Class<?> fieldType = field.getType();
            if (req.injectType != null) {
                try {
                    fieldType = Class.forName(req.injectType, false, classLoader);
                } catch (ClassNotFoundException e) {
                    // fall back to field type
                }
            }
            String name = Objects.requireNonNullElse(req.injectName, "");
            supplierBuilder.injectField(field, getSupplier(fieldType, field.getGenericType(), name, false, filter));
        }

        supplierBuilder.build();

        beanBuilder.build();

        // If the bean implements `Provider<Something>`, then also register the bean info under the thing it provides

        for (Type genericInterface : clazz.getGenericInterfaces()) {
            if (getRawType(genericInterface) == Provider.class) {
                // it's a provider for something
                addOneProvider(builder, genericInterface, clazz.asSubclass(Provider.class), clazzAnnotations);
            }
        }
    }

    /**
     * Add the given class as a SISU item.
     *
     * @param clazz the class to add (must not be {@code null})
     * @param filter the dependency filter to apply (must not be {@code null})
     * @param <T> the class type
     */
    @SuppressWarnings("unchecked")
    public <T> void addClass(Class<T> clazz, DependencyFilter filter) {
        Assert.checkNotNullParam("clazz", clazz);
        Assert.checkNotNullParam("filter", filter);
        if (!visited.add(clazz)) {
            // duplicate
            return;
        }
        final BeanBag.BeanBuilder<T> beanBuilder = builder.addBean(clazz);
        final Annotations clazzAnnotations = Annotations.of(clazz);

        final String named = clazzAnnotations.getNamed();
        if (named != null) {
            beanBuilder.setName(named);
        }
        final List<Class<?>> typed = clazzAnnotations.getTyped();
        if (typed != null) {
            //noinspection RedundantCast
            beanBuilder.addRestrictedTypes((List<Class<? super T>>) (List<?>) typed);
        }
        if (clazzAnnotations.isSingleton()) {
            beanBuilder.setSingleton(true);
        }
        if (clazzAnnotations.hasPriority()) {
            int pv = clazzAnnotations.getPriority();
            if (pv >= 0 && named != null && named.equals("default")) {
                // shift ranking in a similar way to how SISU does it
                pv += Integer.MIN_VALUE;
            }
            beanBuilder.setPriority(pv);
        }
        final BeanBag.SupplierBuilder<T> supplierBuilder = beanBuilder.buildSupplier();
        Constructor<T> ctor = findConstructor(clazz);
        for (Parameter parameter : ctor.getParameters()) {
            Annotations paramAnnotations = Annotations.of(parameter);
            final boolean optional = paramAnnotations.isNullable();
            final String paramNamed = paramAnnotations.getNamed();
            final String name = paramNamed == null ? "" : paramNamed;
            final Class<?> parameterType = parameter.getType();
            supplierBuilder.addConstructorArgument(
                    getSupplier(parameterType, parameter.getParameterizedType(), name, optional, filter));
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
                addOneProvider(builder, genericInterface, clazz.asSubclass(Provider.class), clazzAnnotations);
            }
        }
    }

    /**
     * Perform SISU configuration on the given builder.
     *
     * @param classLoader the class loader to look in for SISU resources (must not be {@code null})
     * @param builder the container builder to configure (must not be {@code null})
     * @param filter a filter to apply to all SISU dependency resolutions (must not be {@code null})
     */
    public static void configureSisu(ClassLoader classLoader, BeanBag.Builder builder, DependencyFilter filter) {
        createFor(builder).addClassLoader(classLoader, filter);
    }

    /**
     * Create a new SISU configurator for the given BeanBag builder.
     *
     * @param builder the builder (must not be {@code null})
     * @return the new SISU configurator (not {@code null})
     */
    public static Sisu createFor(BeanBag.Builder builder) {
        Assert.checkNotNullParam("builder", builder);
        return new Sisu(builder);
    }

    @SuppressWarnings("unchecked")
    private static <T, P extends Provider<T>> void addOneProvider(BeanBag.Builder builder, Type genericInterface,
            Class<P> clazz, Annotations clazzAnnotations) {
        final Class<T> providedType = (Class<T>) getRawType(getTypeArgument(genericInterface, 0));
        final BeanBag.BeanBuilder<T> providedBuilder = builder.addBean(providedType);
        final String named = clazzAnnotations.getNamed();
        if (named != null) {
            // replicate name from provider bean
            providedBuilder.setName(named);
        }
        if (clazzAnnotations.hasPriority()) {
            // replicate priority from provider bean
            int pv = clazzAnnotations.getPriority();
            if (pv >= 0 && named != null && named.equals("default")) {
                // shift ranking in a similar way to how SISU does it
                pv += Integer.MIN_VALUE;
            }
            providedBuilder.setPriority(pv);
        }
        final String name = named == null ? "" : named;
        providedBuilder
                .setSupplier(BeanSupplier.resolving(clazz, name, false, DependencyFilter.ACCEPT).transform(Provider::get));
        providedBuilder.build();
    }

    private static BeanSupplier<?> getSupplier(final Class<?> rawType, final Type parameterizedType, final String name,
            final boolean optional, final DependencyFilter filter) {
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
        } else if (rawType == String.class) {
            // special handling! funny business!
            if (name.contains("${")) {
                // expression! but with a weird syntax that isn't supported by smallrye-common-expression
                return scope -> parseExpressionString(new StringItr(name), 0, false,
                        Objects.requireNonNullElse(scope.getOptionalBean(Properties.class), new Properties()));
            }
            // just return the raw string
            return BeanSupplier.of(name);
        } else {
            return BeanSupplier.resolving(rawType, name == null ? "" : name, optional, filter);
        }
    }

    private static final class StringItr {
        final String s;
        int pos;
        final int end;

        StringItr(String s) {
            this.s = s;
            this.pos = 0;
            this.end = s.length();
        }

        boolean hasNext() {
            return pos < end;
        }

        char next() {
            return s.charAt(pos++);
        }

        boolean nextMatches(String cmp) {
            int cmpLen = cmp.length();
            return pos + cmpLen <= end && s.regionMatches(pos, cmp, 0, cmpLen);
        }

        boolean match(String cmp) {
            boolean b = nextMatches(cmp);
            if (b) {
                pos += cmp.length();
            }
            return b;
        }
    }

    private static String parseExpressionString(StringItr itr, int recursion, boolean stopOnDefault, Properties properties) {
        if (recursion > 10) {
            throw new IllegalStateException("Deep recursion");
        }
        StringBuilder b = new StringBuilder();
        while (itr.hasNext()) {
            if (recursion > 0 && (itr.nextMatches("}") || stopOnDefault && itr.nextMatches(":-"))) {
                // end
                break;
            } else if (itr.match("${")) {
                doExprPart(b, itr, recursion + 1, properties);
            } else {
                // plain text
                b.append(itr.next());
            }
        }
        return b.toString();
    }

    private static void doExprPart(final StringBuilder b, final StringItr itr, final int recursion, Properties properties) {
        String key = parseExpressionString(itr, recursion, true, properties);
        if (itr.match("}") || !itr.hasNext()) {
            b.append(properties.getProperty(key, ""));
            return;
        } else if (itr.match(":-")) {
            b.append(properties.getProperty(key, parseExpressionString(itr, recursion, false, properties)));
            return;
        } else {
            // ???
            throw new IllegalStateException();
        }
    }

    private static Type getTypeArgument(Type type, int position) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            return pt.getActualTypeArguments()[position];
        } else {
            throw new IllegalArgumentException("No type argument given for " + type);
        }
    }

    private static Class<?> getRawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            return getRawType(pt.getRawType());
        } else if (type instanceof GenericArrayType) {
            GenericArrayType gat = (GenericArrayType) type;
            // Class.arrayType() is JDK 12+
            return getArrayType(getRawType(gat.getGenericComponentType()));
        } else if (type instanceof WildcardType) {
            WildcardType wt = (WildcardType) type;
            final Type[] ub = wt.getUpperBounds();
            return ub.length >= 1 ? getRawType(ub[0]) : Object.class;
        } else if (type instanceof TypeVariable<?>) {
            TypeVariable<?> tv = (TypeVariable<?>) type;
            final Type[] bounds = tv.getBounds();
            return bounds.length >= 1 ? getRawType(bounds[0]) : Object.class;
        } else {
            throw new IllegalArgumentException("Cannot determine raw type of " + type);
        }
    }

    private static final ClassValue<Class<?>> arrayTypes = new ClassValue<Class<?>>() {
        protected Class<?> computeValue(final Class<?> type) {
            return Array.newInstance(type, 0).getClass();
        }
    };

    @SuppressWarnings("unchecked")
    private static <T> Class<T[]> getArrayType(Class<T> elementType) {
        return (Class<T[]>) arrayTypes.get(elementType);
    }

    private static <T> void addFieldInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder,
            final DependencyFilter filter) {
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
            final Annotations fieldAnnotations = Annotations.of(field);
            if (!fieldAnnotations.isInject()) {
                continue;
            }
            field.setAccessible(true);
            boolean optional = fieldAnnotations.isNullable();
            final String paramNamed = fieldAnnotations.getNamed();
            final String name = paramNamed == null ? "" : paramNamed;
            final Class<?> fieldType = field.getType();
            supplierBuilder.injectField(field, getSupplier(fieldType, field.getGenericType(), name, optional, filter));
        }
    }

    private static <T> void addMethodInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder,
            final DependencyFilter filter) {
        addMethodInjections(clazz, supplierBuilder, filter, new HashSet<>());
    }

    @SuppressWarnings("unchecked")
    private static <T> void addMethodInjections(final Class<? super T> clazz, final BeanBag.SupplierBuilder<T> supplierBuilder,
            final DependencyFilter filter, final Set<Class<? super T>> visited) {
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
                final Annotations methodAnnotations = Annotations.of(method);
                if (!methodAnnotations.isInject()) {
                    continue;
                }
                if (method.getParameterCount() != 1) {
                    continue;
                }
                final String named = methodAnnotations.getNamed();
                final String name = named == null ? "" : named;
                final Parameter argParam = method.getParameters()[0];
                boolean optional = Annotations.of(argParam).isNullable();
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
            if (Annotations.of(constructor).isInject()) {
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

    private static final ClassValue<Function<Annotation, String>> GET_NAMED_VALUE_FN = new ClassValue<Function<Annotation, String>>() {
        protected Function<Annotation, String> computeValue(final Class<?> type) {
            return new MethodFunction<>(type, "javax.inject.Named");
        }
    };

    private static final ClassValue<Function<Annotation, Integer>> GET_PRIORITY_VALUE_FN = new ClassValue<Function<Annotation, Integer>>() {
        protected Function<Annotation, Integer> computeValue(final Class<?> type) {
            return new MethodFunction<>(type, "org.eclipse.sisu.Priority");
        }
    };

    private static final ClassValue<Function<Annotation, Class<?>[]>> GET_TYPED_VALUE_FN = new ClassValue<Function<Annotation, Class<?>[]>>() {
        protected Function<Annotation, Class<?>[]> computeValue(final Class<?> type) {
            return new MethodFunction<>(type, "org.eclipse.sisu.Typed");
        }
    };

    static class MethodFunction<R> implements Function<Annotation, R> {
        private final Method method;

        MethodFunction(Class<?> type, String expectedName) {
            final Class<? extends Annotation> annotationType = type.asSubclass(Annotation.class);
            if (!annotationType.getName().equals(expectedName)) {
                throw new IllegalArgumentException("Wrong class name");
            }
            final Method method;
            try {
                method = annotationType.getDeclaredMethod("value");
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            this.method = method;
        }

        @SuppressWarnings("unchecked")
        public R apply(final Annotation annotation) {
            try {
                return (R) method.invoke(annotation);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class Annotations {
        private final boolean hasPriority;
        private final int priority;
        private final String named;
        private final boolean inject;
        private final boolean singleton;
        private final boolean nullable;
        private final List<Class<?>> typed;

        private Annotations(AnnotatedElement element) {
            boolean hasPriority = false;
            int priority = 0;
            String named = null;
            boolean inject = false;
            boolean singleton = false;
            boolean nullable = false;
            List<Class<?>> typed = null;
            for (Annotation annotation : element.getAnnotations()) {
                final Class<? extends Annotation> annoType = annotation.annotationType();
                final String annoName = annoType.getName();
                switch (annoName) {
                    case "javax.inject.Inject":
                        inject = true;
                        break;
                    case "javax.inject.Singleton":
                    case "org.eclipse.sisu.EagerSingleton":
                    case "org.sonatype.inject.EagerSingleton":
                        singleton = true;
                        break;
                    case "javax.inject.Named":
                        named = GET_NAMED_VALUE_FN.get(annoType).apply(annotation);
                        break;
                    case "org.eclipse.sisu.Nullable":
                    case "org.sonatype.inject.Nullable":
                        nullable = true;
                        break;
                    case "org.eclipse.sisu.Priority": {
                        priority = GET_PRIORITY_VALUE_FN.get(annoType).apply(annotation).intValue();
                        break;
                    }
                    case "org.eclipse.sisu.Typed": {
                        typed = List.of(GET_TYPED_VALUE_FN.get(annoType).apply(annotation));
                        break;
                    }
                }
            }
            this.hasPriority = hasPriority;
            this.priority = priority;
            this.named = named;
            this.inject = inject;
            this.singleton = singleton;
            this.nullable = nullable;
            this.typed = typed;
        }

        static Annotations of(AnnotatedElement element) {
            return new Annotations(element);
        }

        boolean hasPriority() {
            return hasPriority;
        }

        int getPriority() {
            return priority;
        }

        String getNamed() {
            return named;
        }

        boolean isInject() {
            return inject;
        }

        boolean isSingleton() {
            return singleton;
        }

        boolean isNullable() {
            return nullable;
        }

        List<Class<?>> getTyped() {
            return typed;
        }
    }
}
