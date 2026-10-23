package build.jenesis.module;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public class ModularStaging implements BuildStep {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final boolean includeTests;

    public ModularStaging() {
        this(false);
    }

    public static ModularStaging ofEnvironment(Environment environment) {
        return new ModularStaging(environment.flag("stage.tests"));
    }

    public ModularStaging(boolean includeTests) {
        this.includeTests = includeTests;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, SequencedMap<String, Path>> attachments = Inventory.attachments(arguments.values());
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
                String testsOf = inventory.getProperty(prefix + ".test");
                if (!includeTests && testsOf != null || !inventory.flag(prefix + ".stage", true)) {
                    continue;
                }
                String moduleName = inventory.getProperty(prefix + ".module");
                if (moduleName == null) {
                    continue;
                }
                SAFE_SEGMENT.accept("module name", moduleName);
                Path artifact = single(Inventory.paths(inventory, argument.folder(), prefix + ".artifacts"),
                        prefix,
                        "artifacts",
                        true,
                        ".jar",
                        inventoryFile);
                Path sources = single(Inventory.supplemented(inventory, argument.folder(), prefix + ".sources", supplements),
                        prefix,
                        "sources",
                        false,
                        ".jar",
                        inventoryFile);
                Path javadoc = single(Inventory.supplemented(inventory, argument.folder(), prefix + ".documentation", supplements),
                        prefix,
                        "documentation",
                        false,
                        ".jar",
                        inventoryFile);
                Path jmod = single(Inventory.paths(inventory, argument.folder(), prefix + ".jmod"),
                        prefix,
                        "jmod",
                        false,
                        ".jmod",
                        inventoryFile);
                Path bom = single(Inventory.paths(inventory, argument.folder(), prefix + ".bomfile"),
                        prefix,
                        "bomfile",
                        false,
                        ".properties",
                        inventoryFile);
                String pomRelative = inventory.getProperty(prefix + ".pom");
                Path pom = pomRelative == null ? null : argument.folder().resolve(pomRelative).normalize();
                String version = inventory.getProperty(prefix + ".version");
                if (version != null) {
                    SAFE_SEGMENT.accept("version", version);
                }
                Path target = version == null
                        ? context.next().resolve(moduleName)
                        : context.next().resolve(moduleName).resolve(version);
                if (!target.normalize().startsWith(context.next().normalize())) {
                    throw new IllegalStateException("Resolved path escapes the staging root: " + target);
                }
                Files.createDirectories(target);
                link(artifact, target.resolve(moduleName + ".jar"));
                link(sources, target.resolve(moduleName + "-sources.jar"));
                link(javadoc, target.resolve(moduleName + "-javadoc.jar"));
                link(jmod, target.resolve(moduleName + ".jmod"));
                link(pom, target.resolve(moduleName + ".pom"));
                link(bom, target.resolve(moduleName + ".properties"));
                for (Map.Entry<String, Path> attachment : attachments.getOrDefault(prefix, Collections.emptyNavigableMap()).entrySet()) {
                    SAFE_SEGMENT.accept("classifier", attachment.getKey());
                    String name = attachment.getValue().getFileName().toString();
                    Path staged = target.resolve(moduleName + "-" + attachment.getKey()
                            + (name.lastIndexOf('.') < 0 ? "" : name.substring(name.lastIndexOf('.'))));
                    if (Files.exists(staged)) {
                        throw new IllegalArgumentException("Cannot attach " + attachment.getValue() + " to " + moduleName
                                + " as " + attachment.getKey() + " - " + staged.getFileName() + " is staged already, so give"
                                + " the attachment another classifier or switch off what stages that file");
                    }
                    BuildStep.linkOrCopy(staged, attachment.getValue());
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static Path single(List<Path> entries,
                               String prefix,
                               String kind,
                               boolean required,
                               String extension,
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
        List<Path> matches = new ArrayList<>();
        for (Path candidate : entries) {
            if (candidate.getFileName().toString().endsWith(extension) && Files.isRegularFile(candidate)) {
                matches.add(candidate);
            }
        }
        if (matches.isEmpty()) {
            if (required) {
                throw new IllegalStateException("No '" + extension + "' file listed in '"
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
        if (matches.size() > 1) {
            throw new IllegalStateException((required ? "Expected exactly one '" : "Expected at most one '")
                    + extension
                    + "' in '"
                    + prefix
                    + "."
                    + kind
                    + "', got "
                    + matches.size()
                    + " ("
                    + matches
                    + ") in inventory: "
                    + inventoryFile);
        }
        return matches.getFirst();
    }

    private static void link(Path source, Path target) throws IOException {
        if (source != null && !Files.exists(target)) {
            BuildStep.linkOrCopy(target, source);
        }
    }
}
