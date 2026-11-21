package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;

public class ProjectModuleDescriptor implements ProjectModule {

    private final String name;
    private final SequencedSet<Path> configuration;
    private final SequencedSet<String> dependencies;
    private final SequencedSet<String> sources;
    private final SequencedSet<String> resources;
    private final SequencedSet<String> manifests;
    private final SequencedSet<String> coordinates;
    private final SequencedSet<String> artifacts;
    private final SequencedSet<String> spdx;
    private final SequencedSet<String> content;
    private final SequencedSet<String> synthetics;
    private final boolean test;
    private final boolean source;
    private final boolean documentation;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final Path location;
    private final boolean includeResources;
    private final Path root;

    public ProjectModuleDescriptor(ProjectModule base) {
        this(base.name(),
                Collections.emptyNavigableSet(),
                immutable(base.dependencies()),
                immutable(base.sources()),
                immutable(base.resources()),
                immutable(base.manifests()),
                immutable(base.coordinates()),
                immutable(base.artifacts()),
                immutable(base.spdx()),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                true,
                false,
                false,
                null,
                PathPlacement.INFERRED,
                null,
                true,
                null);
    }

    private ProjectModuleDescriptor(String name,
                                    SequencedSet<Path> configuration,
                                    SequencedSet<String> dependencies,
                                    SequencedSet<String> sources,
                                    SequencedSet<String> resources,
                                    SequencedSet<String> manifests,
                                    SequencedSet<String> coordinates,
                                    SequencedSet<String> artifacts,
                                    SequencedSet<String> spdx,
                                    SequencedSet<String> content,
                                    SequencedSet<String> synthetics,
                                    boolean test,
                                    boolean source,
                                    boolean documentation,
                                    Pinning pinning,
                                    PathPlacement pathPlacement,
                                    Path location,
                                    boolean includeResources,
                                    Path root) {
        this.name = name;
        this.configuration = configuration;
        this.dependencies = dependencies;
        this.sources = sources;
        this.resources = resources;
        this.manifests = manifests;
        this.coordinates = coordinates;
        this.artifacts = artifacts;
        this.spdx = spdx;
        this.content = content;
        this.synthetics = synthetics;
        this.test = test;
        this.source = source;
        this.documentation = documentation;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.location = location;
        this.includeResources = includeResources;
        this.root = root;
    }

    public SequencedSet<Path> configuration() {
        return configuration;
    }

    public ProjectModuleDescriptor configuration(SequencedSet<Path> configuration) {
        return new ProjectModuleDescriptor(name,
                immutable(configuration),
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor configuration(Path... configuration) {
        return configuration(new LinkedHashSet<>(List.of(configuration)));
    }

    public ProjectModuleDescriptor toInherited() {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                prefix(sources),
                prefix(resources),
                prefix(manifests),
                prefix(coordinates),
                prefix(artifacts),
                prefix(spdx),
                prefix(content),
                prefix(synthetics),
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    @Override
    public String name() {
        return name;
    }

    public ProjectModuleDescriptor name(String name) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    @Override
    public SequencedSet<String> dependencies() {
        return dependencies;
    }

    public ProjectModuleDescriptor dependencies(SequencedSet<String> dependencies) {
        return new ProjectModuleDescriptor(name,
                configuration,
                immutable(dependencies),
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor dependencies(String... dependencies) {
        return dependencies(new LinkedHashSet<>(List.of(dependencies)));
    }

    @Override
    public SequencedSet<String> sources() {
        return sources;
    }

    public ProjectModuleDescriptor sources(SequencedSet<String> sources) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                immutable(sources),
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor sources(String... sources) {
        return sources(new LinkedHashSet<>(List.of(sources)));
    }

    @Override
    public SequencedSet<String> resources() {
        return resources;
    }

    public ProjectModuleDescriptor resources(SequencedSet<String> resources) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                immutable(resources),
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor resources(String... resources) {
        return resources(new LinkedHashSet<>(List.of(resources)));
    }

    @Override
    public SequencedSet<String> manifests() {
        return manifests;
    }

    public ProjectModuleDescriptor manifests(SequencedSet<String> manifests) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                immutable(manifests),
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor manifests(String... manifests) {
        return manifests(new LinkedHashSet<>(List.of(manifests)));
    }

    @Override
    public SequencedSet<String> coordinates() {
        return coordinates;
    }

    public ProjectModuleDescriptor coordinates(SequencedSet<String> coordinates) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                immutable(coordinates),
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor coordinates(String... coordinates) {
        return coordinates(new LinkedHashSet<>(List.of(coordinates)));
    }

    @Override
    public SequencedSet<String> artifacts() {
        return artifacts;
    }

    @Override
    public SequencedSet<String> spdx() {
        return spdx;
    }

    public ProjectModuleDescriptor artifacts(SequencedSet<String> artifacts) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                immutable(artifacts),
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor artifacts(String... artifacts) {
        return artifacts(new LinkedHashSet<>(List.of(artifacts)));
    }

    public SequencedSet<String> content() {
        return content;
    }

    public ProjectModuleDescriptor content(SequencedSet<String> content) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                immutable(content),
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor content(String... content) {
        return content(new LinkedHashSet<>(List.of(content)));
    }

    public SequencedSet<String> synthetics() {
        return synthetics;
    }

    public ProjectModuleDescriptor synthetics(SequencedSet<String> synthetics) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                immutable(synthetics),
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor synthetics(String... synthetics) {
        return synthetics(new LinkedHashSet<>(List.of(synthetics)));
    }

    public boolean test() {
        return test;
    }

    public ProjectModuleDescriptor test(boolean test) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public boolean source() {
        return source;
    }

    public ProjectModuleDescriptor source(boolean source) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public boolean documentation() {
        return documentation;
    }

    public ProjectModuleDescriptor documentation(boolean documentation) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public Pinning pinning() {
        return pinning;
    }

    public ProjectModuleDescriptor pinning(Pinning pinning) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public PathPlacement pathPlacement() {
        return pathPlacement;
    }

    public Path location() {
        return location;
    }

    public ProjectModuleDescriptor location(Path location) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public ProjectModuleDescriptor pathPlacement(PathPlacement pathPlacement) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public boolean includeResources() {
        return includeResources;
    }

    public ProjectModuleDescriptor includeResources(boolean includeResources) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    public Path root() {
        return root;
    }

    public ProjectModuleDescriptor root(Path root) {
        return new ProjectModuleDescriptor(name,
                configuration,
                dependencies,
                sources,
                resources,
                manifests,
                coordinates,
                artifacts,
                spdx,
                content,
                synthetics,
                test,
                source,
                documentation,
                pinning,
                pathPlacement,
                location,
                includeResources,
                root);
    }

    private static <T> SequencedSet<T> immutable(SequencedSet<T> values) {
        return Collections.unmodifiableSequencedSet(new LinkedHashSet<>(values));
    }

    private static SequencedSet<String> prefix(SequencedSet<String> values) {
        LinkedHashSet<String> prefixed = new LinkedHashSet<>();
        for (String value : values) {
            prefixed.add(BuildExecutorModule.PREVIOUS + value);
        }
        return Collections.unmodifiableSequencedSet(prefixed);
    }
}
