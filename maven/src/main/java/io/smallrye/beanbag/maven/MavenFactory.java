package io.smallrye.beanbag.maven;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Activation;
import org.apache.maven.settings.ActivationFile;
import org.apache.maven.settings.ActivationOS;
import org.apache.maven.settings.ActivationProperty;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.building.SettingsProblem;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.BeanInstantiationException;
import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.beanbag.Scope;
import io.smallrye.beanbag.sisu.Sisu;
import io.smallrye.common.constraint.Assert;

/**
 * A factory for objects that are useful for resolving dependencies via Maven.
 */
public final class MavenFactory {
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final String NL = System.lineSeparator();

    private final BeanBag container;

    private MavenFactory(final List<ClassLoader> classLoaders, final Consumer<BeanBag.Builder> configurator,
            final DependencyFilter dependencyFilter) {
        final BeanBag.Builder builder = BeanBag.builder();
        configurator.accept(builder);
        final Sisu sisu = Sisu.createFor(builder);
        builder.addBean(BeanBag.class)
                .setSupplier(Scope::getContainer)
                .build();
        // add our simple plexus container
        builder.addBean(PlexusContainerImpl.class)
                .setPriority(-100)
                .setSingleton(true)
                .setSupplier(scope -> new PlexusContainerImpl(scope.getContainer()))
                .build();
        for (ClassLoader classLoader : classLoaders) {
            sisu.addClassLoader(classLoader, dependencyFilter);
        }
        // this will mimic the behavior of `component.xml` from maven-core < 4; if 4 is used, a better bean becomes available
        builder.addBean(SecDispatcher.class)
                .setName("maven")
                .setPriority(-100)
                .setSingleton(true)
                .setSupplier(
                        scope -> scope.getBean(SecDispatcher.class, "", false, (type, name, priority) -> name.isEmpty()))
                .build();
        container = builder.build();
    }

    /**
     * Create a new factory.
     * The given class loader instances are used to find the components of the Maven resolver.
     *
     * @param classLoaders the class loaders to search (must not be {@code null})
     * @param configurator an additional configurator which can be used to modify the container configuration (must not be
     *        {@code null})
     * @param dependencyFilter a filter which can be used to exclude certain implementations (must not be {@code null})
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create(List<ClassLoader> classLoaders, Consumer<BeanBag.Builder> configurator,
            DependencyFilter dependencyFilter) {
        return new MavenFactory(Assert.checkNotNullParam("classLoaders", classLoaders), configurator, dependencyFilter);
    }

    /**
     * Create a new factory.
     * The given class loader instance is used to find the components of the Maven resolver.
     *
     * @param classLoader the class loader (must not be {@code null})
     * @param configurator an additional configurator which can be used to modify the container configuration (must not be
     *        {@code null})
     * @param dependencyFilter a filter which can be used to exclude certain implementations (must not be {@code null})
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create(ClassLoader classLoader, Consumer<BeanBag.Builder> configurator,
            DependencyFilter dependencyFilter) {
        return create(List.of(Assert.checkNotNullParam("classLoader", classLoader)), configurator, dependencyFilter);
    }

    /**
     * Create a new factory.
     * The given class loader instance is used to find the components of the Maven resolver.
     *
     * @param classLoader the class loader (must not be {@code null})
     * @param configurator an additional configurator which can be used to modify the container configuration (must not be
     *        {@code null})
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create(ClassLoader classLoader, Consumer<BeanBag.Builder> configurator) {
        return create(classLoader, configurator, DependencyFilter.ACCEPT);
    }

    /**
     * Create a new factory.
     * The given class loader instance is used to find the components of the Maven resolver.
     *
     * @param classLoader the class loader (must not be {@code null})
     * @return the Maven factory instance (not {@code null})
     */
    public static MavenFactory create(ClassLoader classLoader) {
        return create(classLoader, ignored -> {
        });
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
     * Get the bean container used to set up the Maven environment.
     *
     * @return the container (not {@code null})
     */
    public BeanBag getContainer() {
        return container;
    }

    /**
     * Locate the Maven repository system instance.
     *
     * @return the repository system instance (not {@code null})
     * @throws BeanInstantiationException if there is some problem finding or creating the repository system instance
     */
    public RepositorySystem getRepositorySystem() throws BeanInstantiationException {
        return getContainer().requireBean(RepositorySystem.class);
    }

    /**
     * Locate the Maven settings builder instance.
     *
     * @return the repository system instance (not {@code null})
     * @throws BeanInstantiationException if there is some problem finding or creating the repository system instance
     */
    public SettingsBuilder getSettingsBuilder() throws BeanInstantiationException {
        return getContainer().requireBean(SettingsBuilder.class);
    }

    /**
     * Create a basic settings from the container {@link SettingsBuilder} using reasonable defaults.
     *
     * @param globalSettings the global settings file (may be {@code null} if none)
     * @param userSettings the user settings file (may be {@code null} if none)
     * @param problemHandler the problem handler (may be {@code null} if none)
     * @return the settings (not {@code null})
     * @throws SettingsBuildingException if creating the settings has failed
     */
    public Settings createSettingsFromContainer(File globalSettings, File userSettings,
            Consumer<SettingsProblem> problemHandler)
            throws SettingsBuildingException {
        return getSettings(globalSettings, userSettings, problemHandler, getSettingsBuilder());
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
    public static Settings createSettings(File globalSettings, File userSettings, Consumer<SettingsProblem> problemHandler)
            throws SettingsBuildingException {
        return getSettings(globalSettings, userSettings, problemHandler, new DefaultSettingsBuilderFactory().newInstance());
    }

    private static Settings getSettings(final File globalSettings, final File userSettings,
            final Consumer<SettingsProblem> problemHandler, final SettingsBuilder builder) throws SettingsBuildingException {
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        if (globalSettings != null) {
            request.setGlobalSettingsFile(globalSettings);
        }
        if (userSettings != null) {
            request.setUserSettingsFile(userSettings);
        }
        request.setSystemProperties(System.getProperties());

        SettingsBuildingResult result = builder.build(request);
        if (problemHandler != null) {
            result.getProblems().forEach(problemHandler);
        }
        Settings settings = result.getEffectiveSettings();
        // now apply some defaults...
        if (settings.getLocalRepository() == null) {
            settings.setLocalRepository(
                    System.getProperty("user.home", "") + File.separator + ".m2" + File.separator + "repository");
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
        final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        // offline = "simple"
        // normal = "enhanced"
        String repositoryType = "default";
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session,
                new LocalRepository(new File(settings.getLocalRepository()), repositoryType)));
        // todo: workspace reader, transfer listener, repo listener
        session.setOffline(settings.isOffline());

        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, false, mirror.getMirrorOf(),
                    mirror.getMirrorOfLayouts());
        }
        session.setMirrorSelector(mirrorSelector);

        DefaultProxySelector proxySelector = new DefaultProxySelector();
        for (org.apache.maven.settings.Proxy proxy : settings.getProxies()) {
            proxySelector.add(convertProxy(proxy), proxy.getNonProxyHosts());
        }
        session.setProxySelector(proxySelector);

        session.setSystemProperties(System.getProperties());
        session.setConfigProperties(System.getProperties());

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
        if (!settings.isOffline()) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder("central", "default", MAVEN_CENTRAL);
            builder.setSnapshotPolicy(new RepositoryPolicy(false, null, null));
            RemoteRepository remoteRepository = builder.build();
            basicList = List.of(remoteRepository);
        } else {
            return List.of();
        }
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        for (Mirror mirror : settings.getMirrors()) {
            mirrorSelector.add(mirror.getId(), mirror.getUrl(), mirror.getLayout(), false, mirror.isBlocked(),
                    mirror.getMirrorOf(),
                    mirror.getMirrorOfLayouts());
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

    /**
     * Dump the given settings to a string.
     * Any passwords or secrets in the settings are masked.
     * This is mainly useful for debugging; the output format may change and is not intended to be parsed by machines.
     *
     * @param settings the settings to dump (must not be {@code null})
     * @return a string containing the dump of the settings
     */
    public static String dumpSettings(final Settings settings) {
        return dumpSettings(new StringBuilder(), settings, 0).toString();
    }

    /**
     * Dump the given settings to a string.
     * Any passwords or secrets in the settings are masked.
     * This is mainly useful for debugging; the output format may change and is not intended to be parsed by machines.
     *
     * @param sb the string builder to append to (must not be {@code null})
     * @param settings the settings to dump (must not be {@code null})
     * @param indent the indentation level
     * @return the string builder {@code sb}
     */
    public static StringBuilder dumpSettings(final StringBuilder sb, final Settings settings, int indent) {
        indent(sb, indent).append("settings={").append(NL);
        joinStrings(indent(sb, indent + 1).append("activeProfiles=["), settings.getActiveProfiles()).append(']').append(NL);
        indent(sb, indent + 1).append("localRepository=").append(settings.getLocalRepository()).append(NL);
        dumpMirrors(sb, settings.getMirrors(), indent + 1).append(NL);
        indent(sb, indent + 1).append("modelEncoding=").append(settings.getModelEncoding()).append(NL);
        joinStrings(indent(sb, indent + 1).append("pluginGroups=["), settings.getPluginGroups()).append(']').append(NL);
        dumpProfiles(sb, settings.getProfiles(), indent + 1).append(NL);
        dumpProxies(sb, settings.getProxies(), indent + 1).append(NL);
        dumpServers(sb, settings.getServers(), indent + 1).append(NL);
        indent(sb, indent + 1).append("interactiveMode=").append(settings.isInteractiveMode()).append(NL);
        indent(sb, indent + 1).append("offline=").append(settings.isOffline()).append(NL);
        indent(sb, indent + 1).append("usePluginRegistry=").append(settings.isUsePluginRegistry()).append(NL);
        return indent(sb, indent).append("}");
    }

    private static StringBuilder dumpServers(final StringBuilder sb, final List<Server> servers, final int indent) {
        indent(sb, indent).append("servers=[");
        Iterator<Server> iterator = servers.iterator();
        if (iterator.hasNext()) {
            sb.append(NL);
            dumpServer(sb, iterator.next(), indent + 1);
            while (iterator.hasNext()) {
                sb.append(',').append(NL);
                dumpServer(sb, iterator.next(), indent + 1);
            }
            sb.append(NL);
            indent(sb, indent);
        }
        return sb.append(']');
    }

    private static StringBuilder dumpServer(final StringBuilder sb, final Server server, final int indent) {
        indent(sb, indent).append("server={").append(NL);
        indent(sb, indent + 1).append("username=").append(server.getUsername()).append(NL);
        indent(sb, indent + 1).append("password=").append(server.getPassword() == null ? "<NOT SET>" : "<SET>").append(NL);
        indent(sb, indent + 1).append("privateKey=").append(server.getPrivateKey() == null ? "<NOT SET>" : "<SET>").append(NL);
        indent(sb, indent + 1).append("passphrase=").append(server.getPassphrase() == null ? "<NOT SET>" : "<SET>").append(NL);
        indent(sb, indent + 1).append("filePermissions=").append(server.getFilePermissions()).append(NL);
        indent(sb, indent + 1).append("directoryPermissions=").append(server.getDirectoryPermissions()).append(NL);
        indent(sb, indent + 1).append("configuration=").append(server.getConfiguration()).append(NL);
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpProxies(final StringBuilder sb, final List<org.apache.maven.settings.Proxy> proxies,
            final int indent) {
        indent(sb, indent).append("proxies=[");
        Iterator<org.apache.maven.settings.Proxy> iterator = proxies.iterator();
        if (iterator.hasNext()) {
            sb.append(NL);
            dumpProxy(sb, iterator.next(), indent + 1);
            while (iterator.hasNext()) {
                sb.append(',').append(NL);
                dumpProxy(sb, iterator.next(), indent + 1);
            }
            sb.append(NL);
            indent(sb, indent);
        }
        return sb.append(']');
    }

    private static StringBuilder dumpProxy(final StringBuilder sb, final org.apache.maven.settings.Proxy proxy,
            final int indent) {
        indent(sb, indent).append("proxy={").append(NL);
        indent(sb, indent + 1).append("active=").append(proxy.isActive()).append(NL);
        indent(sb, indent + 1).append("protocol=").append(proxy.getProtocol()).append(NL);
        indent(sb, indent + 1).append("username=").append(proxy.getUsername()).append(NL);
        indent(sb, indent + 1).append("password=").append(proxy.getPassword() == null ? "<NOT SET>" : "<SET>").append(NL);
        indent(sb, indent + 1).append("port=").append(proxy.getPort()).append(NL);
        indent(sb, indent + 1).append("host=").append(proxy.getHost()).append(NL);
        indent(sb, indent + 1).append("nonProxyHosts=").append(proxy.getNonProxyHosts()).append(NL);
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpProfiles(final StringBuilder sb, final List<Profile> profiles, final int indent) {
        indent(sb, indent).append("profiles=[");
        Iterator<Profile> iterator = profiles.iterator();
        if (iterator.hasNext()) {
            sb.append(NL);
            dumpProfile(sb, iterator.next(), indent + 1);
            while (iterator.hasNext()) {
                sb.append(',').append(NL);
                dumpProfile(sb, iterator.next(), indent + 1);
            }
            sb.append(NL);
            indent(sb, indent);
        }
        return sb.append(']');
    }

    private static StringBuilder dumpProfile(final StringBuilder sb, final Profile profile, final int indent) {
        indent(sb, indent).append("profile={").append(NL);
        if (profile.getActivation() != null) {
            dumpActivation(sb, profile.getActivation(), indent + 1).append(NL);
        }
        dumpRepositories(sb, "repositories", profile.getRepositories(), indent + 1).append(NL);
        dumpRepositories(sb, "pluginRepositories", profile.getPluginRepositories(), indent + 1).append(NL);
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpRepositories(final StringBuilder sb, final String label,
            final List<Repository> repositories, final int indent) {
        indent(sb, indent).append(label).append("=[");
        Iterator<Repository> iterator = repositories.iterator();
        if (iterator.hasNext()) {
            sb.append(NL);
            dumpRepository(sb, iterator.next(), indent + 1);
            while (iterator.hasNext()) {
                sb.append(',').append(NL);
                dumpRepository(sb, iterator.next(), indent + 1);
            }
            sb.append(NL);
            indent(sb, indent);
        }
        return sb.append(']');
    }

    private static StringBuilder dumpRepository(final StringBuilder sb, final Repository repository, final int indent) {
        indent(sb, indent).append("repository={").append(NL);
        indent(sb, indent + 1).append("id=").append(repository.getId()).append(NL);
        indent(sb, indent + 1).append("name=").append(repository.getName()).append(NL);
        indent(sb, indent + 1).append("url=").append(repository.getUrl()).append(NL);
        indent(sb, indent + 1).append("layout=").append(repository.getLayout()).append(NL);
        if (repository.getReleases() != null) {
            dumpRepositoryPolicy(sb, "releasesPolicy", repository.getReleases(), indent + 1).append(NL);
        }
        if (repository.getSnapshots() != null) {
            dumpRepositoryPolicy(sb, "snapshotsPolicy", repository.getSnapshots(), indent + 1).append(NL);
        }
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpRepositoryPolicy(final StringBuilder sb, final String label,
            final org.apache.maven.settings.RepositoryPolicy repositoryPolicy, final int indent) {
        indent(sb, indent).append(label).append("={").append(NL);
        indent(sb, indent + 1).append("enabled=").append(repositoryPolicy.isEnabled()).append(NL);
        indent(sb, indent + 1).append("updatePolicy=").append(repositoryPolicy.getUpdatePolicy()).append(NL);
        indent(sb, indent + 1).append("checksumPolicy=").append(repositoryPolicy.getChecksumPolicy()).append(NL);
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpActivation(final StringBuilder sb, final Activation activation, final int indent) {
        indent(sb, indent).append("activation={").append(NL);
        indent(sb, indent + 1).append("activeByDefault=").append(activation.isActiveByDefault()).append(NL);
        ActivationFile file = activation.getFile();
        if (file != null) {
            String exists = file.getExists();
            String missing = file.getMissing();
            if (exists != null || missing != null) {
                indent(sb, indent + 1);
                if (exists != null) {
                    sb.append("file=").append(exists);
                    if (missing != null) {
                        sb.append(',');
                    }
                }
                if (missing != null) {
                    sb.append("!file=").append(missing);
                }
                sb.append(NL);
            }
        }
        String jdk = activation.getJdk();
        if (jdk != null) {
            indent(sb, indent + 1).append("jdk=").append(jdk).append(NL);
        }
        ActivationOS os = activation.getOs();
        if (os != null) {
            String arch = os.getArch();
            String name = os.getName();
            String family = os.getFamily();
            String version = os.getVersion();
            if (arch != null || name != null || family != null || version != null) {
                indent(sb, indent + 1);
                if (arch != null) {
                    sb.append("osArch=").append(arch);
                    if (name != null || family != null || version != null) {
                        sb.append(',');
                    }
                }
                if (name != null) {
                    sb.append("osName=").append(name);
                    if (family != null || version != null) {
                        sb.append(',');
                    }
                }
                if (family != null) {
                    sb.append("osFamily=").append(family);
                    if (version != null) {
                        sb.append(',');
                    }
                }
                if (version != null) {
                    sb.append("osVersion=").append(version);
                }
                sb.append(NL);
            }
        }
        ActivationProperty property = activation.getProperty();
        if (property != null) {
            String name = property.getName();
            String value = property.getValue();
            if (name != null) {
                indent(sb, indent + 1).append("property=").append(name);
                if (value != null) {
                    sb.append("=").append(value);
                }
                sb.append(NL);
            }
        }
        return indent(sb, indent).append('}');
    }

    private static StringBuilder dumpMirrors(final StringBuilder sb, final List<Mirror> mirrors, int indent) {
        indent(sb, indent).append("mirrors=[");
        Iterator<Mirror> iterator = mirrors.iterator();
        if (iterator.hasNext()) {
            sb.append(NL);
            dumpMirror(sb, iterator.next(), indent + 1);
            while (iterator.hasNext()) {
                sb.append(',').append(NL);
                dumpMirror(sb, iterator.next(), indent + 1);
            }
            sb.append(NL);
            indent(sb, indent);
        }
        return sb.append(']');
    }

    private static StringBuilder dumpMirror(final StringBuilder sb, final Mirror mirror, int indent) {
        indent(sb, indent).append("mirror={").append(NL);
        indent(sb, indent + 1).append("name=").append(mirror.getName()).append(NL);
        indent(sb, indent + 1).append("url=").append(mirror.getUrl()).append(NL);
        indent(sb, indent + 1).append("layout=").append(mirror.getLayout()).append(NL);
        indent(sb, indent + 1).append("mirrorOf=").append(mirror.getMirrorOf()).append(NL);
        indent(sb, indent + 1).append("mirrorOfLayouts=").append(mirror.getMirrorOfLayouts()).append(NL);
        indent(sb, indent + 1).append("blocked=").append(mirror.isBlocked()).append(NL);
        return indent(sb, indent).append('}');
    }

    private static StringBuilder joinStrings(final StringBuilder sb, final List<String> strings) {
        Iterator<String> iterator = strings.iterator();
        if (iterator.hasNext()) {
            sb.append(iterator.next());
            while (iterator.hasNext()) {
                sb.append(',').append(iterator.next());
            }
        }
        return sb;
    }

    private static StringBuilder indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        return sb;
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
