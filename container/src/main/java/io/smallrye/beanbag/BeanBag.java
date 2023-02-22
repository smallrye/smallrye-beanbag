package io.smallrye.beanbag;

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
public final class BeanBag {

    private final Scope singletonScope;
    private final ScopeDefinition scopeDefinition;

    BeanBag(Builder builder) {
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
        // create a copy of the non-singleton scope so singletons can inject from there
        final ScopeDefinition scopeDefinition = new ScopeDefinition(List.copyOf(definitions));
        singletonScope = new Scope(null, scopeDefinition, new ScopeDefinition(singletonBeans));
        this.scopeDefinition = scopeDefinition;
    }

    private <T> BeanDefinition<T> makeDefinition(final BeanBuilder<T> beanBuilder) {
        final String name = beanBuilder.name;
        final Set<Class<? super T>> restrictedTypes = Set
                .copyOf(Objects.requireNonNullElse(beanBuilder.restrictedTypes, List.of()));
        final BeanSupplier<T> supplier = beanBuilder.supplier;
        final int priority = beanBuilder.priority;
        final Class<T> type = beanBuilder.type;
        return new BeanDefinition<>(name, priority, type, restrictedTypes, supplier);
    }

    /**
     * Create a new resolution scope.
     * A resolution scope maintains independent instances of its beans.
     *
     * @return the new resolution scope (not {@code null})
     */
    public Scope newScope() {
        return new Scope(singletonScope, null, scopeDefinition);
    }

    /**
     * Get all constructable beans of the given type from a new resolution scope.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the (possibly empty) list of all matching beans
     * @param <T> the allowed bean type
     */
    public <T> List<T> getAllBeans(final Class<T> type) {
        return newScope().getAllBeans(type);
    }

    /**
     * Require a single bean with the given type from a new resolution scope.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the single bean (not {@code null})
     * @param <T> the allowed bean type
     * @throws NoSuchBeanException if the bean is not present
     * @throws BeanInstantiationException if some error occurred when instantiating the bean
     */
    public <T> T requireBean(Class<T> type) {
        return newScope().requireBean(type);
    }

    /**
     * Require a single bean with the given type and name from a new resolution scope.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @return the single bean (not {@code null})
     * @param <T> the allowed bean type
     * @throws NoSuchBeanException if the bean is not present
     * @throws BeanInstantiationException if some error occurred when instantiating the bean
     */
    public <T> T requireBean(Class<T> type, String name) {
        return newScope().requireBean(type, name);
    }

    /**
     * Get a single bean with the given type from a new resolution scope, if it exists and can be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     */
    public <T> T getOptionalBean(Class<T> type) {
        return newScope().getOptionalBean(type);
    }

    /**
     * Get a single bean with the given type and name from a new resolution scope, if it exists and can be instantiated.
     *
     * @param type the allowed bean type class (must not be {@code null})
     * @param name the name of the bean which should be returned, or {@code ""} for any (must not be {@code null})
     * @return the single bean, or {@code null} if it is not present
     * @param <T> the allowed bean type
     */
    public <T> T getOptionalBean(Class<T> type, String name) {
        return newScope().getOptionalBean(type, name);
    }

    /**
     * Construct a new container builder.
     *
     * @return the new builder (not {@code null})
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for a new container.
     */
    public static final class Builder {

        private final List<BeanBuilder<?>> beanBuilders = new ArrayList<>();

        Builder() {
        }

        /**
         * Add a new bean with the given type, returning a builder to configure it.
         * The given type must be the concrete type of the bean <em>or</em> a class representing a supertype of that concrete
         * type.
         *
         * @param type the bean type class (must not be {@code null})
         * @return the bean builder (not {@code null})
         * @param <T> the bean type
         */
        public <T> BeanBuilder<T> addBean(final Class<T> type) {
            Assert.checkNotNullParam("type", type);
            return new BeanBuilder<T>(this, type);
        }

        /**
         * Build a new container instance with the beans that were previously configured in this builder.
         *
         * @return the new container (not {@code null})
         */
        public BeanBag build() {
            return new BeanBag(this);
        }
    }

    /**
     * A builder for an individual bean's configuration.
     *
     * @param <T> the bean type
     */
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

        /**
         * Set the bean priority. Higher numbers have higher precedence.
         * Users should normally configure beans with a priority of {@code 0} or higher.
         *
         * @param priority the bean priority
         * @return this builder (not {@code null})
         */
        public BeanBuilder<T> setPriority(final int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Set the bean name. Beans with no name have a name of the empty string {@code ""}.
         *
         * @param name the bean name (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public BeanBuilder<T> setName(final String name) {
            Assert.checkNotNullParam("name", name);
            this.name = name;
            return this;
        }

        /**
         * Set the supplier for this bean.
         * Setting a supplier will overwrite a supplier created via {@link #buildSupplier()} (if any).
         *
         * @param supplier the supplier instance (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public BeanBuilder<T> setSupplier(final BeanSupplier<T> supplier) {
            Assert.checkNotNullParam("supplier", supplier);
            this.supplier = supplier;
            return this;
        }

        /**
         * Construct a reflective supplier for this bean.
         * Completing this builder will overwrite the supplier created via {@link #setSupplier(BeanSupplier)} (if any).
         *
         * @return a new supplier builder (not {@code null})
         */
        public SupplierBuilder<T> buildSupplier() {
            return new SupplierBuilder<>(this);
        }

        /**
         * Set the singleton flag for this bean.
         * A singleton is created in a scope which is global to a single container.
         *
         * @param singleton the value of the singleton flag
         * @return this builder (not {@code null})
         */
        public BeanBuilder<T> setSingleton(final boolean singleton) {
            this.singleton = singleton;
            return this;
        }

        /**
         * Restrict the types of this bean.
         * The bean will only be able to be looked up using one of these types.
         *
         * @param types the restricted types (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public BeanBuilder<T> addRestrictedTypes(Collection<Class<? super T>> types) {
            Assert.checkNotNullParam("types", types);
            if (restrictedTypes != null) {
                restrictedTypes.addAll(types);
            } else {
                restrictedTypes = new ArrayList<>(types);
            }
            return this;
        }

        /**
         * Commit this bean definition into the enclosing container builder.
         *
         * @return the container builder (not {@code null})
         */
        public Builder build() {
            builder.beanBuilders.add(this);
            return builder;
        }
    }

    /**
     * A builder for a bean supplier which constructs a bean using reflection.
     *
     * @param <T> the bean type
     */
    public static final class SupplierBuilder<T> {
        private final BeanBuilder<T> beanBuilder;
        private final List<BeanSupplier<?>> argumentSuppliers = new ArrayList<>();
        private Constructor<T> constructor;
        private List<Injector<T>> injectors;

        SupplierBuilder(final BeanBuilder<T> beanBuilder) {
            this.beanBuilder = beanBuilder;
        }

        /**
         * Set the constructor to use to instantiate the bean.
         *
         * @param constructor the constructor (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> setConstructor(final Constructor<T> constructor) {
            Assert.checkNotNullParam("constructor", constructor);
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

        /**
         * Add a general field injection.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param supplier the supplier of the field's value (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, BeanSupplier<?> supplier) {
            getInjectorList().add(Injector.forField(Assert.checkNotNullParam("field", field),
                    Assert.checkNotNullParam("supplier", supplier)));
            return this;
        }

        /**
         * Add a bean dependency field injection.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param injectType the bean type to inject (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, Class<?> injectType, String beanName, boolean optional,
                DependencyFilter filter) {
            return injectField(
                    field,
                    BeanSupplier.resolving(
                            injectType,
                            beanName,
                            optional,
                            filter));
        }

        /**
         * Add a bean dependency field injection.
         * The type of the bean is derived from the field's type.
         *
         * @param field the field to inject into (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field) {
            return injectField(field, "");
        }

        /**
         * Add a bean dependency field injection.
         * The type of the bean is derived from the field's type.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, String beanName) {
            return injectField(field, beanName, false);
        }

        /**
         * Add a bean dependency field injection.
         * The type of the bean is derived from the field's type.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, boolean optional) {
            return injectField(field, "", optional);
        }

        /**
         * Add a bean dependency field injection.
         * The type of the bean is derived from the field's type.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, String beanName, boolean optional) {
            return injectField(field, beanName, optional, DependencyFilter.ACCEPT);
        }

        /**
         * Add a bean dependency field injection.
         * The type of the bean is derived from the field's type.
         *
         * @param field the field to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectField(Field field, String beanName, boolean optional, DependencyFilter filter) {
            return injectField(Assert.checkNotNullParam("field", field), field.getType(), beanName, optional, filter);
        }

        /**
         * Add a general method injection.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param supplier the supplier of the method's value (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method, BeanSupplier<?> supplier) {
            getInjectorList().add(Injector.forSetterMethod(Assert.checkNotNullParam("method", method), supplier));
            return this;
        }

        /**
         * Add a bean dependency method injection.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param injectType the bean type to inject (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method, Class<?> injectType, String beanName, boolean optional,
                DependencyFilter filter) {
            return injectMethod(
                    method,
                    BeanSupplier.resolving(
                            injectType,
                            beanName,
                            optional,
                            filter));
        }

        /**
         * Add a bean dependency method injection.
         * The type of the bean is derived from the method's sole argument type.
         *
         * @param method the method to inject into (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method) {
            return injectMethod(method, "");
        }

        /**
         * Add a bean dependency method injection.
         * The type of the bean is derived from the method's sole argument type.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method, String beanName) {
            return injectMethod(method, beanName, false);
        }

        /**
         * Add a bean dependency method injection.
         * The type of the bean is derived from the method's sole argument type.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method, boolean optional) {
            return injectMethod(method, "", optional);
        }

        /**
         * Add a bean dependency method injection.
         * The type of the bean is derived from the method's sole argument type.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */

        public SupplierBuilder<T> injectMethod(Method method, String beanName, boolean optional) {
            return injectMethod(Assert.checkNotNullParam("method", method), beanName, optional, DependencyFilter.ACCEPT);
        }

        /**
         * Add a bean dependency method injection.
         * The type of the bean is derived from the method's sole argument type.
         *
         * @param method the method to inject into (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> injectMethod(Method method, String beanName, boolean optional, DependencyFilter filter) {
            return injectMethod(Assert.checkNotNullParam("method", method), method.getParameterTypes()[0], beanName, optional,
                    filter);
        }

        /**
         * Add a general constructor argument injection.
         *
         * @param supplier the supplier of the argument's value (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(BeanSupplier<?> supplier) {
            argumentSuppliers.add(Assert.checkNotNullParam("supplier", supplier));
            return this;
        }

        /**
         * Add a bean dependency constructor argument injection.
         *
         * @param injectType the bean type to inject (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType) {
            return addConstructorArgument(injectType, false);
        }

        /**
         * Add a bean dependency constructor argument injection.
         *
         * @param injectType the bean type to inject (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, boolean optional) {
            return addConstructorArgument(injectType, "", optional);
        }

        /**
         * Add a bean dependency constructor argument injection.
         *
         * @param injectType the bean type to inject (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName) {
            return addConstructorArgument(injectType, beanName, false);
        }

        /**
         * Add a bean dependency constructor argument injection.
         *
         * @param injectType the bean type to inject (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName, boolean optional) {
            return addConstructorArgument(injectType, beanName, optional, DependencyFilter.ACCEPT);
        }

        /**
         * Add a bean dependency constructor argument injection.
         *
         * @param injectType the bean type to inject (must not be {@code null})
         * @param beanName the bean name to inject or {@code ""} for any (must not be {@code null})
         * @param optional {@code true} to allow {@code null} to be injected, or {@code false} otherwise
         * @param filter the filter to apply to determine whether a given bean should be included (must not be {@code null})
         * @return this builder (not {@code null})
         */
        public SupplierBuilder<T> addConstructorArgument(Class<?> injectType, String beanName, boolean optional,
                DependencyFilter filter) {
            return addConstructorArgument(
                    BeanSupplier.resolving(
                            injectType,
                            beanName,
                            optional,
                            filter));
        }

        /**
         * Commit this supplier definition into the enclosing bean builder.
         * Any supplier previously set on the bean builder is overwritten.
         *
         * @return the enclosing bean builder (not {@code null})
         */
        public BeanBuilder<T> build() {
            BeanSupplier<T> supplier = new ConstructorSupplier<>(Assert.checkNotNullParam("constructor", constructor),
                    argumentSuppliers);
            final List<Injector<T>> injectors = this.injectors;
            if (injectors != null) {
                supplier = new InjectingSupplier<>(supplier, List.copyOf(injectors));
            }
            beanBuilder.setSupplier(supplier);
            return beanBuilder;
        }
    }
}
