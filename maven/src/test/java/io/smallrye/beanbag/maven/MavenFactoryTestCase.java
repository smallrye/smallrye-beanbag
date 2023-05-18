package io.smallrye.beanbag.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.Test;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

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
    public void testNamedSecDispatcherProvider() throws SecDispatcherException {
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
    public void testWagonThings() {
        final MavenFactory mavenFactory = MavenFactory.create(MavenFactory.class.getClassLoader());
        mavenFactory.getContainer().requireBean(Wagon.class);
        mavenFactory.getContainer().requireBean(Wagon.class, "file");
        mavenFactory.getContainer().requireBean(Wagon.class, "http");
        mavenFactory.getContainer().requireBean(Wagon.class, "https");

        HttpWagon wagon = mavenFactory.getContainer().requireBean(HttpWagon.class);
        assertNotNull(wagon);
    }

    private static void handleProblem(SettingsProblem settingsProblem) {
        fail(settingsProblem::toString);
    }

}
