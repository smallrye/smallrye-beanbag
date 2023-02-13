package io.github.dmlloyd.maven;

import io.github.dmlloyd.unnamed.container.Container;
import io.github.dmlloyd.unnamed.container.DependencyFilter;
import io.github.dmlloyd.unnamed.container.sisu.Sisu;
import io.smallrye.common.constraint.Assert;
import org.eclipse.aether.RepositorySystem;

/**
 *
 */
public final class MavenFactory {
    private final Container container;

    private MavenFactory(final ClassLoader classLoader) {
        final Container.Builder builder = Container.builder();
        Sisu.configureSisu(classLoader, builder, DependencyFilter.ACCEPT);
        container = builder.build();
    }

    public static MavenFactory create(ClassLoader classLoader) {
        return new MavenFactory(Assert.checkNotNullParam("classLoader", classLoader));
    }

    public RepositorySystem getRepositorySystem() {
        return container.requireBeanOfType(RepositorySystem.class);
    }
}
