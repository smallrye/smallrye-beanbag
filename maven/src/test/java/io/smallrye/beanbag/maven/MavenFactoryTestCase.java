package io.smallrye.beanbag.maven;

import io.smallrye.beanbag.maven.MavenFactory;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 */
public final class MavenFactoryTestCase {

    @Test
    public void testGetRepositorySystem() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        final RepositorySystem rs = mavenFactory.getRepositorySystem();
        Assertions.assertNotNull(rs);
    }
}
