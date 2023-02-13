package io.github.dmlloyd.maven;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.dmlloyd.unnamed.container.BeanInstantiationException;
import io.github.dmlloyd.unnamed.container.Container;
import io.github.dmlloyd.unnamed.container.DependencyFilter;
import io.github.dmlloyd.unnamed.container.sisu.Sisu;
import io.smallrye.common.constraint.Assert;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsProblem;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.artifact.DefaultArtifactTypeRegistry;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.manager.ClassicDependencyManager;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.transformer.ChainedDependencyGraphTransformer;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.graph.transformer.JavaDependencyContextRefiner;
import org.eclipse.aether.util.graph.transformer.JavaScopeDeriver;
import org.eclipse.aether.util.graph.transformer.JavaScopeSelector;
import org.eclipse.aether.util.graph.transformer.NearestVersionSelector;
import org.eclipse.aether.util.graph.transformer.SimpleOptionalitySelector;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

/**
 * A factory for objects that are useful for resolving dependencies via Maven.
 */
public final class MavenFactory {
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";

    private final Container container;

    private MavenFactory(final ClassLoader classLoader) {
        final Container.Builder builder = Container.builder();
        Sisu.configureSisu(classLoader, builder, DependencyFilter.ACCEPT);
        container = builder.build();
    }

    /**
     * Create a new factory.
     * The given class loader instance is used to find the components of the Maven resolver.
     *
     * @param classLoader the class loader (must not be {@code null})
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create(ClassLoader classLoader) {
        return new MavenFactory(Assert.checkNotNullParam("classLoader", classLoader));
    }

    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Create a new factory.
     * The caller's class loader instance is used to find the components of the Maven resolver.
     *
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create() {
        return create(WALKER.getCallerClass().getClassLoader());
    }

    /**
     * Locate the Maven repository system instance.
     *
     * @return the repository system instance (not {@code null})
     * @throws BeanInstantiationException if there is some problem finding or creating the repository system instance
     */
    public RepositorySystem getRepositorySystem() throws BeanInstantiationException {
        return container.requireBeanOfType(RepositorySystem.class);
    }

    /**
     * Create a basic settings instance using reasonable defaults.
     *
     * @param globalSettings the global settings file (may be {@code null} if none)
     * @param userSettings the user settings file (may be {@code null} if none)
     * @param problemHandler the problem handler (may be {@code null} if none)
     * @return the settings (not {@code null})
     * @throws SettingsBuildingException if creating the settings has failed
     */
    public static Settings createSettings(File globalSettings, File userSettings, Consumer<SettingsProblem> problemHandler) throws SettingsBuildingException {
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        if (globalSettings != null) {
            request.setGlobalSettingsFile(globalSettings);
        }
        if (userSettings != null) {
            request.setUserSettingsFile(userSettings);
        }
        request.setSystemProperties(System.getProperties());
        SettingsBuilder builder = new DefaultSettingsBuilderFactory().newInstance();

        SettingsBuildingResult result = builder.build(request);
        if (problemHandler != null) {
            result.getProblems().forEach(problemHandler);
        }
        Settings settings = result.getEffectiveSettings();
        // now apply some defaults...
        if (settings.getLocalRepository() == null) {
            settings.setLocalRepository(System.getProperty("user.home", "") + File.separator + ".m2" + File.separator + "repository");
        }
        return settings;
    }

    /**
     * Create a repository system session using the given settings with reasonable default behavior.
     *
     * @param settings the settings to use (must not be {@code null})
     * @return the repository system session (not {@code null})
     * @throws BeanInstantiationException if there is some problem finding or creating the repository system instance
     */
    public RepositorySystemSession createSession(final Settings settings) throws BeanInstantiationException {
        Assert.checkNotNullParam("settings", settings);
        final RepositorySystem system = getRepositorySystem();
        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession();
        // offline = "simple"
        // normal = "enhanced"
        String repositoryType = "default";
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(new File(settings.getLocalRepository()), repositoryType)));
        // todo: workspace reader, transfer listener, repo listener
        session.setOffline(settings.isOffline());

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, false, mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
        }
        session.setMirrorSelector(mirrorSelector);

        DefaultProxySelector proxySelector = new DefaultProxySelector();
        for (org.apache.maven.settings.Proxy proxy : settings.getProxies()) {
            proxySelector.add(convertProxy(proxy), proxy.getNonProxyHosts());
        }
        session.setProxySelector(proxySelector);

        session.setDependencyManager(new ClassicDependencyManager());
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));
        session.setDependencyTraverser(new FatArtifactTraverser());

        DependencyGraphTransformer dependencyGraphTransformer = new ChainedDependencyGraphTransformer(
            new ConflictResolver(
                new NearestVersionSelector(),
                new JavaScopeSelector(),
                new SimpleOptionalitySelector(),
                new JavaScopeDeriver()
            ),
            new JavaDependencyContextRefiner()
        );
        session.setDependencyGraphTransformer(dependencyGraphTransformer);

        DefaultArtifactTypeRegistry artifactTypeRegistry = new DefaultArtifactTypeRegistry();
        artifactTypeRegistry.add(new DefaultArtifactType("pom"));
        artifactTypeRegistry.add(new DefaultArtifactType("maven-plugin", "jar", "", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("jar", "jar", "", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("ejb", "jar", "", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("ejb-client", "jar", "client", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("test-jar", "jar", "tests", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("javadoc", "jar", "javadoc", "java"));
        artifactTypeRegistry.add(new DefaultArtifactType("java-source", "jar", "sources", "java", false, false));
        artifactTypeRegistry.add(new DefaultArtifactType("war", "war", "", "java", false, true));
        artifactTypeRegistry.add(new DefaultArtifactType("ear", "ear", "", "java", false, true));
        artifactTypeRegistry.add(new DefaultArtifactType("rar", "rar", "", "java", false, true));
        artifactTypeRegistry.add(new DefaultArtifactType("par", "par", "", "java", false, true));
        session.setArtifactTypeRegistry(artifactTypeRegistry);

        session.setSystemProperties(System.getProperties());
        session.setConfigProperties(System.getProperties());

        session.setDependencySelector(new AndDependencySelector(
            new ScopeDependencySelector(Set.of(JavaScopes.RUNTIME, JavaScopes.COMPILE), Set.of()),
            new OptionalDependencySelector()
        ));

        return session;
    }

    /**
     * Create a remote repository list from the given settings.
     *
     * @param settings the settings (must not be {@code null})
     * @return the remote repository list (not {@code null})
     */
    public static List<RemoteRepository> createRemoteRepositoryList(final Settings settings) {
        Assert.checkNotNullParam("settings", settings);
        List<RemoteRepository> basicList;
        if (! settings.isOffline()) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL);
            builder.setSnapshotPolicy(new RepositoryPolicy(false, null, null));
            RemoteRepository remoteRepository = builder.build();
            basicList = List.of(remoteRepository);
        } else {
            return List.of();
        }
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, mirror.getMirrorOf(), mirror.getMirrorOfLayouts());
        }
        Set<RemoteRepository> mirroredRepos = new LinkedHashSet<RemoteRepository>();
        for (RemoteRepository repository : basicList) {
            RemoteRepository mirror = mirrorSelector.getMirror(repository);
            mirroredRepos.add(mirror != null ? mirror : repository);
        }
        final Set<RemoteRepository> authorizedRepos = new LinkedHashSet<RemoteRepository>();
        for (RemoteRepository remoteRepository : mirroredRepos) {
            final RemoteRepository.Builder builder = new RemoteRepository.Builder(remoteRepository);
            Server server = settings.getServer(remoteRepository.getId());
            if (server != null) {
                final AuthenticationBuilder authenticationBuilder = new AuthenticationBuilder()
                        .addUsername(server.getUsername())
                        .addPassword(server.getPassword())
                        .addPrivateKey(server.getPrivateKey(), server.getPassphrase());
                builder.setAuthentication(authenticationBuilder.build());
            }
            authorizedRepos.add(builder.build());
        }
        return List.copyOf(authorizedRepos);
    }

    /**
     * Get the location of the global settings file, if it can be determined.
     * The file may or may not actually exist.
     *
     * @return the location of the global settings file, or {@code null} if it cannot be determined
     */
    public static File getGlobalSettingsLocation() {
        String mavenHome = System.getProperty("maven.home");
        return mavenHome == null ? null : new File(mavenHome + File.separator + "conf" + File.separator + "settings.xml");
    }

    /**
     * Get the location of the user settings file, if it can be determined.
     * The file may or may not actually exist.
     *
     * @return the location of the user settings file, or {@code null} if it cannot be determined
     */
    public static File getUserSettingsLocation() {
        String userHome = System.getProperty("user.home");
        return userHome == null ? null : new File(userHome + File.separator + ".m2" + File.separator + "settings.xml");
    }

    private static Proxy convertProxy(org.apache.maven.settings.Proxy proxy) {
        final Authentication authentication;
        if (proxy.getUsername() != null || proxy.getPassword() != null) {
            authentication = new AuthenticationBuilder().addUsername(proxy.getUsername())
                .addPassword(proxy.getPassword()).build();
        } else {
            authentication = null;
        }
        return new Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), authentication);
    }
}
