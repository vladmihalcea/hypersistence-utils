package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredSourceFormattingModule implements BuildExecutorModule {

    public static final String OPENREWRITE = "openrewrite",
            GOOGLEJAVAFORMAT = "googlejavaformat",
            PALANTIR = "palantir",
            ECLIPSE = "eclipse",
            KTFMT = "ktfmt",
            KTLINT = "ktlint",
            SCALAFMT = "scalafmt";
    private static final String LICENSE = "license",
            TEXT = "text",
            RECIPES = "recipes";

    private final SequencedSet<Path> configuration;
    private final boolean verify;
    private final LicenseHeaderModule licenseModule;
    private final Function<LicenseHeaderModule, BuildExecutorModule> license;
    private final ExternalModule openRewriteModule;
    private final boolean openrewrite;
    private final ExternalModule googleJavaFormatModule;
    private final boolean googlejavaformat;
    private final ExternalModule palantirJavaFormatModule;
    private final boolean palantir;
    private final ExternalModule eclipseJavaFormatModule;
    private final boolean eclipse;
    private final ExternalModule ktfmtModule;
    private final boolean ktfmt;
    private final ExternalModule ktlintModule;
    private final boolean ktlint;
    private final ExternalModule scalafmtModule;
    private final boolean scalafmt;
    private final SequencedSet<String> dependencies;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration,
                true,
                new LicenseHeaderModule(),
                value -> value,
                ExternalModule.official(Environment.NONE, "openrewrite", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "googlejavaformat", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "palantir", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "eclipse", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktfmt", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "ktlint", "format", repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "scalafmt", "format", repositories, resolvers),
                true,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap());
    }

    public static InferredSourceFormattingModule ofEnvironment(Environment environment,
                                                               SequencedSet<Path> configuration,
                                                               Map<String, Repository> repositories,
                                                               Map<String, Resolver> resolvers) {
        InferredSourceFormattingModule module = new InferredSourceFormattingModule(configuration,
                true,
                new LicenseHeaderModule(),
                value -> value,
                ExternalModule.official(environment, "openrewrite", null, repositories, resolvers),
                environment.flag("format.openrewrite", true),
                ExternalModule.official(environment, "googlejavaformat", null, repositories, resolvers),
                environment.flag("format.googlejavaformat", true),
                ExternalModule.official(environment, "palantir", null, repositories, resolvers),
                environment.flag("format.palantir", true),
                ExternalModule.official(environment, "eclipse", null, repositories, resolvers),
                environment.flag("format.eclipse", true),
                ExternalModule.official(environment, "ktfmt", null, repositories, resolvers),
                environment.flag("format.ktfmt", true),
                ExternalModule.official(environment, "ktlint", "format", repositories, resolvers),
                environment.flag("format.ktlint", true),
                ExternalModule.official(environment, "scalafmt", "format", repositories, resolvers),
                environment.flag("format.scalafmt", true),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap());
        Boolean rewrite = environment.flagOrNull("format.rewrite");
        if (rewrite != null) {
            module = module.verify(!rewrite);
        }
        Boolean license = environment.flagOrNull("format.license");
        if (license != null) {
            module = module.license(license ? value -> value : null);
        }
        return module;
    }

    private InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                           boolean verify,
                                           LicenseHeaderModule licenseModule,
                                           Function<LicenseHeaderModule, BuildExecutorModule> license,
                                           ExternalModule openRewriteModule,
                                           boolean openrewrite,
                                           ExternalModule googleJavaFormatModule,
                                           boolean googlejavaformat,
                                           ExternalModule palantirJavaFormatModule,
                                           boolean palantir,
                                           ExternalModule eclipseJavaFormatModule,
                                           boolean eclipse,
                                           ExternalModule ktfmtModule,
                                           boolean ktfmt,
                                           ExternalModule ktlintModule,
                                           boolean ktlint,
                                           ExternalModule scalafmtModule,
                                           boolean scalafmt,
                                           SequencedSet<String> dependencies,
                                           SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.verify = verify;
        this.licenseModule = licenseModule;
        this.license = license;
        this.openRewriteModule = openRewriteModule;
        this.openrewrite = openrewrite;
        this.googleJavaFormatModule = googleJavaFormatModule;
        this.googlejavaformat = googlejavaformat;
        this.palantirJavaFormatModule = palantirJavaFormatModule;
        this.palantir = palantir;
        this.eclipseJavaFormatModule = eclipseJavaFormatModule;
        this.eclipse = eclipse;
        this.ktfmtModule = ktfmtModule;
        this.ktfmt = ktfmt;
        this.ktlintModule = ktlintModule;
        this.ktlint = ktlint;
        this.scalafmtModule = scalafmtModule;
        this.scalafmt = scalafmt;
        this.dependencies = dependencies;
        this.custom = custom;
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule license(Function<LicenseHeaderModule, BuildExecutorModule> license) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule openrewrite(boolean openrewrite) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule googlejavaformat(boolean googlejavaformat) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule palantir(boolean palantir) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule eclipse(boolean eclipse) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule ktfmt(boolean ktfmt) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule ktlint(boolean ktlint) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule scalafmt(boolean scalafmt) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule dependencies(SequencedSet<String> dependencies) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceFormattingModule(configuration,
                verify,
                licenseModule,
                license,
                openRewriteModule,
                openrewrite,
                googleJavaFormatModule,
                googlejavaformat,
                palantirJavaFormatModule,
                palantir,
                eclipseJavaFormatModule,
                eclipse,
                ktfmtModule,
                ktfmt,
                ktlintModule,
                ktlint,
                scalafmtModule,
                scalafmt,
                dependencies,
                custom);
    }

    public InferredSourceFormattingModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredSourceFormattingModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        SequencedSet<String> inputs = inherited.sequencedKeySet().stream()
                .filter(key -> dependencies.stream()
                        .map(name -> PREVIOUS + name)
                        .noneMatch(name -> key.equals(name) || key.startsWith(name + "/")))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (Bind.configuredByProperties(buildExecutor,
                new LinkedHashSet<>(inputs),
                LICENSE,
                license,
                LicenseHeaderModule.configurationFile(configuration),
                properties -> {
                    for (String key : properties.stringPropertyNames()) {
                        if (!key.equals(TEXT)) {
                            throw new IllegalArgumentException(LicenseHeaderModule.CONFIGURATION + " names " + key
                                    + ", but takes " + TEXT + "=<the header> alone");
                        }
                    }
                    return licenseModule.verify(verify).text(properties.value(TEXT));
                })) {
            inputs.add(LICENSE);
        }
        Path openRewriteFile = BuildStep.locate(configuration, "openrewrite.properties"),
                recipesFile = BuildStep.locate(configuration, "rewrite.yml");
        if ((openRewriteFile != null || recipesFile != null) && openrewrite) {
            ExternalModule module = openRewriteModule;
            if (openRewriteFile == null) {
                module = module.bound(ExternalModule.RULES, Path.of("rewrite.yml"), recipesFile);
            } else {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                SequencedProperties.ofFiles(openRewriteFile).forEachProperty(values::put);
                String recipes = values.remove(ExternalModule.CONFIGURATION);
                if ((recipes == null || recipes.isBlank()) && (values.get(RECIPES) == null || values.get(RECIPES).isBlank())) {
                    throw new IllegalArgumentException(openRewriteFile + " names neither " + ExternalModule.CONFIGURATION
                            + " nor " + RECIPES + " - add " + ExternalModule.CONFIGURATION + "=rewrite.yml, the file"
                            + " declaring the recipes relative to it, or " + RECIPES + "=<recipe>[,<recipe>...]");
                }
                module = module.properties(values);
                if (recipes != null && !recipes.isBlank()) {
                    module = module.bound(ExternalModule.RULES,
                            Path.of("rewrite.yml"),
                            ExternalModule.named(openRewriteFile, ExternalModule.CONFIGURATION, recipes));
                }
            }
            SequencedSet<String> rewritten = new LinkedHashSet<>(inputs);
            inherited.sequencedKeySet().stream().filter(key -> !inputs.contains(key)).forEach(rewritten::add);
            buildExecutor.addModule(OPENREWRITE, module, rewritten);
            inputs.add(OPENREWRITE);
        }
        SequencedMap<String, ExternalModule> formatters = new LinkedHashMap<>();
        Path googleJavaFormatFile = BuildStep.locate(configuration, GOOGLEJAVAFORMAT + ".properties");
        if (googleJavaFormatFile != null && googlejavaformat) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(googleJavaFormatFile).forEachProperty(values::put);
            formatters.put(GOOGLEJAVAFORMAT, googleJavaFormatModule.properties(values));
        }
        Path palantirFile = BuildStep.locate(configuration, PALANTIR + ".properties");
        if (palantirFile != null && palantir) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(palantirFile).forEachProperty(values::put);
            formatters.put(PALANTIR, palantirJavaFormatModule.properties(values));
        }
        Path eclipseFile = BuildStep.locate(configuration, ECLIPSE + ".properties"),
                profileFile = BuildStep.locate(configuration, "eclipse-formatter.xml");
        if ((eclipseFile != null || profileFile != null) && eclipse) {
            ExternalModule module = eclipseJavaFormatModule;
            if (eclipseFile == null) {
                module = module.bound(ExternalModule.RULES, Path.of("eclipse-formatter.xml"), profileFile);
            } else {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                SequencedProperties.ofFiles(eclipseFile).forEachProperty(values::put);
                String profile = values.remove(ExternalModule.CONFIGURATION);
                module = module.properties(values);
                if (profile != null && !profile.isBlank()) {
                    module = module.bound(ExternalModule.RULES,
                            Path.of("eclipse-formatter.xml"),
                            ExternalModule.named(eclipseFile, ExternalModule.CONFIGURATION, profile));
                }
            }
            formatters.put(ECLIPSE, module);
        }
        if (formatters.size() > 1) {
            throw new IllegalArgumentException("A configuration folder switches on " + formatters.sequencedKeySet()
                    + ", which would each format the same Java sources - keep the <name>.properties of one, or"
                    + " switch the others off with jenesis.format.<name>=false");
        }
        formatters.forEach((name, module) -> buildExecutor.addModule(name, module, inputs));
        Path ktfmtFile = ktfmt ? BuildStep.locate(configuration, "ktfmt.properties") : null;
        ExternalModule ktlintDiscovered = ktlint ? ktlintModule.discovered(configuration, Path.of(".editorconfig")) : null;
        if (ktfmtFile != null && ktlintDiscovered != null) {
            throw new IllegalArgumentException(ktfmtFile + " selects ktfmt while ktlint formats the same Kotlin sources,"
                    + " as a configuration folder holds a ktlint.properties or an .editorconfig - set"
                    + " jenesis.format.ktlint=false to format with ktfmt alone, or remove ktfmt.properties");
        }
        if (ktfmtFile != null) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(ktfmtFile).forEachProperty(values::put);
            buildExecutor.addModule(KTFMT, ktfmtModule.properties(values), inputs);
        }
        if (ktlintDiscovered != null) {
            buildExecutor.addModule(KTLINT, ktlintDiscovered, inputs);
        }
        ExternalModule scalafmtDiscovered = scalafmt ? scalafmtModule.discovered(configuration, Path.of(".scalafmt.conf")) : null;
        if (scalafmtDiscovered != null) {
            buildExecutor.addModule(SCALAFMT, scalafmtDiscovered, inputs);
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inputs);
        }
    }
}
