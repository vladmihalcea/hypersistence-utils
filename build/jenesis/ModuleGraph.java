package build.jenesis;

import module java.base;

public class ModuleGraph {

    public static final String JAVA_OPTIONS = "javaOptions";
    private static final String SELF_CONTAINED = "selfContainedModuleGraph";
    private static final String ADD_MODULES = "--add-modules",
            ROOTS = "ALL-MODULE-PATH,ALL-DEFAULT",
            ENABLE_NATIVE_ACCESS = "--enable-native-access",
            ALL_UNNAMED = "ALL-UNNAMED",
            LAYER_NATIVE_ACCESS = "-Djlayer.enableNativeAccess.",
            ENABLE_PREVIEW = "--enable-preview";
    private static final Set<String> SYSTEM_MODULES = ModuleFinder.ofSystem().findAll().stream()
            .map(reference -> reference.descriptor().name())
            .collect(Collectors.toUnmodifiableSet());

    private final SequencedSet<String> nativeAccess = new LinkedHashSet<>();
    private final SequencedMap<String, SequencedSet<String>> layerAccess = new TreeMap<>();
    private boolean modular, automatic, unnamed, preview;

    public static boolean isSystemModule(String name) {
        return SYSTEM_MODULES.contains(name);
    }

    public void place(PathPlacement placement, Path file, List<String> modulePath, List<String> classPath)
            throws IOException {
        (place(placement, file) ? modulePath : classPath).add(file.toString());
    }

    public boolean place(PathPlacement placement, Path file) throws IOException {
        if (placement.test(file)) {
            module(file);
            return true;
        }
        preview |= PathPlacement.preview(file) != null;
        unnamed();
        return false;
    }

    public void module(Path file) throws IOException {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        modular = true;
        automatic |= descriptor == null ? !Files.isDirectory(file) : descriptor.isAutomatic();
        preview |= PathPlacement.preview(file) != null;
    }

    public void unnamed() {
        unnamed = true;
    }

    public void enablePreview() {
        preview = true;
    }

    public void enableNativeAccess(String module) {
        nativeAccess.add(module == null ? ALL_UNNAMED : module);
    }

    public void enableNativeAccess(Path file, boolean module) {
        nativeAccess.add(module ? name(file) : ALL_UNNAMED);
    }

    public void enableNativeAccess(String layer, Path file, boolean module) {
        if (module) {
            layerAccess.computeIfAbsent(layer, _ -> new LinkedHashSet<>()).add(name(file));
        } else {
            nativeAccess.add(ALL_UNNAMED);
        }
    }

    private static String name(Path file) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        if (descriptor == null) {
            Set<ModuleReference> references = ModuleFinder.of(file).findAll();
            if (references.size() != 1) {
                throw new IllegalStateException("Cannot grant native access to "
                        + file
                        + ", which is placed on the module path but describes no module");
            }
            descriptor = references.iterator().next().descriptor();
        }
        return descriptor.name();
    }

    private List<String> enablingOptions() {
        List<String> options = new ArrayList<>();
        if (preview) {
            options.add(ENABLE_PREVIEW);
        }
        if (!nativeAccess.isEmpty()) {
            options.add(ENABLE_NATIVE_ACCESS + "=" + String.join(",", nativeAccess));
        }
        layerAccess.forEach((layer, modules) -> options.add(LAYER_NATIVE_ACCESS + layer + "=" + String.join(",", modules)));
        return options;
    }

    private boolean selfContained() {
        return !modular || !automatic && !unnamed;
    }

    public List<String> arguments() {
        List<String> arguments = new ArrayList<>();
        if (!selfContained()) {
            arguments.add(ADD_MODULES);
            arguments.add(ROOTS);
        }
        arguments.addAll(enablingOptions());
        return arguments;
    }

    public List<String> options() {
        List<String> options = new ArrayList<>();
        if (!selfContained()) {
            options.add(ADD_MODULES + "=" + ROOTS);
        }
        options.addAll(enablingOptions());
        return options;
    }

    public void store(SequencedProperties properties) {
        List<String> options = options();
        if (!options.isEmpty()) {
            properties.setProperty(JAVA_OPTIONS, String.join(" ", options));
        }
    }

    public static List<String> load(Properties properties) {
        String options = properties.getProperty(JAVA_OPTIONS);
        if (options != null) {
            return Arrays.stream(options.split(" ")).filter(option -> !option.isEmpty()).toList();
        }
        String legacy = properties.getProperty(SELF_CONTAINED);
        return legacy == null || Boolean.parseBoolean(legacy)
                ? List.of()
                : List.of("--add-modules=ALL-MODULE-PATH");
    }
}
