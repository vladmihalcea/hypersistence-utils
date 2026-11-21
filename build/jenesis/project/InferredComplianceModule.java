package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.LicenseCheck;
import build.jenesis.step.OsvDownload;
import build.jenesis.step.VulnerabilityCheck;

public class InferredComplianceModule implements BuildExecutorModule {

    private static final String LICENSE = "license", VULNERABILITY = "vulnerability";
    private static final Set<String> LICENSING_KEYS = Set.of("allowed", "denied", "unknown");
    private static final Set<String> VULNERABILITY_KEYS = Set.of("severity", "warn", "osv.endpoint");

    private final SequencedSet<Path> configuration;
    private final OsvDownload osv;
    private final Function<BuildExecutorModule, BuildExecutorModule> license;
    private final Function<BuildExecutorModule, BuildExecutorModule> vulnerability;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredComplianceModule(SequencedSet<Path> configuration) {
        this(configuration,
                new OsvDownload(),
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredComplianceModule ofEnvironment(Environment environment,
                                                         SequencedSet<Path> configuration) {
        InferredComplianceModule module = new InferredComplianceModule(configuration,
                OsvDownload.ofEnvironment(environment),
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
        Boolean license = environment.flagOrNull("compliance.license");
        if (license != null) {
            module = module.license(license ? value -> value : null);
        }
        Boolean vulnerability = environment.flagOrNull("compliance.vulnerability");
        if (vulnerability != null) {
            module = module.vulnerability(vulnerability ? value -> value : null);
        }
        return module;
    }

    private InferredComplianceModule(SequencedSet<Path> configuration,
                                     OsvDownload osv,
                                     Function<BuildExecutorModule, BuildExecutorModule> license,
                                     Function<BuildExecutorModule, BuildExecutorModule> vulnerability,
                                     SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.osv = osv;
        this.license = license;
        this.vulnerability = vulnerability;
        this.custom = custom;
    }

    public InferredComplianceModule license(Function<BuildExecutorModule, BuildExecutorModule> license) {
        return new InferredComplianceModule(configuration,
                osv,
                license,
                vulnerability,
                custom);
    }

    public InferredComplianceModule vulnerability(Function<BuildExecutorModule, BuildExecutorModule> vulnerability) {
        return new InferredComplianceModule(configuration,
                osv,
                license,
                vulnerability,
                custom);
    }

    public InferredComplianceModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredComplianceModule(configuration,
                osv,
                license,
                vulnerability,
                custom);
    }

    public InferredComplianceModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredComplianceModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                LICENSE,
                license,
                BuildStep.locate(configuration, "licensing.properties"),
                properties -> {
                    if (properties.stringPropertyNames().isEmpty()) {
                        return null;
                    }
                    return (nested, nestedInherited) -> nested.addStep("check",
                            licenseCheck(properties),
                            nestedInherited.sequencedKeySet().stream());
                });
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                VULNERABILITY,
                vulnerability,
                BuildStep.locate(configuration, "vulnerability.properties"),
                properties -> {
                    if (properties.stringPropertyNames().isEmpty()) {
                        return null;
                    }
                    return (nested, nestedInherited) -> {
                        nested.addStep("osv", osvDownload(properties), nestedInherited.sequencedKeySet().stream());
                        nested.addStep("check",
                                vulnerabilityCheck(properties),
                                Stream.concat(nestedInherited.sequencedKeySet().stream(), Stream.of("osv")));
                    };
                });
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    private static LicenseCheck licenseCheck(SequencedProperties properties) {
        SequencedMap<String, String> overrides = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("override.")) {
                overrides.put(key.substring("override.".length()), properties.getProperty(key));
            } else if (!LICENSING_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown licensing property: " + key);
            }
        }
        return new LicenseCheck()
                .allowed(licenses(properties.entries("allowed")))
                .denied(licenses(properties.entries("denied")))
                .unknown(unknown(properties.value("unknown")))
                .overrides(overrides);
    }

    private OsvDownload osvDownload(SequencedProperties properties) {
        String endpoint = properties.value("osv.endpoint");
        return endpoint == null ? osv : osv.endpoint(URI.create(endpoint));
    }

    private static VulnerabilityCheck vulnerabilityCheck(SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!VULNERABILITY_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown vulnerability property: " + key);
            }
        }
        return new VulnerabilityCheck()
                .failOn(severity(properties.value("severity")))
                .warn(properties.flag("warn"));
    }

    private static SequencedSet<String> licenses(List<String> entries) {
        return entries == null || entries.isEmpty() ? null : new LinkedHashSet<>(entries);
    }

    private static LicenseCheck.Unknown unknown(String value) {
        if (value == null) {
            return LicenseCheck.Unknown.FAIL;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "ignore" -> LicenseCheck.Unknown.IGNORE;
            case "warn" -> LicenseCheck.Unknown.WARN;
            default -> LicenseCheck.Unknown.FAIL;
        };
    }

    private static VulnerabilityCheck.Severity severity(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VulnerabilityCheck.Severity.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
