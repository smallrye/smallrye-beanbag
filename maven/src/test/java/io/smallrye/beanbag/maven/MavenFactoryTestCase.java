package io.smallrye.beanbag.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsProblem;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.Test;

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

    private static void handleProblem(SettingsProblem settingsProblem) {
        fail(settingsProblem::toString);
    }

}
