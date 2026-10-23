package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public class MavenRepositoryStaging implements BuildStep {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final boolean includeTests;

    public MavenRepositoryStaging() {
        this(false);
    }

    public static MavenRepositoryStaging ofEnvironment(Environment environment) {
        return new MavenRepositoryStaging(environment.flag("stage.tests"));
    }

    public MavenRepositoryStaging(boolean includeTests) {
        this.includeTests = includeTests;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Collected collected = collectModules(arguments);
        Pairings pairings = pairTests(collected.stagedByArtifactId(), collected.artifactIds(), collected.testModules());
        stageModules(context.next(), collected.stagedByArtifactId(), pairings, Inventory.attachments(arguments.values()));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private Collected collectModules(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, Module> stagedByArtifactId = new LinkedHashMap<>();
        List<Module> testModules = new ArrayList<>();
        Map<String, String> artifactIds = new HashMap<>();
        Set<String> unstaged = new HashSet<>();
        SequencedMap<String, List<Path>> supplements = Inventory.supplements(arguments.values());
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String prefix : Inventory.prefixes(inventory)) {
                Path pom = resolve(argument.folder(), inventory.getProperty(prefix + ".pom"));
                if (pom == null) {
                    continue;
                }
                Coordinates coordinates = parseCoordinates(pom);
                String moduleName = inventory.getProperty(prefix + ".module");
                if (moduleName != null && inventory.getProperty(prefix + ".test") == null) {
                    artifactIds.put(moduleName, coordinates.artifactId());
                }
                if (!inventory.flag(prefix + ".stage", true)) {
                    if (inventory.getProperty(prefix + ".test") == null) {
                        unstaged.add(coordinates.artifactId());
                    }
                    continue;
                }
                boolean abstractTest = inventory.flag(prefix + ".abstract");
                if (abstractTest && !includeTests) {
                    continue;
                }
                boolean packaged = !"pom".equals(inventory.value(prefix + ".packaging"));
                Path artifact = singleJar(Inventory.paths(inventory, argument.folder(), prefix + ".artifacts"),
                        prefix,
                        "artifacts",
                        packaged,
                        inventoryFile);
                Path sources = singleJar(Inventory.supplemented(inventory, argument.folder(), prefix + ".sources", supplements),
                        prefix,
                        "sources",
                        false,
                        inventoryFile);
                Path javadoc = singleJar(Inventory.supplemented(inventory, argument.folder(), prefix + ".documentation", supplements),
                        prefix,
                        "documentation",
                        false,
                        inventoryFile);
                Path sbom = sbomReport(inventory, argument.folder(), prefix);
                String testsOf = inventory.getProperty(prefix + ".test");
                Module module = new Module(prefix, coordinates, artifact, sources, javadoc, pom, testsOf, sbom);
                if (testsOf == null || abstractTest) {
                    Module previous = stagedByArtifactId.putIfAbsent(coordinates.artifactId(), module);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate staged artifactId '"
                                + coordinates.artifactId()
                                + "' declared by inventories '"
                                + previous.prefix()
                                + "' ("
                                + previous.coordinates().groupId()
                                + ":"
                                + previous.coordinates().artifactId()
                                + ":"
                                + previous.coordinates().version()
                                + ") and '"
                                + prefix
                                + "' ("
                                + coordinates.groupId()
                                + ":"
                                + coordinates.artifactId()
                                + ":"
                                + coordinates.version()
                                + ")");
                    }
                } else if (includeTests) {
                    testModules.add(module);
                }
            }
        }
        testModules.removeIf(test -> unstaged.contains(artifactIds.getOrDefault(test.testsOf(), test.testsOf())));
        return new Collected(stagedByArtifactId, artifactIds, testModules);
    }

    private static Pairings pairTests(SequencedMap<String, Module> stagedByArtifactId,
                                      Map<String, String> artifactIds,
                                      List<Module> testModules) throws IOException {
        SequencedMap<String, Module> mainsByArtifactId = new LinkedHashMap<>();
        stagedByArtifactId.forEach((artifactId, module) -> {
            if (module.testsOf() == null) {
                mainsByArtifactId.put(artifactId, module);
            }
        });
        SequencedMap<String, Module> testByMain = new LinkedHashMap<>();
        SequencedMap<String, List<DependencyEntry>> testDepsByMain = new LinkedHashMap<>();
        for (Module test : testModules) {
            Module main;
            if (test.testsOf().isEmpty()) {
                if (mainsByArtifactId.isEmpty()) {
                    throw new IllegalStateException("Test module '"
                            + test.prefix()
                            + "' does not name the main module it tests (bare @jenesis.test) "
                            + "but no main module is present to attach it to");
                }
                if (mainsByArtifactId.size() > 1) {
                    throw new IllegalStateException("Test module '"
                            + test.prefix()
                            + "' does not name the main module it tests (bare @jenesis.test) "
                            + "but multiple main modules are present; "
                            + "specify an explicit @jenesis.test <artifactId> (known mains: "
                            + mainsByArtifactId.keySet()
                            + ")");
                }
                main = mainsByArtifactId.values().iterator().next();
            } else {
                main = stagedByArtifactId.get(artifactIds.getOrDefault(test.testsOf(), test.testsOf()));
                if (main == null) {
                    throw new IllegalStateException("Test module '"
                            + test.prefix()
                            + "' references unknown module '"
                            + test.testsOf()
                            + "' (known modules: "
                            + stagedByArtifactId.keySet()
                            + ")");
                }
            }
            Module previous = testByMain.putIfAbsent(main.coordinates().artifactId(), test);
            if (previous != null) {
                throw new IllegalStateException("Multiple test modules name '"
                        + main.coordinates().artifactId()
                        + "' as the module they test (would collide on the '-tests' classifier): "
                        + List.of(previous.prefix(), test.prefix()));
            }
            if (test.pom() != null) {
                Set<String> excluded = new HashSet<>(mainsByArtifactId.keySet());
                excluded.add(main.coordinates().artifactId());
                collectDependencies(test.pom(),
                        excluded,
                        testDepsByMain.computeIfAbsent(main.coordinates().artifactId(), _ -> new ArrayList<>()));
            }
        }
        return new Pairings(testByMain, testDepsByMain);
    }

    private static void stageModules(Path target,
                                     SequencedMap<String, Module> stagedByArtifactId,
                                     Pairings pairings,
                                     SequencedMap<String, SequencedMap<String, Path>> attachments) throws IOException {
        for (Module main : stagedByArtifactId.values()) {
            Coordinates coordinates = main.coordinates();
            SAFE_SEGMENT.accept("groupId", coordinates.groupId());
            SAFE_SEGMENT.accept("artifactId", coordinates.artifactId());
            SAFE_SEGMENT.accept("version", coordinates.version());
            Path baseDir = target
                    .resolve(coordinates.groupId().replace('.', '/'))
                    .resolve(coordinates.artifactId())
                    .resolve(coordinates.version());
            if (!baseDir.normalize().startsWith(target.normalize())) {
                throw new IllegalStateException("Resolved path escapes the staging root: " + baseDir);
            }
            Files.createDirectories(baseDir);
            String prefix = coordinates.artifactId() + "-" + coordinates.version();
            link(main.artifact(), baseDir.resolve(prefix + ".jar"));
            link(main.sources(), baseDir.resolve(prefix + "-sources.jar"));
            link(main.javadoc(), baseDir.resolve(prefix + "-javadoc.jar"));
            Path stagedPom = baseDir.resolve(prefix + ".pom");
            if (!Files.exists(stagedPom)) {
                List<DependencyEntry> deps = pairings.testDepsByMain().getOrDefault(coordinates.artifactId(), List.of());
                if (deps.isEmpty()) {
                    BuildStep.linkOrCopy(stagedPom, main.pom());
                } else {
                    writeMergedPom(main.pom(), deps, stagedPom);
                }
            }
            if (main.sbom() != null) {
                String name = main.sbom().getFileName().toString();
                int dot = name.lastIndexOf('.');
                link(main.sbom(), baseDir.resolve(prefix + "-cyclonedx" + (dot < 0 ? "" : name.substring(dot))));
            }
            Module test = pairings.testByMain().get(coordinates.artifactId());
            if (test != null) {
                link(test.artifact(), baseDir.resolve(prefix + "-tests.jar"));
                link(test.sources(), baseDir.resolve(prefix + "-tests-sources.jar"));
                link(test.javadoc(), baseDir.resolve(prefix + "-tests-javadoc.jar"));
            }
            for (Map.Entry<String, Path> attachment : attachments.getOrDefault(main.prefix(), Collections.emptyNavigableMap()).entrySet()) {
                SAFE_SEGMENT.accept("classifier", attachment.getKey());
                String name = attachment.getValue().getFileName().toString();
                Path staged = baseDir.resolve(prefix + "-" + attachment.getKey()
                        + (name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.'))));
                if (Files.exists(staged)) {
                    throw new IllegalArgumentException("Cannot attach " + attachment.getValue() + " to " + main.prefix()
                            + " as " + attachment.getKey() + " - " + staged.getFileName() + " is staged already, so give"
                            + " the attachment another classifier or switch off what stages that file");
                }
                BuildStep.linkOrCopy(staged, attachment.getValue());
            }
        }
    }

    private record Collected(SequencedMap<String, Module> stagedByArtifactId,
                             Map<String, String> artifactIds,
                             List<Module> testModules) {
    }

    private record Pairings(SequencedMap<String, Module> testByMain,
                            SequencedMap<String, List<DependencyEntry>> testDepsByMain) {
    }

    private record Module(String prefix,
                          Coordinates coordinates,
                          Path artifact,
                          Path sources,
                          Path javadoc,
                          Path pom,
                          String testsOf,
                          Path sbom) {
    }

    private record Coordinates(String groupId, String artifactId, String version) {
    }

    private record DependencyEntry(String groupId, String artifactId, String version) {
    }

    private static Path resolve(Path base, String relative) {
        if (relative == null) {
            return null;
        }
        Path resolved = base.resolve(relative).normalize();
        return Files.isRegularFile(resolved) ? resolved : null;
    }

    private static Path sbomReport(SequencedProperties inventory, Path base, String prefix) throws IOException {
        String value = inventory.getProperty(prefix + ".report.sbom");
        if (value == null) {
            return null;
        }
        Path folder = base.resolve(value).normalize();
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    return file;
                }
            }
        }
        return null;
    }

    private static Path singleJar(List<Path> entries,
                                  String prefix,
                                  String kind,
                                  boolean required,
                                  Path inventoryFile) {
        if (entries.isEmpty()) {
            if (required) {
                throw new IllegalStateException("Missing '"
                        + prefix
                        + "."
                        + kind
                        + "' in inventory: "
                        + inventoryFile);
            }
            return null;
        }
        List<Path> jars = new ArrayList<>();
        for (Path candidate : entries) {
            if (candidate.getFileName().toString().endsWith(".jar") && Files.isRegularFile(candidate)) {
                jars.add(candidate);
            }
        }
        if (jars.isEmpty()) {
            if (required) {
                throw new IllegalStateException("No '.jar' file listed in '"
                        + prefix
                        + "."
                        + kind
                        + "' ("
                        + entries
                        + ") in inventory: "
                        + inventoryFile);
            }
            return null;
        }
        if (jars.size() > 1) {
            throw new IllegalStateException((required ? "Expected exactly one '.jar' in '" : "Expected at most one '.jar' in '")
                    + prefix
                    + "."
                    + kind
                    + "', got "
                    + jars.size()
                    + " ("
                    + jars
                    + ") in inventory: "
                    + inventoryFile);
        }
        return jars.getFirst();
    }

    private static void link(Path source, Path target) throws IOException {
        if (source != null && !Files.exists(target)) {
            BuildStep.linkOrCopy(target, source);
        }
    }

    private static Coordinates parseCoordinates(Path pom) throws IOException {
        Document document;
        try (InputStream in = Files.newInputStream(pom)) {
            document = parse(in);
        }
        Element project = document.getDocumentElement();
        String groupId = childText(project, "groupId");
        String artifactId = childText(project, "artifactId");
        String version = childText(project, "version");
        if (groupId == null || artifactId == null || version == null) {
            throw new IllegalStateException("Missing maven coordinates in pom "
                    + pom
                    + " (groupId="
                    + groupId
                    + ", artifactId="
                    + artifactId
                    + ", version="
                    + version
                    + ")");
        }
        return new Coordinates(groupId, artifactId, version);
    }

    private static void collectDependencies(Path pom,
                                            Set<String> excludeArtifactIds,
                                            List<DependencyEntry> sink) throws IOException {
        try (InputStream in = Files.newInputStream(pom)) {
            NodeList depNodes = parse(in).getElementsByTagNameNS("*", "dependency");
            for (int index = 0; index < depNodes.getLength(); index++) {
                Element dep = (Element) depNodes.item(index);
                String artifactId = childText(dep, "artifactId");
                if (artifactId == null || excludeArtifactIds.contains(artifactId)) {
                    continue;
                }
                sink.add(new DependencyEntry(
                        childText(dep, "groupId"),
                        artifactId,
                        childText(dep, "version")));
            }
        }
    }

    private static void writeMergedPom(Path source,
                                       List<DependencyEntry> testDeps,
                                       Path target) throws IOException {
        Document document;
        try (InputStream in = Files.newInputStream(source)) {
            document = parse(in);
        }
        Element project = document.getDocumentElement();
        String namespace = project.getNamespaceURI();
        Element dependencies = findChild(project, "dependencies");
        if (dependencies == null) {
            dependencies = namespace == null
                    ? document.createElement("dependencies")
                    : document.createElementNS(namespace, "dependencies");
            project.appendChild(dependencies);
        }
        Set<String> existing = new HashSet<>();
        NodeList existingDeps = dependencies.getChildNodes();
        for (int index = 0; index < existingDeps.getLength(); index++) {
            Node node = existingDeps.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element dep = (Element) node;
            existing.add(childText(dep, "groupId") + ":" + childText(dep, "artifactId"));
        }
        for (DependencyEntry entry : testDeps) {
            if (!existing.add(entry.groupId() + ":" + entry.artifactId())) {
                continue;
            }
            Element dependency = namespace == null
                    ? document.createElement("dependency")
                    : document.createElementNS(namespace, "dependency");
            appendChild(document, dependency, namespace, "groupId", entry.groupId());
            appendChild(document, dependency, namespace, "artifactId", entry.artifactId());
            appendChild(document, dependency, namespace, "version", entry.version());
            appendChild(document, dependency, namespace, "scope", "test");
            dependencies.appendChild(dependency);
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

    private static Document parse(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(in);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException(e);
        }
    }

    private static Element findChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
            if (localName.equals(name)) {
                return (Element) node;
            }
        }
        return null;
    }

    private static String childText(Element parent, String localName) {
        Element child = findChild(parent, localName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static void appendChild(Document document,
                                    Element parent,
                                    String namespace,
                                    String localName,
                                    String value) {
        if (value == null) {
            return;
        }
        Element child = namespace == null
                ? document.createElement(localName)
                : document.createElementNS(namespace, localName);
        child.setTextContent(value);
        parent.appendChild(child);
    }
}
