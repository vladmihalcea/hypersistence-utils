package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Abi;
import build.jenesis.step.Assign;
import build.jenesis.step.Classifiers;

public class MultiProjectDependencies implements BuildStep {

    private final Predicate<String> isModule;

    public <P extends Predicate<String> & Serializable> MultiProjectDependencies(P isModule) {
        this.isModule = isModule;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(REQUIRES),
                Path.of(VERSIONS),
                Path.of(ALIASES),
                Path.of(BOMS),
                Path.of(SIGNATURES),
                Path.of(EXCLUSIONS),
                Path.of(OVERRIDES),
                Path.of(IDENTITY),
                Path.of(Abi.ABI),
                Path.of(Abi.ABIS),
                Path.of(Assign.VARIANTS),
                Path.of(Assign.INACTIVE),
                Path.of(ARTIFACTS)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Path> coordinates = new LinkedHashMap<>(), abis = new TreeMap<>();
        SequencedMap<String, String> dependencies = new LinkedHashMap<>(), variants = new TreeMap<>(), inactive = new TreeMap<>();
        String path = null, tested = null;
        SequencedMap<String, SequencedMap<String, String>> inherited = new LinkedHashMap<>();
        for (String name : List.of(VERSIONS, ALIASES, BOMS, SIGNATURES, EXCLUSIONS, OVERRIDES)) {
            inherited.put(name, new LinkedHashMap<>());
        }
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            if (isModule.test(entry.getKey())) {
                Path moduleFile = entry.getValue().folder().resolve(MODULE);
                if (path == null && Files.isRegularFile(moduleFile)) {
                    SequencedProperties module = SequencedProperties.ofFiles(moduleFile);
                    path = module.value("path");
                    tested = module.value("test");
                }
                Path requiresPath = entry.getValue().folder().resolve(REQUIRES);
                if (Files.exists(requiresPath)) {
                    SequencedProperties.ofFiles(requiresPath).forEachProperty(dependencies::put);
                }
                for (Map.Entry<String, SequencedMap<String, String>> merged : inherited.entrySet()) {
                    Path file = entry.getValue().folder().resolve(merged.getKey());
                    if (Files.exists(file)) {
                        if (merged.getKey().equals(SIGNATURES)) {
                            SequencedProperties.ofFiles(file).forEachProperty((key, value) -> merged.getValue()
                                    .merge(key, value, (first, second) -> Stream
                                            .concat(Stream.of(first.split(" ")), Stream.of(second.split(" ")))
                                            .distinct()
                                            .sorted()
                                            .collect(Collectors.joining(" "))));
                        } else {
                            SequencedProperties.ofFiles(file).forEachProperty(merged.getValue()::putIfAbsent);
                        }
                    }
                }
            } else {
                Path variantsFile = entry.getValue().folder().resolve(Assign.VARIANTS),
                        inactiveFile = entry.getValue().folder().resolve(Assign.INACTIVE);
                if (Files.isRegularFile(variantsFile)) {
                    SequencedProperties.ofFiles(variantsFile).forEachProperty(variants::putIfAbsent);
                }
                if (Files.isRegularFile(inactiveFile)) {
                    SequencedProperties.ofFiles(inactiveFile).forEachProperty(inactive::putIfAbsent);
                }
                Path file = entry.getValue().folder().resolve(IDENTITY);
                if (Files.exists(file)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(file);
                    Path folder = entry.getValue().folder();
                    for (String property : properties.stringPropertyNames()) {
                        String value = properties.getProperty(property);
                        if (!value.isEmpty()) {
                            coordinates.put(property, folder.resolve(value).normalize());
                        }
                    }
                }
                Path assigned = entry.getValue().folder().resolve(Abi.ABIS);
                if (Files.exists(assigned)) {
                    Path folder = entry.getValue().folder();
                    SequencedProperties.ofFiles(assigned).forEachProperty((coordinate, value) ->
                            abis.put(coordinate, folder.resolve(value).normalize()));
                }
            }
        }
        SequencedProperties properties = new SequencedProperties();
        Set<Path> required = new HashSet<>();
        SequencedMap<String, String> versions = inherited.get(VERSIONS);
        for (Map.Entry<String, String> entry : dependencies.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            String coordinate = key.substring(second + 1), main = null, classifier = null;
            String pinned = first < 0 ? "" : versions.getOrDefault(key.substring(0, first) + "/" + coordinate, "");
            if (pinned.startsWith(":") && coordinates.containsKey(coordinate)) {
                main = coordinate;
                classifier = pinned.substring(1);
                if (!Classifiers.NAME.matcher(classifier).matches()) {
                    throw new IllegalArgumentException((path == null ? "A module" : path) + " pins "
                            + coordinate.substring(coordinate.indexOf('/') + 1) + " to " + pinned + ", but this build"
                            + " builds that module and resolves it as it is built - select its variant by the classifier"
                            + " alone, as @jenesis.pin " + coordinate.substring(coordinate.indexOf('/') + 1)
                            + " :<classifier>");
                }
                coordinate = Assign.classified(coordinate, classifier);
            } else if (!coordinates.containsKey(coordinate) && coordinates.containsKey(Assign.unclassified(coordinate))) {
                main = Assign.unclassified(coordinate);
                String[] segments = coordinate.split("/");
                classifier = segments.length > 4 ? segments[4] : segments[1].substring(segments[1].indexOf('-') + 1);
            }
            Path candidate = coordinates.get(coordinate);
            if (candidate == null && main != null) {
                String profiles = inactive.get(coordinate);
                throw new IllegalArgumentException((path == null ? "A module" : path) + " asks for the classifier "
                        + classifier + " of " + main + ", which the module of this build that builds it "
                        + (profiles == null
                                ? "does not declare - declare it as " + classifier + "=<folder>[,<folder>...] in a"
                                        + " classifiers.properties of that module's configuration, or as " + classifier
                                        + "= to publish its jar as it is under that classifier"
                                : "declares only in the profile " + profiles + " - activate it with"
                                        + " -Djenesis.make.profiles=" + profiles));
            }
            properties.setProperty(key,
                    candidate != null
                            ? fingerprint(arguments.values(), candidate)
                            : entry.getValue());
            if (candidate != null) {
                required.add(candidate);
            }
        }
        Set<Path> tests = new HashSet<>();
        if (tested != null) {
            for (Map.Entry<String, Path> coordinate : coordinates.entrySet()) {
                String[] segments = coordinate.getKey().split("/");
                if (coordinate.getKey().equals("module/" + tested)
                        || segments.length == 4 && segments[0].equals("maven") && segments[2].equals(tested)) {
                    tests.add(coordinate.getValue());
                }
            }
        }
        for (Map.Entry<String, Path> abi : abis.entrySet()) {
            Path jar = coordinates.get(abi.getKey());
            if (jar != null && required.contains(jar) && !tests.contains(jar)) {
                Path target = context.next()
                        .resolve(Abi.FOLDER)
                        .resolve(BuildExecutorModule.encode(abi.getKey()) + ".properties");
                Files.createDirectories(target.getParent());
                Files.copy(abi.getValue(), target);
            }
        }
        if (!variants.isEmpty()) {
            SequencedProperties listed = new SequencedProperties();
            variants.forEach(listed::setProperty);
            listed.store(context.next().resolve(Assign.VARIANTS));
        }
        properties.store(context.next().resolve(REQUIRES));
        for (Map.Entry<String, SequencedMap<String, String>> merged : inherited.entrySet()) {
            if (!merged.getValue().isEmpty()) {
                SequencedProperties mergedProperties = new SequencedProperties();
                merged.getValue().forEach(mergedProperties::setProperty);
                mergedProperties.store(context.next().resolve(merged.getKey()));
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String fingerprint(Collection<BuildStepArgument> arguments, Path artifact) {
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            if (artifact.startsWith(argument.folder())) {
                String checksum = argument.checksum(argument.folder().relativize(artifact));
                if (checksum != null) {
                    return checksum;
                }
            }
        }
        throw new IllegalStateException("No tracked checksum for sibling artifact: " + artifact);
    }
}
