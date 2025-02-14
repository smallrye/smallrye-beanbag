package io.smallrye.beanbag.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.codehaus.plexus.components.secdispatcher.SecDispatcherException;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.maven.beans.Phaseolus;
import io.smallrye.beanbag.maven.beans.Pisum;
import io.smallrye.beanbag.maven.beans.Vigna;
import io.smallrye.beanbag.maven.beans.africa.Cyamopsis;
import io.smallrye.beanbag.maven.beans.africa.Tamarindus;

/**
 *
 */
public final class MavenFactoryTestCase {

    @Test
    public void testGetRepositorySystem() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        final RepositorySystem rs = mavenFactory.getRepositorySystem();
        assertNotNull(rs);
    }

    @Test
    public void testSettings() throws SettingsBuildingException {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        Settings settings1 = mavenFactory.createSettingsFromContainer(MavenFactory.getGlobalSettingsLocation(),
                MavenFactory.getUserSettingsLocation(), MavenFactoryTestCase::handleProblem);
        Settings settings2 = MavenFactory.createSettings(MavenFactory.getGlobalSettingsLocation(),
                MavenFactory.getUserSettingsLocation(), MavenFactoryTestCase::handleProblem);
        assertEquals(MavenFactory.dumpSettings(settings1), MavenFactory.dumpSettings(settings2));
    }

    @Test
    public void testNamedSecDispatcherProvider() throws SecDispatcherException, IOException {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        SecDispatcher sd = mavenFactory.getContainer().requireBean(SecDispatcher.class, "maven");
        assertEquals("hello", sd.decrypt("hello"));
    }

    @Test
    public void testPlexusContainer() throws ComponentLookupException {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        PlexusContainer pc = mavenFactory.getContainer().requireBean(PlexusContainer.class);
        pc.lookup(SecDispatcher.class);
    }

    @Test
    public void testPlexusContainerImpl() throws ComponentLookupException {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        PlexusContainer pc = mavenFactory.getContainer().requireBean(PlexusContainerImpl.class);
        pc.lookup(SecDispatcher.class);
    }

    @Test
    public void testWagonThings() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        mavenFactory.getContainer().requireBean(Wagon.class);
        mavenFactory.getContainer().requireBean(Wagon.class, "file");
        mavenFactory.getContainer().requireBean(Wagon.class, "http");
        mavenFactory.getContainer().requireBean(Wagon.class, "https");

        HttpWagon wagon = mavenFactory.getContainer().requireBean(HttpWagon.class);
        assertNotNull(wagon);
    }

    @Test
    public void testAllTestBeansDiscovered() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        assertNotNull(mavenFactory.getContainer().requireBean(Phaseolus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Pisum.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Vigna.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Tamarindus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Cyamopsis.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Wagon.class));
    }

    @Test
    public void testAllTestBeansDiscoveredWithCommonPackage() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader(), builder -> {
            builder.includePackage("io.smallrye.beanbag.maven.beans");
        });
        assertNotNull(mavenFactory.getContainer().requireBean(Phaseolus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Pisum.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Vigna.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Tamarindus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Cyamopsis.class));
        // Wagon is not included
        assertNull(mavenFactory.getContainer().getOptionalBean(Wagon.class));
    }

    @Test
    public void testExclusionOfIncludedPackages() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader(), builder -> {
            builder.includePackage("io.smallrye.beanbag.maven.beans")
                    .excludePackage("io.smallrye.beanbag.maven.beans.africa");
        });
        assertNotNull(mavenFactory.getContainer().requireBean(Phaseolus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Pisum.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Vigna.class));
        assertNull(mavenFactory.getContainer().getOptionalBean(Tamarindus.class));
        assertNull(mavenFactory.getContainer().getOptionalBean(Cyamopsis.class));
        // Wagon is not included
        assertNull(mavenFactory.getContainer().getOptionalBean(Wagon.class));
    }

    @Test
    public void testPackageExclusion() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader(), builder -> {
            builder.excludePackage("io.smallrye.beanbag.maven.beans.africa");
        });
        assertNotNull(mavenFactory.getContainer().requireBean(Phaseolus.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Pisum.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Vigna.class));
        assertNull(mavenFactory.getContainer().getOptionalBean(Tamarindus.class));
        assertNull(mavenFactory.getContainer().getOptionalBean(Cyamopsis.class));
        assertNotNull(mavenFactory.getContainer().requireBean(Wagon.class));
    }

    private static void handleProblem(SettingsProblem settingsProblem) {
        fail(settingsProblem::toString);
    }

}
