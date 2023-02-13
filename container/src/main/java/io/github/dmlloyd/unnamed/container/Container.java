package io.github.dmlloyd.unnamed.container;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.smallrye.common.constraint.Assert;

/**
 * A basic bean container.
 */
public final class Container {

    private final List<BeanDefinition<?>> beanDefinitions;
    private final Scope singletonScope;

    Container(Builder builder) {
        final List<BeanDefinition<?>> definitions = new ArrayList<>();
        final List<BeanDefinition<?>> singletonBeans = new ArrayList<>();
        for (BeanBuilder<?> beanBuilder : builder.beanBuilders) {
            BeanDefinition<?> definition = makeDefinition(beanBuilder);
            if (beanBuilder.singleton) {
                singletonBeans.add(definition);
            } else {
                definitions.add(definition);
            }
        }
        singletonScope = Scope.of(null, singletonBeans);
        beanDefinitions = List.copyOf(definitions);
    }

    private <T> BeanDefinition<T> makeDefinition(final BeanBuilder<T> beanBuilder) {
        final String name = beanBuilder.name;
        final Set<Class<? super T>> restrictedTypes = Set.copyOf(Objects.requireNonNullElse(beanBuilder.restrictedTypes, List.of()));
        final BeanSupplier<T> supplier = beanBuilder.supplier;
        final int priority = beanBuilder.priority;
        final Class<T> type = beanBuilder.type;
        return new BeanDefinition<>(name, priority, type, restrictedTypes, supplier);
    }

    public Scope newScope() {
        return Scope.of(singletonScope, beanDefinitions);
    }

    public <T> List<T> getAllBeansOfType(final Class<T> type) {
        return newScope().getAllBeansOfType(type);
    }

    public <T> T requireBeanOfType(Class<T> type) {
        return newScope().requireBeanOfType(type);
    }

    public <T> T requireBeanOfTypeAndName(Class<T> type, String name) {
        return newScope().requireBeanOfTypeAndName(type, name);
    }

    public <T> T getOptionalBeanOfType(Class<T> type) {
        return newScope().getOptionalBeanOfType(type);
    }

    public <T> T getOptionalBeanOfTypeAndName(Class<T> type, String name) {
        return newScope().getOptionalBeanOfTypeAndName(type, name);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<BeanBuilder<?>> beanBuilders = new ArrayList<>();

        Builder() {}

        public <T> BeanBuilder<T> addBean(final Class<T> type) {
            return new BeanBuilder<T>(this, type);
        }

        public Container build() {
            return new Container(this);
        }
    }

    public static final class BeanBuilder<T> {
        private final Builder builder;
        private final Class<T> type;

        private int priority = 0;
        private List<Class<? super T>> restrictedTypes;
        private String name = "";
        private BeanSupplier<T> supplier;
        private boolean singleton;

        BeanBuilder(final Builder builder, final Class<T> type) {
            this.builder = builder;
            this.type = type;
        }

        public BeanBuilder<T> setPriority(final int priority) {
            this.priority = priority;
            return this;
        }

        public BeanBuilder<T> setName(final String name) {
            Assert.checkNotNullParam("name", name);
            this.name = name;
            return this;
        }

        public BeanBuilder<T> setSupplier(final BeanSupplier<T> supplier) {
            Assert.checkNotNullParam("supplier", supplier);
            this.supplier = supplier;
            return this;
        }

        public SupplierBuilder<T> buildSupplier() {
            return new SupplierBuilder<>(this);
        }

        public BeanBuilder<T> setSingleton(final boolean singleton) {
            this.singleton = singleton;
            return this;
        }

        public BeanBuilder<T> addRestrictedTypes(Collection<Class<? super T>> types) {
            Assert.checkNotNullParam("types", types);
            if (restrictedTypes != null) {
                restrictedTypes.addAll(types);
            } else {
                restrictedTypes = new ArrayList<>(types);
            }
            return this;
        }

        public Builder build() {
            builder.beanBuilders.add(this);
            return builder;
        }
    }

    public static final class SupplierBuilder<T> {
        private final BeanBuilder<T> beanBuilder;
        private final List<BeanSupplier<?>> argumentSuppliers = new ArrayList<>();
        private Constructor<T> constructor;
        private List<Injector<T>> injectors;

        SupplierBuilder(final BeanBuilder<T> beanBuilder) {
            this.beanBuilder = beanBuilder;
        }

        public SupplierBuilder<T> setConstructor(final Constructor<T> constructor) {
            this.constructor = constructor;
            return this;
        }

        private List<Injector<T>> getInjectorList() {
            final List<Injector<T>> injectors = this.injectors;
            if (injectors == null) {
                return this.injectors = new ArrayList<>();
            }
            return injectors;
        }

        public SupplierBuilder<T> injectField(Field field, BeanSupplier<?> supplier) {
            getInjectorList().add(Injector.forField(Assert.checkNotNullParam("field", field), supplier));
            return this;
        }

        public SupplierBuilder<T> injectField(Field field, Class<?> injectType, String beanName, boolean optional, DependencyFilter filter) {
            return injectField(
                field,
                BeanSupplier.resolving(
                    injectType,
                    Assert.checkNotNullParam("beanName", beanName),
                    optional,
                    Assert.checkNotNullParam("filter", filter)
                )
            );
        }

        public SupplierBuilder<T> injectField(Field field) {
            return injectField(field, "");
        }

        public SupplierBuilder<T> injectField(Field field, String beanName) {
            return injectField(field, beanName, false);
        }

        public SupplierBuilder<T> injectField(Field field, boolean optional) {
            return injectField(field, "", optional);
        }

        public SupplierBuilder<T> injectField(Field field, String beanName, boolean optional) {
            return injectField(Assert.checkNotNullParam("field", field), beanName, optional, DependencyFilter.ACCEPT);
        }

        public SupplierBuilder<T> injectField(Field field, String beanName, boolean optional, DependencyFilter filter) {
            return injectField(Assert.checkNotNullParam("field", field), field.getType(), beanName, optional, filter);
        }

        public SupplierBuilder<T> injectMethod(Method method, BeanSupplier<?> supplier) {
            getInjectorList().add(Injector.forSetterMethod(Assert.checkNotNullParam("method", method), supplier));
            return this;
        }

        public SupplierBuilder<T> injectMethod(Method method, Class<?> injectType, String beanName, boolean optional, DependencyFilter filter) {
            return injectMethod(
                method,
                BeanSupplier.resolving(
                    injectType,
                    Assert.checkNotNullParam("beanName", beanName),
                    optional,
                    Assert.checkNotNullParam("filter", filter)
                )
            );
        }

        public SupplierBuilder<T> injectMethod(Method method) {
            return injectMethod(method, "");
        }

        public SupplierBuilder<T> injectMethod(Method method, String beanName) {
            return injectMethod(method, beanName, false);
        }

        public SupplierBuilder<T> injectMethod(Method method, boolean optional) {
            return injectMethod(method, "", optional);
        }

        public SupplierBuilder<T> injectMethod(Method method, String beanName, boolean optional) {
            return injectMethod(Assert.checkNotNullParam("method", method), beanName, optional, DependencyFilter.ACCEPT);
        }

        public SupplierBuilder<T> injectMethod(Method method, String beanName, boolean optional, DependencyFilter filter) {
            return injectMethod(Assert.checkNotNullParam("method", method), method.getParameterTypes()[0], beanName, optional, filter);
        }

        public SupplierBuilder<T> addConstructorArgument(BeanSupplier<?> supplier) {
            argumentSuppliers.add(Assert.checkNotNullParam("supplier", supplier));
            return this;
        }

        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType) {
            return addConstructorArgument(injectType, false);
        }

        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, boolean optional) {
            return addConstructorArgument(injectType, "", optional);
        }

        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName) {
            return addConstructorArgument(injectType, beanName, false);
        }

        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName, boolean optional) {
            return addConstructorArgument(injectType, beanName, optional, DependencyFilter.ACCEPT);
        }

        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName, boolean optional, DependencyFilter filter) {
            return addConstructorArgument(
                BeanSupplier.resolving(
                    Assert.checkNotNullParam("injectType", injectType),
                    Assert.checkNotNullParam("beanName", beanName),
                    optional,
                    Assert.checkNotNullParam("filter", filter)
                )
            );
        }

        public BeanBuilder<T> build() {
            BeanSupplier<T> supplier = new ConstructorSupplier<>(Assert.checkNotNullParam("constructor", constructor), argumentSuppliers);
            final List<Injector<T>> injectors = this.injectors;
            if (injectors != null) {
                supplier = new InjectingSupplier<>(supplier, List.copyOf(injectors));
            }
            beanBuilder.setSupplier(supplier);
            return beanBuilder;
        }
    }
}
