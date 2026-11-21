package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.PathPlacement;
import java.util.jar.Attributes;

public class ClassPathCompatibility implements BuildStep {

    public static final String CONFIGURATION = "classpath.properties";
    private static final String SERVICES = "META-INF/services/",
            MODULE_INFO = "module-info.class",
            ENABLE_NATIVE_ACCESS = "Enable-Native-Access";

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(CLASSES),
                Path.of(RESOURCES),
                Path.of(Versions.MANIFEST)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        ModuleDescriptor descriptor = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(CLASSES + MODULE_INFO);
            if (Files.isRegularFile(candidate)) {
                try (InputStream inputStream = Files.newInputStream(candidate)) {
                    descriptor = ModuleDescriptor.read(inputStream);
                }
                break;
            }
        }
        if (descriptor == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        for (ModuleDescriptor.Provides provides : descriptor.provides()) {
            for (BuildStepArgument argument : arguments.values()) {
                Path shipped = argument.removed()
                        ? null
                        : argument.folder().resolve(RESOURCES + SERVICES + provides.service());
                if (shipped != null && Files.exists(shipped)) {
                    throw new IllegalArgumentException("The module " + descriptor.name() + " ships " + SERVICES
                            + provides.service() + " beside its provides clause, which " + CONFIGURATION
                            + " generates from module-info - remove the file from the module's resources");
                }
            }
            Path service = context.next().resolve(RESOURCES + SERVICES + provides.service());
            Files.createDirectories(service.getParent());
            Files.writeString(service, String.join("\n", provides.providers()) + "\n");
        }
        boolean granted = false, enabled = false;
        for (BuildStepArgument argument : arguments.values()) {
            Path fragment = argument.removed() ? null : argument.folder().resolve(Versions.MANIFEST);
            if (fragment == null || !Files.isRegularFile(fragment)) {
                continue;
            }
            Attributes attributes;
            try (InputStream inputStream = Files.newInputStream(fragment)) {
                attributes = new Manifest(inputStream).getMainAttributes();
            }
            String access = attributes.getValue(PathPlacement.NATIVE_ACCESS);
            granted |= access != null && List.of(access.split(",")).contains(descriptor.name());
            enabled |= attributes.getValue(ENABLE_NATIVE_ACCESS) != null;
        }
        if (granted && !enabled) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(ENABLE_NATIVE_ACCESS, "ALL-UNNAMED");
            try (OutputStream outputStream = Files.newOutputStream(context.next().resolve(Versions.MANIFEST))) {
                manifest.write(outputStream);
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
