package build.jenesis.module;

import module java.base;
import build.jenesis.DiscoveredLocation;
import build.jenesis.Discovery;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.maven.MavenRepository;

public class DiscoveryModuleRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final Pattern VERSIONED = Pattern.compile("META-INF/versions/[0-9]+/module-info\\.class");

    private final Scope scope;
    private final Discovery discovery;
    private final MavenRepository maven;
    private final Repository.Connection connection;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Map<URI, JenesisRepository> repositories = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, MavenDependencyKey>, JenesisRepository> coordinates = new ConcurrentHashMap<>();

    public DiscoveryModuleRepository() {
        this(Scope.MODULE,
             new Discovery(),
             MavenDefaultRepository.of(),
             new Repository.Connection(),
             null,
             Palette.NONE);
    }

    public static DiscoveryModuleRepository ofEnvironment(Environment environment, Scope scope) {
        return ofEnvironment(environment, scope, Discovery.ofEnvironment(environment));
    }

    public static DiscoveryModuleRepository ofEnvironment(Environment environment, Scope scope, Discovery discovery) {
        return new DiscoveryModuleRepository(scope,
                                             discovery,
                                             MavenDefaultRepository.ofEnvironment(environment, discovery),
                                             Repository.Connection.ofEnvironment(environment),
                                             environment.flag("print.fetch") ? environment.out() : null,
                                             Palette.ofEnvironment(environment));
    }

    public DiscoveryModuleRepository(Scope scope,
                                     Discovery discovery,
                                     MavenRepository maven,
                                     Repository.Connection connection,
                                     Consumer<String> printing,
                                     Palette palette) {
        this.scope = scope;
        this.discovery = discovery;
        this.maven = maven;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public DiscoveryModuleRepository scope(Scope scope) {
        return new DiscoveryModuleRepository(scope, discovery, maven, connection, printing, palette);
    }

    public DiscoveryModuleRepository discovery(Discovery discovery) {
        return new DiscoveryModuleRepository(scope, discovery, maven, connection, printing, palette);
    }

    public DiscoveryModuleRepository maven(MavenRepository maven) {
        return new DiscoveryModuleRepository(scope, discovery, maven, connection, printing, palette);
    }

    public DiscoveryModuleRepository connection(Repository.Connection connection) {
        return new DiscoveryModuleRepository(scope, discovery, maven, connection, printing, palette);
    }

    public DiscoveryModuleRepository printing(Consumer<String> printing, Palette palette) {
        return new DiscoveryModuleRepository(scope, discovery, maven, connection, printing, palette);
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String module,
                                          String classifier,
                                          String version,
                                          String type) throws IOException {
        SAFE_SEGMENT.accept("module name", module);
        if (classifier != null) {
            SAFE_SEGMENT.accept("classifier", classifier);
        }
        if (version != null) {
            SAFE_SEGMENT.accept("version", version);
        }
        SAFE_SEGMENT.accept("type", type);
        for (String key : scope == Scope.MODULE
                ? List.of("module", "moduletomaven")
                : List.of("moduletomaven", "module")) {
            DiscoveredLocation location = discovery.lookup(module, key, module).orElse(null);
            if (location == null) {
                continue;
            }
            String resolved = version == null
                    ? location.latest(Map.of("module", module, "-suffix", location.suffix(module)), connection)
                            .orElse(null)
                    : version;
            if (!location.admits(resolved)) {
                continue;
            }
            Optional<RepositoryItem> item;
            if (key.equals("moduletomaven")) {
                if (!location.coordinate()) {
                    throw new IllegalArgumentException(location.key() + " in " + location.source() + " names "
                            + location.target() + ", where it expects"
                            + " <groupId>:<artifactId>[:<extension>[:<classifier>]] - a location belongs in module");
                }
                String text = location.expand(Map.of("module", module, "-suffix", location.suffix(module)))
                        .orElseThrow();
                String[] elements = text.split(":", -1);
                if (elements.length < 2 || elements.length > 4 || Arrays.stream(elements).anyMatch(String::isEmpty)) {
                    throw new IllegalArgumentException(location.key() + " in " + location.source() + " maps " + module
                            + " to " + text + ", where it expects <groupId>:<artifactId>[:<extension>[:<classifier>]]");
                }
                MavenDependencyKey coordinate = new MavenDependencyKey(elements[0],
                        elements[1],
                        elements.length > 2 ? elements[2] : "jar",
                        elements.length > 3 ? elements[3] : null);
                item = coordinates.computeIfAbsent(Map.entry(module, coordinate), _ -> new MavenModuleRepository(maven)
                        .mapping(Map.of(module, coordinate))).fetch(executor, module, classifier, resolved, type);
            } else if (location.coordinate()) {
                throw new IllegalArgumentException(location.key() + " in " + location.source() + " names "
                        + location.target() + ", where it expects a location naming :// - a Maven coordinate"
                        + " belongs in moduletomaven");
            } else if (!location.template()) {
                URI root = location.root(connection).resolve(scope == Scope.MODULE ? "module/" : "artifact/");
                item = repositories.computeIfAbsent(root, _ -> new JenesisModuleRepository(root)
                        .connection(connection)
                        .printing(printing, palette)).fetch(executor, module, classifier, resolved, type);
            } else {
                Map<String, String> values = new HashMap<>();
                values.put("module", module);
                values.put("-suffix", location.suffix(module));
                values.put("version", resolved);
                values.put("-classifier", classifier == null ? "" : "-" + classifier);
                values.put("type", type);
                item = location.fetch(values, type.indexOf('.') < 0, connection, printing, palette);
            }
            if (item.isPresent()) {
                return classifier == null && type.equals("jar")
                        ? Optional.of(declaring(item.get(), module, location))
                        : item;
            }
        }
        return Optional.empty();
    }

    private static RepositoryItem declaring(RepositoryItem item, String module, DiscoveredLocation location)
            throws IOException {
        byte[] bytes;
        try (InputStream inputStream = item.toInputStream()) {
            bytes = inputStream.readAllBytes();
        }
        String automatic = null, named = null, versioned = null;
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(bytes))) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                automatic = manifest.getMainAttributes().getValue("Automatic-Module-Name");
            }
            for (JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (entry.getName().equals("module-info.class")) {
                    named = ModuleDescriptor.read(jar).name();
                } else if (versioned == null && VERSIONED.matcher(entry.getName()).matches()) {
                    versioned = ModuleDescriptor.read(jar).name();
                }
            }
        }
        String declared = named != null ? named : versioned != null ? versioned : automatic;
        if (!module.equals(declared)) {
            throw new IllegalArgumentException(location.key() + " in " + location.source() + " answers " + module
                    + " with a jar that " + (declared == null ? "declares no module name" : "declares " + declared)
                    + ", where it must declare " + module + " in its module-info or as its Automatic-Module-Name");
        }
        return () -> new ByteArrayInputStream(bytes);
    }
}
